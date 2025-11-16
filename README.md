<div align="center">
<h1>ASL Translator (Android Native)</h1>
<p>
Native Android application for learning American Sign Language through real-time AI-powered gesture recognition and gamified lessons.
</p>
<p>
<a href="https://github.com/Lerner98/TH_Final_Project/issues">
<img src="https://img.shields.io/github/issues/Lerner98/TH_Final_Project?style=for-the-badge" alt="Issues" />
</a>
</p>
</div>

## 📱 App Preview

<p align="center">
<img src="screenshots/splashscreen.png" alt="Splash Screen" width="180" />
<img src="screenshots/mainmenu.png" alt="Main Menu" width="180" />
<img src="screenshots/lessons.png" alt="Lessons" width="180" />
<img src="screenshots/learning.png" alt="Learning" width="180" />
</p>

## 🚀 Features

- **5 Progressive Lessons** - Greetings, Emotions, Actions, Objects, Questions (20 ASL signs total)
- **Real-Time Recognition** - CameraX + WebSocket integration with Python ML backend
- **Gamified Learning** - 6 unlockable achievements with Firebase progress tracking
- **Practice Modes** - Single-sign mastery (5 correct detections) or full-level practice
- **AI Model Training** - Advanced users can train custom gesture models
- **Adaptive Networking** - Auto-detects development environment configurations

## 🏗️ Tech Stack

**Platform:** Native Android (Java, API 24+)

**Core Technologies:**
- CameraX for real-time video capture
- Firebase (Authentication + Realtime Database)
- WebSocket (OkHttp) for low-latency ML inference
- Material Design 3 with custom purple theme
- Gson for JSON parsing

**Architecture:**
```
├── activities/        # MainActivity, LearningActivity, PracticeActivity, TrainingActivity
├── adapters/          # RecyclerView adapters for lessons and achievements  
├── fragments/         # LessonContentFragment for step-by-step instructions
├── models/            # Lesson, Achievement, GestureResponse data classes
├── network/           # WebSocketManager for real-time server communication
└── utils/             # AchievementManager, Constants (network configuration)
```

## 🛠️ Quick Start

1. **Clone and switch to Android branch:**
```bash
git clone https://github.com/Lerner98/TH_Final_Project.git
cd TH_Final_Project
git checkout Android-Native-App
```

2. **Open in Android Studio** and sync Gradle dependencies

3. **Add Firebase config:**
   - Download `google-services.json` from Firebase Console
   - Place in `app/` directory

4. **Configure backend server:**
   - Update server endpoint in `Constants.java`
   - Ensure Python ML server is running (see `Android Python Server/` folder)

5. **Run** on device/emulator (requires camera permissions)

## 🎯 Key Implementation Highlights

- **Step-based progression** - Lessons unlock sequentially; each step requires practice completion
- **Gesture mastery system** - 5 correct detections with 70%+ confidence to advance
- **Throttled frame capture** - 300ms intervals to optimize network/battery performance  
- **Centralized achievements** - `AchievementManager` handles all unlock logic with Firebase sync
- **Automatic reconnection** - WebSocket manager with exponential backoff (max 5 attempts)

## 📊 Practice System

Each gesture requires **5 successful recognitions** at 70%+ confidence. Progress tracked per-step with Firebase persistence. Users can retry unlimited times or advance after mastery.

## 🏆 Achievements

Early Bird • First Steps • Speed Learner • Consistency Champion • AI Trainer • Master Signer

## 🤝 Contributing

1. Fork the repo
2. Create feature branch (`git checkout -b feature/NewFeature`)
3. Commit changes (`git commit -m 'Add NewFeature'`)
4. Push to branch (`git push origin feature/NewFeature`)
5. Open Pull Request

---

*Part of TranslationHub project - see `main` branch for React Native cross-platform version*
