package com.example.asltranslator.models;

import java.util.HashMap;
import java.util.Map;

public class User {
    private String uid;
    private String email;
    private String displayName;
    private String profilePhotoUrl;
    private long createdAt;
    private long lastLoginAt;
    private Map<String, LessonProgress> progress;
    private Map<String, Achievement> achievements;
    private long totalPracticeTime;
    private int totalTranslations;
    private String merkleRoot;

    // Constructor
    public User() {
        this.progress = new HashMap<>();
        this.achievements = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.lastLoginAt = System.currentTimeMillis();
    }

    public User(String uid, String email, String displayName) {
        this();
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
    }

    // Getters and setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Map<String, LessonProgress> getProgress() {
        return progress;
    }

    public void setProgress(Map<String, LessonProgress> progress) {
        this.progress = progress;
    }

    public Map<String, Achievement> getAchievements() {
        return achievements;
    }

    public void setAchievements(Map<String, Achievement> achievements) {
        this.achievements = achievements;
    }

    public long getTotalPracticeTime() {
        return totalPracticeTime;
    }

    public void setTotalPracticeTime(long totalPracticeTime) {
        this.totalPracticeTime = totalPracticeTime;
    }

    public int getTotalTranslations() {
        return totalTranslations;
    }

    public void setTotalTranslations(int totalTranslations) {
        this.totalTranslations = totalTranslations;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }
}