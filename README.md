# 📶 Network Toggle (5G / 4G Switcher & Widgets)

An Android application built with **Jetpack Compose**, **Material 3**, and hardware telephony APIs to monitor, toggle, and force network modes between **5G NR** and **4G LTE**. Features real-time modem detection, 3 selectable UI styles, 3 home screen widgets, a Quick Settings tile, launcher shortcuts, and signature-level cryptographic permission isolation.

---

## 🚀 Key Features

### 🎛️ 1. Multi-Engine Switching & Hardware Integration
- **Direct Mode Choice**: Explicitly select between **5G NR** (5G Only), **AUTO** (5G/4G Hybrid), and **4G LTE** (Battery Saver).
- **Multi-Engine Switching**:
  - **Engine 1 (Root/Privileged)**: Automatic modem frequency bitmask execution via shell.
  - **Engine 2 (ADB 1-Click)**: Full background auto-switching if `WRITE_SECURE_SETTINGS` is granted.
  - **Engine 3 (Hardware Force Menu `*#*#4636#*#*`)**: Universal 1-tap launcher into Android's hidden RadioInfo hardware menu to force `NR only` or `LTE only`.
  - **Engine 4 (SIM Settings)**: Direct shortcut to system mobile network & preferred network type settings.

### 🛡️ 2. Strict Truthful Telemetry & Failure Detection (No Fake Switches)
- **Real-Time Live Modem Detection**: Uses `TelephonyManager`, `TelephonyCallback`, and `ConnectivityManager` to inspect active cellular state (`5G NR Standalone`, `5G NSA`, `4G LTE`, etc.).
- **Modem Verification**: Never falsely claims a switch occurred until verified by hardware.
- **Clean Inline Failure Indicator**: If a carrier or hardware restriction prevents switching, a subtle inline badge flags `🔴 Not Switched • Hardware is on 4G LTE` with direct 1-tap access to the Force Menu—**zero annoying pop-ups or modal dialogues**.

### 🎨 3. Three Selectable UI Styles (Minimal Clean by Default)
Switch between 3 distinct interface themes at any moment via the top bar:
- 🎨 **Minimal Clean (Default)**: Modern, ultra-streamlined Material 3 slate card layout (`#0F172A`) with high-contrast typography, compact pill buttons, and distraction-free telemetry.
- ⚡ **Cyber Neon**: Deep OLED pitch-black background (`#08090D`) with glowing circular gauge dial and glassmorphism cards.
- 🏎️ **Speedometer**: Curved bandwidth meter gauge with turbo toggle controls.

### 🤖 4. Process-Aware Network Macro & Streamlined Diagnostics
- **Process Inspection**: Inspects the core Android telephony subsystem `com.android.phone` (reads active Process ID / PID, process execution state, carrier MCC/MNC, active SIM slot, and live dBm signal strength).
- **Automated Macro Presets**:
  - ⚡ **Turbo 5G Ultra-Lock**: Inspects modem process, flushes degraded cell cache, applies preferred 5G NR bitmask across SIM slots, and establishes carrier 5G SA/NSA handshake.
  - 🛰️ **Cell Tower Reseat**: Drops degraded cellular tower anchors, cycles RF link, and forces connection re-registration to the strongest nearby cellular mast.
  - 🌱 **Battery Saver 4G Eco**: Locks 4G LTE-only mode to immediately halt aggressive 5G millimeter-wave/sub-6 background band scanning.
- **Collapsible Live Terminal Console**: Clean 1-line live ticker when collapsed for zero distraction, expandable to full terminal diagnostics with timestamps, status tags (`[PROCESS]`, `[EXEC]`, `[SUCCESS]`), and 1-tap **COPY LOGS** and **CLEAR** controls.

### 🎛️ 5. Control Panel / Quick Settings Widgets
- **Quick Settings Tile 1 (Network Mode Toggle)**: Direct 1-tap 5G/4G mode switcher in the pull-down notification / Control Panel shade.
- **Quick Settings Tile 2 (5G Macro Optimizer)**: Dedicated tile to run the Turbo 5G Lock macro directly from your Control Panel without opening the app.
- **1-Tap "Add to Control Panel"**: Native Android 13+ (API 33+) integration via `StatusBarManager.requestAddTileService` to add tiles with a single click, plus guided instructions for older versions and custom OEM skins (MIUI/HyperOS, ColorOS, OneUI).

### 📱 6. Three Home Screen Widget Options
- **1×1 Compact Quick-Toggle Widget**: Minimalist circular dial with live 5G/4G indicator; tap to toggle directly from home screen.
- **2×1 Cyber Pill Widget**: Horizontal glassmorphism pill with live carrier connection text and a glowing "SWITCH" button.
- **4×2 Multi-Option Dashboard Widget**: Full home screen console with direct mode selector chips (`5G NR`, `AUTO`, `4G LTE`), plus **Force Menu** and **Settings** shortcuts.
- **In-App 1-Tap Widget Pinning**: Add any widget to your home screen directly from the in-app manager.

### ⚡ 7. Additional Shortcuts & Security
- **App Launcher Shortcuts**: Long-press the app icon on the home screen for direct **"5G Mode"** and **"4G Mode"** actions.
- **Cryptographic Permission Isolation**: Custom `com.example.networktoggle.permission.CONTROL_NETWORK_TOGGLE` signature-level protection ensures zero unauthorized third-party apps can invoke toggles.

---

## 📦 Download & Installation

Ready-to-install debug APK is available directly in this repository:
- 📱 **Latest Release (v1.5):** [`apk/NetworkToggle-v1.5.apk`](apk/NetworkToggle-v1.5.apk)
- 🔗 **Direct Latest Alias:** [`apk/NetworkToggle-latest.apk`](apk/NetworkToggle-latest.apk)

### Install via ADB:
```bash
adb install -r apk/NetworkToggle-v1.5.apk
```

---

## 🛠️ Building from Source

### Prerequisites:
- JDK 17 or JDK 21
- Android SDK (API Level 36 / 35 / 34)
- Gradle 8.x+ / 9.x+

### Build Debug APK:
```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```
Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📜 Permissions Explained

| Permission | Type | Why It's Needed |
|---|---|---|
| `READ_PHONE_STATE` | Runtime | Detects live connection speed (5G NR vs 4G LTE) via `TelephonyManager.getDataNetworkType()`. |
| `ACCESS_NETWORK_STATE` | Normal | Monitors network connectivity changes. |
| `CONTROL_NETWORK_TOGGLE` | Signature | Custom signature-level permission protecting widget receivers against third-party invocation. |
| `WRITE_SECURE_SETTINGS` | Privileged (Optional) | Allows optional 1-click automated background band switching via ADB. |

---

## 📄 License
MIT License. Free to use, modify, and distribute.
