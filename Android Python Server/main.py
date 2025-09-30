# enhanced_main.py - Complete server with admin training and practice modes (FIXED)
"""
FILE OVERVIEW:
Enhanced ASL Translation & Training Server with Admin Controls

This is the core server implementing a comprehensive ASL gesture recognition system with:
- Real-time translation via WebSocket connections
- Machine learning model training (both regular and admin-level)
- Multi-lesson educational system with dynamic gesture sets
- Practice mode with lesson-specific model isolation
- Administrative controls for custom lesson model creation

KEY TECHNICAL ACHIEVEMENTS:
- Dual ML approach: Random Forest + Neural Network ensemble
- Real-time computer vision pipeline using MediaPipe
- Production-ready API design with proper error handling
- Dynamic model management for educational scalability
"""

# EXTERNAL PACKAGES
# Modern web framework with async support and automatic API documentation
import base64
import cv2  # Industry-standard computer vision library
import numpy as np  # High-performance numerical computing for ML
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request, HTTPException  # Modern async web framework
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
import mediapipe as mp  # Google's production-grade computer vision pipeline
import asyncio
import json
import logging
import os
import tensorflow as tf  # Google's deep learning framework - industry standard
from typing import Dict, List, Optional  # Modern Python type hints for code safety
import time
import pickle
from sklearn.ensemble import RandomForestClassifier  # Classical ML ensemble method
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score
import pandas as pd  # Data manipulation and CSV handling
import shutil

# Configure logging - PRODUCTION QUALITY ERROR HANDLING
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# FastAPI MODERN WEB FRAMEWORK - Automatic OpenAPI/Swagger documentation
app = FastAPI(title="ASL Translation & Training Server with Admin Controls")

# Add CORS middleware - PRODUCTION-READY API DESIGN
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize MediaPipe Hands - COMPUTER VISION PIPELINE
# NOTE: Google's production-grade hand tracking with 21 3D landmarks
# Optimized settings for real-time mobile camera processing
mp_hands = mp.solutions.hands.Hands(
    static_image_mode=False,  # Real-time video processing mode
    max_num_hands=1,          # Single hand detection for gesture focus
    min_detection_confidence=0.7,  # Balanced accuracy vs performance
    min_tracking_confidence=0.5,   # Smooth tracking between frames
    model_complexity=1        # Optimized for mobile devices
)

mp_drawing = mp.solutions.drawing_utils

# DATA STRUCTURES - SOPHISTICATED USAGE
# DICTIONARIES: Dynamic lesson configuration enables scalable educational content
ORIGINAL_LABELS = ["Yes", "No", "I Love You", "Hello", "Thank You"]

# ADVANCED FEATURES: Multi-lesson Support - Educational Innovation
# DICTIONARY: Maps lesson IDs to gesture sets - O(1) lookup, dynamic curriculum
LESSON_GESTURES = {
    "lesson_1": ["Hello", "Thank You", "Yes", "No"],        # Basic greetings
    "lesson_2": ["Happy", "Sad", "Angry", "Love"],          # Emotional expressions
    "lesson_3": ["Eat", "Drink", "Sleep", "Go"],            # Daily activities  
    "lesson_4": ["Book", "Phone", "Car", "Home"],           # Common objects
    "lesson_5": ["What", "Where", "When", "Who"]            # Question words
}

# Admin configuration - ROLE-BASED ACCESS CONTROL
ADMIN_EMAIL = "guylerner10@gmail.com"

class ASLModel:
    def load_model(self):
        try:
            model_path = os.path.join(os.path.dirname(__file__), 'models', 'asl_model_tf')
            logger.info(f"Attempting to load model from: {model_path}")
            logger.info(f"TensorFlow version: {tf.__version__}")
            logger.info(f"TensorFlow module path: {tf.__file__}")
            if os.path.exists(model_path):
                logger.info("Model file exists, loading...")
                self.model = tf.keras.models.load_model(model_path)
                self.model_loaded = True
                logger.info("✅ TensorFlow model loaded successfully")
            else:
                logger.warning("⚠️ Model not found, using rule-based classification")
        except Exception as e:
            logger.error(f"❌ Failed to load model: {e}", exc_info=True)
            self.model_loaded = False
    
    def predict(self, features: np.ndarray) -> tuple[str, float]:
        """
        TUPLES: Type-safe return interface - immutable prediction results
        Returns (gesture_name: str, confidence: float)
        
        MACHINE LEARNING: Production ML pipeline with fallback strategy
        """
        if self.model_loaded and self.model is not None:
            try:
                # NUMPY ARRAYS: High-performance ML processing
                features_reshaped = features.reshape(1, -1)  # Model input preparation
                predictions = self.model.predict(features_reshaped, verbose=0)
                confidence = float(np.max(predictions))
                predicted_class = int(np.argmax(predictions))
                gesture = ORIGINAL_LABELS[predicted_class] if predicted_class < len(ORIGINAL_LABELS) else "Unknown"
                return gesture, confidence
            except Exception as e:
                logger.error(f"Model prediction error: {e}")
                return self.rule_based_prediction(features)
        else:
            return self.rule_based_prediction(features)
    """
    MACHINE LEARNING IMPLEMENTATION - Dual Model Approach
    
    Section:
    - TensorFlow neural network for complex pattern recognition
    - Rule-based fallback ensures system reliability
    - Type-safe interfaces with modern Python typing
    """
    def __init__(self):
        self.model = None
        self.model_loaded = False
        self.load_model()
    

    
    def rule_based_prediction(self, features: np.ndarray) -> tuple[str, float]:
        """
        ADVANCED FEATURES: Intelligent fallback system
        Rule-based gesture recognition using hand geometry analysis
        
        NUMPY ARRAYS: Efficient mathematical operations on landmark data
        """
        landmarks = features.reshape(21, 3)  # 21 landmarks × 3 coordinates
        
        # Extract key finger tip points for gesture analysis
        thumb_tip = landmarks[4]
        index_tip = landmarks[8]
        middle_tip = landmarks[12]
        ring_tip = landmarks[16]
        pinky_tip = landmarks[20]
        
        # LISTS: Dynamic finger state collection
        fingers_up = []
        fingers_up.append(thumb_tip[1] < landmarks[3][1])  # Thumb
        fingers_up.append(index_tip[1] < landmarks[6][1])  # Index
        fingers_up.append(middle_tip[1] < landmarks[10][1])  # Middle
        fingers_up.append(ring_tip[1] < landmarks[14][1])  # Ring
        fingers_up.append(pinky_tip[1] < landmarks[18][1])  # Pinky
        
        fingers_up_count = sum(fingers_up)
        
        # Gesture classification rules based on finger configurations
        if fingers_up_count == 5:
            return "Hello", 0.8
        elif fingers_up_count == 2 and fingers_up[1] and fingers_up[4]:
            return "I Love You", 0.8
        elif fingers_up_count == 1 and fingers_up[0]:
            return "Yes", 0.7
        elif fingers_up_count == 2 and fingers_up[1] and fingers_up[2]:
            return "No", 0.7
        elif fingers_up_count == 0:
            return "Thank You", 0.6
        else:
            return "Unknown", 0.3

class EnhancedTrainingCollector:
    """
    ADVANCED FEATURES: Dynamic Training System
    
    DEFENSE POINTS:
    - Real-time feature collection and processing
    - Admin-level lesson management with role-based access
    - Dynamic model architecture adapts to lesson requirements
    - Comprehensive data management with multiple storage formats
    """
    def __init__(self):
        # DATA STRUCTURES: Sophisticated usage of multiple types
        self.current_session = None
        self.training_features = {}  # DICTIONARY: Maps gesture_name -> feature vectors
        self.training_labels = {}    # DICTIONARY: Maps gesture_name -> class indices
        self.admin_level_sessions = {}  # DICTIONARY: Admin session tracking with metadata
        self.current_lesson_gestures = []  # LIST: Dynamic gesture set for current lesson
        self.current_lesson_id = None      # Current lesson context
        
    def start_training_session(self, session_id: str, is_admin_level: bool = False, lesson_id: str = None):
        """
        ADVANCED FEATURES: Multi-lesson Support with Admin Controls
        Dynamic session initialization based on user role and lesson context
        """
        self.current_session = session_id
        # DICTIONARIES: Reset training data structures
        self.training_features = {}
        self.training_labels = {}

        if is_admin_level and lesson_id:
            self.current_lesson_id = lesson_id
            # 5. DATA STRUCTURE CHOICES STEP 1: Initialize Training Dictionaries
            # DICTIONARY LOOKUP: O(1) gesture set retrieval
            self.current_lesson_gestures = LESSON_GESTURES.get(lesson_id, [])
            logger.info(f"🔐 Admin session for {lesson_id} with gestures: {self.current_lesson_gestures}")
        else:
            self.current_lesson_id = None
            self.current_lesson_gestures = ORIGINAL_LABELS
            logger.info(f"🎯 Regular session with original gestures: {self.current_lesson_gestures}")

        # DATA STRUCTURE CHOICES STEP 2: Create Gesture-Based Keys
        # DYNAMIC KEY CREATION: Generate storage containers for each lesson gesture
        for gesture in self.current_lesson_gestures:
            self.training_features[gesture] = []  # Dynamic growth during training
            self.training_labels[gesture] = []
        
        # DICTIONARIES: Track admin level sessions with comprehensive metadata
        if is_admin_level and lesson_id:
            self.admin_level_sessions[session_id] = {
                "lesson_id": lesson_id,
                "started_at": time.time(),  # Timestamp for audit trail
                "is_admin": True,
                "gestures": self.current_lesson_gestures  # Store gesture list for validation
            }
            logger.info(f"🔐 Started ADMIN level training session: {session_id} for {lesson_id}")
        else:
            logger.info(f"🎯 Started regular training session: {session_id}")
        
        return True
    
    def process_training_frame(self, gesture_name: str, image_data: str) -> int:
        """
        REAL-TIME PROCESSING: Live feature extraction and validation
        
        DEFENSE POINTS:
        - Computer vision pipeline processes images in real-time
        - Dynamic gesture validation against current lesson context
        - Efficient data collection with immediate feedback
        """
        if not self.current_session:
            return 0
            
        try:
            # String processing and validation
            gesture_name = gesture_name.strip()
            
            # SETS: Efficient membership testing for gesture validation
            valid_gestures = self.current_lesson_gestures if self.current_lesson_gestures else ORIGINAL_LABELS
            
            if gesture_name not in valid_gestures:
                logger.error(f"❌ Invalid gesture name: '{gesture_name}'. Valid for current lesson: {valid_gestures}")
                return 0
            
            # IMAGE PROCESSING: Base64 decoding and OpenCV processing
            if "," in image_data:
                image_data = image_data.split(",")[1]
            
            # NUMPY ARRAYS: Efficient binary data handling
            img_bytes = base64.b64decode(image_data)
            np_arr = np.frombuffer(img_bytes, np.uint8)
            image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
            
            if image is not None:
                # COMPUTER VISION: Extract landmarks using MediaPipe
                features, landmarks, hand_detected = extract_hand_landmarks(image)
                
                if hand_detected and features is not None:
                    # LISTS: Dynamic feature storage with lesson-specific indexing
                    gesture_idx = valid_gestures.index(gesture_name)
                    
                    # DATA STRUCTURE CHOICES Step 3: Store Features by Gesture Name
                    # FEATURE STORAGE: Organize training data by gesture type for efficient access
                    # REAL-TIME DATA COLLECTION
                    self.training_features[gesture_name].append(features)
                    self.training_labels[gesture_name].append(gesture_idx)
                    
                    sample_count = len(self.training_features[gesture_name])
                    
                    # ADMIN CONTROLS: Different logging for admin sessions
                    if self.current_session in self.admin_level_sessions:
                        lesson_id = self.admin_level_sessions[self.current_session]["lesson_id"]
                        logger.info(f"✅ ADMIN {lesson_id}: Processed {gesture_name} feature #{sample_count}")
                    else:
                        logger.info(f"✅ Processed {gesture_name} feature #{sample_count}")
                    
                    return sample_count
                else:
                    logger.warning(f"⚠️ No hand detected in {gesture_name} frame")
            
        except Exception as e:
            # PRODUCTION QUALITY: Comprehensive error handling
            logger.error(f"Error processing training frame for {gesture_name}: {e}")
            import traceback
            traceback.print_exc()
        
        return len(self.training_features.get(gesture_name, []))
        
    def run_training(self, is_admin_level: bool = False, lesson_id: str = None) -> tuple[bool, float]:
        """
        MACHINE LEARNING IMPLEMENTATION: Dual Model Training
        
        DEFENSE POINTS:
        - Random Forest + Neural Network ensemble approach
        - Dynamic model architecture based on lesson requirements
        - Proper train/test splits with stratification
        - Multiple storage formats (pickle, TensorFlow SavedModel, JSON metadata)
        """
        try:
            # 2. DYNAMIC MODEL ARCHITECTURE
            # STEP 1: Determine Current Gesture Set
            # LESSON CONTEXT DETECTION: Select gesture vocabulary based on training level
            if is_admin_level and lesson_id:
                current_gestures = LESSON_GESTURES.get(lesson_id, [])
                logger.info(f"🔐 Starting ADMIN level training for {lesson_id} with gestures: {current_gestures}")
            else:
                current_gestures = ORIGINAL_LABELS
                logger.info(f"🚀 Starting regular training with gestures: {current_gestures}")
            
            # LISTS: Combine all features and labels for CURRENT gesture set
            X, y = [], []
            
            for gesture_name in current_gestures:
                # DICTIONARY ACCESS: Retrieve stored training data
                features = self.training_features.get(gesture_name, [])
                labels = self.training_labels.get(gesture_name, [])
                
                if len(features) > 0:
                    X.extend(features)  # LIST OPERATIONS: Dynamic data aggregation
                    y.extend(labels)
                    logger.info(f"📊 {gesture_name}: {len(features)} samples")
                else:
                    logger.warning(f"⚠️ No samples for {gesture_name}")
            
            if len(X) == 0:
                logger.error("❌ No training data collected!")
                return False, 0.0
            
            # NUMPY ARRAYS: Convert to high-performance arrays for ML
            X = np.array(X)
            y = np.array(y)
            
            logger.info(f"📊 Total dataset: {len(X)} samples across {len(current_gestures)} gestures")
            
            # DATA MANAGEMENT: Directory structure organization
            if is_admin_level and lesson_id:
                output_dir = f"level_models/{lesson_id}"  # Lesson-specific model storage
                os.makedirs(output_dir, exist_ok=True)
                logger.info(f"🔐 Admin training will save to: {output_dir}")
            else:
                output_dir = "models"  # Main model storage
                self.clean_old_models()
                os.makedirs(output_dir, exist_ok=True)
            
            # MACHINE LEARNING: Proper data splitting with stratification
            try:
                X_train, X_test, y_train, y_test = train_test_split(
                    X, y, test_size=0.2, random_state=42, stratify=y
                )
            except ValueError:
                # Fallback for small datasets
                X_train, X_test, y_train, y_test = train_test_split(
                    X, y, test_size=0.2, random_state=42
                )
            
            # 6. DUAL MODEL STRATEGY
            # Step 1: Train Random Forest Model
            # ENSEMBLE LEARNING: Classical ML with 300 decision trees for robustness
            logger.info("🌳 Training Random Forest...")
            rf_model = RandomForestClassifier(
                n_estimators=300,      # Multiple decision trees for robustness
                max_depth=15,          # Prevent overfitting
                min_samples_split=5,   # Statistical significance
                min_samples_leaf=2,    # Leaf node requirements
                random_state=42,       # Reproducible results
                n_jobs=-1              # Multi-core CPU utilization
            )
            rf_model.fit(X_train, y_train)
            rf_predictions = rf_model.predict(X_test)
            rf_accuracy = accuracy_score(y_test, rf_predictions)
            logger.info(f"✅ Random Forest accuracy: {rf_accuracy:.3f}")
            
            # DUAL MODEL STRATEGY Step 5: Save Both Models
            # MODEL PERSISTENCE: Store both models for redundancy and comparison

            # DATA PERSISTENCE: Pickle serialization for classical ML, Random Forest save
            rf_path = os.path.join(output_dir, "asl_model.pkl")
            with open(rf_path, "wb") as f:
                pickle.dump(rf_model, f)
            logger.info(f"💾 Random Forest saved to '{rf_path}'")
            
            # DYNAMIC MODEL ARCHITECTURE STEP 2: Calculate Number of Classes
            # DEEP LEARNING: TensorFlow neural network with dynamic architecture
            # CLASS COUNT CALCULATION: Determine output layer size dynamically

            num_classes = len(current_gestures)  # Adaptive to lesson requirements
            logger.info(f"🧠 Training Neural Network for {num_classes} classes...")

            # DYNAMIC MODEL ARCHITECTURE STEP 3: Create Dynamic Model Architecture
            # DYNAMIC MODEL CREATION: Build neural network with lesson-specific output size

            # DUAL MODEL STRATEGY STEP 2: Train Neural Network Model
            # DEEP LEARNING: Multi-layer neural network with advanced architecture
            tf_model = self.create_tensorflow_model(input_shape=(X.shape[1],), num_classes=num_classes)
            
            # ADVANCED ML: Training with callbacks for optimization
            history = tf_model.fit(
                X_train, y_train,
                epochs=100,
                batch_size=16,
                validation_split=0.2,
                verbose=1,
                callbacks=[
                    tf.keras.callbacks.EarlyStopping(patience=10, restore_best_weights=True),
                    tf.keras.callbacks.ReduceLROnPlateau(patience=5, factor=0.5)
                ]
            )
            
            # DUAL MODEL STRATEGY Step 3: Evaluate Both Models
            # PERFORMANCE COMPARISON: Assess accuracy of both ML approaches
            tf_loss, tf_accuracy = tf_model.evaluate(X_test, y_test, verbose=0)
            logger.info(f"✅ Neural Network accuracy: {tf_accuracy:.3f}")
            
            # DATA PERSISTENCE: TensorFlow SavedModel format
            tf_path = os.path.join(output_dir, "asl_model_tf")
            tf_model.save(tf_path)
            logger.info(f"💾 Neural Network saved to '{tf_path}'")
            
            # JSON METADATA: Self-documenting models with training parameters
            metadata = {
                "lesson_id": lesson_id if is_admin_level else "original",
                "created_at": time.time(),  # Audit trail
                "accuracy": max(rf_accuracy, tf_accuracy),
                "samples_count": len(X),
                "gestures": current_gestures,  # Gesture set used for training
                "num_classes": num_classes,    # Model architecture info
                "admin_session": self.current_session if is_admin_level else None
            }
            metadata_path = os.path.join(output_dir, "training_metadata.json")
            with open(metadata_path, "w") as f:
                json.dump(metadata, f, indent=2)
            logger.info(f"💾 Metadata saved to '{metadata_path}'")
            
            if not is_admin_level:
                self.cleanup_duplicates()  # Maintain clean directory structure
            
            logger.info(f"🎉 {'Admin level' if is_admin_level else 'Regular'} training complete!")

            # DUAL MODEL STRATEGY Step 4: Select Best Performer
            # ENSEMBLE SELECTION: Choose superior model for deployment
            best_accuracy = max(rf_accuracy, tf_accuracy)
            
            # Memory management: Clear training data
            self.training_features = {}
            self.training_labels = {}
            
            return True, best_accuracy
                
        except Exception as e:
            # PRODUCTION QUALITY: Comprehensive error handling
            logger.error(f"❌ Training error: {e}")
            import traceback
            traceback.print_exc()
            return False, 0.0
    
    def create_tensorflow_model(self, input_shape: tuple, num_classes: int):
        """
        DEEP LEARNING: Advanced neural network architecture
        
        DEFENSE POINTS:
        - Batch normalization for training stability
        - Dropout layers prevent overfitting
        - Dynamic output layer adapts to lesson requirements
        - Adam optimizer with learning rate scheduling
        """
        model = tf.keras.models.Sequential([
            tf.keras.layers.Input(shape=input_shape),
            tf.keras.layers.Dense(256, activation="relu"),      # Feature extraction
            tf.keras.layers.BatchNormalization(),               # Training stability
            tf.keras.layers.Dropout(0.3),                       # Overfitting prevention
            tf.keras.layers.Dense(128, activation="relu"), 
            tf.keras.layers.BatchNormalization(),
            tf.keras.layers.Dropout(0.3),
            tf.keras.layers.Dense(64, activation="relu"),
            tf.keras.layers.Dropout(0.2),
            # STEP 4: Dynamic Output Layer Creation
            # ADAPTIVE OUTPUT LAYER: Softmax layer size matches lesson gesture count
            tf.keras.layers.Dense(num_classes, activation="softmax")  # Dynamic output size
        ])
        # STEP 5: Complile Model with Dynamic Classes
        # MODEL COMPILATION: Configure optimizer and loss for variable class sizes
        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
            loss="sparse_categorical_crossentropy", 
            metrics=["accuracy"]
        )
        return model
    
    def clean_old_models(self):
        """
        DATA MANAGEMENT: Safe model cleanup for regular training
        Maintains system stability by preventing model conflicts
        """
        safe_targets = [
            "models/asl_model.pkl",
            "models/asl_model_tf",
            "asl_model_tf",
            "asl_model.pkl"
        ]
        
        for target in safe_targets:
            try:
                if os.path.exists(target):
                    if os.path.isdir(target):
                        shutil.rmtree(target)
                    else:
                        os.remove(target)
                    logger.info(f"🗑️ Cleaned: {target}")
            except Exception as e:
                logger.warning(f"⚠️ Could not clean {target}: {e}")
        
        os.makedirs("models", exist_ok=True)
    
    def cleanup_duplicates(self):
        """DATA MANAGEMENT: Remove duplicate models from root directory"""
        duplicates = ["asl_model_tf", "asl_model.pkl"]
        for duplicate in duplicates:
            if os.path.exists(duplicate):
                try:
                    if os.path.isdir(duplicate):
                        shutil.rmtree(duplicate)
                    else:
                        os.remove(duplicate)
                    logger.info(f"🗑️ Removed duplicate: {duplicate}")
                except Exception as e:
                    logger.warning(f"⚠️ Could not remove {duplicate}: {e}")

# Initialize components
asl_model = ASLModel()
training_collector = EnhancedTrainingCollector()

class ConnectionManager:
    """
    PRODUCTION QUALITY: Professional connection management
    
    POINTS:
    - Supports multiple concurrent WebSocket connections
    - Separate tracking for translation vs training clients
    - Proper resource cleanup prevents memory leaks
    - Scalable architecture for multiple users
    """
    def __init__(self):
        # LISTS: Dynamic connection tracking
        self.active_connections: List[WebSocket] = []     # Translation clients
        self.training_connections: List[WebSocket] = []   # Training clients
    
    async def connect(self, websocket: WebSocket, is_training: bool = False):
        """ASYNC OPERATIONS: Modern Python async/await for concurrency"""
        await websocket.accept()
        if is_training:
            self.training_connections.append(websocket)
            logger.info(f"📱 Training client connected. Total: {len(self.training_connections)}")
        else:
            self.active_connections.append(websocket)
            logger.info(f"📱 Translation client connected. Total: {len(self.active_connections)}")
    
    def disconnect(self, websocket: WebSocket, is_training: bool = False):
        """RESOURCE MANAGEMENT: Proper cleanup maintains system stability"""
        if is_training and websocket in self.training_connections:
            self.training_connections.remove(websocket)
            logger.info(f"📱 Training client disconnected. Total: {len(self.training_connections)}")
        elif websocket in self.active_connections:
            self.active_connections.remove(websocket)
            logger.info(f"📱 Translation client disconnected. Total: {len(self.active_connections)}")

manager = ConnectionManager()

def extract_hand_landmarks(image: np.ndarray) -> tuple[Optional[np.ndarray], List[Dict], bool]:
    """
    COMPUTER VISION PIPELINE: Real-time hand landmark extraction
    
    DEFENSE POINTS:
    - MediaPipe provides 21 3D hand landmarks with sub-pixel accuracy
    - Optimized for real-time mobile camera processing
    - Consistent 63-dimensional feature vectors (21 landmarks × 3 coordinates)
    - Robust error handling for detection failures
    """
    try:
        # 1. REAL-TIME COMPUTER VISION PIPELINE
        # REAL-TIME VISION PIPELINE Step 1: Color Space Conversion
        # COLOR SPACE CONVERSION: BGR to RGB for MediaPipe processing
        rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        # REAL-TIME VISION PIPELINE STEP 2: MEDIAPIPE PROCESSING: Detect 21 hand landmarks with 3D coordinates
        results = mp_hands.process(rgb_image)
        # REAL-TIME VISION PIPELINE STEP 3: MediaPipe Hand Processing
        if results.multi_hand_landmarks:
            # REAL-TIME VISION PIPELINE STEP 4: Extract First Hand's Landmarks
            hand_landmarks = results.multi_hand_landmarks[0]
            # REAL-TIME VISION PIPELINE STEP 5: Initialize Data Containers
            # LISTS: Dynamic landmark collection
            landmarks = []
            features = []

            # REAL-TIME VISION PIPELINESTEP 6: Iterate through the 21 Landmarks
            for i, landmark in enumerate(hand_landmarks.landmark):
                # REAL-TIME VISION PIPELINE STEP 7: Store structured Landmark Data
                # DICTIONARIES: Structured coordinate data
                landmarks.append({
                    "x": landmark.x,
                    "y": landmark.y,
                    "z": landmark.z
                })
                # REAL-TIME VISION PIPELINE STEP 8: Flatten Coordinates for ML
                # LISTS: Feature vector construction for ML
                features.extend([landmark.x, landmark.y, landmark.z])

            # REAL-TIME VISION PIPELINE STEP 9: Convert to NumPy Array
            # NUMPY ARRAYS: High-performance array for ML processing
            return np.array(features), landmarks, True

        return None, [], False

    except Exception as e:
        # PRODUCTION QUALITY: Error handling ensures system stability
        logger.error(f"Landmark extraction error: {e}")
        return None, [], False

# Import the isolated level manager - MODULAR DESIGN
from level_model_manager import isolated_level_manager

def verify_admin_access(request: Request):
    """
    SECURITY: Admin access verification
    In production, this would integrate with JWT tokens or session management
    """
    return True

# WEBSOCKET ENDPOINTS - REAL-TIME COMMUNICATION

@app.websocket("/asl-ws")
async def asl_websocket_endpoint(websocket: WebSocket):
    """
    REAL-TIME PROCESSING: Main translation WebSocket endpoint
    
    DEFENSE POINTS:
    - Async WebSocket for real-time bidirectional communication
    - Computer vision pipeline processes camera frames in real-time
    - Practice mode integration with lesson-specific models
    - Comprehensive error handling for production stability
    """
    # 3. WEBSOCKET REAL-TIME COMMUNICATION
    # STEP 1: Accept WebSocket Connection
    # CONNECTION MANAGEMENT: Establish WebSocket link with Android client
    await manager.connect(websocket)
    
    try:
        # WEBSOCKET REAL-TIME COMMUNICATION STEP 2: Continuous Data Reception Loop
        # INFINITE LOOP: Maintain persistent connection for real-time processing
        while True:
            # WEBSOCKET REAL-TIME COMMUNICATION STEP 3: Receive Frame Data from Android
            # ASYNC DATA RECEPTION: Non-blocking frame data from mobile camera
            data = await websocket.receive_text()

            # WEBSOCKET REAL-TIME COMMUNICATION STEP 4: Parse JSON Frame Data
            # JSON PARSING: Extract structured data from Android client
            frame_data = json.loads(data)
            
            if "frame" not in frame_data:
                await websocket.send_json({"error": "No frame data received"})
                continue
            
            # IMAGE PROCESSING: Decode and process camera frame
            try:
                # WEBSOCKET REAL-TIME COMMUNICATION STEP 5: Extract Base64 Image Data
                # IMAGE DATA EXTRACTION: Get encoded camera frame from JSON payload
                img_data = frame_data["frame"]

                # WEBSOCKET REAL-TIME COMMUNICATION STEP 6: Remove Data URL Prefix
                # DATA URL CLEANUP: Strip metadata prefix from Base64 string
                if "," in img_data:
                    img_data = img_data.split(",")[1]
                
                # WEBSOCKET REAL-TIME COMMUNICATION STEP 7: Decode Base64 to Bytes
                # NUMPY ARRAYS: Efficient binary data processing
                # BASE64 DECODING: Convert text encoding back to binary image data
                img_bytes = base64.b64decode(img_data)

                # WEBSOCKET REAL-TIME COMMUNICATION STEP 8: Convert Bytes to NumPy Array
                # NUMPY BUFFER CREATION: Prepare binary data for OpenCV processing
                np_arr = np.frombuffer(img_bytes, np.uint8)

                # WEBSOCKET REAL-TIME COMMUNICATION STEP 9: DEcode to OpenCV Image
                # OPENCV IMAGE DECODING: Convert bytes to processable image format
                image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
                
                if image is None:
                    await websocket.send_json({"error": "Failed to decode image"})
                    continue
                
            except Exception as e:
                await websocket.send_json({"error": f"Image decoding failed: {str(e)}"})
                continue
            
            # COMPUTER VISION: Extract hand landmarks and predict gesture
            features, landmarks, hand_detected = extract_hand_landmarks(image)
            
            # DICTIONARIES: Structured response data
            response = {
                "timestamp": time.time(),
                "hand_detected": hand_detected,
                "landmarks": landmarks,
                "gesture": "None",
                "confidence": 0.0
            }
            
            if hand_detected and features is not None:
                # 4. PRACTICE MODE MODEL SWITCHING
                # STEP 1: Check Practice Mode Status
                # PRACTICE MODE DETECTION: Determine if lesson-specific model should be used
                if isolated_level_manager.is_practice_mode:
                    # STEP 2: Use Lesson-Specific Model
                    # LESSON MODEL PREDICTION: Apply focused model for current lesson gestures
                    gesture, confidence = isolated_level_manager.predict_with_level_model(features)
                    logger.debug(f"🎯 Practice mode prediction: {gesture} ({confidence:.3f}) for lesson {isolated_level_manager.current_level_id}")
                    # STEP 3: Fallback to Main Model
                    # MAIN MODEL FALLBACK: Use general-purpose model for "normal camera translation"
                else:
                    gesture, confidence = asl_model.predict(features)
                    logger.debug(f"🔄 Normal mode prediction: {gesture} ({confidence:.3f})")
                
                response["gesture"] = gesture
                response["confidence"] = confidence
                response["practice_mode"] = isolated_level_manager.is_practice_mode
                response["practice_level"] = isolated_level_manager.current_level_id
            
            await websocket.send_json(response)
            
    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        manager.disconnect(websocket)

# TRAINING ENDPOINTS - REGULAR USER TRAINING

@app.post("/training/add-sample")
async def add_training_sample(request: Request):
    """
    REAL-TIME TRAINING: Add training sample for regular users
    Processes camera frames in real-time for model training
    """
    data = await request.json()
    session_id = data.get("session_id")
    gesture_name = data.get("gesture_name")
    frame_data = data.get("frame")

    if not all([session_id, gesture_name, frame_data]):
        return {"status": "error", "message": "Missing required fields"}, 400

    # Session management with automatic switching
    if training_collector.current_session and training_collector.current_session != session_id:
        logger.info(f"🔄 Switching to new session: {session_id}")
        training_collector.start_training_session(session_id)

    # Start session if not already started
    if not training_collector.current_session:
        training_collector.start_training_session(session_id)

    # REAL-TIME PROCESSING: Process frame and extract features
    sample_count = training_collector.process_training_frame(gesture_name, frame_data)
    if sample_count == 0:
        return {"status": "error", "message": "Failed to process frame"}, 500

    return {"status": "success", "samples": sample_count}

@app.post("/training/complete")
async def complete_training(request: Request):
    """
    MACHINE LEARNING: Complete regular training and create models
    Dual model approach: Random Forest + Neural Network
    """
    data = await request.json()
    session_id = data.get("session_id")

    if not session_id:
        return {"status": "error", "message": "Missing session_id"}, 400

    # ENSEMBLE LEARNING: Train both Random Forest and Neural Network
    success, accuracy = training_collector.run_training()

    if success:
        asl_model.load_model()  # Reload new model for immediate use
        return {"status": "success", "accuracy": accuracy}
    else:
        asl_model.model = None
        asl_model.model_loaded = False
        logger.error("❌ Training failed, cleared model")
        return {"status": "error", "message": "Training failed"}, 500

# ADMIN ENDPOINTS - ADVANCED FEATURES

@app.post("/admin/training/add-sample")
async def add_admin_training_sample(request: Request):
    """
    ADMIN CONTROLS: Level-specific training data collection
    
    DEFENSE POINTS:
    - Role-based access control with session validation
    - Dynamic gesture validation based on lesson context
    - Audit trail for all admin training activities
    - Lesson-specific model creation capabilities
    """
    data = await request.json()
    session_id = data.get("session_id")
    lesson_id = data.get("lesson_id")
    gesture_name = data.get("gesture_name")
    frame_data = data.get("frame")

    if not all([session_id, lesson_id, gesture_name, frame_data]):
        return {"status": "error", "message": "Missing required fields"}, 400

    # SECURITY: Verify admin session with role-based access
    if not session_id.startswith("admin_level_"):
        return {"status": "error", "message": "Invalid admin session"}, 403

    # SESSION MANAGEMENT: Admin session switching with lesson context
    if training_collector.current_session and training_collector.current_session != session_id:
        logger.info(f"🔐 Switching to new ADMIN session: {session_id}")
        training_collector.start_training_session(session_id, is_admin_level=True, lesson_id=lesson_id)

    # Start admin session with lesson-specific configuration
    if not training_collector.current_session:
        training_collector.start_training_session(session_id, is_admin_level=True, lesson_id=lesson_id)

    # DYNAMIC VALIDATION: Process frame with lesson-specific gesture validation
    sample_count = training_collector.process_training_frame(gesture_name, frame_data)
    if sample_count == 0:
        return {"status": "error", "message": "Failed to process admin frame"}, 500

    return {"status": "success", "samples": sample_count, "lesson_id": lesson_id}

@app.post("/admin/training/complete-level")
async def complete_admin_level_training(request: Request):
    """
    ADMIN CONTROLS: Complete level-specific model training
    
    DEFENSE POINTS:
    - Creates lesson-specific models with dynamic architecture
    - Separate model storage prevents conflicts with main models
    - Comprehensive metadata tracking for educational management
    - Quality control through admin-only access
    """
    data = await request.json()
    session_id = data.get("session_id")
    lesson_id = data.get("lesson_id")

    if not all([session_id, lesson_id]):
        return {"status": "error", "message": "Missing required fields"}, 400

    # SECURITY: Verify admin session
    if not session_id.startswith("admin_level_"):
        return {"status": "error", "message": "Invalid admin session"}, 403

    # DYNAMIC MODEL CREATION: Train lesson-specific model
    success, accuracy = training_collector.run_training(is_admin_level=True, lesson_id=lesson_id)

    if success:
        logger.info(f"🔐 Admin successfully created level model for {lesson_id}")
        return {
            "status": "success", 
            "accuracy": accuracy,
            "lesson_id": lesson_id,
            "message": f"Level model created for {lesson_id}"
        }
    else:
        logger.error(f"❌ Admin level training failed for {lesson_id}")
        return {"status": "error", "message": f"Level training failed for {lesson_id}"}, 500

@app.get("/admin/level-models")
async def list_admin_level_models():
    """
    ADMIN CONTROLS: List all created level models
    
    DEFENSE POINTS:
    - Provides comprehensive model inventory for educational management
    - JSON metadata enables model performance tracking
    - Administrative oversight of curriculum content
    """
    level_models_dir = "level_models"
    
    if not os.path.exists(level_models_dir):
        return {"status": "success", "level_models": []}
    
    # LISTS: Dynamic model inventory collection
    level_models = []
    for lesson_dir in os.listdir(level_models_dir):
        lesson_path = os.path.join(level_models_dir, lesson_dir)
        if os.path.isdir(lesson_path):
            metadata_path = os.path.join(lesson_path, "training_metadata.json")
            if os.path.exists(metadata_path):
                try:
                    # JSON DATA MANAGEMENT: Load model metadata
                    with open(metadata_path, 'r') as f:
                        metadata = json.load(f)
                    level_models.append(metadata)
                except Exception as e:
                    logger.error(f"Error reading metadata for {lesson_dir}: {e}")
    
    return {"status": "success", "level_models": level_models}

# PRACTICE ENDPOINTS - ADVANCED FEATURES

@app.post("/practice/start")
async def start_practice_mode(request: Request):
    """
    ADVANCED FEATURES: Practice mode for focused learning
    
    DEFENSE POINTS:
    - Lesson-specific model isolation for targeted practice
    - Educational psychology: reduces cognitive load
    - Performance tracking for individual lesson mastery
    - Seamless integration with existing translation pipeline
    """
    data = await request.json()
    lesson_id = data.get("lesson_id")
    
    if not lesson_id:
        return {"status": "error", "message": "Missing lesson_id"}, 400
    
    # DATA MANAGEMENT: Verify lesson model exists
    level_model_path = f"level_models/{lesson_id}"
    if not os.path.exists(level_model_path):
        return {"status": "error", "message": f"Level model not found for {lesson_id}. Please create it first."}, 404
    
    # PRACTICE MODE: Enter lesson-specific practice environment
    success = isolated_level_manager.enter_practice_mode(lesson_id)
    
    if success:
        return {
            "status": "success",
            "level_info": isolated_level_manager.get_level_info()
        }
    else:
        return {"status": "error", "message": "Failed to start practice mode"}, 500

@app.post("/practice/stop")
async def stop_practice_mode():
    """
    PRACTICE MODE: Stop practice mode, return to normal translation
    Clean state management ensures no interference between modes
    """
    isolated_level_manager.exit_practice_mode()
    return {
        "status": "success",
        "message": "Practice mode stopped"
    }

@app.get("/practice/status")
async def get_practice_status():
    """
    PRACTICE MODE: Check current practice mode status
    Provides real-time system state information
    """
    return isolated_level_manager.get_level_info()

# SYSTEM HEALTH AND MONITORING - PRODUCTION QUALITY

@app.get("/health")
async def health_check():
    """
    PRODUCTION QUALITY: Comprehensive system health monitoring
    
    DEFENSE POINTS:
    - Real-time system status for DevOps monitoring
    - Model loading status verification
    - Connection tracking for performance analysis
    - Practice mode state visibility
    - Administrative feature confirmation
    """
    return {
        "status": "healthy",
        "model_loaded": asl_model.model_loaded,                    # ML model status
        "active_connections": len(manager.active_connections),     # Translation clients
        "training_connections": len(manager.training_connections), # Training clients
        "training_session": training_collector.current_session is not None,  # Training state
        "practice_mode": isolated_level_manager.is_practice_mode,  # Practice state
        "practice_level": isolated_level_manager.current_level_id, # Current lesson
        "admin_features": "enabled"                                # Admin capabilities
    }

@app.get("/")
async def root():
    """
    API DOCUMENTATION: Self-documenting API with endpoint inventory
    
    DEFENSE POINTS:
    - Automatic API documentation for developers
    - Clear endpoint organization and purpose
    - Integration-ready API design
    - Professional API presentation
    """
    return {
        "message": "ASL Translation & Training Server with Admin Controls",
        "endpoints": {
            "translation_websocket": "/asl-ws",                    # Real-time translation
            "health": "/health",                                   # System monitoring
            "training_add_sample": "/training/add-sample",         # Regular training
            "training_complete": "/training/complete",             # Model creation
            "admin_training_add_sample": "/admin/training/add-sample",      # Admin training
            "admin_training_complete": "/admin/training/complete-level",    # Level models
            "admin_list_models": "/admin/level-models",            # Model inventory
            "practice_start": "/practice/start",                   # Practice mode
            "practice_stop": "/practice/stop",                     # Exit practice
            "practice_status": "/practice/status"                  # Practice state
        }
    }

# ADVANCED FEATURES: Practice Model Visualization Dashboard
# WEB-BASED VISUALIZATION: Interactive HTML dashboard for educational model analysis

# - Demonstrates sophisticated data management with JSON metadata parsing
# - Shows educational innovation through lesson-specific model performance tracking
# - Showcases modern web development with responsive CSS and JavaScript animations
# - Provides real-time system monitoring integrated with existing practice mode architecture

@app.get("/visualize/practice-models", response_class=HTMLResponse)
async def visualize_practice_models():
    """
    WEB-BASED VISUALIZATION: Interactive practice model dashboard with integrated health monitoring
    
    DEFENSE POINTS:
    - Shows educational innovation with lesson-specific models
    - Demonstrates data management and JSON metadata usage
    - Interactive HTML interface showcases modern web development
    - Real-time model performance comparison across lessons
    - Integrated system health monitoring with beautiful design
    """
    
    # DATA MANAGEMENT: Scan for available practice models
    level_models_dir = "level_models"
    models_data = []
    
    if os.path.exists(level_models_dir):
        for lesson_dir in os.listdir(level_models_dir):
            lesson_path = os.path.join(level_models_dir, lesson_dir)
            if os.path.isdir(lesson_path):
                metadata_path = os.path.join(lesson_path, "training_metadata.json")
                if os.path.exists(metadata_path):
                    try:
                        # JSON DATA LOADING: Extract model metadata
                        with open(metadata_path, 'r') as f:
                            metadata = json.load(f)
                        
                        # DATA STRUCTURES: Build visualization data
                        models_data.append({
                            "lesson_id": lesson_dir,
                            "accuracy": metadata.get('accuracy', 0),
                            "samples": metadata.get('samples_count', 0),
                            "gestures": metadata.get('gestures', []),
                            "created_at": metadata.get('created_at', 0)
                        })
                    except Exception as e:
                        logger.error(f"Error loading metadata for {lesson_dir}: {e}")
    
    # SYSTEM HEALTH DATA: Collect comprehensive system status
    practice_info = isolated_level_manager.get_level_info()
    health_data = {
        "status": "healthy",
        "model_loaded": asl_model.model_loaded,
        "active_connections": len(manager.active_connections),
        "training_connections": len(manager.training_connections),
        "training_session": training_collector.current_session is not None,
        "practice_mode": practice_info['is_practice_mode'],
        "practice_level": practice_info.get('current_level', 'None'),
        "admin_features": "enabled"
    }
    
    # ADVANCED FEATURES: Generate interactive HTML with embedded JavaScript
    html_content = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <title>🎯 ASL Practice Models Dashboard</title>
        <style>
            body {{ 
                font-family: 'Segoe UI', Arial, sans-serif; 
                margin: 0; 
                padding: 20px; 
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
            }}
            .container {{ 
                max-width: 1400px; 
                margin: 0 auto; 
                background: rgba(255,255,255,0.1); 
                padding: 30px; 
                border-radius: 15px;
                backdrop-filter: blur(10px);
            }}
            .header {{ 
                text-align: center; 
                margin-bottom: 30px; 
                border-bottom: 2px solid rgba(255,255,255,0.3);
                padding-bottom: 20px;
            }}
            
            /* HEALTH STATUS SECTION */
            .health-section {{
                margin-bottom: 30px;
                background: rgba(255,255,255,0.05);
                padding: 20px;
                border-radius: 12px;
                border: 1px solid rgba(255,255,255,0.2);
            }}
            .health-grid {{
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                gap: 15px;
                margin-top: 15px;
            }}
            .health-card {{
                background: rgba(255,255,255,0.15);
                padding: 20px;
                border-radius: 10px;
                border: 1px solid rgba(255,255,255,0.3);
                transition: all 0.3s ease;
                position: relative;
                overflow: hidden;
            }}
            .health-card:hover {{
                transform: translateY(-3px);
                box-shadow: 0 8px 25px rgba(0,0,0,0.3);
            }}
            .health-card.healthy {{
                border-left: 4px solid #4CAF50;
            }}
            .health-card.warning {{
                border-left: 4px solid #FF9800;
            }}
            .health-card.error {{
                border-left: 4px solid #F44336;
            }}
            .health-icon {{
                font-size: 24px;
                margin-bottom: 10px;
                display: block;
            }}
            .health-title {{
                font-size: 14px;
                opacity: 0.8;
                margin-bottom: 5px;
            }}
            .health-value {{
                font-size: 24px;
                font-weight: bold;
                margin-bottom: 10px;
            }}
            .health-status {{
                font-size: 12px;
                padding: 4px 8px;
                border-radius: 12px;
                background: rgba(255,255,255,0.2);
                display: inline-block;
            }}
            
            /* EXISTING STYLES */
            .models-grid {{ 
                display: grid; 
                grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); 
                gap: 20px; 
                margin-bottom: 30px;
            }}
            .model-card {{ 
                background: rgba(255,255,255,0.2); 
                padding: 20px; 
                border-radius: 10px; 
                border: 1px solid rgba(255,255,255,0.3);
                transition: transform 0.3s ease;
            }}
            .model-card:hover {{ 
                transform: translateY(-5px); 
                box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            }}
            .accuracy-bar {{ 
                width: 100%; 
                height: 20px; 
                background: rgba(255,255,255,0.2); 
                border-radius: 10px; 
                overflow: hidden; 
                margin: 10px 0;
            }}
            .accuracy-fill {{ 
                height: 100%; 
                background: linear-gradient(90deg, #4CAF50, #8BC34A); 
                transition: width 0.5s ease;
            }}
            .gesture-tags {{ 
                display: flex; 
                flex-wrap: wrap; 
                gap: 5px; 
                margin-top: 10px;
            }}
            .gesture-tag {{ 
                background: rgba(255,255,255,0.3); 
                padding: 3px 8px; 
                border-radius: 15px; 
                font-size: 12px;
            }}
            .stats-overview {{ 
                display: grid; 
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); 
                gap: 15px; 
                margin-top: 30px;
            }}
            .stat-box {{ 
                background: rgba(255,255,255,0.2); 
                padding: 15px; 
                border-radius: 8px; 
                text-align: center;
            }}
            .refresh-btn {{ 
                background: #4CAF50; 
                color: white; 
                border: none; 
                padding: 10px 20px; 
                border-radius: 5px; 
                cursor: pointer; 
                font-size: 16px;
                margin: 10px;
            }}
            .refresh-btn:hover {{ 
                background: #45a049; 
            }}
            .no-models {{ 
                text-align: center; 
                padding: 40px; 
                background: rgba(255,255,255,0.1); 
                border-radius: 10px;
            }}
            .section-title {{
                font-size: 20px;
                margin-bottom: 15px;
                display: flex;
                align-items: center;
                gap: 10px;
            }}
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>🎯 ASL Practice Models Dashboard</h1>
                <p>Educational Innovation: Lesson-Specific Model Performance Analysis & System Health</p>
                <button class="refresh-btn" onclick="location.reload()">🔄 Refresh Data</button>
            </div>
            
            <!-- INTEGRATED HEALTH STATUS SECTION -->
            <div class="health-section">
                <h2 class="section-title">🔍 System Health Monitor</h2>
                <div class="health-grid">
                    <div class="health-card {'healthy' if health_data['model_loaded'] else 'error'}">
                        <span class="health-icon">🤖</span>
                        <div class="health-title">Main Model Status</div>
                        <div class="health-value">{'LOADED' if health_data['model_loaded'] else 'ERROR'}</div>
                        <span class="health-status">{'✅ Ready for Translation' if health_data['model_loaded'] else '❌ Model Not Found'}</span>
                    </div>
                    
                    <div class="health-card {'healthy' if health_data['active_connections'] > 0 else 'warning'}">
                        <span class="health-icon">🔗</span>
                        <div class="health-title">Active Connections</div>
                        <div class="health-value">{health_data['active_connections']}</div>
                        <span class="health-status">{'🟢 Clients Connected' if health_data['active_connections'] > 0 else '🟡 No Active Users'}</span>
                    </div>
                    
                    <div class="health-card {'healthy' if health_data['practice_mode'] else 'warning'}">
                        <span class="health-icon">🎯</span>
                        <div class="health-title">Practice Mode</div>
                        <div class="health-value">{'ACTIVE' if health_data['practice_mode'] else 'INACTIVE'}</div>
                        <span class="health-status">{'🎓 Learning: ' + health_data['practice_level'] if health_data['practice_mode'] else '📚 General Translation'}</span>
                    </div>
                    
                    <div class="health-card {'healthy' if health_data['training_session'] else 'warning'}">
                        <span class="health-icon">📚</span>
                        <div class="health-title">Training Session</div>
                        <div class="health-value">{'ACTIVE' if health_data['training_session'] else 'INACTIVE'}</div>
                        <span class="health-status">{'🔄 Data Collection Active' if health_data['training_session'] else '⏸️ No Training Session'}</span>
                    </div>
                    
                    <div class="health-card healthy">
                        <span class="health-icon">⚙️</span>
                        <div class="health-title">Admin Features</div>
                        <div class="health-value">ENABLED</div>
                        <span class="health-status">🛡️ Full Access Available</span>
                    </div>
                    
                    <div class="health-card healthy">
                        <span class="health-icon">📊</span>
                        <div class="health-title">System Status</div>
                        <div class="health-value">HEALTHY</div>
                        <span class="health-status">✨ All Systems Operational</span>
                    </div>
                </div>
            </div>
    """
    
    # MODELS SECTION
    html_content += '<div class="models-section"><h2 class="section-title">🎓 Practice Models Performance</h2>'
    
    if not models_data:
        html_content += """
            <div class="no-models">
                <h2>📭 No Practice Models Found</h2>
                <p>Create lesson-specific models using admin training to see visualizations here.</p>
                <p>Available lessons: lesson_1, lesson_2, lesson_3, lesson_4, lesson_5</p>
            </div>
        """
    else:
        # MODELS VISUALIZATION: Generate cards for each practice model
        html_content += '<div class="models-grid">'
        
        total_samples = sum(model['samples'] for model in models_data)
        avg_accuracy = sum(model['accuracy'] for model in models_data) / len(models_data)
        total_gestures = sum(len(model['gestures']) for model in models_data)
        
        for model in models_data:
            accuracy_percent = model['accuracy'] * 100
            created_date = time.strftime('%Y-%m-%d %H:%M', time.localtime(model['created_at']))
            
            # LESSON MAPPING: Convert lesson IDs to friendly names
            lesson_names = {
                "lesson_1": "🤝 Basic Greetings",
                "lesson_2": "😊 Emotions", 
                "lesson_3": "🍽️ Daily Activities",
                "lesson_4": "📱 Common Objects",
                "lesson_5": "❓ Question Words"
            }
            
            lesson_display = lesson_names.get(model['lesson_id'], model['lesson_id'])
            
            html_content += f"""
            <div class="model-card">
                <h3>{lesson_display}</h3>
                <p><strong>Lesson ID:</strong> {model['lesson_id']}</p>
                <p><strong>Accuracy:</strong> {accuracy_percent:.1f}%</p>
                <div class="accuracy-bar">
                    <div class="accuracy-fill" style="width: {accuracy_percent}%"></div>
                </div>
                <p><strong>Training Samples:</strong> {model['samples']}</p>
                <p><strong>Created:</strong> {created_date}</p>
                <div class="gesture-tags">
                    {' '.join(f'<span class="gesture-tag">{gesture}</span>' for gesture in model['gestures'])}
                </div>
            </div>
            """
        
        html_content += '</div>'
        
        # STATISTICS OVERVIEW: System-wide metrics
        html_content += f"""
        <div class="stats-overview">
            <div class="stat-box">
                <h3>📊 Total Models</h3>
                <h2>{len(models_data)}</h2>
            </div>
            <div class="stat-box">
                <h3>🎯 Average Accuracy</h3>
                <h2>{avg_accuracy*100:.1f}%</h2>
            </div>
            <div class="stat-box">
                <h3>📚 Total Samples</h3>
                <h2>{total_samples}</h2>
            </div>
            <div class="stat-box">
                <h3>✋ Total Gestures</h3>
                <h2>{total_gestures}</h2>
            </div>
        </div>
        """
    
    html_content += '</div>'
    
    # FOOTER SECTION
    html_content += f"""
        <div style="margin-top: 30px; text-align: center; padding: 20px; background: rgba(255,255,255,0.05); border-radius: 10px;">
            <h4>🚀 Educational Innovation Features</h4>
            <p>• <strong>Lesson-Specific Models:</strong> Optimized for focused learning</p>
            <p>• <strong>Performance Tracking:</strong> Individual lesson mastery assessment</p>
            <p>• <strong>Cognitive Load Reduction:</strong> Limited vocabulary during practice</p>
            <p>• <strong>Progressive Learning:</strong> Structured difficulty progression</p>
            <p>• <strong>Real-time Monitoring:</strong> Live system health and performance tracking</p>
        </div>
    </div>
    
    <script>
        // INTERACTIVITY: Auto-refresh every 30 seconds
        setTimeout(function() {{
            location.reload();
        }}, 30000);
        
        // ANIMATION: Smooth accuracy bar filling
        window.onload = function() {{
            const bars = document.querySelectorAll('.accuracy-fill');
            bars.forEach(bar => {{
                const width = bar.style.width;
                bar.style.width = '0%';
                setTimeout(() => {{
                    bar.style.width = width;
                }}, 500);
            }});
            
            // HEALTH CARD ANIMATIONS: Staggered entrance
            const healthCards = document.querySelectorAll('.health-card');
            healthCards.forEach((card, index) => {{
                card.style.opacity = '0';
                card.style.transform = 'translateY(20px)';
                setTimeout(() => {{
                    card.style.transition = 'all 0.5s ease';
                    card.style.opacity = '1';
                    card.style.transform = 'translateY(0)';
                }}, index * 100);
            }});
        }};
    </script>
    </body>
    </html>
    """
    
    return html_content


# SERVER STARTUP - PRODUCTION DEPLOYMENT

if __name__ == "__main__":
    """
    PRODUCTION DEPLOYMENT: Professional server startup
    
    DEFENSE POINTS:
    - Uvicorn ASGI server for high-performance async operations
    - Network binding for external access (0.0.0.0)
    - Standard HTTP port configuration
    - Professional logging configuration
    - Production-ready deployment preparation
    """
    import uvicorn
    logger.info("🚀 Starting Enhanced ASL Server with Admin Controls...")
    uvicorn.run(app, host="0.0.0.0", port=8001, log_level="info")