package com.example.asltranslator.models;

public class LessonProgress {
    private String lessonId;
    private String lessonName;
    private boolean completed;
    private int score;
    private long timestamp;
    private int attempts;
    private long practiceTime;
    private String merkleProof;

    // Constructor
    public LessonProgress() {
        this.timestamp = System.currentTimeMillis();
        this.attempts = 0;
        this.score = 0;
        this.practiceTime = 0;
    }

    public LessonProgress(String lessonId, String lessonName) {
        this();
        this.lessonId = lessonId;
        this.lessonName = lessonName;
    }

    // Getters and setters
    public String getLessonId() {
        return lessonId;
    }

    public void setLessonId(String lessonId) {
        this.lessonId = lessonId;
    }

    public String getLessonName() {
        return lessonName;
    }

    public void setLessonName(String lessonName) {
        this.lessonName = lessonName;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public long getPracticeTime() {
        return practiceTime;
    }

    public void setPracticeTime(long practiceTime) {
        this.practiceTime = practiceTime;
    }

    public String getMerkleProof() {
        return merkleProof;
    }

    public void setMerkleProof(String merkleProof) {
        this.merkleProof = merkleProof;
    }
}