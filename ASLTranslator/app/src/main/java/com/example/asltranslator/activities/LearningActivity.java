package com.example.asltranslator.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.asltranslator.utils.AchievementManager;
import com.example.asltranslator.R;
import com.example.asltranslator.fragments.LessonContentFragment;
import com.example.asltranslator.models.Achievement;
import com.example.asltranslator.models.LessonProgress;
import com.example.asltranslator.models.LessonStep;
import com.example.asltranslator.utils.Constants;
import com.example.asltranslator.utils.MerkleTreeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🎯 UPDATED LearningActivity - Now connects to level-specific practice system
 * Uses PracticeActivity for level-based learning with proper progression
 */
public class LearningActivity extends AppCompatActivity {


    private static final String TAG = "LearningActivity";
    private static final int PRACTICE_REQUEST_CODE = 101;

    private Toolbar toolbar;
    private TextView tvLessonTitle, tvProgressText;
    private CircularProgressIndicator circularProgress;
    private ViewPager2 viewPagerSteps;
    private MaterialButton btnPrevious, btnNext;
//    private FloatingActionButton fabPractice;

    private String lessonId;
    private String lessonName;
    private List<LessonStep> lessonSteps;
    private int currentStep = 0;
    private long startTime;

    // 🔧 NEW: Step completion tracking
    private Set<Integer> completedSteps = new HashSet<>(); // Track completed steps
    private int maxUnlockedStep = 0; // Track furthest unlocked step

    private DatabaseReference mDatabase;
    private String userId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learning);

        // Get lesson info from intent
        lessonId = getIntent().getStringExtra("lesson_id");
        lessonName = getIntent().getStringExtra("lesson_name");

        initViews();
        initFirebase();
        setupToolbar();
        loadLessonSteps();
        setupViewPager();
        setupClickListeners();

        // 🎯 LOAD EXISTING PROGRESS
        loadExistingProgress();

        startTime = System.currentTimeMillis();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvLessonTitle = findViewById(R.id.tv_lesson_title);
        tvProgressText = findViewById(R.id.tv_progress_text);
        circularProgress = findViewById(R.id.circular_progress);
        viewPagerSteps = findViewById(R.id.view_pager_steps);
        btnPrevious = findViewById(R.id.btn_previous);
        btnNext = findViewById(R.id.btn_next);
//        fabPractice = findViewById(R.id.fab_practice);

        tvLessonTitle.setText(lessonName);
    }

    private void initFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            mDatabase = FirebaseDatabase.getInstance().getReference();
        } else {
            Intent intent = new Intent(this, AuthActivity.class);
            startActivity(intent);
            finish();
            return;
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

// 🔧 IN LearningActivity.java - Replace the loadLessonSteps method (around line 103)

    private void loadLessonSteps() {
        lessonSteps = new ArrayList<>();

        switch (lessonId) {
            case "lesson_1":
                lessonSteps.add(new LessonStep("Hello",
                        "Start with your dominant hand flat at your forehead, then move it forward and down",
                        "Tip: This gesture is similar to a salute but more relaxed"));
                lessonSteps.add(new LessonStep("Thank You",
                        "Touch your chin with your fingertips, then move your hand forward",
                        "Tip: Think of blowing a kiss of gratitude"));
                lessonSteps.add(new LessonStep("Yes",
                        "Make a fist and nod it up and down, like nodding your head",
                        "Tip: The motion mimics a head nod"));
                lessonSteps.add(new LessonStep("No",
                        "Extend your index and middle finger, then tap them against your thumb",
                        "Tip: Like a bird's beak closing"));
                break;

            case "lesson_2": // EMOTIONS - Accurate ASL descriptions
                lessonSteps.add(new LessonStep("Happy",
                        "Place one or both hands on your chest, then move them upward in a smooth motion",
                        "Tip: The upward movement from your chest shows joy rising from within"));
                lessonSteps.add(new LessonStep("Sad",
                        "Start with both hands together in front of your face, then move them downward",
                        "Tip: Like tears falling - start high and move down slowly"));
                lessonSteps.add(new LessonStep("Angry",
                        "Make clawed hands near your belly, then move them upward toward your face with palms facing out",
                        "Tip: Shows anger rising up - start low and move to face level"));
                lessonSteps.add(new LessonStep("Love",
                        "Cross both closed fists over your chest, squeeze them inward pointing toward each other",
                        "Tip: Hold love close to your heart with both fists crossed and squeezed"));
                break;

            case "lesson_3": // ACTIONS - Clear body movements
                lessonSteps.add(new LessonStep("Eat",
                        "Bring your fingertips to your mouth repeatedly, like putting food in",
                        "Tip: Mimic the action of eating food"));
                lessonSteps.add(new LessonStep("Drink",
                        "Make a C-shape with your hand and tip it toward your mouth like drinking",
                        "Tip: Like tipping a cup to drink"));
                lessonSteps.add(new LessonStep("Sleep",
                        "Place your flat hand against your cheek and tilt your head like sleeping",
                        "Tip: Rest your head on your hand like a pillow"));
                lessonSteps.add(new LessonStep("Go",
                        "Point both index fingers forward, then move them away from your body",
                        "Tip: Both hands moving away means 'go away' or 'leave'"));
                break;

            case "lesson_4": // OBJECTS - Distinct shapes
                lessonSteps.add(new LessonStep("Book",
                        "Place your palms together, then open them like opening a book",
                        "Tip: Your hands are the book pages opening"));
                lessonSteps.add(new LessonStep("Phone",
                        "Hold your hand like holding an old phone, thumb to ear, pinky to mouth",
                        "Tip: Like holding an old-fashioned telephone"));
                lessonSteps.add(new LessonStep("Car",
                        "Make two fists and move them like you're steering a steering wheel",
                        "Tip: Both hands on the steering wheel driving"));
                lessonSteps.add(new LessonStep("Home",  // 🔄 Changed House → Home
                        "Make a triangle roof with both hands, then bring them down to form walls",
                        "Tip: Draw the outline of a home - roof, then walls"));
                break;

            case "lesson_5": // QUESTION WORDS - Essential for conversations
                lessonSteps.add(new LessonStep("What",
                        "Point your index finger down and shake it side to side slightly",
                        "Tip: Like pointing down and asking 'what is this?'"));
                lessonSteps.add(new LessonStep("Where",
                        "Point your index finger up and move it back and forth in the air",
                        "Tip: Searching around in the air - 'where could it be?'"));
                lessonSteps.add(new LessonStep("When",
                        "Touch your left index finger with your right index finger, then circle around it",
                        "Tip: One finger points to time, the other circles around it"));
                lessonSteps.add(new LessonStep("Who",
                        "Make an 'O' shape around your mouth with your index finger and thumb",
                        "Tip: Circling around your mouth - 'who is speaking?'"));
                break;

            default:
                Log.e(TAG, "Unknown lesson_id: " + lessonId);
                // Add empty lesson or basic fallback
                lessonSteps.add(new LessonStep("Error",
                        "Unknown lesson",
                        "Please select a valid lesson"));
                break;
        }

        updateProgress();
    }

    // 🔧 MINIMAL UPDATE: Just add progress save when ViewPager changes
    private void setupViewPager() {
        LessonPagerAdapter adapter = new LessonPagerAdapter(this);
        viewPagerSteps.setAdapter(adapter);
        viewPagerSteps.setUserInputEnabled(false); // Disable swipe

        viewPagerSteps.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentStep = position;
                updateProgress();
                updateNavigationButtons();

                // 🔧 NEW: Save current step position for lesson card progress
                saveCurrentStepToFirebase(currentStep);

                Log.d(TAG, "ViewPager page selected: " + currentStep);
            }
        });
    }

    // 🔧 ENHANCED: Click listeners with step locking
    private void setupClickListeners() {
        btnPrevious.setOnClickListener(v -> {
            if (currentStep > 0) {
                viewPagerSteps.setCurrentItem(currentStep - 1);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentStep < lessonSteps.size() - 1) {
                // Check if next step is unlocked
                int nextStep = currentStep + 1;
                if (nextStep <= maxUnlockedStep || completedSteps.contains(currentStep)) {
                    // Advance to next step
                    viewPagerSteps.setCurrentItem(nextStep);
                } else {
                    // Show locked message
                    showStepLockedDialog();
                }
            } else {
                // Last step - complete lesson if current step is completed
                if (completedSteps.contains(currentStep)) {
                    completeLesson();
                } else {
                    showMustCompleteDialog();
                }
            }
        });

        // Practice FAB - always allow practice
//        fabPractice.setOnClickListener(v -> showPracticeOptions());
    }

    // 🔧 NEW: Dialog for locked steps
    private void showStepLockedDialog() {
        String currentGesture = lessonSteps.get(currentStep).getTitle();

        new AlertDialog.Builder(this)
                .setTitle("🔒 Next Step Locked")
                .setMessage(String.format(
                        "Complete the practice for '%s' first!\n\n" +
                                "🎯 Practice until you master the gesture with good accuracy.\n\n" +
                                "Once completed, the next step will automatically unlock.",
                        currentGesture
                ))
                .setPositiveButton("Practice Now", (dialog, which) -> {
                    startSingleSignPractice();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 🔧 NEW: Dialog for must complete current step
    private void showMustCompleteDialog() {
        String currentGesture = lessonSteps.get(currentStep).getTitle();

        new AlertDialog.Builder(this)
                .setTitle("🎯 Complete Current Step")
                .setMessage(String.format(
                        "Master '%s' practice to complete the lesson!\n\n" +
                                "🏆 You're almost done - just one more step!",
                        currentGesture
                ))
                .setPositiveButton("Practice Now", (dialog, which) -> {
                    startSingleSignPractice();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    // 🔧 CRITICAL FIX: startSingleSignPractice - use current step gesture
    // 🔧 REPLACE startSingleSignPractice time-based check:
    private void startSingleSignPractice() {
        if (currentStep >= 0 && currentStep < lessonSteps.size()) {
            String currentGesture = lessonSteps.get(currentStep).getTitle();

            Log.d(TAG, "🎯 Starting single sign practice - Step: " + currentStep +
                    ", Gesture: " + currentGesture + ", Lesson: " + lessonId);

            // 🆕 USE CENTRALIZED ACHIEVEMENT SYSTEM
            AchievementManager.getInstance().checkTimeBasedAchievements(this);

            Intent intent = new Intent(this, PracticeActivity.class);
            intent.putExtra("lesson_id", lessonId);
            intent.putExtra("practice_mode", PracticeActivity.PRACTICE_MODE_SINGLE);
            intent.putExtra("current_gesture", currentGesture);
            startActivityForResult(intent, PRACTICE_REQUEST_CODE);
        } else {
            Log.e(TAG, "❌ Invalid currentStep: " + currentStep + ", lessonSteps size: " + lessonSteps.size());
            Toast.makeText(this, "Error: Invalid step", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFullLevelPractice() {
        Log.d(TAG, "🏆 Starting full level practice for: " + lessonId);

        // 🔥 FIXED: Use PracticeActivity for level-specific models
        Intent intent = new Intent(this, PracticeActivity.class);
        intent.putExtra("lesson_id", lessonId);
        intent.putExtra("practice_mode", PracticeActivity.PRACTICE_MODE_FULL_LEVEL);
        startActivityForResult(intent, PRACTICE_REQUEST_CODE);
    }

    private void updateProgress() {
        int progress = ((currentStep + 1) * 100) / lessonSteps.size();
        circularProgress.setProgress(progress);
        tvProgressText.setText((currentStep + 1) + "/" + lessonSteps.size() + " Steps");
    }

    // 🔧 ENHANCED: Navigation with step locking
    private void updateNavigationButtons() {
        // Previous button - always enabled if not first step
        btnPrevious.setEnabled(currentStep > 0);

        // Next button logic
        if (currentStep == lessonSteps.size() - 1) {
            // Last step
            if (completedSteps.contains(currentStep)) {
                btnNext.setText("Complete Lesson");
                btnNext.setIcon(getDrawable(R.drawable.ic_check_circle));
                btnNext.setEnabled(true);
            } else {
                btnNext.setText("Complete Practice First");
                btnNext.setIcon(getDrawable(R.drawable.ic_lock));
                btnNext.setEnabled(false);
            }
        } else {
            // Not last step
            int nextStep = currentStep + 1;
            if (nextStep <= maxUnlockedStep || completedSteps.contains(currentStep)) {
                // Next step is unlocked
                btnNext.setText("Next");
                btnNext.setIcon(getDrawable(R.drawable.ic_arrow_forward));
                btnNext.setEnabled(true);
            } else {
                // Next step is locked
                btnNext.setText("🔒 Complete Practice");
                btnNext.setIcon(getDrawable(R.drawable.ic_lock));
                btnNext.setEnabled(false);
            }
        }
    }

    // 🔧 UPDATE the completeLesson method to use centralized achievement checking:
    private void completeLesson() {
        long practiceTime = System.currentTimeMillis() - startTime;

        // Create lesson progress
        LessonProgress progress = new LessonProgress(lessonId, lessonName);
        progress.setCompleted(true);
        progress.setScore(lessonSteps.size());
        progress.setPracticeTime(practiceTime);
        progress.setAttempts(1);

        // Generate Merkle proof
        String merkleProof = MerkleTreeManager.getInstance()
                .generateMerkleProof(new ArrayList<>(), lessonId);
        progress.setMerkleProof(merkleProof);

        // Save to Firebase
        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child(Constants.PROGRESS_NODE)
                .child(lessonId)
                .setValue(progress)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // 🔧 USE CENTRALIZED ACHIEVEMENT SYSTEM
                        AchievementManager.getInstance().checkLessonCompletionAchievements(this, lessonId);

                        Toast.makeText(this, "Lesson completed! 🎉", Toast.LENGTH_LONG).show();

                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("lesson_completed", true);
                        resultIntent.putExtra("lesson_id", lessonId);
                        setResult(RESULT_OK, resultIntent);

                        finish();
                    }
                });

        // Update total practice time
        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .child("totalPracticeTime")
                .setValue(ServerValue.increment(practiceTime));
    }

    // 🔧 REPLACE direct calls in onActivityResult with centralized calls:
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PRACTICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            boolean practiceCompleted = data.getBooleanExtra("practice_completed", false);
            String practiceMode = data.getStringExtra("practice_mode");
            double accuracy = data.getDoubleExtra("accuracy", 0);
            long practiceTime = data.getLongExtra("practice_time", 0);
            boolean gestureMastered = data.getBooleanExtra("gesture_mastered", false);
            boolean highAccuracy = data.getBooleanExtra("high_accuracy", false);
            String completedGesture = data.getStringExtra("completed_gesture");

            if (practiceCompleted && gestureMastered) {
                Log.d(TAG, "✅ Gesture mastered: " + completedGesture + " (Step " + currentStep + ")");

                // 🎯 MARK CURRENT STEP AS COMPLETED
                completedSteps.add(currentStep);

                // 🔓 UNLOCK NEXT STEP
                int nextStep = currentStep + 1;
                if (nextStep < lessonSteps.size()) {
                    maxUnlockedStep = Math.max(maxUnlockedStep, nextStep);
                    Log.d(TAG, "🔓 Unlocked step " + nextStep + ": " + lessonSteps.get(nextStep).getTitle());
                }

                // 💾 SAVE PROGRESS TO FIREBASE
                saveStepCompletion(currentStep, accuracy, practiceTime);

                // 🆕 USE CENTRALIZED ACHIEVEMENT SYSTEM DIRECTLY
                AchievementManager.getInstance().updateConsistencyStreak(this);
                AchievementManager.getInstance().checkPracticeAchievements(this, accuracy, practiceTime);

                // Show practice results with advancement option
                showPracticeResultsWithAdvancement(accuracy, practiceTime, completedGesture, nextStep);

                // Update navigation buttons to reflect new state
                updateNavigationButtons();
            } else if (practiceCompleted) {
                // Practice completed but not mastered
                showPracticeResults(accuracy, practiceTime, completedGesture);
                updatePracticeProgress(accuracy, practiceTime);

                // 🆕 STILL UPDATE CONSISTENCY AND CHECK ACHIEVEMENTS EVEN IF NOT MASTERED
                AchievementManager.getInstance().updateConsistencyStreak(this);
                AchievementManager.getInstance().checkPracticeAchievements(this, accuracy, practiceTime);
            }
        }
    }

    private void showPracticeResultsWithAdvancement(double accuracy, long practiceTime,
                                                    String completedGesture, int nextStep) {
        long minutes = practiceTime / 60000;
        long seconds = (practiceTime % 60000) / 1000;

        if (nextStep < lessonSteps.size()) {
            String nextGesture = lessonSteps.get(nextStep).getTitle();

            new AlertDialog.Builder(this)
                    .setTitle("🎉 Gesture Mastered!")
                    .setMessage(String.format(
                            "Excellent work on '%s'!\n\n" +
                                    "📊 Accuracy: %.1f%%\n" +
                                    "⏱ Time: %dm %ds\n\n" +
                                    "🔓 Next gesture unlocked: '%s'\n\n" +
                                    "What would you like to do?",
                            completedGesture,
                            accuracy,
                            minutes, seconds,
                            nextGesture
                    ))
                    .setPositiveButton("Continue Learning →", (dialog, which) -> {
                        Log.d(TAG, "🚀 BEFORE advancement: currentStep = " + currentStep +
                                ", nextStep = " + nextStep);

                        if (nextStep >= 0 && nextStep < lessonSteps.size()) {
                            currentStep = nextStep;
                            viewPagerSteps.setCurrentItem(nextStep, true);

                            // 🔧 NEW: Save progress for lesson card
                            saveCurrentStepToFirebase(currentStep);

                            updateProgress();
                            updateNavigationButtons();

                            Log.d(TAG, "🚀 AFTER advancement: currentStep = " + currentStep);
                            Toast.makeText(this, "📚 Now learning: " + nextGesture, Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Practice Again", (dialog, which) -> {
                        startSingleSignPractice();
                    })
                    .setNeutralButton("Stay Here", null)
                    .setCancelable(false)
                    .show();
        } else {
            // Last step - offer lesson completion
            new AlertDialog.Builder(this)
                    .setTitle("🏆 Final Gesture Mastered!")
                    .setMessage(String.format(
                            "Outstanding! You've mastered '%s'!\n\n" +
                                    "📊 Accuracy: %.1f%%\n" +
                                    "⏱ Time: %dm %ds\n\n" +
                                    "🎓 You've completed all gestures in this lesson!",
                            completedGesture,
                            accuracy,
                            minutes, seconds
                    ))
                    .setPositiveButton("Complete Lesson 🎓", (dialog, which) -> {
                        completeLesson();
                    })
                    .setNegativeButton("Review More", (dialog, which) -> {
                        // Show review options dialog
                        showReviewOptionsDialog();
                    })
                    .show();
        }
    }

    private void showReviewOptionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📚 Review Options")
                .setMessage("What would you like to review?")
                .setPositiveButton("🏠 All Lessons", (dialog, which) -> {
                    // Return to MainActivity to see all lessons
                    returnToMainActivity();
                })
                .setNegativeButton("🔄 This Lesson", (dialog, which) -> {
                    // Go back to step 1 of current lesson for review
                    reviewCurrentLesson();
                })
                .setNeutralButton("🎯 Practice Again", (dialog, which) -> {
                    // Start single sign practice for current gesture
                    startSingleSignPractice();
                })
                .show();
    }

    private void returnToMainActivity() {
        Log.d(TAG, "🏠 Returning to MainActivity for lesson overview");

        // Set result to indicate lesson was completed for MainActivity
        Intent resultIntent = new Intent();
        resultIntent.putExtra("lesson_completed", true);
        resultIntent.putExtra("lesson_id", lessonId);
        resultIntent.putExtra("review_requested", true); // Flag for MainActivity
        setResult(RESULT_OK, resultIntent);

        // Clear task stack and go to MainActivity
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(mainIntent);
        finish();
    }

    private void reviewCurrentLesson() {
        Log.d(TAG, "🔄 Starting lesson review from step 1");

        // Reset to first step
        currentStep = 0;
        viewPagerSteps.setCurrentItem(0, true);

        // Update progress and UI
        updateProgress();
        updateNavigationButtons();
        saveCurrentStepToFirebase(0);

        // Show encouraging message
        Toast.makeText(this, "📚 Review mode: Go through the lesson steps again!", Toast.LENGTH_LONG).show();

        // Optionally, you could also reset completion status to allow re-practicing
        // completedSteps.clear(); // Uncomment if you want to require re-completion
    }

    // 🔧 ENHANCED: Save current step position AND overall lesson progress
    private void saveCurrentStepToFirebase(int stepIndex) {
        if (userId != null && lessonId != null) {
            // Calculate overall lesson progress percentage
            int totalSteps = lessonSteps.size();
            int completedSteps = this.completedSteps.size(); // Number of completed steps
            int currentStepProgress = Math.max(stepIndex, completedSteps); // Current position

            // Progress percentage (0-100)
            int progressPercentage = (int) ((double) currentStepProgress / totalSteps * 100);

            Map<String, Object> updates = new HashMap<>();
            updates.put("currentStep", stepIndex);
            updates.put("totalSteps", totalSteps);
            updates.put("completedStepsCount", completedSteps);
            updates.put("progressPercentage", progressPercentage);
            updates.put("lastAccessTime", System.currentTimeMillis());
            updates.put("lessonStarted", true); // Mark as started

            // 🔧 NEW: Update lesson status text
            String statusText;
            if (completedSteps == totalSteps) {
                statusText = "✅ Complete (100%)";
                updates.put("completed", true);
            } else if (stepIndex > 0 || !this.completedSteps.isEmpty()) {
                statusText = String.format("📚 Step %d of %d (%d%%)",
                        Math.min(stepIndex + 1, totalSteps), totalSteps, progressPercentage);
            } else {
                statusText = "📚 Start Learning";
                updates.put("progressPercentage", 0);
            }
            updates.put("statusText", statusText);

            mDatabase.child(Constants.USERS_NODE)
                    .child(userId)
                    .child(Constants.PROGRESS_NODE)
                    .child(lessonId)
                    .updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Lesson progress saved:");
                        Log.d(TAG, "   - Current step: " + stepIndex);
                        Log.d(TAG, "   - Progress: " + progressPercentage + "%");
                        Log.d(TAG, "   - Status: " + statusText);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to save lesson progress", e);
                    });
        }
    }

    // 🔧 ENHANCED: Better practice results dialog
    private void showPracticeResults(double accuracy, long practiceTime, String completedGesture) {
        long minutes = practiceTime / 60000;
        long seconds = (practiceTime % 60000) / 1000;

        String accuracyLevel;
        String emoji;
        // 🔧 FIX: Use accuracy as-is (already percentage from PracticeActivity)
        if (accuracy >= 90) {  // 🔧 FIX: Compare to 90 not 0.9
            accuracyLevel = "Excellent!";
            emoji = "🌟";
        } else if (accuracy >= 80) {  // 🔧 FIX: Compare to 80 not 0.8
            accuracyLevel = "Great!";
            emoji = "🎉";
        } else if (accuracy >= 70) {  // 🔧 FIX: Compare to 70 not 0.7
            accuracyLevel = "Good!";
            emoji = "👍";
        } else {
            accuracyLevel = "Keep practicing!";
            emoji = "💪";
        }

        new AlertDialog.Builder(this)
                .setTitle(emoji + " Practice Results")
                .setMessage(String.format(
                        "Gesture: %s\n" +
                                "Level: %s\n\n" +
                                "📊 Accuracy: %.1f%% - %s\n" +  // 🔧 FIX: Remove * 100
                                "⏱ Practice Time: %dm %ds\n\n" +
                                "%s",
                        completedGesture,
                        lessonName,
                        accuracy,  // 🔧 FIX: Don't multiply by 100 (already percentage)
                        accuracyLevel,
                        minutes, seconds,
                        accuracy >= 80 ? "🚀 Ready for the next challenge!" : "🎯 Practice more to improve!"  // 🔧 FIX: Compare to 80
                ))
                .setPositiveButton("Continue", null)
                .show();
    }

    // 🔧 MINIMAL UPDATE: Just add progress update after step completion
    private void saveStepCompletion(int stepIndex, double accuracy, long practiceTime) {
        if (userId != null && lessonId != null) {
            String stepKey = "step_" + stepIndex;
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("completed", true);
            stepData.put("accuracy", accuracy);
            stepData.put("practiceTime", practiceTime);
            stepData.put("completedAt", System.currentTimeMillis());
            stepData.put("gestureName", lessonSteps.get(stepIndex).getTitle());

            mDatabase.child(Constants.USERS_NODE)
                    .child(userId)
                    .child(Constants.PROGRESS_NODE)
                    .child(lessonId)
                    .child("steps")
                    .child(stepKey)
                    .setValue(stepData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Step completion saved: " + stepKey);

                        // 🔧 NEW: Update lesson progress for the lesson card
                        saveCurrentStepToFirebase(currentStep);
                    });
        }
    }


    // 🔧 ENHANCED: Load existing progress with current step
    private void loadExistingProgress() {
        if (userId != null && lessonId != null) {
            mDatabase.child(Constants.USERS_NODE)
                    .child(userId)
                    .child(Constants.PROGRESS_NODE)
                    .child(lessonId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            completedSteps.clear();
                            maxUnlockedStep = 0;

                            // 🔧 LOAD CURRENT STEP POSITION
                            Integer savedCurrentStep = snapshot.child("currentStep").getValue(Integer.class);
                            if (savedCurrentStep != null && savedCurrentStep >= 0 && savedCurrentStep < lessonSteps.size()) {
                                currentStep = savedCurrentStep;
                                viewPagerSteps.setCurrentItem(currentStep, false);
                                maxUnlockedStep = Math.max(maxUnlockedStep, currentStep);
                                Log.d(TAG, "📚 Restored current step: " + currentStep);
                            }

                            // Load completed steps
                            DataSnapshot stepsSnapshot = snapshot.child("steps");
                            for (DataSnapshot stepSnapshot : stepsSnapshot.getChildren()) {
                                String stepKey = stepSnapshot.getKey();
                                if (stepKey != null && stepKey.startsWith("step_")) {
                                    try {
                                        int stepIndex = Integer.parseInt(stepKey.replace("step_", ""));
                                        Boolean completed = stepSnapshot.child("completed").getValue(Boolean.class);

                                        if (Boolean.TRUE.equals(completed)) {
                                            completedSteps.add(stepIndex);
                                            maxUnlockedStep = Math.max(maxUnlockedStep, stepIndex + 1);
                                            Log.d(TAG, "📚 Loaded completed step: " + stepIndex);
                                        }
                                    } catch (NumberFormatException e) {
                                        Log.w(TAG, "Invalid step key: " + stepKey);
                                    }
                                }
                            }

                            // Update UI with loaded progress
                            updateNavigationButtons();
                            updateProgress();

                            Log.d(TAG, "📊 Progress loaded - Current: " + currentStep +
                                    ", Completed: " + completedSteps +
                                    ", Max unlocked: " + maxUnlockedStep);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Failed to load progress", error.toException());
                        }
                    });
        }
    }

    // 🔧 REPLACE updatePracticeProgress method:
    private void updatePracticeProgress(double accuracy, long practiceTime) {
        // Update Firebase with practice statistics
        if (userId != null && lessonId != null) {
            DatabaseReference progressRef = mDatabase
                    .child(Constants.USERS_NODE)
                    .child(userId)
                    .child(Constants.PROGRESS_NODE)
                    .child(lessonId);

            // Update practice stats
            progressRef.child("practiceAttempts").setValue(ServerValue.increment(1));
            progressRef.child("practiceTime").setValue(ServerValue.increment(practiceTime));
            progressRef.child("lastAccuracy").setValue(accuracy);

            // 🔧 USE CENTRALIZED ACHIEVEMENT SYSTEM
            AchievementManager.getInstance().checkPracticeAchievements(this, accuracy, practiceTime);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    private class LessonPagerAdapter extends FragmentStateAdapter {

        public LessonPagerAdapter(@NonNull LearningActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return LessonContentFragment.newInstance(lessonSteps.get(position));
        }

        @Override
        public int getItemCount() {
            return lessonSteps.size();
        }
    }
}