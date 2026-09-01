# VEXO - Voice Assistant Guide

**Last Updated**: September 1, 2026

## 📱 App Overview

VEXO is a premium Android voice assistant with:
- On-device speech recognition and synthesis
- "Hey VEXO" wake word detection
- Speaker identification
- Apple-style premium UI
- Customizable app icons

---

## 🎨 App Icons

VEXO includes 3 premium icon variations. Users can switch between them in **Settings**.

### Icon Variants

1. **Liquid Gold** (Default) - Luxury, sophisticated
2. **Electric Blue** - Modern, tech-focused
3. **Titanium Minimal** - Ultra-minimal, professional

All icons feature:
- Black background
- Premium gradient V logo
- Apple-style quality (12 layers of depth)
- Adaptive icon support (all launcher shapes)

See `ICON_VARIATIONS.md` for technical details.

---

## ⚙️ Settings

### Permissions
- Microphone - Required for voice commands
- Notifications - Shows listening indicator
- Display over apps - Opens VEXO from any screen

### Wake Word
- Toggle "Hey VEXO" always-listening mode
- 15 MiB model downloads on first use
- On-device processing (privacy-focused)

### Voice
- Select from 10 neural voices
- Preview before applying
- Neural voice pack downloads on Wi-Fi

### My Voice
- Enroll voice profile (3 recordings)
- Test voice match
- Enable "Only wake for my voice"

### App Icon (NEW!)
- Choose between 3 premium icon variants
- Live preview
- Apply immediately (no restart required)

---

## 🛠️ Development

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Min SDK**: 33 (Android 13)
- **Target SDK**: 37

### Key Features
- RuntimeShader wave animation (AGSL)
- On-device neural TTS (sherpa-onnx)
- Speaker recognition (on-device)
- Material Design 3 dark theme

### Build
```bash
./gradlew assembleDebug
```

---

## 🔌 Active Plugins

- **code-review** - Kotlin/Compose quality
- **github** - Git operations
- **figma** - Design system
- **frontend-design** - Premium UI
- **model-interaction-design** - Voice UX patterns
- **prompt-architecture** - Voice optimization
- **ui-ux-pro-max** - 53 Jetpack Compose guidelines

See `PLUGIN_CLEANUP_COMPLETE.md` for plugin management.

---

## 📂 Project Structure

```
app/src/main/
├── java/com/vexo/
│   ├── MainActivity.kt
│   ├── SettingsActivity.kt
│   ├── assistant/         # Voice assistant logic
│   ├── speaker/           # Voice recognition
│   ├── voice/             # Speech & TTS
│   ├── ui/
│   │   ├── assistant/     # Wave animation
│   │   ├── settings/      # Settings UI
│   │   └── theme/         # VexoTheme
│   └── wake/              # Wake word service
└── res/
    ├── drawable/          # Icon variants
    └── mipmap-*/          # Launcher icons
```

---

## 🚀 Future Roadmap

- [ ] Multiple language support
- [ ] Custom wake word training
- [ ] Voice command shortcuts
- [ ] Wear OS companion app
- [ ] Assistant routines

---

For detailed documentation:
- **Icon Design**: `LOGO_DESIGN.md`, `ICON_VARIATIONS.md`
- **Plugin Setup**: `PLUGIN_CLEANUP_COMPLETE.md`
- **Original Docs**: `README.md`
