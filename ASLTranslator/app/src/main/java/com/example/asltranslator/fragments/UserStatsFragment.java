package com.example.asltranslator.fragments;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.asltranslator.utils.Constants;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import com.example.asltranslator.R;
import com.example.asltranslator.models.Achievement;
import com.example.asltranslator.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.asltranslator.activities.AchievementsActivity;
import com.example.asltranslator.utils.AchievementManager;

import java.util.Map;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Displays user statistics (name, level, translations, practice time, achievements) in MainActivity.
 * Includes achievement tracking and unlocking functionality.
 */
public class UserStatsFragment extends Fragment {

    private static final String TAG = "UserStatsFragment";

    private TextView tvUserName;
    private TextView tvLevel;
    private TextView tvTranslationsCount;
    private TextView tvPracticeTime;
    private TextView tvAchievementsCount;

    private DatabaseReference mDatabase;
    private String userId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        initFirebase();

        tvUserName.setText("Loading...");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();

            if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                tvUserName.setText("Welcome back, " + currentUser.getDisplayName() + "!");
            }

            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference(Constants.USERS_NODE)
                    .child(currentUser.getUid());

            userRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        updateStats(user);
                    } else {
                        User newUser = new User(currentUser.getUid(),
                                currentUser.getEmail(),
                                currentUser.getDisplayName());
                        userRef.setValue(newUser);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to load user", error.toException());
                }
            });

            // 🔧 USE CENTRALIZED ACHIEVEMENT SYSTEM DIRECTLY
            AchievementManager.getInstance().checkTimeBasedAchievements(getContext());

            // 🆕 Load achievement count separately for real-time updates
            loadAchievementCount();
        }
    }

    private void initViews(View view) {
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvLevel = view.findViewById(R.id.tv_level);
        tvTranslationsCount = view.findViewById(R.id.tv_translations_count);
        tvPracticeTime = view.findViewById(R.id.tv_practice_time);
        tvAchievementsCount = view.findViewById(R.id.tv_achievements_count);

        // 🆕 Make achievements count clickable
        tvAchievementsCount.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AchievementsActivity.class);
            startActivity(intent);
        });
    }

    private void initFirebase() {
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Updates UI with user statistics from Firebase data.
     *
     * @param user User data containing stats.
     */
    public void updateStats(User user) {
        if (user == null || !isAdded()) return;

        String nameToShow = "User";
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            nameToShow = user.getDisplayName();
        } else if (user.getEmail() != null && user.getEmail().contains("@")) {
            nameToShow = user.getEmail().split("@")[0];
        }

        tvUserName.setText("Welcome back, " + nameToShow + "!");

        // FIXED: Actually call the real level calculation method
        calculateActualLevel();

        tvTranslationsCount.setText(String.valueOf(user.getTotalTranslations()));
        tvPracticeTime.setText(formatPracticeTime(user.getTotalPracticeTime()));

        // Achievement count is handled separately in loadAchievementCount()
    }

    private void calculateActualLevel() {
        if (userId == null) return;

        // 🆕 USE CENTRALIZED POINT CALCULATION
        AchievementManager.getInstance().getTotalUserPoints(getContext(), totalPoints -> {
            if (!isAdded()) return; // Safety check

            // 🆕 USE CENTRALIZED LEVEL DISPLAY CALCULATION
            AchievementManager.LevelDisplayInfo levelInfo =
                    AchievementManager.getInstance().calculateLevelDisplay(totalPoints);

            // Update UI with centralized data
            String levelText = "Level " + levelInfo.level + " - " + levelInfo.title;
            if (tvLevel != null) {
                tvLevel.setText(levelText);
            }
        });
    }

    // 🆕 ACHIEVEMENT STATISTICS TRACKING
    private void loadAchievementCount() {
        if (userId == null) return;

        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("achievements")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int unlockedCount = 0;
                        for (DataSnapshot achievementSnapshot : snapshot.getChildren()) {
                            Achievement achievement = achievementSnapshot.getValue(Achievement.class);
                            if (achievement != null && achievement.isUnlocked()) {
                                unlockedCount++;
                            }
                        }

                        if (tvAchievementsCount != null && isAdded()) {
                            tvAchievementsCount.setText(String.valueOf(unlockedCount));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load achievements count", error.toException());
                    }
                });
    }

    // 🆕 PUBLIC METHODS FOR ACHIEVEMENT UNLOCKING (called from activities)


    private String formatPracticeTime(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis / (1000 * 60)) % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}