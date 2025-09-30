// AchievementsActivity.java
package com.example.asltranslator.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.asltranslator.utils.AchievementManager;
import com.example.asltranslator.R;
import com.example.asltranslator.adapters.AchievementsAdapter;
import com.example.asltranslator.models.Achievement;
import com.example.asltranslator.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays all available achievements with unlock status and dates.
 * Shows progress towards locked achievements and celebrates completed ones.
 */
public class AchievementsActivity extends AppCompatActivity {

    private static final String TAG = "AchievementsActivity";

    private Toolbar toolbar;
    private RecyclerView rvAchievements;
    private AchievementsAdapter adapter;
    private List<Achievement> achievementsList;

    // Level progress views
    private TextView tvCurrentLevel, tvTotalPoints, tvNextLevelInfo;
    private com.google.android.material.progressindicator.LinearProgressIndicator progressLevel;

    private DatabaseReference mDatabase;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        initViews();
        initFirebase();
        setupToolbar();
        loadAchievements();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvAchievements = findViewById(R.id.rv_achievements);

        // Level progress views
        tvCurrentLevel = findViewById(R.id.tv_current_level);
        tvTotalPoints = findViewById(R.id.tv_total_points);
        tvNextLevelInfo = findViewById(R.id.tv_next_level_info);
        progressLevel = findViewById(R.id.progress_level);

        achievementsList = new ArrayList<>();
        adapter = new AchievementsAdapter(achievementsList);
        rvAchievements.setLayoutManager(new LinearLayoutManager(this));
        rvAchievements.setAdapter(adapter);
    }

    private void initFirebase() {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("🏆 Achievements");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadAchievements() {
        // Define all possible achievements
        createPredefinedAchievements();

        // Load user's unlocked achievements from Firebase
        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("achievements")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        updateAchievementStatus(snapshot);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load achievements", error.toException());
                    }
                });
    }

    private void createPredefinedAchievements() {
        // Use centralized achievement definitions from AchievementManager
        achievementsList.clear();
        achievementsList.addAll(AchievementManager.getAllPredefinedAchievements());
        adapter.notifyDataSetChanged();
    }

    private void updateAchievementStatus(DataSnapshot snapshot) {
        for (Achievement achievement : achievementsList) {
            DataSnapshot achievementSnapshot = snapshot.child(achievement.getId());
            if (achievementSnapshot.exists()) {
                Achievement firebaseAchievement = achievementSnapshot.getValue(Achievement.class);
                if (firebaseAchievement != null) {
                    achievement.setUnlocked(firebaseAchievement.isUnlocked());
                    achievement.setUnlockedAt(firebaseAchievement.getUnlockedAt());
                }
            }
        }
        adapter.notifyDataSetChanged();

        // Update level progress after loading achievements
        updateLevelProgress();
    }

    private void updateLevelProgress() {
        // 🆕 USE CENTRALIZED POINT CALCULATION FROM FIREBASE
        AchievementManager.getInstance().getTotalUserPoints(this, totalPoints -> {
            // 🆕 USE CENTRALIZED LEVEL DISPLAY CALCULATION
            AchievementManager.LevelDisplayInfo levelInfo =
                    AchievementManager.getInstance().calculateLevelDisplay(totalPoints);

            // Update UI with centralized data
            tvCurrentLevel.setText(String.format("Level %d - %s", levelInfo.level, levelInfo.title));
            tvTotalPoints.setText(String.format("%d / %d points",
                    levelInfo.progressInLevel, levelInfo.pointsNeededInLevel));
            progressLevel.setProgress(levelInfo.progressPercentage);

            if (levelInfo.isMaxLevel) {
                tvNextLevelInfo.setText("🎉 Maximum level achieved!");
            } else {
                tvNextLevelInfo.setText(String.format("%d more points to reach Level %d",
                        levelInfo.pointsToNext, levelInfo.level + 1));
            }
        });
    }
}