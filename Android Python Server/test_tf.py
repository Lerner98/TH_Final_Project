import tensorflow as tf
model_path = "models/asl_model_tf"  # Adjust to ".h5" if needed
try:
    model = tf.keras.models.load_model(model_path)
    print("Model loaded successfully!")
except Exception as e:
    print("Model loading error:", e)