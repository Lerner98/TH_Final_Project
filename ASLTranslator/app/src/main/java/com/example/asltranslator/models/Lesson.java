package com.example.asltranslator.models;

public class Lesson {
    private String lessonId;
    private String title;
    private String description;
    private int iconResourceId;
    private int totalSteps;
    private LessonProgress progress;
    private boolean isLocked;

    public Lesson() {
        // Default constructor for Firebase
    }

    public Lesson(String lessonId, String title, String description,
                  int iconResourceId, int totalSteps) {
        this.lessonId = lessonId;
        this.title = title;
        this.description = description;
        this.iconResourceId = iconResourceId;
        this.totalSteps = totalSteps;
        this.isLocked = false;
    }

    // Getters and setters
    public String getLessonId() {
        return lessonId;
    }

    public void setLessonId(String lessonId) {
        this.lessonId = lessonId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getIconResourceId() {
        return iconResourceId;
    }

    public void setIconResourceId(int iconResourceId) {
        this.iconResourceId = iconResourceId;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public LessonProgress getProgress() {
        return progress;
    }

    public void setProgress(LessonProgress progress) {
        this.progress = progress;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public int getProgressPercentage() {
        if (progress == null || totalSteps == 0) return 0;
        return (progress.getScore() * 100) / totalSteps;
    }
}