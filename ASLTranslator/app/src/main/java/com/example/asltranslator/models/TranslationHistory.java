package com.example.asltranslator.models;

public class TranslationHistory {
    private String historyId;
    private String gesture;
    private float confidence;
    private long timestamp;
    private String imageUrl;

    public TranslationHistory() {
        // Default constructor for Firebase
    }

    public TranslationHistory(String historyId, String gesture, float confidence, long timestamp) {
        this.historyId = historyId;
        this.gesture = gesture;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}