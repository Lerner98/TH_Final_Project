package com.example.asltranslator.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.asltranslator.models.Achievement;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Centralized Achievement Management System
 * Handles all achievement unlocking, tracking, and validation across the entire app
 */
public class AchievementManager {

    private static final String TAG = "AchievementManager";
    private static AchievementManager instance;

    private DatabaseReference mDatabase;
    private String userId;

    private AchievementManager() {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    public static synchronized AchievementManager getInstance() {
        if (instance == null) {
            instance = new AchievementManager();
        }
        return instance;
    }

    /**
     * Get all predefined achievements for display in AchievementsActivity
     */
    public static List<Achievement> getAllPredefinedAchievements() {
        List<Achievement> achievements = new ArrayList<>();

        // Learning Achievements
        achievements.add(new Achievement("first_lesson", "First Steps",
                "Complete your first ASL lesson", 10));
        achievements.add(new Achievement("master_learner", "Master Learner",
                "Complete all ASL lessons", 50));
        achievements.add(new Achievement("perfect_practice", "Perfect Practice",
                "Achieve 90%+ accuracy in practice", 25));
        achievements.add(new Achievement("dedicated_learner", "Dedicated Learner",
                "Practice for 5+ minutes continuously", 15));

        // Practice Achievements
        achievements.add(new Achievement("gesture_master", "Gesture Master",
                "Master 10 different gestures", 30));
        achievements.add(new Achievement("speed_demon", "Speed Demon",
                "Complete a lesson in under 5 minutes", 20));
        achievements.add(new Achievement("consistency_king", "Consistency King",
                "Practice 3 days in a row", 25));

        // Advanced Achievements
        achievements.add(new Achievement("trainer", "AI Trainer",
                "Complete model training successfully", 40));
        achievements.add(new Achievement("early_bird", "Early Bird",
                "Practice before 8 AM", 15));
        achievements.add(new Achievement("night_owl", "Night Owl",
                "Practice after 10 PM", 15));

        return achievements;
    }

    /**
     * Universal method to unlock any achievement with duplicate prevention
     */
    public void unlockAchievement(Context context, String achievementId, String name, String description) {
        if (userId == null) {
            Log.w(TAG, "Cannot unlock achievement - user not authenticated");
            return;
        }

        // Check if achievement is already unlocked
        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("achievements")
                .child(achievementId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Achievement existingAchievement = snapshot.getValue(Achievement.class);
                        if (existingAchievement == null || !existingAchievement.isUnlocked()) {
                            // Achievement not unlocked yet, unlock it
                            Achievement achievement = new Achievement(achievementId, name, description, getPointsForAchievement(achievementId));
                            achievement.setUnlocked(true);
                            achievement.setUnlockedAt(System.currentTimeMillis());

                            mDatabase.child(Constants.USERS_NODE)
                                    .child(userId)
                                    .child("achievements")
                                    .child(achievementId)
                                    .setValue(achievement)
                                    .addOnSuccessListener(aVoid -> {
                                        if (context != null) {
                                            Toast.makeText(context, "🏆 Achievement Unlocked: " + name, Toast.LENGTH_LONG).show();
                                        }
                                        Log.d(TAG, "🏆 Achievement unlocked: " + name);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to unlock achievement: " + name, e);
                                    });
                        } else {
                            Log.d(TAG, "🏆 Achievement already unlocked: " + name);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check existing achievement", error.toException());
                    }
                });
    }

    /**
     * Check time-based achievements (Early Bird, Night Owl)
     */
    public void checkTimeBasedAchievements(Context context) {
        if (userId == null) return;

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("lastTimeCheck")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String lastCheck = snapshot.getValue(String.class);

                        if (!today.equals(lastCheck)) {
                            // Early Bird: 5 AM - 8 AM
                            if (hour >= 5 && hour < 8) {
                                unlockAchievement(context, "early_bird", "Early Bird", "Used app before 8 AM");
                                Log.d(TAG, "🌅 Early Bird achievement triggered at " + hour + ":xx");
                            }

                            // Night Owl: 10 PM - 3 AM
                            if (hour >= 22 || hour < 3) {
                                unlockAchievement(context, "night_owl", "Night Owl", "Used app after 10 PM");
                                Log.d(TAG, "🦉 Night Owl achievement triggered at " + hour + ":xx");
                            }

                            // Update last check date
                            mDatabase.child(Constants.USERS_NODE)
                                    .child(userId)
                                    .child("lastTimeCheck")
                                    .setValue(today);

                            Log.d(TAG, "⏰ Time-based achievements checked for today: " + today + " at " + hour + ":xx");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check time achievements", error.toException());
                    }
                });
    }

    /**
     * Update practice consistency streak
     */
    public void updateConsistencyStreak(Context context) {
        if (userId == null) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("practiceHistory")
                .child(today)
                .setValue(true)
                .addOnSuccessListener(aVoid -> {
                    checkConsistencyAchievement(context);
                });
    }

    /**
     * Get points required for a specific level (centralized from AchievementsActivity)
     */
    public int getPointsRequiredForLevel(int level) {
        switch (level) {
            case 1: return 0;
            case 2: return 50;
            case 3: return 120;
            case 4: return 220;
            case 5: return 350;
            default: return 500; // For level 6+ (max)
        }
    }

    /**
     * Check if user practiced for 3 consecutive days
     */
    private void checkConsistencyAchievement(Context context) {
        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("practiceHistory")
                .orderByKey()
                .limitToLast(3)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.getChildrenCount() >= 3) {
                            List<String> dates = new ArrayList<>();
                            for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                                dates.add(dateSnapshot.getKey());
                            }

                            if (areConsecutiveDays(dates)) {
                                unlockAchievement(context, "consistency_king", "Consistency King", "Practice 3 days in a row");
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check consistency", error.toException());
                    }
                });
    }

    /**
     * Check practice-related achievements
     */
    public void checkPracticeAchievements(Context context, double accuracy, long practiceTime) {
        // Perfect Practice achievement
        if (accuracy >= 0.9) {
            unlockAchievement(context, "perfect_practice", "Perfect Practice", "Achieved 90%+ accuracy in practice");
        }

        // Dedicated Learner achievement
        if (practiceTime >= 300000) { // 5 minutes
            unlockAchievement(context, "dedicated_learner", "Dedicated Learner", "Practiced for 5+ minutes continuously");
        }

        // Speed Demon achievement - if completed quickly with good accuracy
        if (practiceTime <= 300000 && accuracy >= 0.8) { // Under 5 minutes with 80%+ accuracy
            unlockAchievement(context, "speed_demon", "Speed Demon", "Completed practice quickly with high accuracy");
        }

        // 🆕 MISSING: Check for gesture mastery based on practice attempts
        checkGestureMasteryAchievement(context);
    }

    /**
     * 🆕 MISSING METHOD: Check gesture mastery achievement
     */
    private void checkGestureMasteryAchievement(Context context) {
        // Count total successful gestures across all lessons
        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("lessonStats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int totalMasteredGestures = 0;
                        for (DataSnapshot lessonSnapshot : snapshot.getChildren()) {
                            DataSnapshot highAccuracySnapshot = lessonSnapshot.child("highAccuracyCount");
                            Integer highAccuracyCount = highAccuracySnapshot.getValue(Integer.class);
                            if (highAccuracyCount != null && highAccuracyCount >= 5) {
                                totalMasteredGestures++;
                            }
                        }

                        if (totalMasteredGestures >= 10) {
                            unlockAchievement(context, "gesture_master", "Gesture Master", "Master 10 different gestures");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check gesture mastery", error.toException());
                    }
                });
    }

    /**
     * Check lesson completion achievements
     */
    public void checkLessonCompletionAchievements(Context context, String completedLessonId) {
        // First lesson achievement
        if ("lesson_1".equals(completedLessonId)) {
            unlockAchievement(context, "first_lesson", "First Steps", "Completed your first ASL lesson!");
        }

        // Check if all lessons are completed for master learner achievement
        if ("lesson_5".equals(completedLessonId)) {
            checkAllLessonsCompleted(context);
        }
    }

    /**
     * Check if all lessons are completed
     */
    private void checkAllLessonsCompleted(Context context) {
        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child(Constants.PROGRESS_NODE)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean allCompleted = true;
                        String[] lessonIds = {"lesson_1", "lesson_2", "lesson_3", "lesson_4", "lesson_5"};

                        for (String lessonId : lessonIds) {
                            DataSnapshot lessonSnapshot = snapshot.child(lessonId);
                            Boolean completed = lessonSnapshot.child("completed").getValue(Boolean.class);
                            if (completed == null || !completed) {
                                allCompleted = false;
                                break;
                            }
                        }

                        if (allCompleted) {
                            unlockAchievement(context, "master_learner", "Master Learner", "Completed all ASL lessons!");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check all lessons completed", error.toException());
                    }
                });
    }

    /**
     * 🆕 MISSING METHOD: Unlock trainer achievement (called from TrainingActivity)
     */
    public void unlockTrainerAchievement(Context context) {
        unlockAchievement(context, "trainer", "AI Trainer", "Successfully completed model training");
    }

    /**
     * Utility method to check if dates are consecutive
     */
    private boolean areConsecutiveDays(List<String> dates) {
        if (dates.size() < 3) return false;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date[] parsedDates = new Date[dates.size()];

            for (int i = 0; i < dates.size(); i++) {
                parsedDates[i] = sdf.parse(dates.get(i));
            }

            for (int i = 1; i < parsedDates.length; i++) {
                long diff = parsedDates[i].getTime() - parsedDates[i-1].getTime();
                long dayInMillis = 24 * 60 * 60 * 1000;
                if (Math.abs(diff - dayInMillis) > dayInMillis) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking consecutive days", e);
            return false;
        }
    }

    /**
     * Get points for specific achievement
     */
    private int getPointsForAchievement(String achievementId) {
        switch (achievementId) {
            case "first_lesson": return 10;
            case "master_learner": return 50;
            case "perfect_practice": return 25;
            case "dedicated_learner": return 15;
            case "gesture_master": return 30;
            case "speed_demon": return 20;
            case "consistency_king": return 25;
            case "trainer": return 40;
            case "early_bird": return 15;
            case "night_owl": return 15;
            default: return 10;
        }
    }

    /**
     * Update user ID when authentication state changes
     */
    public void updateUserId(String newUserId) {
        this.userId = newUserId;
    }

    /**
     * Calculate level from total achievement points (centralized from AchievementsActivity)
     */
    public int calculateLevelFromPoints(int totalPoints) {
        if (totalPoints < 50) return 1;      // 0-49 points
        if (totalPoints < 120) return 2;     // 50-119 points
        if (totalPoints < 220) return 3;     // 120-219 points
        if (totalPoints < 350) return 4;     // 220-349 points
        return 5;                            // 350+ points
    }

    /**
     * Get level title for display (centralized from AchievementsActivity)
     */
    public String getLevelTitle(int level) {
        switch (level) {
            case 1: return "Beginner";
            case 2: return "Novice";
            case 3: return "Intermediate";
            case 4: return "Advanced";
            case 5: return "Expert";
            default: return "Master";
        }
    }

    /**
     * Get total user points from Firebase achievements (centralized approach)
     * @param context Application context
     * @param callback Callback with total points
     */
    public void getTotalUserPoints(Context context, OnPointsLoadedCallback callback) {
        if (userId == null) {
            callback.onPointsLoaded(0);
            return;
        }

        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("achievements")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int totalPoints = 0;
                        for (DataSnapshot achievementSnapshot : snapshot.getChildren()) {
                            Achievement achievement = achievementSnapshot.getValue(Achievement.class);
                            if (achievement != null && achievement.isUnlocked()) {
                                totalPoints += achievement.getPoints();
                            }
                        }
                        callback.onPointsLoaded(totalPoints);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load user points", error.toException());
                        callback.onPointsLoaded(0);
                    }
                });
    }

    /**
     * Callback interface for points loading
     */
    public interface OnPointsLoadedCallback {
        void onPointsLoaded(int totalPoints);
    }

    /**
     * Calculate complete level display information (centralized level logic)
     * @param totalPoints Total achievement points
     * @return LevelDisplayInfo object with all display data
     */
    public LevelDisplayInfo calculateLevelDisplay(int totalPoints) {
        int currentLevel = calculateLevelFromPoints(totalPoints);
        String levelTitle = getLevelTitle(currentLevel);
        int pointsForCurrentLevel = getPointsRequiredForLevel(currentLevel);
        int pointsForNextLevel = getPointsRequiredForLevel(currentLevel + 1);

        // Current level progress calculation
        int progressInCurrentLevel = totalPoints - pointsForCurrentLevel;
        int pointsNeededInLevel = pointsForNextLevel - pointsForCurrentLevel;
        int progressPercentage = Math.max(0, Math.min(100,
                (int) ((progressInCurrentLevel * 100.0) / pointsNeededInLevel)));

        // Points to next level
        int pointsToNext = pointsForNextLevel - totalPoints;

        return new LevelDisplayInfo(
                currentLevel,
                levelTitle,
                progressInCurrentLevel,
                pointsNeededInLevel,
                progressPercentage,
                pointsToNext,
                currentLevel >= 5 // Max level reached
        );
    }

    /**
     * Data class for level display information
     */
    public static class LevelDisplayInfo {
        public final int level;
        public final String title;
        public final int progressInLevel;
        public final int pointsNeededInLevel;
        public final int progressPercentage;
        public final int pointsToNext;
        public final boolean isMaxLevel;

        public LevelDisplayInfo(int level, String title, int progressInLevel,
                                int pointsNeededInLevel, int progressPercentage,
                                int pointsToNext, boolean isMaxLevel) {
            this.level = level;
            this.title = title;
            this.progressInLevel = progressInLevel;
            this.pointsNeededInLevel = pointsNeededInLevel;
            this.progressPercentage = progressPercentage;
            this.pointsToNext = pointsToNext;
            this.isMaxLevel = isMaxLevel;
        }
    }
}