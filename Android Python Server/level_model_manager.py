# level_model_manager.py - Isolated Practice Mode Management System
"""
FILE OVERVIEW - DEFENSE NOTES:
Practice Mode Model Manager for Educational ASL Learning

This module provides isolated model management for lesson-specific practice sessions,
enabling focused learning environments where students can practice targeted gesture sets
without interference from the broader vocabulary.

KEY ARCHITECTURAL DECISIONS:
- Lesson-specific model isolation prevents cross-lesson interference
- Dynamic model loading supports multiple concurrent lesson types
- Fallback mechanisms ensure system reliability during model transitions
- Comprehensive state management maintains practice session integrity

EDUCATIONAL BENEFITS:
- Reduces cognitive load by limiting gesture vocabulary during practice
- Enables progressive learning from basic to advanced concepts
- Supports assessment of lesson-specific mastery
- Provides targeted feedback for individual lesson performance

TECHNICAL IMPLEMENTATION:
- Dual model support: TensorFlow neural networks and Random Forest classifiers
- JSON metadata management for lesson configuration tracking
- Safe model switching with error recovery mechanisms
- Memory-efficient model lifecycle management
"""

# External package integration for specialized functionality
import os  # File system operations for model directory management
import pickle  # Serialization support for classical ML models
import numpy as np  # Numerical computing for feature processing
import logging  # Production-grade logging for debugging and monitoring
import tensorflow as tf  # Deep learning framework for neural network models
import json  # Configuration and metadata management
from typing import Dict, List, Tuple, Optional  # Type annotations for code clarity

logger = logging.getLogger(__name__)

# Data structure: Lesson curriculum definition
# Dictionary mapping lesson identifiers to gesture vocabularies
# Supports progressive difficulty and thematic organization
PRACTICE_LEVEL_GESTURES = {
    "lesson_1": ["Hello", "Thank You", "Yes", "No"],        # Basic social interactions
    "lesson_2": ["Happy", "Sad", "Angry", "Love"],          # Emotional expressions
    "lesson_3": ["Eat", "Drink", "Sleep", "Go"],            # Daily activities
    "lesson_4": ["Book", "Phone", "Car", "Home"],           # Common objects
    "lesson_5": ["What", "Where", "When", "Who"]            # Question formation
}

class IsolatedLevelModelManager:
    """
    Practice Mode Management System
    
    Provides isolated model environments for lesson-specific learning sessions.
    Manages dynamic loading and switching between lesson models while maintaining
    system stability and educational effectiveness.
    
    Architecture Principles:
    - Model isolation prevents vocabulary interference between lessons
    - Dynamic loading supports scalable lesson management
    - Graceful fallbacks ensure continuous system operation
    - State management maintains practice session integrity
    """
    
    def __init__(self):
        """Initialize practice mode manager with clean state"""
        # Directory structure for organized model storage
        self.level_models_dir = "level_models"
        
        # Current session state variables
        self.current_level_labels = []  # List: Active gesture vocabulary
        self.current_level_id = None    # String: Current lesson identifier
        self.is_practice_mode = False   # Boolean: Practice state flag
        
        # Model storage for lesson-specific classifiers
        self.current_level_model = None     # TensorFlow neural network
        self.current_level_rf_model = None  # Random Forest classifier
        self.current_level_metadata = None  # Dictionary: Lesson configuration
        
        # Ensure directory structure exists
        os.makedirs(self.level_models_dir, exist_ok=True)
        logger.info("Practice mode manager initialized with model loading capability")
    
    def enter_practice_mode(self, lesson_id: str) -> bool:
        """
        Activate practice mode for specified lesson
        
        Parameters:
            lesson_id: Identifier for target lesson (e.g., "lesson_1")
        
        Returns:
            Boolean indicating successful practice mode activation
        
        Educational Impact:
        - Isolates learning environment to specific gesture vocabulary
        - Loads lesson-optimized models for improved accuracy
        - Enables focused practice without distraction from other gestures
        """
        try:
            # Set lesson context
            self.current_level_id = lesson_id
            # Dictionary lookup: Retrieve gesture set for lesson
            self.current_level_labels = PRACTICE_LEVEL_GESTURES.get(lesson_id, [])
            
            # Attempt to load lesson-specific models
            if self._load_level_model(lesson_id):
                self.is_practice_mode = True
                logger.info(f"Entered practice mode: {lesson_id} with gestures: {self.current_level_labels}")
                logger.info(f"Loaded lesson-specific model for {lesson_id}")
                return True
            else:
                logger.error(f"Failed to load model for {lesson_id}")
                return False
            
        except Exception as e:
            # Error handling: Graceful failure with logging
            logger.error(f"Failed to enter practice mode: {e}")
            return False
    
    def _load_level_model(self, lesson_id: str) -> bool:
        """
        Load lesson-specific machine learning models
        
        Implements dual model loading strategy with fallback mechanisms.
        Supports both TensorFlow neural networks and Random Forest classifiers
        to ensure system reliability across different model types.
        
        Data Management:
        - Validates model directory existence
        - Loads JSON metadata for lesson configuration
        - Attempts TensorFlow model loading with error recovery
        - Provides Random Forest fallback for system resilience
        """
        try:
            # Construct lesson model directory path
            lesson_model_path = os.path.join(self.level_models_dir, lesson_id)
            
            # Validate model directory exists
            if not os.path.exists(lesson_model_path):
                logger.error(f"Model directory not found: {lesson_model_path}")
                return False
            
            # Load lesson metadata for configuration
            metadata_path = os.path.join(lesson_model_path, "training_metadata.json")
            if os.path.exists(metadata_path):
                # File operations: JSON configuration loading
                with open(metadata_path, 'r') as f:
                    self.current_level_metadata = json.load(f)
                logger.info(f"Loaded metadata for {lesson_id}: {self.current_level_metadata.get('gestures', [])}")
            
            # Attempt TensorFlow model loading
            tf_model_path = os.path.join(lesson_model_path, "asl_model_tf")
            if os.path.exists(tf_model_path):
                try:
                    # TensorFlow model loading with error handling
                    self.current_level_model = tf.keras.models.load_model(tf_model_path)
                    logger.info(f"Loaded TensorFlow model for {lesson_id}")
                except Exception as e:
                    logger.warning(f"Failed to load TensorFlow model: {e}")
                    self.current_level_model = None
            
            # Attempt Random Forest model loading as backup
            rf_model_path = os.path.join(lesson_model_path, "asl_model.pkl")
            if os.path.exists(rf_model_path):
                try:
                    # Pickle deserialization for classical ML model
                    with open(rf_model_path, 'rb') as f:
                        self.current_level_rf_model = pickle.load(f)
                    logger.info(f"Loaded Random Forest model for {lesson_id}")
                except Exception as e:
                    logger.warning(f"Failed to load Random Forest model: {e}")
                    self.current_level_rf_model = None
            
            # Validate successful model loading
            if self.current_level_model is not None or self.current_level_rf_model is not None:
                logger.info(f"Successfully loaded lesson model for {lesson_id}")
                return True
            else:
                logger.error(f"No valid models found for {lesson_id}")
                return False
                
        except Exception as e:
            # Error handling: Comprehensive exception management
            logger.error(f"Error loading level model for {lesson_id}: {e}")
            return False
    
    def exit_practice_mode(self):
        """
        Deactivate practice mode and return to normal translation
        
        Performs clean state reset to ensure no interference with normal
        translation operations. Clears all lesson-specific models and
        configuration to prevent memory leaks and state conflicts.
        
        Memory Management:
        - Resets practice mode flags
        - Clears lesson-specific gesture vocabulary
        - Unloads lesson models to free memory
        - Resets metadata configuration
        """
        # Reset practice mode state
        self.is_practice_mode = False
        self.current_level_labels = []  # List: Clear gesture vocabulary
        self.current_level_id = None
        
        # Clear loaded models to free memory
        self.current_level_model = None
        self.current_level_rf_model = None
        self.current_level_metadata = None
        
        logger.info("Exited practice mode - cleared lesson models, back to normal translation")
    
    def filter_prediction_for_practice(self, main_gesture: str, main_confidence: float) -> Tuple[str, float]:
        """
        Filter predictions based on practice mode context
        
        Legacy method maintained for backward compatibility.
        In current implementation, lesson-specific models handle
        prediction directly rather than filtering main model output.
        
        Returns:
            Tuple containing gesture name and confidence score
        """
        if not self.is_practice_mode:
            # Pass through original prediction when not in practice mode
            return main_gesture, main_confidence
        
        if not self.current_level_labels:
            # No lesson restrictions active
            return main_gesture, main_confidence
        
        # Lesson-specific model handling
        if self.current_level_model is not None or self.current_level_rf_model is not None:
            logger.debug(f"Using lesson-specific model for {self.current_level_id}")
            return main_gesture, main_confidence
        
        # Fallback: Validate prediction against lesson vocabulary
        if main_gesture in self.current_level_labels:
            return main_gesture, main_confidence
        
        # Provide practice guidance for out-of-lesson gestures
        if self.current_level_labels:
            return f"Practice: {', '.join(self.current_level_labels)}", 0.3
        
        return "Unknown", 0.0
    
    def predict_with_level_model(self, features: np.ndarray) -> Tuple[str, float]:
        """
        Generate predictions using lesson-specific models
        
        Primary prediction method for practice mode sessions.
        Implements dual model strategy with TensorFlow preference
        and Random Forest fallback for maximum reliability.
        
        Parameters:
            features: NumPy array containing 63-dimensional hand landmark features
        
        Returns:
            Tuple containing predicted gesture name and confidence score
        
        Machine Learning Pipeline:
        - Validates practice mode status
        - Attempts TensorFlow neural network prediction
        - Falls back to Random Forest if neural network unavailable
        - Uses lesson-specific gesture labels for output mapping
        - Provides comprehensive error handling and logging
        """
        if not self.is_practice_mode:
            return "No practice mode", 0.0
        
        try:
            # TensorFlow model prediction pathway
            if self.current_level_model is not None:
                # Reshape features for model input requirements
                features_reshaped = features.reshape(1, -1)
                # Generate prediction probabilities
                predictions = self.current_level_model.predict(features_reshaped, verbose=0)
                confidence = float(np.max(predictions))  # Extract maximum confidence
                predicted_class = int(np.argmax(predictions))  # Get predicted class index
                
                # Map class index to gesture label using lesson metadata
                if self.current_level_metadata and 'gestures' in self.current_level_metadata:
                    gesture_labels = self.current_level_metadata['gestures']
                else:
                    # Fallback to lesson definition
                    gesture_labels = self.current_level_labels
                
                # Validate class index and return result
                if predicted_class < len(gesture_labels):
                    gesture = gesture_labels[predicted_class]
                    logger.debug(f"Level model prediction: {gesture} ({confidence:.3f})")
                    return gesture, confidence
            
            # Random Forest model fallback pathway
            elif self.current_level_rf_model is not None:
                # Reshape features for scikit-learn model input
                features_reshaped = features.reshape(1, -1)
                # Generate class prediction
                prediction = self.current_level_rf_model.predict(features_reshaped)[0]
                # Extract confidence from probability distribution
                confidence = float(np.max(self.current_level_rf_model.predict_proba(features_reshaped)[0]))
                
                # Map prediction to gesture label
                if self.current_level_metadata and 'gestures' in self.current_level_metadata:
                    gesture_labels = self.current_level_metadata['gestures']
                else:
                    gesture_labels = self.current_level_labels
                
                # Validate prediction and return result
                if prediction < len(gesture_labels):
                    gesture = gesture_labels[prediction]
                    logger.debug(f"Random Forest model prediction: {gesture} ({confidence:.3f})")
                    return gesture, confidence
            
            # No models available for prediction
            logger.warning("No level model available for prediction")
            return "No model", 0.0
            
        except Exception as e:
            # Error handling: Graceful failure with logging
            logger.error(f"Error in level model prediction: {e}")
            return "Error", 0.0
    
    def get_level_info(self) -> Dict:
        """
        Retrieve comprehensive practice session information
        
        Provides detailed status information for debugging, monitoring,
        and client application state synchronization.
        
        Returns:
            Dictionary containing complete practice mode status
        
        Information Provided:
        - Practice mode activation status
        - Current lesson identifier
        - Available gesture vocabulary for lesson
        - Model loading status
        - Lesson metadata and configuration
        - Human-readable status message
        """
        return {
            "is_practice_mode": self.is_practice_mode,
            "current_level": self.current_level_id,
            "allowed_gestures": self.current_level_labels,
            "has_level_model": self.current_level_model is not None or self.current_level_rf_model is not None,
            "model_metadata": self.current_level_metadata,
            "message": f"Practice mode: {self.current_level_id}" if self.is_practice_mode else "Normal translation mode"
        }

# Module-level instance creation for singleton pattern
# Ensures consistent state management across the application
isolated_level_manager = IsolatedLevelModelManager()

"""
DEFENSE SUMMARY FOR LEVEL_MODEL_MANAGER.PY:

EDUCATIONAL INNOVATION:
This module implements sophisticated educational psychology principles by providing
isolated learning environments. Students can focus on specific gesture vocabularies
without cognitive interference from the broader ASL lexicon, enabling more effective
skill acquisition and assessment.

TECHNICAL ARCHITECTURE:
The manager implements a dual-model strategy supporting both TensorFlow neural networks
and Random Forest classifiers. This approach ensures system reliability through
redundancy while optimizing performance for lesson-specific gesture recognition.

DATA STRUCTURE UTILIZATION:
- Dictionary: Lesson gesture mappings and metadata storage
- Lists: Dynamic gesture vocabulary management
- Tuples: Type-safe function returns with gesture/confidence pairs
- Boolean flags: State management for practice mode activation
- JSON objects: Configuration persistence and metadata tracking

MACHINE LEARNING INTEGRATION:
The system supports multiple ML frameworks simultaneously, providing flexibility
in model deployment and ensuring compatibility with different training approaches.
Feature processing maintains consistency with the main translation pipeline while
enabling lesson-specific optimization.

PRODUCTION CONSIDERATIONS:
Comprehensive error handling and logging ensure system stability during model
transitions. Memory management prevents resource leaks during frequent practice
session changes. The modular design supports easy integration with larger
educational management systems.

SCALABILITY FEATURES:
The directory-based model organization supports unlimited lesson expansion.
Dynamic loading enables efficient memory usage for large curriculum systems.
The isolated architecture prevents cross-lesson interference in multi-user
educational environments.
"""