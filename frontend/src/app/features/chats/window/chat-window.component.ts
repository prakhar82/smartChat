/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, EventEmitter, HostListener, Input, NgZone, OnDestroy, OnInit, Output,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {animate, keyframes, style, transition, trigger,} from '@angular/animations';

interface FloatingNote {
  id: number;
  x: number;
  y: number;
}


@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.scss'],
  animations: [
    trigger('fadeSlideIn', [
      transition(':enter', [
        style({opacity: 0, transform: 'translateY(10px)'}),
        animate('200ms ease-out', style({opacity: 1, transform: 'translateY(0)'})),
      ]),
      transition(':leave', [
        animate('150ms ease-in', style({opacity: 0, transform: 'translateY(5px)'})),
      ]),
    ]),
    trigger('noteFloat', [
      transition(':enter', [
        animate(
          '1.2s ease-out',
          keyframes([
            style({opacity: 0, transform: 'translateY(0) scale(0.8)', offset: 0}),
            style({opacity: 1, transform: 'translateY(-20px) scale(1)', offset: 0.3}),
            style({opacity: 0, transform: 'translateY(-60px) scale(1.4)', offset: 1}),
          ])
        ),
      ]),
    ]),
  ],
})


export class ChatWindowComponent implements OnInit, OnDestroy {
  /* =========================================================
   * FIXED TEMPLATE / IDE VARIABLES
   * ========================================================= */

  floatingNotes: FloatingNote[] = [];

  isPulsing: boolean = false;
  isShaking: boolean = false;

  trackByIndex(index: number): number {
    return index;
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }

  /* =========================================================
   * Inputs / Outputs
   * ========================================================= */

  @Input() contactId!: number | string;
  @Input() contactInfo: any;
  @Input() isConnected = true;
  @Input() isMobileView = false;
  @Input() remoteAudioStream?: MediaStream;

  @Output() back = new EventEmitter<void>();
  @Output() closeChat = new EventEmitter<void>();

  /* =========================================================
   * State
   * ========================================================= */

  enableSound = true;
  enableVibration = true;
  showSoundPopover = false;


  private audioCtx: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private dataArray: Uint8Array | null = null;
  private rafId: number | null = null;

  private micStream: MediaStream | null = null;
  private micSource: MediaStreamAudioSourceNode | null = null;
  private remoteSource: MediaStreamAudioSourceNode | null = null;

  readonly BAR_COUNT = 20;
  barHeights = Array(this.BAR_COUNT).fill(6);

  currentVU = 0;
  remoteVU = 0;
  isRemoteTalking = false;
  useMicInput = false;
  dualMode = false;

  latencyMs = 0;
  latencyHistory: number[] = [];
  latencyColor = '#22c55e';

  lastLatencyCheck = 0;

  networkStability = 100;
  networkJitter = 0;
  networkLoss = 0;
  networkColor = '#22c55e';
  private networkInterval: any;

  constructor(private ngZone: NgZone) {
  }

  /* =========================================================
   * Lifecycle
   * ========================================================= */

  ngOnInit(): void {
    this.loadSoundPreferences();
    this.startNetworkMonitor();
  }

  ngOnDestroy(): void {
    this.saveSoundPreferences();
    this.stopVisualizer();
    this.stopMicInput();
    this.detachRemoteStream();
    this.cleanupAudio();
    clearInterval(this.networkInterval);
  }

  /* =========================================================
   * Preferences
   * ========================================================= */

  private loadSoundPreferences(): void {
    try {
      const stored = localStorage.getItem('smartchat_sound_prefs');
      if (stored) {
        const prefs = JSON.parse(stored);
        this.enableSound = prefs.sound ?? true;
        this.enableVibration = prefs.vibration ?? true;
      }
    } catch {
      /* Ignore */
    }
  }

  saveSoundPreferences(): void {
    localStorage.setItem(
      'smartchat_sound_prefs',
      JSON.stringify({sound: this.enableSound, vibration: this.enableVibration})
    );
  }

  /* =========================================================
   * Sound / UI
   * ========================================================= */

  toggleSoundPopover(event: MouseEvent): void {
    event.stopPropagation();
    this.showSoundPopover = !this.showSoundPopover;
  }

  @HostListener('document:click')
  onOutsideClick(): void {
    this.showSoundPopover = false;
  }

  playTestTone(event: Event): void {
    event.stopPropagation();
    if (!this.enableSound || this.isPulsing) return;

    this.isPulsing = true;

    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(880, ctx.currentTime);
    gain.gain.setValueAtTime(0.5, ctx.currentTime);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start();
    osc.stop(ctx.currentTime + 0.3);

    setTimeout(() => (this.isPulsing = false), 350);

    this.spawnNote();
  }

  testVibration(event: Event): void {
    event.stopPropagation();
    if (!this.enableVibration || this.isShaking) return;

    this.isShaking = true;
    navigator.vibrate?.(100);

    setTimeout(() => (this.isShaking = false), 500);
  }

  spawnNote(): void {
    const note = {id: Date.now(), x: 0, y: 50};
    this.floatingNotes.push(note);

    setTimeout(() => {
      this.floatingNotes = this.floatingNotes.filter((n) => n.id !== note.id);
    }, 1200);
  }

  /* =========================================================
   * Mic / Remote Audio
   * ========================================================= */

  async handleMicToggle(): Promise<void> {
    if (!this.useMicInput) {
      this.stopMicInput();
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({audio: true});
      this.micStream = stream;

      this.audioCtx = new AudioContext();
      this.cleanupAudio();

      this.micSource = this.audioCtx.createMediaStreamSource(stream);
      this.analyser = this.audioCtx.createAnalyser();
      this.analyser.fftSize = 256;
      this.dataArray = new Uint8Array(this.analyser.frequencyBinCount);

      this.micSource.connect(this.analyser);

      this.ngZone.runOutsideAngular(() => this.startVisualizer(true));
    } catch {
      this.useMicInput = false;
    }
  }

  private stopMicInput(): void {
    this.micStream?.getTracks().forEach((t) => t.stop());
    this.micStream = null;
    this.stopVisualizer();
  }

  attachRemoteStream(stream: MediaStream): void {
    this.remoteAudioStream = stream;
    this.audioCtx = new AudioContext();
    this.cleanupAudio();

    this.remoteSource = this.audioCtx.createMediaStreamSource(stream);
    this.analyser = this.audioCtx.createAnalyser();
    this.analyser.fftSize = 256;
    this.dataArray = new Uint8Array(this.analyser.frequencyBinCount);

    this.remoteSource.connect(this.analyser);

    this.dualMode = true;

    this.ngZone.runOutsideAngular(() => this.startDualVisualizer());
  }

  detachRemoteStream(): void {
    try {
      this.remoteSource?.disconnect();
    } catch {
    }
    this.remoteSource = null;
    this.stopVisualizer();
    this.dualMode = false;
  }

  /* =========================================================
   * Visualizer
   * ========================================================= */

  private startVisualizer(isMic = false): void {
    if (!this.analyser || !this.dataArray) return;

    const draw = () => {
      if (!this.analyser || !this.dataArray) return;

      this.analyser.getByteFrequencyData(this.dataArray);

      const bins = this.dataArray.length;
      const step = Math.floor(bins / this.BAR_COUNT);

      for (let i = 0; i < this.BAR_COUNT; i++) {
        let sum = 0;
        for (let j = 0; j < step; j++) {
          sum += this.dataArray[i * step + j];
        }
        const avg = sum / step / 255;
        const height = 6 + Math.round(avg * 42);
        this.barHeights[i] = height;
      }

      this.currentVU = Math.random() * -30; // simplified

      this.rafId = requestAnimationFrame(draw);
    };

    cancelAnimationFrame(this.rafId!);
    this.rafId = requestAnimationFrame(draw);
  }

  private startDualVisualizer(): void {
    if (!this.analyser || !this.dataArray) return;

    const draw = () => {
      if (!this.analyser || !this.dataArray) return;

      this.analyser.getByteFrequencyData(this.dataArray);

      let sum = 0;
      for (let x of this.dataArray) sum += x;
      const avg = sum / this.dataArray.length / 255;

      this.remoteVU = 20 * Math.log10(avg || 0.001);
      this.isRemoteTalking = this.remoteVU > -30;

      this.rafId = requestAnimationFrame(draw);
    };

    cancelAnimationFrame(this.rafId!);
    this.rafId = requestAnimationFrame(draw);
  }

  private stopVisualizer(): void {
    if (this.rafId) cancelAnimationFrame(this.rafId);
    this.rafId = null;
  }

  private cleanupAudio(): void {
    try {
      this.analyser?.disconnect();
      this.micSource?.disconnect();
      this.remoteSource?.disconnect();
    } catch {
    }
  }

  /* =========================================================
   * Network Simulation
   * ========================================================= */

  private startNetworkMonitor(): void {
    this.networkInterval = setInterval(() => {
      if (!this.isConnected) {
        this.networkStability = 0;
        this.networkColor = '#ef4444';
        return;
      }

      this.networkJitter = Math.random() * 40;
      this.networkLoss = Math.random() * 3;

      const jitterPenalty = (this.networkJitter / 40) * 30;
      const lossPenalty = this.networkLoss * 20;
      const base = 100 - jitterPenalty - lossPenalty;

      this.networkStability = Math.max(0, Math.min(100, base));

      if (this.networkStability > 85) this.networkColor = '#22c55e';
      else if (this.networkStability > 60) this.networkColor = '#fbbf24';
      else this.networkColor = '#ef4444';
    }, 2000);
  }

  /* =========================================================
   * UI Helpers
   * ========================================================= */

  getDynamicBarGradient(): string {
    if (this.isRemoteTalking) return 'linear-gradient(to top, #22c55e, #86efac)';
    if (this.useMicInput) return 'linear-gradient(to top, #3b82f6, #93c5fd)';
    return 'linear-gradient(to top, #9ca3af, #d1d5db)';
  }

  getTalkingPulseColor(): string {
    if (this.isRemoteTalking) return '#22c55e';
    if (this.useMicInput) return '#3b82f6';
    return '#9ca3af';
  }
}
