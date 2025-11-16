<div align="center">
<!-- Optional: Add a logo here -->
<!-- <img src="path/to/your/logo.png" alt="TranslationHub Logo" width="150" /> -->
<h1>TranslationHub</h1>
<p>
A full-stack, cross-platform translation app with support for text, voice, camera (OCR), and real-time ASL gesture recognition.
</p>
<p>
<!-- Professional Badges -->
<a href="https://github.com/Lerner98/TH_Final_Project/issues">
<img src="https://img.shields.io/github/issues/Lerner98/TH_Final_Project?style=for-the-badge" alt="Issues" />
</a>
</p>
</div>

## 🚀 Key Features

| Feature | Description |
|---------|-------------|
| **Multi-Modal Input** | Translate using text, speech-to-text, or your camera (OCR). |
| **ASL Recognition** | Real-time American Sign Language gesture recognition and translation via WebSocket. |
| **File Translation** | Process and translate text from .pdf, .docx, and .txt files. |
| **50+ Languages** | Powered by OpenAI's GPT models for high-quality, nuanced translations. |
| **Authentication** | Secure user accounts for history and preferences, plus a guest mode. |
| **Cross-Platform** | A single React Native (Expo) codebase for both iOS and Android. |

## 🏗️ Architecture

This project uses a microservice architecture for scalability and maintainability.

<!-- Optional: You can create and add a diagram here if you have one -->
<!-- <img src="path/to/architecture_diagram.png" alt="Architecture Diagram" /> -->

**Client:** React Native (Expo)

**Backend Services (Node.js):**
- **Auth Service:** Manages user registration, login, and sessions.
- **Translation Service:** Handles all OpenAI API requests (GPT, Whisper, TTS).
- **User Data Service:** Manages user history, saved files, and preferences.

**ASL Recognition (Python):**
- A WebSocket server for real-time (frames) -> (text) gesture recognition.

**Databases:**
- **Microsoft SQL Server:** Primary database for user data, translations, etc.
- **MongoDB:** Used for high-volume error logging.

## 🛠️ Installation & Setup

Follow these steps to run the complete application locally.

### Prerequisites

- Node.js (v18+)
- Python (v3.8+)
- Microsoft SQL Server
- Expo CLI (`npm install -g expo-cli`)
- An OpenAI API Key

### 1. Configure Environment Variables

Create `.env` files in both the `client/` and `server/` directories. Use `.env.example` in each as a template.

**Server (`server/.env`)**
```env
# Database
DB_SERVER=your_server
DB_NAME=TranslationHub
DB_USER=your_user
DB_PASSWORD=your_password

# OpenAI
OPENAI_API_KEY=your_key

# Auth
SESSION_SECRET=your_secret
GMAIL_USER=your_email
GMAIL_APP_PASSWORD=your_app_password
```

**Client (`client/.env`)**
```env
# API URLs (use your local IP, not localhost, for mobile testing)
AUTH_API_URL=http://your_ip:3001
TRANSLATION_API_URL=http://your_ip:3002
USER_DATA_API_URL=http://your_ip:3003
WEBSOCKET_URL=ws://your_ip:8000/asl-ws
```

### 2. Run the Backend

**Terminal 1: Node.js Microservices**
```bash
cd server
npm install
npm run dev
```

**Terminal 2: Python WebSocket Server**
```bash
cd server  # (from root)
python -m venv venv

# On Windows
venv\Scripts\activate

# On macOS/Linux
source venv/bin/activate

pip install -r requirements.txt
python main.py
```

### 3. Run the Client (React Native)

**Terminal 3: Expo App**
```bash
cd client
npm install
npx expo start
```

Scan the QR code with the Expo Go app on your mobile device.

## 🤝 Contributing

Contributions are welcome! Please feel free to open an issue or submit a pull request.

1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.
