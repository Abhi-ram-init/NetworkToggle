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

### 🎨 3. Three Selectable UI Styles (User Choice)
Switch between 3 distinct interface themes at any moment via the top bar:
- ⚡ **Cyber Neon**: Deep OLED pitch-black background (`#08090D`) with glowing circular gauge dial and glassmorphism cards.
- 🎨 **Minimal Clean**: Modern Material 3 slate card layout (`#0F172A`) with tactile toggle button.
- 🏎️ **Speedometer**: Curved bandwidth meter gauge with turbo toggle controls.

### 📱 4. Three Home Screen Widget Options
- **1×1 Compact Quick-Toggle Widget**: Minimalist circular dial with live 5G/4G indicator; tap to toggle directly from home screen.
- **2×1 Cyber Pill Widget**: Horizontal glassmorphism pill with live carrier connection text and a glowing "SWITCH" button.
- **4×2 Multi-Option Dashboard Widget**: Full home screen console with direct mode selector chips (`5G NR`, `AUTO`, `4G LTE`), plus **Force Menu** and **Settings** shortcuts.
- **In-App 1-Tap Widget Pinning**: Add any widget to your home screen directly from the in-app manager.

### ⚡ 5. Additional Shortcuts
- **Quick Settings Tile**: Pull down the notification panel and tap the "Network Mode" tile to switch modes.
- **App Launcher Shortcuts**: Long-press the app icon on the home screen for direct **"5G Mode"** and **"4G Mode"** actions.

### 🔒 6. Cryptographic Permission Isolation
- Declares custom permission `com.example.networktoggle.permission.CONTROL_NETWORK_TOGGLE` with `protectionLevel="signature"`.
- Under Android's security sandbox, only applications signed with the identical cryptographic private key can hold this permission. **Zero third-party apps can intercept or trigger network controls.**

---

## 📦 Download & Installation

Ready-to-install debug APK is available in the repository:
- 📁 [`apk/NetworkToggle-v1.0.apk`](apk/NetworkToggle-v1.0.apk)

### Install via ADB:
```bash
adb install -r apk/NetworkToggle-v1.0.apk
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
