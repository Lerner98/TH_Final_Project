# TranslationHub

A full-stack translation application with support for text, voice, camera OCR, file processing, and real-time ASL recognition.

## Overview

TranslationHub is a React Native mobile application with a Node.js/Python backend that provides multi-modal translation services using OpenAI APIs. The application supports both authenticated users with unlimited translations and guest users with daily limits.

**Key Features:**
- Text and voice translation (50+ languages)
- Camera OCR with automatic text extraction
- Document translation (PDF, DOCX, TXT)
- Real-time ASL gesture recognition via WebSocket
- Cross-platform support (iOS/Android)

## Architecture

- **Client:** React Native (Expo) mobile app
- **Backend:** Node.js microservices (Auth, Translation, User Data) + Python WebSocket server for ASL
- **Database:** Microsoft SQL Server
- **AI Services:** OpenAI (GPT-4, Whisper, TTS)

## Installation

### Prerequisites
- Node.js 18+
- Python 3.8+
- SQL Server
- Expo CLI
- OpenAI API key

### Client Setup
```bash
cd client
npm install
npx expo start
Server Setup
bashcd server

# Install Node.js dependencies
npm install

# Set up Python virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt

# Configure environment variables
# Create .env file with required keys (see .env.example)

# Start Node.js microservices
npm run dev

# Start Python WebSocket server (separate terminal)
python main.py
Configuration
Create .env files in both client/ and server/ directories with the following:
Server .env:
DB_SERVER=your_server
DB_NAME=TranslationHub
DB_USER=your_user
DB_PASSWORD=your_password
OPENAI_API_KEY=your_key
SESSION_SECRET=your_secret
GMAIL_USER=your_email
GMAIL_APP_PASSWORD=your_app_password
Client .env:
AUTH_API_URL=http://your_ip:3001
TRANSLATION_API_URL=http://your_ip:3002
USER_DATA_API_URL=http://your_ip:3003
WEBSOCKET_URL=ws://your_ip:8000/asl-ws
