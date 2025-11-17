/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, ElementRef, HostListener, NgZone, OnDestroy, OnInit, ViewChild,} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-sound-settings',
  templateUrl: './sound-settings.component.html',
  styleUrls: ['./sound-settings.component.scss'],
  standalone: true,
  imports: [
    FormsModule, CommonModule
  ],
  // If you use animations in your project, import BrowserAnimationsModule globally.
})
export class SoundSettingsComponent implements OnInit, OnDestroy {
  // connection/prefs
  isConnected = true;
  enableSound = true;
  enableVibration = true;

  // UI
  showSoundPopover = false;
  isPulsing = false;
  isShaking = false;
  showVisualizer = false;

  // visual + floating notes
  floatingNotes: { id: number; x: number; y: number }[] = [];

  // visualizer data
  readonly BAR_COUNT = 20;
  barHeights: number[] = Array(this.BAR_COUNT).fill(6);
  microWavePath = '';
  barGradient = `linear-gradient(180deg, rgba(96,165,250,1), rgba(37,99,235,1))`;

  // audio nodes
  private audioCtx: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private oscillator: OscillatorNode | null = null;
  private gainNode: GainNode | null = null;
  private rafId: number | null = null;
  private dataArray: Uint8Array | null = null;

  @ViewChild('barsWrap', {static: false}) barsWrap?: ElementRef<HTMLDivElement>;
  @ViewChild('soundBtn', {static: false}) soundBtn?: ElementRef<HTMLButtonElement>;
  @ViewChild('popover', {static: false}) popover?: ElementRef<HTMLDivElement>;

  constructor(private ngZone: NgZone, private hostRef: ElementRef) {
  }

  ngOnInit(): void {
    // load prefs
    try {
      const s = localStorage.getItem('smartchat_sound_prefs');
      if (s) {
        const p = JSON.parse(s);
        this.enableSound = !!p.sound;
        this.enableVibration = !!p.vibration;
      }
    } catch {
    }
    this.microWavePath = this.generateMicroWavePath(100, 20, 6);
  }

  ngOnDestroy(): void {
    this.stopVisualizer();
    this.cleanupAudio();
    this.saveSoundPreferences();
  }

  saveSoundPreferences(): void {
    localStorage.setItem('smartchat_sound_prefs', JSON.stringify({
      sound: this.enableSound,
      vibration: this.enableVibration
    }));
  }

  /* ---------------------------
   * Popover controls
   * --------------------------- */
  toggleSoundPopover(evt?: MouseEvent | TouchEvent | Event): void {
    if (evt && (evt as Event).stopPropagation) (evt as Event).stopPropagation();
    this.showSoundPopover = !this.showSoundPopover;
    if (this.showSoundPopover) {
      // request focus after render
      setTimeout(() => this.popover?.nativeElement?.focus?.(), 40);
    } else {
      this.stopVisualizer();
    }
  }

  closePopover(): void {
    this.showSoundPopover = false;
    this.stopVisualizer();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(evt: Event): void {
    // close when clicking outside the component
    const path = (evt as any).path || (evt.composedPath && evt.composedPath());
    const isInside = path ? path.includes(this.hostRef.nativeElement) : this.hostRef.nativeElement.contains(evt.target as Node);
    if (!isInside) this.closePopover();
  }

  /* ---------------------------
   * Test sound + visualizer
   * --------------------------- */
  async playTestTone(evt?: Event, opts: { short?: boolean } = {}): Promise<void> {
    if (!this.enableSound) {
      this.showToast('Sound disabled', 'info');
      return;
    }
    if (this.isPulsing) return;
    this.isPulsing = true;
    this.spawnNoteFromEvent(evt);

    try {
      if (!this.audioCtx) {
        this.audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      }

      // cleanup prior nodes but keep audioCtx open for reuse
      this.cleanupAudio();

      this.oscillator = this.audioCtx.createOscillator();
      this.gainNode = this.audioCtx.createGain();
      this.analyser = this.audioCtx.createAnalyser();

      this.analyser.fftSize = 256;
      this.analyser.smoothingTimeConstant = 0.6;
      this.dataArray = new Uint8Array(this.analyser.frequencyBinCount);

      const freq = opts.short ? 1320 : 880;
      this.oscillator.type = 'sine';
      this.oscillator.frequency.setValueAtTime(freq, this.audioCtx.currentTime);

      const baseVol = this.getBaseVolume();
      this.gainNode.gain.setValueAtTime(baseVol, this.audioCtx.currentTime);

      this.oscillator.connect(this.gainNode);
      this.gainNode.connect(this.analyser);
      this.analyser.connect(this.audioCtx.destination);

      this.oscillator.start();

      const duration = opts.short ? 0.16 : 0.36;
      this.gainNode.gain.exponentialRampToValueAtTime(0.001, this.audioCtx.currentTime + duration);
      this.oscillator.stop(this.audioCtx.currentTime + duration + 0.02);

      this.showVisualizer = true;
      this.ngZone.runOutsideAngular(() => this.startVisualizer());

      this.triggerVibration(40);

      setTimeout(() => {
        this.isPulsing = false;
        setTimeout(() => this.stopVisualizer(), 220);
      }, (duration + 0.15) * 1000);
    } catch (err) {
      console.warn('Audio play error, simulate visual', err);
      this.simulateVisualizerBurst();
      this.isPulsing = false;
    }
  }

  private cleanupAudio(): void {
    try {
      if (this.oscillator) {
        try {
          this.oscillator.disconnect();
        } catch {
        }
        this.oscillator = null;
      }
      if (this.gainNode) {
        try {
          this.gainNode.disconnect();
        } catch {
        }
        this.gainNode = null;
      }
      if (this.analyser) {
        try {
          this.analyser.disconnect();
        } catch {
        }
        this.analyser = null;
      }
      // keep audioCtx open for subsequent user gestures (closing can require another gesture)
    } catch {
    }
  }

  /* Visualizer loop */
  private startVisualizer(): void {
    if (!this.analyser || !this.dataArray) {
      this.simulateVisualizerBurst();
      return;
    }

    const draw = () => {
      if (!this.analyser || !this.dataArray) return;
      this.analyser.getByteFrequencyData(this.dataArray);

      const bins = this.dataArray.length;
      const step = Math.max(1, Math.floor(bins / this.BAR_COUNT));
      for (let i = 0; i < this.BAR_COUNT; i++) {
        let sum = 0;
        const start = i * step;
        for (let j = 0; j < step && start + j < bins; j++) sum += this.dataArray[start + j];
        const avg = sum / step / 255;
        const height = 6 + Math.round(avg * 42);
        this.barHeights[i] = Math.round(this.barHeights[i] * 0.6 + height * 0.4);
      }

      this.microWavePath = this.generateMicroWavePathFromBars(this.barHeights, 100, 20);

      this.rafId = requestAnimationFrame(draw);
    };

    if (this.rafId) cancelAnimationFrame(this.rafId);
    this.rafId = requestAnimationFrame(draw);
  }

  private stopVisualizer(): void {
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    this.showVisualizer = false;

    // gentle decay to resting bars
    for (let i = 0; i < this.BAR_COUNT; i++) {
      const current = this.barHeights[i];
      const target = 6;
      const steps = 8;
      for (let s = 1; s <= steps; s++) {
        setTimeout(() => {
          this.barHeights[i] = Math.round(current - ((current - target) * (s / steps)));
        }, s * 25);
      }
    }
    setTimeout(() => this.microWavePath = this.generateMicroWavePath(100, 20, 6), 220);
  }

  private simulateVisualizerBurst(): void {
    const peakHeights = this.barHeights.map(() => 6 + Math.floor(Math.random() * 40));
    let step = 0;
    const maxSteps = 18;
    const interval = setInterval(() => {
      for (let i = 0; i < this.BAR_COUNT; i++) {
        const decay = Math.max(6, Math.round(peakHeights[i] * (1 - step / maxSteps)));
        this.barHeights[i] = Math.round(this.barHeights[i] * 0.5 + decay * 0.5);
      }
      this.microWavePath = this.generateMicroWavePathFromBars(this.barHeights, 100, 20);
      step++;
      if (step > maxSteps) {
        clearInterval(interval);
        this.stopVisualizer();
      }
    }, 50);
  }

  /* Helpers - micro wave path generation */
  private generateMicroWavePath(width = 100, height = 20, intensity = 6): string {
    const points = 20;
    const step = width / (points - 1);
    let d = `M 0 ${height / 2}`;
    for (let i = 0; i < points; i++) {
      const x = i * step;
      const y = height / 2 + Math.sin((i / points) * Math.PI * 2) * intensity * (Math.random() * 0.5 + 0.7);
      d += ` L ${x.toFixed(2)} ${y.toFixed(2)}`;
    }
    return d;
  }

  private generateMicroWavePathFromBars(bars: number[], width = 100, height = 20): string {
    const p = bars.length;
    const step = width / (p - 1 || 1);
    let d = `M 0 ${height / 2}`;
    for (let i = 0; i < p; i++) {
      const x = i * step;
      const normalized = (bars[i] - 6) / 42;
      const y = height / 2 - (normalized * (height / 2 - 2));
      d += ` L ${x.toFixed(2)} ${y.toFixed(2)}`;
    }
    return d;
  }

  /* Floating note - compute x from event or center of button */
  private spawnNoteFromEvent(evt?: Event | null): void {
    try {
      const id = Date.now();
      let x = 20;
      if (evt && this.soundBtn?.nativeElement) {
        try {
          const btnRect = this.soundBtn.nativeElement.getBoundingClientRect();
          x = Math.max(8, Math.min(btnRect.left + btnRect.width / 2, window.innerWidth - 20));
        } catch {
        }
      }
      this.floatingNotes.push({id, x, y: 50});
      setTimeout(() => this.floatingNotes = this.floatingNotes.filter(n => n.id !== id), 1200);
    } catch {
    }
  }

  testVibration(evt?: Event): void {
    if (!this.enableVibration || this.isShaking) return;
    this.isShaking = true;
    this.triggerVibration(110);
    setTimeout(() => this.isShaking = false, 520);
  }

  private triggerVibration(duration = 40): void {
    try {
      if (this.enableVibration && 'vibrate' in navigator) {
        (navigator as any).vibrate(duration);
      }
    } catch {
    }
  }

  showToast(msg: string, _type: 'info' | 'success' | 'error' = 'info'): void {
    // Minimal toast; integrate with your ToastService if present
    console.log('[SoundSettings] toast:', msg);
  }

  /* Adaptive volume scaling (based on time + focus) */
  private getBaseVolume(): number {
    const h = new Date().getHours();
    let base = (h >= 22 || h < 7) ? 0.18 : 0.55;
    if (typeof document !== 'undefined' && document.hidden) base *= 0.5;
    return Math.max(0.06, Math.min(0.8, base));
  }
}
