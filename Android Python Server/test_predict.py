import numpy as np
import tensorflow as tf
from main import ASLModel  # Adjust import based on your project structure

# Initialize ASLModel
asl_model = ASLModel()
asl_model.load_model()

# Simulate input features (adjust shape based on your model)
features = np.zeros((63,), dtype=np.float32)  # Example: 63 features for hand landmarks
gesture, confidence = asl_model.predict(features)
print(f"Predicted gesture: {gesture}, Confidence: {confidence}")