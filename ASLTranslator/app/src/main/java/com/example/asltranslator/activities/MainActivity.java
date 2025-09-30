package com.example.asltranslator.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.asltranslator.utils.AchievementManager;
import com.example.asltranslator.models.Achievement;
import com.example.asltranslator.network.CryptoManager;
import com.example.asltranslator.R;
import com.example.asltranslator.adapters.LessonAdapter;
import com.example.asltranslator.fragments.UserStatsFragment;
import com.example.asltranslator.models.Lesson;
import com.example.asltranslator.models.LessonProgress;
import com.example.asltranslator.models.User;
import com.example.asltranslator.network.WebSocketManager;
import com.example.asltranslator.utils.Constants;
import com.example.asltranslator.utils.PreferenceManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import com.google.firebase.auth.FirebaseUser;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Main hub for ASL Translator app, displaying lessons and user stats.
 * Manages lesson selection, profile access, and server connectivity.
 */
public class MainActivity extends AppCompatActivity implements LessonAdapter.OnLessonClickListener {

    private static final String TAG = "MainActivity";
    private static final int PROFILE_REQUEST_CODE = 200;

    private static final List<String> ADMIN_EMAILS = Arrays.asList("guylerner10@gmail.com","danielseth1840@gmail.com");
    private static final int LESSON_REQUEST_CODE = 100;
    private static final int ADMIN_TRAINING_REQUEST_CODE = 200;
    private long lastStatusLogTime = 0;
    private boolean isAdmin = false;

    private Toolbar toolbar;
    private RecyclerView rvLessons;
//    private FloatingActionButton fabQuickTranslate;
    private MaterialCardView cardQuickTranslate, cardPractice, cardTraining;
    private ImageView ivProfilePicture;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private PreferenceManager prefManager;

    private LessonAdapter lessonAdapter;
    private List<Lesson> lessonList;
    private User currentUser;

    /**
     * Initializes the MainActivity, setting up the UI, configuration, and core functionality.
     * Called when the activity is created, this method orchestrates the setup of views, Firebase,
     * WebSocket connections, and user data loading.
     *
     * Data Flow:
     * 1. Set layout and initialize UI components (toolbar, RecyclerView, etc.).
     * 2. Configure school demo mode for dynamic server IPs (if needed).
     * 3. Initialize Firebase, check admin access, and set up crypto.
     * 4. Load user data, lessons, and profile picture.
     *
     * @param savedInstanceState Saved state for activity recreation, may be null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        // For testing school demo mode
        Constants.enableSchoolDemo(this);
        initFirebase();
        checkAdminAccess();
        initCrypto();
        checkWebSocketConnection();
        setupToolbar();
        setupUserStatsFragment();
        setupClickListeners();
        loadUserData();
        loadLessons();
        loadProfilePicture();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvLessons = findViewById(R.id.rv_lessons);
//        fabQuickTranslate = findViewById(R.id.fab_quick_translate);
        cardQuickTranslate = findViewById(R.id.card_quick_translate);
        cardPractice = findViewById(R.id.card_practice);
        cardTraining = findViewById(R.id.card_training);
        ivProfilePicture = findViewById(R.id.iv_profile_picture);

        // Setup RecyclerView
        lessonList = new ArrayList<>();
        lessonAdapter = new LessonAdapter(lessonList, this);
        rvLessons.setLayoutManager(new LinearLayoutManager(this));
        rvLessons.setAdapter(lessonAdapter);
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        prefManager = new PreferenceManager(this);
    }

    /**
     * Verifies WebSocket connection status and attempts reconnection if needed.
     * Ensures reliable server connectivity for ASL features.
     */
    private void checkWebSocketConnection() {
        if (!WebSocketManager.getInstance().isConnected()) {
            Log.d(TAG, "🔌 WebSocket not connected, attempting reconnect");
            WebSocketManager.getInstance().connect(
                    Constants.getWebSocketUrl(this),
                    new WebSocketManager.WebSocketCallback() {
                        @Override
                        public void onConnected() {
                            Log.d(TAG, "✅ WebSocket reconnected");
                        }

                        @Override
                        public void onDisconnected() {
                            Log.d(TAG, "🔌 WebSocket disconnected");
                        }

                        @Override
                        public void onGestureDetected(com.example.asltranslator.models.GestureResponse response) {
                            // Not used in MainActivity
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "❌ WebSocket reconnection error: " + error);
                        }
                    });
        } else {
            Log.d(TAG, "✅ WebSocket already connected");
        }
    }

    private void checkAdminAccess() {
        Log.d("MAIN_DEBUG", "=== Checking admin access ===");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            Log.d("MAIN_DEBUG", "Current user email: " + currentUser.getEmail());
            Log.d("MAIN_DEBUG", "Admin emails list: " + ADMIN_EMAILS.toString());

            if (ADMIN_EMAILS.contains(currentUser.getEmail())) {
                isAdmin = true;
                Log.d("MAIN_DEBUG", "✅ ADMIN ACCESS GRANTED");
                Toast.makeText(this, "🔐 Admin Mode Enabled", Toast.LENGTH_SHORT).show();
            } else {
                isAdmin = false;
                Log.d("MAIN_DEBUG", "❌ ADMIN ACCESS DENIED");
            }
        } else {
            Log.d("MAIN_DEBUG", "❌ No user logged in");
            isAdmin = false;
        }

        Log.d("MAIN_DEBUG", "Final isAdmin value: " + isAdmin);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }
        toolbar.setPopupTheme(androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark);
    }

    private void setupUserStatsFragment() {
        UserStatsFragment statsFragment = new UserStatsFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.user_stats_container, statsFragment, "USER_STATS_TAG");
        transaction.commit();
    }

    private void setupClickListeners() {
//        fabQuickTranslate.setOnClickListener(v -> openCameraTranslate());
        cardQuickTranslate.setOnClickListener(v -> openCameraTranslate());
        cardPractice.setOnClickListener(v -> openPracticeMode());

        if (ivProfilePicture != null) {
            ivProfilePicture.setOnClickListener(v -> openProfileActivity());
        }

        if (cardTraining != null) {
            cardTraining.setOnClickListener(v -> {
                Log.d(TAG, "Training card clicked");
                Intent intent = new Intent(MainActivity.this, TrainingActivity.class);
                startActivityForResult(intent, Constants.TRAINING_REQUEST_CODE);
            });
        }
    }

    private void showAdminTrainingOptions() {
        String[] options = {"🔐 Create Level Models", "📚 Regular Training"};

        new AlertDialog.Builder(this)
                .setTitle("🔐 Admin Training Options")
                .setMessage("Choose your training mode")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showLevelModelCreationOptions();
                            break;
                        case 1:
                            Intent intent = new Intent(MainActivity.this, TrainingActivity.class);
                            startActivityForResult(intent, Constants.TRAINING_REQUEST_CODE);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadProfilePicture() {
        if (mAuth.getCurrentUser() == null) return;

        String filename = "profile_" + mAuth.getCurrentUser().getUid() + ".jpg";
        try {
            FileInputStream fis = openFileInput(filename);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            fis.close();
            if (bitmap != null && ivProfilePicture != null) {
                ivProfilePicture.setImageBitmap(bitmap);
                Log.d(TAG, "Profile picture loaded successfully");
            }
        } catch (IOException e) {
            Log.d(TAG, "No profile picture found, using default");
        }
    }

    private void loadUserData() {
        if (mAuth.getCurrentUser() == null) {
            Intent intent = new Intent(this, AuthActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        mDatabase.child(Constants.USERS_NODE).child(userId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        currentUser = snapshot.getValue(User.class);
                        if (currentUser != null) {
                            updateUI();
                            // Reload lessons with current progress
                            loadLessons();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this,
                                "Failed to load user data",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadLessons() {
        lessonList.clear();

        // Lesson 1 - Always unlocked (Basic Greetings)
        Lesson lesson1 = new Lesson("lesson_1", "Basic Greetings",
                "Learn Hello, Thank You, Yes, and No - the foundation of ASL communication",
                R.drawable.ic_lesson_greetings, 4);
        lesson1.setLocked(false); // Always unlocked
        lessonList.add(lesson1);

        // 🔧 UPDATED: Lesson 2 - Emotions & Feelings
        Lesson lesson2 = new Lesson("lesson_2", "Emotions & Feelings",
                "Express your emotions - Happy, Sad, Angry, Love",
                R.drawable.ic_lesson_numbers, 4); // Updated count to 4 gestures
        lesson2.setLocked(!isLessonCompleted("lesson_1"));
        lessonList.add(lesson2);

        // 🔧 UPDATED: Lesson 3 - Daily Actions
        Lesson lesson3 = new Lesson("lesson_3", "Daily Actions",
                "Essential daily activities - Eat, Drink, Sleep, Go",
                R.drawable.ic_lesson_phrases, 4); // Updated count to 4 gestures
        lesson3.setLocked(!isLessonCompleted("lesson_2"));
        lessonList.add(lesson3);

        // 🔧 UPDATED: Lesson 4 - Common Objects
        Lesson lesson4 = new Lesson("lesson_4", "Common Objects",
                "Things you use every day - Book, Phone, Car, House",
                R.drawable.ic_lesson_family, 4); // Updated count to 4 gestures
        lesson4.setLocked(!isLessonCompleted("lesson_3"));
        lessonList.add(lesson4);

        // 🔧 UPDATED: Lesson 5 - Question Words (Fixed)
        Lesson lesson5 = new Lesson("lesson_5", "Question Words",
                "Essential questions - What, Where, When, Who",
                R.drawable.ic_lesson_questions, 4); // 4 question words
        lesson5.setLocked(!isLessonCompleted("lesson_4"));
        lessonList.add(lesson5);

        lessonAdapter.notifyDataSetChanged();
        loadLessonProgress();
    }

    private boolean isLessonCompleted(String lessonId) {
        if (currentUser == null || currentUser.getProgress() == null) {
            return false;
        }

        LessonProgress progress = currentUser.getProgress().get(lessonId);
        return progress != null && progress.isCompleted();
    }

    private void loadLessonProgress() {
        if (currentUser == null) return;

        for (Lesson lesson : lessonList) {
            if (currentUser.getProgress() != null) {
                LessonProgress progress = currentUser.getProgress().get(lesson.getLessonId());
                if (progress != null) {
                    lesson.setProgress(progress);
                }
            }
        }
        lessonAdapter.notifyDataSetChanged();
    }

    private void updateUI() {
        UserStatsFragment fragment = (UserStatsFragment) getSupportFragmentManager()
                .findFragmentByTag("USER_STATS_TAG");
        if (fragment != null && currentUser != null) {
            fragment.updateStats(currentUser);
        }
    }

    private void openCameraTranslate() {
        try {
            Intent intent = new Intent(this, CameraTranslateActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openPracticeMode() {
        // Find first incomplete lesson for practice
        for (Lesson lesson : lessonList) {
            if (!lesson.isLocked() && (lesson.getProgress() == null || !lesson.getProgress().isCompleted())) {
                openLesson(lesson);
                return;
            }
        }

        // If all lessons completed, open first lesson for review
        if (!lessonList.isEmpty()) {
            openLesson(lessonList.get(0));
        }
    }

    private void initCrypto() {
        try {
            CryptoManager.getInstance().initializeKeys();
            Log.d(TAG, "CryptoManager initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CryptoManager", e);
        }
    }

    @Override
    public void onLessonClick(Lesson lesson) {
        if (lesson.isLocked()) {
            showLevelLockedDialog(lesson);
        } else {
            openLesson(lesson);
        }
    }

    private void showLevelLockedDialog(Lesson lesson) {
        String requiredLesson = getRequiredLessonForUnlock(lesson.getLessonId());
        String requiredLessonTitle = getRequiredLessonTitle(requiredLesson);

        new AlertDialog.Builder(this)
                .setTitle("🔒 Level Locked")
                .setMessage(String.format(
                        "Complete '%s' first to unlock this level.\n\n" +
                                "📚 %s\n\n" +
                                "Keep learning to progress through the curriculum!",
                        requiredLessonTitle,
                        lesson.getDescription()
                ))
                .setPositiveButton("Go to Required Lesson", (dialog, which) -> {
                    Lesson requiredLessonObj = findLessonById(requiredLesson);
                    if (requiredLessonObj != null && !requiredLessonObj.isLocked()) {
                        openLesson(requiredLessonObj);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getRequiredLessonForUnlock(String lessonId) {
        switch (lessonId) {
            case "lesson_2": return "lesson_1";
            case "lesson_3": return "lesson_2";
            case "lesson_4": return "lesson_3";
            case "lesson_5": return "lesson_4";
            default: return "lesson_1";
        }
    }

    private String getRequiredLessonTitle(String lessonId) {
        Lesson lesson = findLessonById(lessonId);
        return lesson != null ? lesson.getTitle() : "Previous Lesson";
    }

    private Lesson findLessonById(String lessonId) {
        for (Lesson lesson : lessonList) {
            if (lesson.getLessonId().equals(lessonId)) {
                return lesson;
            }
        }
        return null;
    }

    private void openLesson(Lesson lesson) {
        Intent intent = new Intent(this, LearningActivity.class);
        intent.putExtra("lesson_id", lesson.getLessonId());
        intent.putExtra("lesson_name", lesson.getTitle());
        startActivityForResult(intent, LESSON_REQUEST_CODE); // 🔧 Changed from startActivity
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_profile) {
            openProfileActivity();
            return true;
        } else if (itemId == R.id.action_admin_panel && isAdmin) {
            // 🔐 ADMIN ONLY: Show admin options
            showAdminPanel();
            return true;
        } else if (itemId == R.id.action_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openProfileActivity() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivityForResult(intent, PROFILE_REQUEST_CODE);
    }

    private void logout() {
        mAuth.signOut();
        prefManager.clearAll();
        WebSocketManager.getInstance().disconnect();

        Intent intent = new Intent(this, AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PROFILE_REQUEST_CODE && resultCode == RESULT_OK) {
            loadProfilePicture();
        }

        // Handle lesson completion and achievement tracking
        if (requestCode == LESSON_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            boolean lessonCompleted = data.getBooleanExtra("lesson_completed", false);
            String completedLessonId = data.getStringExtra("lesson_id");

            if (lessonCompleted && completedLessonId != null) {
                checkLessonCompletionAchievements(completedLessonId);
                loadLessons(); // Refresh lesson list to update lock states
            }
        }

        // Handle training completion
        if (requestCode == Constants.TRAINING_REQUEST_CODE && resultCode == RESULT_OK) {
            // 🔧 USE CENTRALIZED ACHIEVEMENT SYSTEM
            AchievementManager.getInstance().unlockTrainerAchievement(this);
            Toast.makeText(this, "AI Model Training completed! 🎉", Toast.LENGTH_LONG).show();
        }


        // Handle admin training completion
        if (requestCode == ADMIN_TRAINING_REQUEST_CODE && resultCode == RESULT_OK) {
            Toast.makeText(this, "Admin training completed! 🔐", Toast.LENGTH_LONG).show();
        }
    }

    private void checkLessonCompletionAchievements(String completedLessonId) {
        AchievementManager.getInstance().checkLessonCompletionAchievements(this, completedLessonId);
        // Remove the old checkAllLessonsCompleted() call - it's now handled inside AchievementManager
    }


    @Override
    protected void onResume() {
        super.onResume();
        loadProfilePicture();
        // Reload lessons to update lock status
        if (currentUser != null) {
            loadLessons();
        }

        // 🆕 Check time-based achievements whenever user opens/returns to app
        AchievementManager.getInstance().checkTimeBasedAchievements(this);
        Log.d(TAG, "🆕 Checking time-based achievements on app resume");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't disconnect WebSocket here as it's shared across activities
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // 🔐 ADMIN DETECTION: Show admin menu only for guylerner10@gmail.com
        MenuItem adminItem = menu.findItem(R.id.action_admin_panel);
        if (adminItem != null) {
            adminItem.setVisible(isAdmin);
            if (isAdmin) {
                Log.d(TAG, "🔐 Admin menu enabled for: " + FirebaseAuth.getInstance().getCurrentUser().getEmail());
            }
        }

        return true;
    }

    private void showAdminPanel() {
        String[] adminOptions = {
                "🔐 Create Level Models",
                "📊 View Level Models",
                "🗑️ Reset All Progress (Debug)",
                "🔧 Server Status"
        };

        new AlertDialog.Builder(this)
                .setTitle("🔐 Admin Panel")
                .setItems(adminOptions, (dialog, which) -> {
                    Log.d(TAG, "🔐 Admin option selected: " + which);
                    switch (which) {
                        case 0:
                            showLevelModelCreationOptions();
                            break;
                        case 1:
                            Toast.makeText(this, "📊 View level models coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            showResetAllProgressDialog();
                            break;
                        case 3:
                            checkServerStatus();
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();

        Log.d(TAG, "🔐 Admin panel dialog created and shown");
    }

    private void showLevelModelCreationOptions() {
        String[] levelOptions = {
                "📚 Lesson 1 - Basic Greetings",
                "✋ Lesson 2 - Practice Gestures",
                "💬 Lesson 3 - Common Phrases",
                "❤️ Lesson 4 - Expressions",
                "⭐ Lesson 5 - Advanced Practice"
        };
        String[] levelIds = {"lesson_1", "lesson_2", "lesson_3", "lesson_4", "lesson_5"};

        new AlertDialog.Builder(this)
                .setTitle("🔐 Select Level to Train")
                // REMOVE THIS LINE: .setMessage("Create specialized models...")
                .setItems(levelOptions, (dialog, which) -> {
                    String selectedLessonId = levelIds[which];
                    Log.d(TAG, "🔐 Admin selected: " + selectedLessonId);
                    startAdminLevelTraining(selectedLessonId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startAdminLevelTraining(String lessonId) {
        Log.d(TAG, "🔐 Starting admin training for: " + lessonId);

        // Show confirmation before launching
        new AlertDialog.Builder(this)
                .setTitle("🔐 Confirm Admin Training")
                .setMessage("Launch AdminTrainingActivity for " + lessonId + "?\n\n" +
                        "This will start the level model creation process.")
                .setPositiveButton("Start Training", (dialog, which) -> {
                    // Launch AdminTrainingActivity for level-specific model creation
                    Intent intent = new Intent(this, AdminTrainingActivity.class);
                    intent.putExtra("lesson_id", lessonId);
                    Log.d(TAG, "🔐 Launching AdminTrainingActivity with lesson_id: " + lessonId);
                    startActivityForResult(intent, ADMIN_TRAINING_REQUEST_CODE);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showResetAllProgressDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🗑️ Reset All User Progress")
                .setMessage("⚠️ WARNING: This will reset progress for ALL users!\n\nThis action cannot be undone!")
                .setPositiveButton("RESET ALL", (dialog, which) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Final Confirmation")
                            .setMessage("Are you absolutely sure? This affects all users!")
                            .setPositiveButton("Yes, Reset All", (d, w) -> resetAllUsersProgress())
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetAllUsersProgress() {
        mDatabase.child(Constants.USERS_NODE)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            userSnapshot.child(Constants.PROGRESS_NODE).getRef().removeValue();
                        }
                        Toast.makeText(MainActivity.this, "🔐 Admin: All user progress reset", Toast.LENGTH_LONG).show();
                        loadUserData(); // Reload current user's progress
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "❌ Failed to reset progress", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    /**
     * Checks the server status and updates the UI with connection results.
     * Logs server status only if 30 seconds have passed since the last log to reduce noise.
     */
    private void checkServerStatus() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatusLogTime < 30000) {
            return; // Skip logging if less than 30 seconds since last log
        }

        Toast.makeText(this, "🔧 Checking server status...", Toast.LENGTH_SHORT).show();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(Constants.getBaseUrl(this) + "/health")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "✅ Server status: Running");
                        Toast.makeText(MainActivity.this, "✅ Server is running", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, "❌ Server status: Error (code: " + response.code() + ")");
                        Toast.makeText(MainActivity.this, "❌ Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
                response.close();
                lastStatusLogTime = System.currentTimeMillis(); // Update log time
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "❌ Server status: Not reachable");
                    Toast.makeText(MainActivity.this, "❌ Server not reachable", Toast.LENGTH_SHORT).show();
                });
                lastStatusLogTime = System.currentTimeMillis(); // Update log time
            }
        });
    }
}
