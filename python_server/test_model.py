import tensorflow as tf
import numpy as np
import os
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

model_path = "C:/TransltionHubFinalVer 8.20.25/TranslationHub/python_server/models/asl_model_tf"
try:
    model = tf.saved_model.load(model_path)
    logger.info("Model loaded successfully using saved_model.load!")
    infer = model.signatures.get("serving_default")
    if infer:
        logger.info("Inference signature found!")
        dummy_input = tf.constant(np.zeros((1, 63), dtype=np.float32))
        _ = infer(dummy_input)
        logger.info("Model warmed up successfully!")
    else:
        logger.warning("No serving_default signature found.")
except Exception as e:
    logger.error(f"Error loading model with saved_model.load: {e}")