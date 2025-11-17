# 🎨 SmartChat Design Token Sheet (2025)

> **Version:** 1.0  
> **Maintainer:** SmartChat UI Team  
> **Purpose:** Central reference for UI color, typography, spacing, and component tokens.  
> **Source:** `/src/styles/variables.scss`, `/src/styles/theme.scss`

---

## 🎨 Color System

| Token              | Value                    | Description                    |
|--------------------|--------------------------|--------------------------------|
| `--primary`        | `#3b82f6`                | SmartChat Brand Blue           |
| `--primary-hover`  | `#2563eb`                | Accent Hover / Active State    |
| `--primary-light`  | `#93c5fd`                | Soft Highlight / Gradient Tint |
| `--surface`        | `rgba(255,255,255,0.65)` | Main card background           |
| `--surface-dark`   | `rgba(17,27,33,0.8)`     | Dark mode panel background     |
| `--surface-border` | `rgba(255,255,255,0.15)` | Subtle glass border            |
| `--glass-blue`     | `rgba(59,130,246,0.08)`  | Glass tint for UI panels       |
| `--glass-border`   | `rgba(59,130,246,0.2)`   | Glass outline highlight        |
| `--text-color`     | `#111827`                | Primary text color             |
| `--text-muted`     | `#6b7280`                | Secondary / subtle text color  |

---

## 🌗 Dark Mode Overrides

| Token              | Value                        | Description                      |
|--------------------|------------------------------|----------------------------------|
| `--surface`        | `rgba(17,27,33,0.75)`        | Dark surface                     |
| `--surface-border` | `rgba(255,255,255,0.08)`     | Border in dark mode              |
| `--text-color`     | `#f9fafb`                    | Primary text (light gray)        |
| `--text-muted`     | `#9ca3af`                    | Muted text                       |
| `--glass-blue`     | `rgba(59,130,246,0.12)`      | Blue-tinted glass background     |
| `--glass-border`   | `rgba(59,130,246,0.25)`      | Bright glass border              |
| `--shadow-soft`    | `0 4px 10px rgba(0,0,0,0.4)` | Deeper shadows for dark contrast |

---

## ✨ Shadow & Depth Tokens

| Token             | Value                             | Use Case                     |
|-------------------|-----------------------------------|------------------------------|
| `--shadow-soft`   | `0 4px 12px rgba(0,0,0,0.1)`      | Default cards & avatars      |
| `--shadow-strong` | `0 4px 24px rgba(37,99,235,0.25)` | Focused or elevated elements |
| `--radius`        | `12px`                            | Global border radius         |

---

## 💨 Motion & Timing

| Token            | Value                     | Description                  |
|------------------|---------------------------|------------------------------|
| `--transition`   | `all 0.25s ease`          | Standard UI animation timing |
| `fadeInPop`      | `0.25s ease`              | Popover entry animation      |
| `pulse-ring`     | `2s infinite ease-in-out` | Connection pulse effect      |
| `reconnectSwirl` | `1.2s linear infinite`    | Reconnecting spinner effect  |

---

## 🔘 Component-Specific Tokens

### **Presence System**

| State        | Color                 | Effect                |
|--------------|-----------------------|-----------------------|
| Available    | `#22c55e`             | Green glow ring       |
| Away         | `#eab308`             | Yellow idle indicator |
| Busy         | `#ef4444`             | Red do-not-disturb    |
| Offline      | `#6b7280`             | Gray muted indicator  |
| Reconnecting | Animated `pulse-glow` | Cyan-blue swirl blend |

---

### **Theme Toggle Button**

| Property    | Light Mode | Dark Mode |
|-------------|------------|-----------|
| Icon Color  | `#333`     | `#e2e8f0` |
| Hover Color | `#2563eb`  | `#60a5fa` |
| Rotation    | `15deg`    | `15deg`   |

---

## 🧱 Component Scale Guidelines

| Type                 | Padding       | Gap   | Font     |
|----------------------|---------------|-------|----------|
| Header               | `12px 18px`   | `8px` | `1.1rem` |
| Avatar               | `42px x 42px` | —     | `1.1rem` |
| Popover Button       | `10px 16px`   | —     | `0.9rem` |
| Status Popover Width | `160px`       | —     | —        |

---

## 🧩 Typography

| Token              | Value                                                       |
|--------------------|-------------------------------------------------------------|
| Font Family        | `'Inter', 'Segoe UI', Roboto, Helvetica, Arial, sans-serif` |
| Font Weight (name) | `600`                                                       |
| Letter Spacing     | `0.3px`                                                     |
| Title Font Size    | `1.1rem`                                                    |
| Body Font Size     | `0.9rem`                                                    |

---

## 🌍 Export Info

- **Format:** Figma Tokens Plugin (JSON or Style Dictionary)
- **Version:** 1.0
- **Author:** SmartChat Contributors
- **License:** Internal Use Only
