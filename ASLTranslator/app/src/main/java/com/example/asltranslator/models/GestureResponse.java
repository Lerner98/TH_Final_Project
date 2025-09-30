package com.example.asltranslator.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GestureResponse {
    @SerializedName("timestamp")
    private double timestamp; // ← Changed from long to double to handle Python's time.time()

    @SerializedName("hand_detected")
    private boolean handDetected;

    @SerializedName("landmarks")
    private List<Landmark> landmarks;

    @SerializedName("gesture")
    private String gesture;

    @SerializedName("confidence")
    private float confidence;

    // Getters and setters
    public long getTimestamp() {
        // Convert double timestamp to long milliseconds for Android usage
        return Math.round(timestamp * 1000);
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    // Alternative method to get raw timestamp as double
    public double getRawTimestamp() {
        return timestamp;
    }

    public boolean isHandDetected() {
        return handDetected;
    }

    public void setHandDetected(boolean handDetected) {
        this.handDetected = handDetected;
    }

    public List<Landmark> getLandmarks() {
        return landmarks;
    }

    public void setLandmarks(List<Landmark> landmarks) {
        this.landmarks = landmarks;
    }

    public String getGesture() {
        return gesture;
    }

    public void setGesture(String gesture) {
        this.gesture = gesture;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    // Inner class for landmarks
    public static class Landmark {
        @SerializedName("x")
        private float x;

        @SerializedName("y")
        private float y;

        @SerializedName("z")
        private float z;

        // Getters and setters
        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }

        public float getZ() {
            return z;
        }

        public void setZ(float z) {
            this.z = z;
        }
    }
}