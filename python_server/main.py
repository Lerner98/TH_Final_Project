# enhanced_main.py - OPTIMIZED FOR MINIMAL LATENCY
# enhanced_main.py - OPTIMIZED FOR MINIMAL LATENCY
import base64
import cv2
import numpy as np
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import mediapipe as mp
import asyncio
import json
import logging
import os
import tensorflow as tf
from typing import Dict, List, Optional
import time
from concurrent.futures import ThreadPoolExecutor
import threading
from queue import Queue, Empty
import socket


def find_free_port(start_port=8000, max_tries=50):
    """
    Try to bind from start_port upwards until a free one is found.
    Returns the free port number.
    """
    for port in range(start_port, start_port + max_tries):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            try:
                s.bind(("0.0.0.0", port))
                return port  # Success → return free port
            except OSError:
                continue
    raise RuntimeError(f"No free ports found in range {start_port}-{start_port + max_tries}")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="ASL Translation Server - Optimized")

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# PERFORMANCE OPTIMIZATIONS
MAX_WORKERS = 4  # Thread pool size for parallel processing
PROCESSING_QUEUE_SIZE = 10  # Limit queue to prevent memory buildup
TARGET_IMAGE_SIZE = (480, 640)  # Smaller size for faster processing
JPEG_QUALITY = 70  # Balance between quality and speed

# Initialize MediaPipe Hands with OPTIMIZED settings for speed
mp_hands = mp.solutions.hands.Hands(
    static_image_mode=False,
    max_num_hands=1,
    min_detection_confidence=0.6,  # Slightly lower for faster detection
    min_tracking_confidence=0.4,   # Lower for smoother tracking
    model_complexity=0  # Use LITE model for speed (0=fastest, 1=balanced, 2=accurate)
)

mp_drawing = mp.solutions.drawing_utils

# ASL Labels
LABELS = ["Yes", "No", "I Love You", "Hello", "Thank You"]

# Thread pool for parallel processing
executor = ThreadPoolExecutor(max_workers=MAX_WORKERS)

class OptimizedASLModel:
    def __init__(self):
        self.model = None
        self.model_loaded = False
        self.inference_lock = threading.RLock()
        self.load_model()
    
    def load_model(self):
        """Load TensorFlow model with optimizations"""
        try:
            model_path = os.path.join(os.path.dirname(__file__), 'models', 'asl_model_tf')
            if os.path.exists(model_path):
                self.model = tf.saved_model.load(model_path)
                logger.info("✅ TensorFlow SavedModel loaded")
                # Warm up the model
                dummy_input = tf.constant(np.zeros((1, 63), dtype=np.float32))
                infer = self.model.signatures.get("serving_default")
                if infer:
                    _ = infer(dummy_input)
                    self.model_loaded = True
                    logger.info("✅ Model warmed up")
                else:
                    logger.warning("⚠️ No serving_default signature found, using rule-based fallback")
                    self.model_loaded = False
            else:
                logger.warning("⚠️ Model not found, using optimized rule-based classification")
                self.model_loaded = False
        except Exception as e:
            logger.error(f"❌ Failed to load model: {e}")
            self.model_loaded = False
    
    def predict_fast(self, features: np.ndarray) -> tuple[str, float]:
        """Ultra-fast prediction with thread safety"""
        with self.inference_lock:
            if self.model_loaded and self.model is not None:
                try:
                    start_time = time.perf_counter()
                    features_reshaped = tf.constant(features.reshape(1, -1), dtype=tf.float32)
                    if hasattr(self.model, "signatures"):
                        # SavedModel inference
                        infer = self.model.signatures["serving_default"]
                        predictions = infer(features_reshaped)
                        # Adjust based on actual output tensor name (inspect signatures if needed)
                        output_key = list(predictions.keys())[0]
                        predictions = predictions[output_key].numpy()
                    else:
                        # Fallback to Keras model inference (if applicable)
                        predictions = self.model.predict(features_reshaped, verbose=0)
                    confidence = float(np.max(predictions))
                    predicted_class = int(np.argmax(predictions))
                    gesture = LABELS[predicted_class] if predicted_class < len(LABELS) else "Unknown"
                    inference_time = (time.perf_counter() - start_time) * 1000
                    if inference_time > 50:
                        logger.warning(f"⚠️ Slow inference: {inference_time:.1f}ms")
                    return gesture, confidence
                except Exception as e:
                    logger.error(f"Model prediction error: {e}")
                    return self.rule_based_prediction_fast(features)
            else:
                return self.rule_based_prediction_fast(features)
    
    def rule_based_prediction_fast(self, features: np.ndarray) -> tuple[str, float]:
        """Ultra-fast rule-based gesture recognition"""
        try:
            # Convert flattened features back to landmarks (optimized)
            landmarks = features.reshape(21, 3)
            
            # Extract key points (pre-calculated indices for speed)
            wrist_y = landmarks[0, 1]
            thumb_tip_y = landmarks[4, 1]
            thumb_ip_y = landmarks[3, 1]
            index_tip_y = landmarks[8, 1]
            index_pip_y = landmarks[6, 1]
            middle_tip_y = landmarks[12, 1]
            middle_pip_y = landmarks[10, 1]
            ring_tip_y = landmarks[16, 1]
            ring_pip_y = landmarks[14, 1]
            pinky_tip_y = landmarks[20, 1]
            pinky_pip_y = landmarks[18, 1]
            
            # Fast finger detection (vectorized operations)
            fingers_up = [
                thumb_tip_y < thumb_ip_y,  # Thumb
                index_tip_y < index_pip_y,  # Index
                middle_tip_y < middle_pip_y,  # Middle
                ring_tip_y < ring_pip_y,  # Ring
                pinky_tip_y < pinky_pip_y   # Pinky
            ]
            
            fingers_up_count = sum(fingers_up)
            
            # Optimized gesture classification
            if fingers_up_count == 5:
                return "Hello", 0.85
            elif fingers_up_count == 2 and fingers_up[1] and fingers_up[4]:  # Index + Pinky
                return "I Love You", 0.8
            elif fingers_up_count == 1 and fingers_up[0]:  # Just thumb
                return "Yes", 0.75
            elif fingers_up_count == 2 and fingers_up[1] and fingers_up[2]:  # Index + Middle
                return "No", 0.75
            elif fingers_up_count == 0:
                return "Thank You", 0.7
            else:
                return "Unknown", 0.3
                
        except Exception as e:
            logger.error(f"Rule-based prediction error: {e}")
            return "Unknown", 0.1

# Initialize optimized model
asl_model = OptimizedASLModel()

def resize_image_fast(image: np.ndarray) -> np.ndarray:
    """Fast image resizing for consistent processing"""
    height, width = image.shape[:2]
    
    # Only resize if image is significantly larger
    if width > TARGET_IMAGE_SIZE[1] or height > TARGET_IMAGE_SIZE[0]:
        return cv2.resize(image, TARGET_IMAGE_SIZE, interpolation=cv2.INTER_LINEAR)
    
    return image

def extract_hand_landmarks_fast(image: np.ndarray) -> tuple[Optional[np.ndarray], bool]:
    """Ultra-fast hand landmark extraction"""
    try:
        start_time = time.perf_counter()
        
        # Resize for faster processing
        processed_image = resize_image_fast(image)
        
        # Convert BGR to RGB (MediaPipe requirement)
        rgb_image = cv2.cvtColor(processed_image, cv2.COLOR_BGR2RGB)
        
        # Process with MediaPipe
        results = mp_hands.process(rgb_image)
        
        processing_time = (time.perf_counter() - start_time) * 1000
        
        if results.multi_hand_landmarks:
            # Extract first hand landmarks only (fastest path)
            hand_landmarks = results.multi_hand_landmarks[0]
            
            # Fast feature extraction (list comprehension)
            features = [coord for landmark in hand_landmarks.landmark 
                       for coord in (landmark.x, landmark.y, landmark.z)]
            
            if processing_time > 30:  # Log slow processing
                logger.warning(f"⚠️ Slow landmark extraction: {processing_time:.1f}ms")
            
            return np.array(features, dtype=np.float32), True
        
        return None, False
        
    except Exception as e:
        logger.error(f"Landmark extraction error: {e}")
        return None, False

def process_frame_sync(image_data: str, timestamp: float) -> dict:
    """Synchronous frame processing for thread pool"""
    try:
        frame_start = time.perf_counter()
        
        # Decode base64 image (optimized)
        if "," in image_data:
            image_data = image_data.split(",", 1)[1]
        
        img_bytes = base64.b64decode(image_data)
        np_arr = np.frombuffer(img_bytes, np.uint8)
        image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        
        if image is None:
            return {"error": "Failed to decode image", "timestamp": timestamp}
        
        decode_time = (time.perf_counter() - frame_start) * 1000
        
        # Extract hand landmarks
        landmark_start = time.perf_counter()
        features, hand_detected = extract_hand_landmarks_fast(image)
        landmark_time = (time.perf_counter() - landmark_start) * 1000
        
        # Prepare response
        response = {
            "timestamp": timestamp,
            "hand_detected": hand_detected,
            "gesture": "None",
            "confidence": 0.0,
            "processing_times": {
                "decode_ms": round(decode_time, 1),
                "landmark_ms": round(landmark_time, 1),
                "total_ms": 0
            }
        }
        
        # Predict gesture if hand detected
        if hand_detected and features is not None:
            prediction_start = time.perf_counter()
            gesture, confidence = asl_model.predict_fast(features)
            prediction_time = (time.perf_counter() - prediction_start) * 1000
            
            response["gesture"] = gesture
            response["confidence"] = confidence
            response["processing_times"]["prediction_ms"] = round(prediction_time, 1)
            
            if confidence > 0.5:  # Only log confident predictions
                logger.info(f"🤟 {gesture} ({confidence:.2f}) - Total: {round((time.perf_counter() - frame_start) * 1000, 1)}ms")
        
        response["processing_times"]["total_ms"] = round((time.perf_counter() - frame_start) * 1000, 1)
        return response
        
    except Exception as e:
        logger.error(f"Frame processing error: {e}")
        return {"error": f"Processing failed: {str(e)}", "timestamp": timestamp}

class OptimizedConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []
        self.processing_queue = Queue(maxsize=PROCESSING_QUEUE_SIZE)
        self.connection_lock = threading.RLock()
    
    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        with self.connection_lock:
            self.active_connections.append(websocket)
        logger.info(f"📱 Client connected. Total: {len(self.active_connections)}")
    
    def disconnect(self, websocket: WebSocket):
        with self.connection_lock:
            if websocket in self.active_connections:
                self.active_connections.remove(websocket)
        logger.info(f"📱 Client disconnected. Total: {len(self.active_connections)}")

manager = OptimizedConnectionManager()

@app.websocket("/asl-ws")
async def asl_websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    
    try:
        while True:
            # Receive frame data
            data = await websocket.receive_text()
            receive_time = time.perf_counter()
            
            try:
                frame_data = json.loads(data)
            except json.JSONDecodeError:
                await websocket.send_json({"error": "Invalid JSON data"})
                continue
            
            if "frame" not in frame_data:
                continue
            
            # Get timestamp for latency measurement
            timestamp = frame_data.get("timestamp", receive_time)
            client_timestamp = frame_data.get("client_timestamp", receive_time * 1000)
            
            # CRITICAL: Process frame in thread pool for non-blocking operation
            try:
                # Submit to thread pool for parallel processing
                future = executor.submit(
                    process_frame_sync, 
                    frame_data["frame"], 
                    timestamp
                )
                
                # Wait for result with timeout to prevent blocking
                result = await asyncio.get_event_loop().run_in_executor(None, future.result, 2.0)
                
                # Add network latency info
                result["network_latency_ms"] = round((receive_time * 1000) - client_timestamp, 1)
                
                # Send response immediately
                await websocket.send_json(result)
                
            except Exception as e:
                logger.error(f"Processing error: {e}")
                await websocket.send_json({
                    "error": f"Processing failed: {str(e)}", 
                    "timestamp": timestamp
                })
            
    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        manager.disconnect(websocket)

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "model_loaded": asl_model.model_loaded,
        "active_connections": len(manager.active_connections),
        "thread_pool_size": MAX_WORKERS,
        "optimization_mode": "ultra_fast"
    }

@app.get("/performance")
async def performance_info():
    """Performance monitoring endpoint"""
    return {
        "target_image_size": TARGET_IMAGE_SIZE,
        "jpeg_quality": JPEG_QUALITY,
        "mediapipe_model": "lite",
        "thread_pool_workers": MAX_WORKERS,
        "processing_queue_size": PROCESSING_QUEUE_SIZE
    }

@app.get("/")
async def root():
    return {
        "message": "ASL Translation Server - Optimized for Speed",
        "optimizations": [
            "Parallel processing with ThreadPoolExecutor",
            "MediaPipe Lite model for faster inference",
            "Optimized image resizing",
            "Fast rule-based fallback",
            "Thread-safe model inference",
            "Performance monitoring"
        ],
        "endpoints": {
            "websocket": "/asl-ws",
            "health": "/health",
            "performance": "/performance"
        }
    }

if __name__ == "__main__":
    import uvicorn
    logger.info("🚀 Starting OPTIMIZED ASL Translation Server...")
    logger.info(f"⚡ Thread pool size: {MAX_WORKERS}")
    logger.info(f"📐 Target image size: {TARGET_IMAGE_SIZE}")
    logger.info(f"🧠 MediaPipe model: Lite (fastest)")
    
    uvicorn.run(
        app, 
        host="0.0.0.0", 
        port=8000,
        log_level="info",
        access_log=False,  # Disable access logs for performance
        workers=1  # Single worker to avoid model loading multiple times
    )