package com.example.asltranslator.models;

public class Achievement {
    private String id;  // This should match achievementId for consistency
    private String achievementId;
    private String name;
    private String description;
    private String iconUrl;
    private long unlockedAt;
    private String proofHash; // Zero-knowledge proof hash
    private int points;
    private boolean unlocked;

    // Default constructor (required for Firebase)
    public Achievement() {
        this.unlockedAt = 0; // 0 means locked
        this.unlocked = false;
    }

    // Primary constructor
    public Achievement(String achievementId, String name, String description, int points) {
        this.achievementId = achievementId;
        this.id = achievementId; // Keep id and achievementId synchronized
        this.name = name;
        this.description = description;
        this.points = points;
        this.unlockedAt = 0;
        this.unlocked = false;
    }

    // Getters
    public String getId() {
        // Return achievementId if id is null (for backward compatibility)
        return this.id != null ? this.id : this.achievementId;
    }

    public String getAchievementId() {
        return achievementId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public long getUnlockedAt() {
        return unlockedAt;
    }

    public String getProofHash() {
        return proofHash;
    }

    public int getPoints() {
        return points;
    }

    public boolean isUnlocked() {
        return unlockedAt > 0 || unlocked; // Check both conditions
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setAchievementId(String achievementId) {
        this.achievementId = achievementId;
        this.id = achievementId; // Keep synchronized
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public void setUnlockedAt(long unlockedAt) {
        this.unlockedAt = unlockedAt;
        this.unlocked = unlockedAt > 0; // Synchronize unlocked state
    }

    public void setProofHash(String proofHash) {
        this.proofHash = proofHash;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
        if (unlocked && this.unlockedAt == 0) {
            this.unlockedAt = System.currentTimeMillis(); // Auto-set unlock time
        }
    }
}