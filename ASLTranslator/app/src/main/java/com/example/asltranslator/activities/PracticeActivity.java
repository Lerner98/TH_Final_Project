package com.example.asltranslator.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.example.asltranslator.models.Achievement;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.asltranslator.R;
import com.example.asltranslator.utils.AchievementManager;
import com.example.asltranslator.utils.Constants;
import com.example.asltranslator.network.WebSocketManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 🎯 PracticeActivity - Core ASL Practice Implementation
 *
 * Handles real-time ASL gesture practice with level-specific models.
 * Users can practice either single gestures or complete lesson sets with live feedback.
 *
 * Key Features:
 * - Real-time camera capture and gesture recognition
 * - Level-specific model integration via server practice mode
 * - Live progress tracking and gesture mastery detection
 * - Firebase statistics integration for user progress
 *
 * Data Flow:
 * Intent → Setup → Camera → Server → Recognition → Completion → Return to LearningActivity
 */
public class PracticeActivity extends AppCompatActivity implements WebSocketManager.WebSocketCallback {

    private static final String TAG = "PracticeActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // Practice mode types
    public static final String PRACTICE_MODE_SINGLE = "single_sign";
    public static final String PRACTICE_MODE_FULL_LEVEL = "full_level";

    // Camera frame capture settings
    private static final long PRACTICE_FRAME_INTERVAL = 1000;

    // Level-specific gesture mappings - UPDATED WITH DISTINCT GESTURES
    private static final Map<String, List<String>> LEVEL_GESTURES = new HashMap<String, List<String>>() {{
        put("lesson_1", Arrays.asList("Hello", "Thank You", "Yes", "No"));
        put("lesson_2", Arrays.asList("Happy", "Sad", "Angry", "Love"));
        put("lesson_3", Arrays.asList("Eat", "Drink", "Sleep", "Go"));
        put("lesson_4", Arrays.asList("Book", "Phone", "Car", "Home"));
        put("lesson_5", Arrays.asList("What", "Where", "When", "Who"));  // 🔄 Question Words
    }};

    // CAMERA COMPONENTS
    private PreviewView cameraPreview;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private boolean cameraReady = false;

    // FRAME CAPTURE
    private Handler practiceHandler;
    private Runnable practiceRunnable;

    // Gesture tracking
    private String lastGestureRef = null;
    private Map<String, Integer> gestureCountRef = new HashMap<>();

    // UI Components
    private Toolbar toolbar;
    private TextView tvLessonTitle, tvPracticeMode, tvExpectedGesture, tvCurrentGesture,
            tvConfidence, tvScore, tvTimer, tvInstructions, tvProgress, tvConnectionStatus;
    private MaterialButton btnStartPractice, btnFinishPractice, btnRetry;
    private CircularProgressIndicator progressIndicator;
    private LinearProgressIndicator levelProgress;
    private View gestureCard, practiceContainer, connectionIndicator;

    // Practice state
    private String lessonId;
    private String practiceMode;
    private String currentStepGesture;
    private List<String> levelGestures;
    private int currentGestureIndex = 0;
    private boolean isPracticing = false;
    private long practiceStartTime;
    private int correctDetections = 0;
    private int totalAttempts = 0;
    private int targetDetections = 5;
    private int currentDetections = 0;
    private boolean gestureCompleted = false;

    // Networking
    private WebSocketManager webSocketManager;
    private OkHttpClient httpClient;

    // Firebase
    private DatabaseReference mDatabase;
    private String userId;

    /**
     * 🏗️ Activity Creation - Initialize practice session from intent parameters
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        // Get practice parameters from intent
        lessonId = getIntent().getStringExtra("lesson_id");
        practiceMode = getIntent().getStringExtra("practice_mode");
        currentStepGesture = getIntent().getStringExtra("current_gesture");

        if (lessonId == null || practiceMode == null) {
            Toast.makeText(this, "Invalid practice parameters", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        initNetworking();
        initFirebase();
        setupPracticeSession();
    }

    /**
     * 🎨 Initialize UI Components - Setup all interface elements
     */
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Practice Mode");

        tvLessonTitle = findViewById(R.id.tv_lesson_title);
        tvPracticeMode = findViewById(R.id.tv_practice_mode);
        tvExpectedGesture = findViewById(R.id.tv_expected_gesture);
        tvCurrentGesture = findViewById(R.id.tv_current_gesture);
        tvConfidence = findViewById(R.id.tv_confidence);
        tvScore = findViewById(R.id.tv_score);
        tvTimer = findViewById(R.id.tv_timer);
        tvInstructions = findViewById(R.id.tv_instructions);
        tvProgress = findViewById(R.id.tv_progress);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);

        btnStartPractice = findViewById(R.id.btn_start_practice);
        btnFinishPractice = findViewById(R.id.btn_finish_practice);
        btnRetry = findViewById(R.id.btn_retry);

        progressIndicator = findViewById(R.id.progress_indicator);
        levelProgress = findViewById(R.id.level_progress);
        gestureCard = findViewById(R.id.gesture_card);
        practiceContainer = findViewById(R.id.practice_container);
        connectionIndicator = findViewById(R.id.connection_indicator);
        cameraPreview = findViewById(R.id.camera_preview);

        btnStartPractice.setOnClickListener(v -> startPractice());
        btnFinishPractice.setOnClickListener(v -> finishPractice());
        btnRetry.setOnClickListener(v -> retryCurrentGesture());

        toolbar.setNavigationOnClickListener(v -> {
            if (isPracticing) {
                showExitDialog();
            } else {
                finish();
            }
        });

        checkCameraPermission();
    }

    /**
     * 📷 Camera Permission Management - Ensure camera access for gesture capture
     */
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required for practice", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * 📷 Initialize Camera System - Setup CameraX for gesture capture
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                cameraReady = true;
                Log.d(TAG, "✅ Camera ready for practice");
                updateConnectionStatus(false);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to start camera", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * 📷 Configure Camera Use Cases - Bind preview and capture functionality
     */
    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(240, 320))
                .build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(240, 320))
                .setJpegQuality(40)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build();

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            Log.d(TAG, "✅ Camera bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Camera binding failed", e);
            Toast.makeText(this, "Camera failed to start. Please try again.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * 🌐 Update Connection Status UI - Visual feedback for server connectivity
     */
    private void updateConnectionStatus(boolean connected) {
        if (connectionIndicator != null && tvConnectionStatus != null) {
            if (connected) {
                connectionIndicator.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.success));
                tvConnectionStatus.setText("🟢 Connected to practice server");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
            } else {
                connectionIndicator.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.error));
                tvConnectionStatus.setText("🔴 Connecting to practice server...");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
            }
        }
    }

    /**
     * 🌐 Initialize Networking Components - Setup HTTP and WebSocket clients
     */
    private void initNetworking() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        webSocketManager = WebSocketManager.getInstance();
    }

    /**
     * 🔥 Initialize Firebase Services - Setup user data persistence
     */
    private void initFirebase() {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }


    /**
     * 🎯 Setup Practice Session - Configure gesture list based on practice mode
     */
    private void setupPracticeSession() {
        tvLessonTitle.setText(lessonId.toUpperCase().replace("_", " "));

        if (PRACTICE_MODE_SINGLE.equals(practiceMode)) {
            tvPracticeMode.setText("🎯 Single Sign Practice");

            // Use the current step gesture for single mode
            if (currentStepGesture != null && !currentStepGesture.isEmpty()) {
                tvInstructions.setText("Practice the current lesson step: " + currentStepGesture);
                levelGestures = Arrays.asList(currentStepGesture);
                tvExpectedGesture.setText(currentStepGesture);
            } else {
                Log.e(TAG, "❌ currentStepGesture is null or empty!");
                Toast.makeText(this, "Error: No gesture specified", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } else {
            tvPracticeMode.setText("🏆 Full Level Practice");
            levelGestures = LEVEL_GESTURES.get(lessonId);
            tvInstructions.setText("Practice all gestures for " + lessonId);

            if (levelGestures != null && !levelGestures.isEmpty()) {
                tvExpectedGesture.setText(levelGestures.get(0));
            }
        }

        if (levelGestures == null || levelGestures.isEmpty()) {
            Log.e(TAG, "❌ No gestures defined - levelGestures: " + levelGestures);
            Toast.makeText(this, "No gestures defined for this practice", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "✅ Practice session setup complete");
        updateUI();
    }


    /**
     * 🚀 Start Practice Session - Begin gesture recognition and tracking
     */
    private void startPractice() {
        Log.d(TAG, "🚀 startPractice() called - Current state:");
        Log.d(TAG, "   - isPracticing: " + isPracticing);
        Log.d(TAG, "   - gestureCompleted: " + gestureCompleted);
        Log.d(TAG, "   - Button enabled: " + btnStartPractice.isEnabled());

        // 🔧 SAFETY: Prevent multiple starts
        if (isPracticing) {
            Log.w(TAG, "⚠️ Already practicing, ignoring start request");
            return;
        }

        if (gestureCompleted) {
            Log.w(TAG, "⚠️ Gesture already completed, ignoring start request");
            return;
        }

        if (!btnStartPractice.isEnabled()) {
            Log.w(TAG, "⚠️ Start button disabled, ignoring start request");
            return;
        }

        Log.d(TAG, "✅ Starting practice session...");
        startServerPracticeMode();
    }

    /**
     * 🌐 Enable Server Practice Mode - Configure server for lesson-specific recognition
     */
    private void startServerPracticeMode() {
        tvInstructions.setText("🔄 Starting practice mode...");
        btnStartPractice.setEnabled(false);

        String url = Constants.getBaseUrl(this) + "/practice/start";
        String jsonData = String.format("{\"lesson_id\":\"%s\"}", lessonId);

        RequestBody body = RequestBody.create(jsonData, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        startCameraPractice();
                    } else {
                        tvInstructions.setText("❌ Failed to start practice mode");
                        btnStartPractice.setEnabled(true);
                        Toast.makeText(PracticeActivity.this, "Server error", Toast.LENGTH_SHORT).show();
                    }
                });
                response.close();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    tvInstructions.setText("❌ Connection failed");
                    btnStartPractice.setEnabled(true);
                    Toast.makeText(PracticeActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 📷 Start Camera Practice - Begin automated gesture capture and recognition
     */
    private void startCameraPractice() {
        isPracticing = true;
        practiceStartTime = System.currentTimeMillis();
        currentGestureIndex = 0;
        currentDetections = 0;
        correctDetections = 0;
        totalAttempts = 0;
        gestureCompleted = false;

        gestureCountRef.clear();
        lastGestureRef = null;

        // 🆕 USE CENTRALIZED ACHIEVEMENT SYSTEM DIRECTLY
        AchievementManager.getInstance().checkTimeBasedAchievements(this);

        webSocketManager.connect(Constants.getWebSocketUrl(this), this);

        updateCurrentGesture();
        updateUI();

        String expectedGesture = getCurrentExpectedGesture();
        Log.d(TAG, "🎯 Starting practice for gesture: " + expectedGesture);

        tvInstructions.setText("🎯 Show the gesture: " + expectedGesture);
        tvExpectedGesture.setText(expectedGesture);

        btnStartPractice.setVisibility(View.GONE);
        btnFinishPractice.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.VISIBLE);

        updateConnectionStatus(true);
        tvConnectionStatus.setText("🟢 Practice mode active - Level: " + lessonId + ", Gesture: " + expectedGesture);

        startPracticeFrameCapture();
        startTimer();

        Log.d(TAG, "✅ Practice started for " + lessonId + " in " + practiceMode + " mode - Expected: " + expectedGesture);
    }



    /**
     * 🎯 Get Current Expected Gesture - Safely retrieve the gesture user should practice
     */
    private String getCurrentExpectedGesture() {
        if (levelGestures != null && currentGestureIndex >= 0 && currentGestureIndex < levelGestures.size()) {
            String gesture = levelGestures.get(currentGestureIndex);
            Log.d(TAG, "🎯 getCurrentExpectedGesture() returning: " + gesture + " (index: " + currentGestureIndex + ")");
            return gesture;
        }

        Log.e(TAG, "❌ getCurrentExpectedGesture() - Invalid state: levelGestures=" + levelGestures +
                ", currentGestureIndex=" + currentGestureIndex);
        return "Unknown";
    }

    /**
     * 📷 Start Practice Frame Capture - Begin automated gesture capture loop
     */
    private void startPracticeFrameCapture() {
        practiceHandler = new Handler(Looper.getMainLooper());
        practiceRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPracticing && cameraReady) {
                    capturePracticeFrame();
                    practiceHandler.postDelayed(this, PRACTICE_FRAME_INTERVAL);
                }
            }
        };
        practiceHandler.post(practiceRunnable);
    }

    /**
     * 📷 Stop Practice Frame Capture - Clean up automated capture system
     */
    private void stopPracticeFrameCapture() {
        if (practiceHandler != null && practiceRunnable != null) {
            practiceHandler.removeCallbacks(practiceRunnable);
            practiceHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * 📸 Capture Practice Frame - Take single frame for gesture recognition
     */
    private void capturePracticeFrame() {
        if (!isPracticing || imageCapture == null) return;

        try {
            File outputFile = createTempFile();
            ImageCapture.OutputFileOptions outputFileOptions =
                    new ImageCapture.OutputFileOptions.Builder(outputFile).build();

            imageCapture.takePicture(
                    outputFileOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                            processPracticeFrame(Uri.fromFile(outputFile));
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Error capturing practice frame", exception);
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error capturing practice frame", e);
        }
    }

    /**
     * 🔄 Process Practice Frame - Convert captured image to Base64 and send to server
     */
    private void processPracticeFrame(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            inputStream.close();

            String base64Image = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
            webSocketManager.sendFrame(base64Image);
            Log.d(TAG, "📡 Practice frame sent to server");

        } catch (Exception e) {
            Log.e(TAG, "Error processing practice frame", e);
        } finally {
            try {
                String filePath = imageUri.getPath();
                if (filePath != null) {
                    File tempFile = new File(filePath);
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not delete temp file", e);
            }
        }
    }

    /**
     * 📁 Create Temporary File - Generate unique temp file for image capture
     */
    private File createTempFile() {
        File outputDir = getCacheDir();
        return new File(outputDir, "practice_" + System.currentTimeMillis() + ".jpg");
    }


    /**
     * 🎯 Update Current Gesture - Set expected gesture and reset progress
     */
    private void updateCurrentGesture() {
        if (currentGestureIndex < levelGestures.size()) {
            String expectedGesture = levelGestures.get(currentGestureIndex);
            Log.d(TAG, "🎯 updateCurrentGesture() - Setting expected gesture to: " + expectedGesture);

            tvExpectedGesture.setText(expectedGesture);
            currentDetections = 0;
            updateProgress();
            tvInstructions.setText("🎯 Show the gesture: " + expectedGesture);

            Log.d(TAG, "✅ UI updated for gesture: " + expectedGesture);
        } else {
            Log.w(TAG, "⚠️ currentGestureIndex out of bounds: " + currentGestureIndex);
        }
    }

    /**
     * 🎨 Update UI Elements - Refresh all progress and status displays
     */
    private void updateUI() {
        double accuracy = totalAttempts > 0 ? (double) correctDetections / totalAttempts * 100 : 0;
        tvScore.setText(String.format("Score: %d/%d (%.1f%%)", correctDetections, totalAttempts, accuracy));

        if (isPracticing) {
            int totalGestures = levelGestures.size();
            int completedGestures = currentGestureIndex;
            int overallProgress = (int) ((double) completedGestures / totalGestures * 100);
            levelProgress.setProgress(overallProgress);

            tvProgress.setText(String.format("Gesture %d of %d", currentGestureIndex + 1, totalGestures));
        }

        updateProgress();
    }

    /**
     * 📊 Update Progress Indicators - Update gesture-specific progress tracking
     */
    private void updateProgress() {
        int progress = (int) ((double) currentDetections / targetDetections * 100);
        progressIndicator.setProgress(progress);

        if (currentDetections >= targetDetections) {
            gestureCard.setBackgroundColor(Color.parseColor("#4CAF50"));
        }
    }

    /**
     * ➡️ Move to Next Gesture - Advance to next gesture or complete practice
     */
    private void moveToNextGesture() {
        currentGestureIndex++;
        if (currentGestureIndex >= levelGestures.size()) {
            completePractice();
        } else {
            updateCurrentGesture();
            tvInstructions.setText("🎯 Next gesture: " + getCurrentExpectedGesture());
            updateUI();
        }
    }

// REPLACE the retryCurrentGesture() method in PracticeActivity.java

    /**
     * 🔄 Retry Current Gesture - Reset entire practice session and restart
     */
    private void retryCurrentGesture() {
        Log.d(TAG, "🔄 retryCurrentGesture() called - restarting entire practice session");

        // 🔧 STOP current practice session completely
        isPracticing = false;
        gestureCompleted = false;
        stopPracticeFrameCapture();

        // 🔧 Disconnect WebSocket cleanly
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }

        // 🔧 RESET ALL practice statistics
        currentGestureIndex = 0;
        currentDetections = 0;
        correctDetections = 0;
        totalAttempts = 0;

        // 🔧 RESET UI completely
        btnStartPractice.setVisibility(View.VISIBLE);
        btnStartPractice.setEnabled(true);
        btnFinishPractice.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);

        // 🔧 RESET practice container and cards
        practiceContainer.setVisibility(View.GONE);
        gestureCard.setBackgroundColor(Color.parseColor("#FFFFFF"));

        // 🔧 RESET text colors and content
        if (tvCurrentGesture != null) {
            tvCurrentGesture.setTextColor(Color.parseColor("#000000"));
            tvCurrentGesture.setText("Waiting to start...");
        }

        // 🔧 RESET progress indicators
        progressIndicator.setProgress(0);
        levelProgress.setProgress(0);

        // 🔧 RESET instructions and expected gesture
        String expectedGesture = getCurrentExpectedGesture();
        tvInstructions.setText("Ready to practice " + expectedGesture + " again!");
        tvExpectedGesture.setText(expectedGesture);

        // 🔧 RESET connection status
        updateConnectionStatus(false);
        tvConnectionStatus.setText("🔴 Ready to start practice");

        // 🔧 RESET timer display
        tvTimer.setText("00:00");

        // 🔧 UPDATE UI elements
        updateUI();

        Log.d(TAG, "✅ Complete practice restart - ready to start fresh");
        Log.d(TAG, "   - isPracticing: " + isPracticing);
        Log.d(TAG, "   - gestureCompleted: " + gestureCompleted);
        Log.d(TAG, "   - correctDetections: " + correctDetections);
        Log.d(TAG, "   - totalAttempts: " + totalAttempts);
        Log.d(TAG, "   - Start button visible and enabled");

        Toast.makeText(this, "Practice reset - ready to start fresh!", Toast.LENGTH_SHORT).show();
    }


    /**
     * 🎉 Complete Practice Session - Handle practice completion and show results
     */
    private void completePractice() {
        Log.d(TAG, "🔥🔥🔥 completePractice() ENTRY - gestureCompleted: " + gestureCompleted +
                ", isFinishing: " + isFinishing() +
                ", isDestroyed: " + isDestroyed());

        if (gestureCompleted && (System.currentTimeMillis() - practiceStartTime) < 1000) {
            Log.d(TAG, "🔥 DUPLICATE CALL DETECTED - Already completed recently, returning early");
            return;
        }

        if (!gestureCompleted) {
            Log.d(TAG, "🔥 Setting gestureCompleted = true");
            gestureCompleted = true;
        }

        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "🔥 Activity finishing/destroyed - direct return to lesson");
            returnToLesson();
            return;
        }

        isPracticing = false;
        stopPracticeFrameCapture();

        long practiceTime = System.currentTimeMillis() - practiceStartTime;
        double accuracy = totalAttempts > 0 ? (double) correctDetections / totalAttempts : 0;

        Log.d(TAG, "🔥 Practice stats - Time: " + practiceTime + "ms, Accuracy: " + (accuracy * 100) + "%");

        stopServerPracticeMode();
        updatePracticeStats(practiceTime, accuracy);

        // 🆕 USE CENTRALIZED ACHIEVEMENT SYSTEM DIRECTLY
        AchievementManager.getInstance().updateConsistencyStreak(this);
        AchievementManager.getInstance().checkPracticeAchievements(this, accuracy, practiceTime);

        String message = String.format(
                "Practice Complete!\n\n" +
                        "Mode: %s\n" +
                        "Accuracy: %.1f%%\n" +
                        "Time: %.1f seconds\n" +
                        "Correct: %d/%d attempts",
                practiceMode.equals(PRACTICE_MODE_SINGLE) ? "Single Sign" : "Full Level",
                accuracy * 100,
                practiceTime / 1000.0,
                correctDetections,
                totalAttempts
        );

        try {
            Log.d(TAG, "🔥 Creating AlertDialog...");

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("🎉 Practice Complete!");
            builder.setMessage(message);
            builder.setPositiveButton("Continue Learning", (d, which) -> {
                Log.d(TAG, "🔥 Continue Learning clicked - calling returnToLesson()");
                returnToLesson();
            });
            builder.setNegativeButton("Practice Again", (d, which) -> {
                Log.d(TAG, "🔥 Practice Again clicked - calling restartPractice()");
                restartPractice();
            });
            builder.setCancelable(false);

            AlertDialog dialog = builder.create();

            Log.d(TAG, "🔥 Showing dialog...");
            dialog.show();
            Log.d(TAG, "🔥🔥🔥 DIALOG SHOWN SUCCESSFULLY!");

        } catch (Exception e) {
            Log.e(TAG, "🔥💥 DIALOG CREATION FAILED: " + e.getMessage(), e);
            e.printStackTrace();

            android.widget.Toast.makeText(this, "Practice Complete! Returning to lesson...", android.widget.Toast.LENGTH_LONG).show();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                returnToLesson();
            }, 2000);
        }

        Log.d(TAG, "🔥 completePractice() EXIT");
    }


    private void stopServerPracticeMode() {
        String url = Constants.getBaseUrl(this) + "/practice/stop";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.get("application/json")))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.d(TAG, "Practice mode stopped on server");
                response.close();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to stop practice mode", e);
            }
        });
    }

    private void updatePracticeStats(long practiceTime, double accuracy) {
        Map<String, Object> updates = new HashMap<>();

        updates.put("lastPracticeTime", System.currentTimeMillis());
        updates.put("totalPracticeTime", ServerValue.increment(practiceTime));
        updates.put("practiceSessionsCompleted", ServerValue.increment(1));
        updates.put("totalTranslations", ServerValue.increment(correctDetections));

        String lessonStatsPath = "lessonStats/" + lessonId;
        updates.put(lessonStatsPath + "/practiceAttempts", ServerValue.increment(1));
        updates.put(lessonStatsPath + "/totalPracticeTime", ServerValue.increment(practiceTime));
        updates.put(lessonStatsPath + "/lastAccuracy", accuracy);
        updates.put(lessonStatsPath + "/totalCorrectDetections", ServerValue.increment(correctDetections));
        updates.put(lessonStatsPath + "/totalAttempts", ServerValue.increment(totalAttempts));

        // 🔧 FIX: Use decimal comparison (0.8 = 80%)
        if (accuracy >= 0.8) {
            updates.put(lessonStatsPath + "/highAccuracyCount", ServerValue.increment(1));
        }

        String progressPath = Constants.PROGRESS_NODE + "/" + lessonId;
        updates.put(progressPath + "/practiceAttempts", ServerValue.increment(1));
        updates.put(progressPath + "/practiceTime", ServerValue.increment(practiceTime));
        updates.put(progressPath + "/lastAccuracy", accuracy);
        updates.put(progressPath + "/lastPracticeTime", System.currentTimeMillis());

        mDatabase.child(Constants.USERS_NODE)
                .child(userId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Practice stats updated:");
                    Log.d(TAG, "   - Translations added: " + correctDetections);
                    Log.d(TAG, "   - Practice time added: " + (practiceTime / 1000) + "s");
                    Log.d(TAG, "   - Lesson: " + lessonId);
                    // 🔧 FIX: Convert decimal to percentage for logging
                    Log.d(TAG, "   - Accuracy: " + (accuracy * 100) + "%");
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update practice stats", e));
    }

    private void showPracticeCompleteDialog(long practiceTime, double accuracy) {
        // 🔧 FIX: Ensure dialog is shown properly and buttons work
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity finishing/destroyed, not showing completion dialog");
            return;
        }

        String message = String.format(
                "Practice Complete!\n\n" +
                        "Mode: %s\n" +
                        "Accuracy: %.1f%%\n" +
                        "Time: %.1f seconds\n" +
                        "Correct: %d/%d attempts",
                practiceMode.equals(PRACTICE_MODE_SINGLE) ? "Single Sign" : "Full Level",
                accuracy * 100,  // ✅ This was already correct - converts decimal to percentage
                practiceTime / 1000.0,
                correctDetections,
                totalAttempts
        );

        Log.d(TAG, "🎉 Showing practice completion dialog");

        new AlertDialog.Builder(this)
                .setTitle("🎉 Practice Complete!")
                .setMessage(message)
                .setPositiveButton("Continue Learning", (dialog, which) -> {
                    Log.d(TAG, "✅ User chose 'Continue Learning' - returning to lesson");
                    returnToLesson();
                })
                .setNegativeButton("Practice Again", (dialog, which) -> {
                    Log.d(TAG, "🔄 User chose 'Practice Again' - restarting");
                    restartPractice();
                })
                .setCancelable(false)
                .show();
    }

    private void returnToLesson() {
        Log.d(TAG, "🎯 returnToLesson() called");

        try {
            double accuracyDecimal = totalAttempts > 0 ? (double) correctDetections / totalAttempts : 0;
            double accuracyPercentage = accuracyDecimal * 100;

            Intent resultIntent = new Intent();
            resultIntent.putExtra("practice_completed", true);
            resultIntent.putExtra("practice_mode", practiceMode);
            resultIntent.putExtra("accuracy", accuracyPercentage); // Send as percentage
            resultIntent.putExtra("practice_time", System.currentTimeMillis() - practiceStartTime);
            resultIntent.putExtra("correct_detections", correctDetections);
            resultIntent.putExtra("total_attempts", totalAttempts);
            resultIntent.putExtra("lesson_id", lessonId);

            // 🔧 FIXED: Safe gesture completion data extraction
            String masteredGesture = null;

            if (PRACTICE_MODE_SINGLE.equals(practiceMode)) {
                masteredGesture = currentStepGesture;
            } else if (PRACTICE_MODE_FULL_LEVEL.equals(practiceMode)) {
                if (levelGestures != null && currentGestureIndex >= 0 && currentGestureIndex < levelGestures.size()) {
                    masteredGesture = levelGestures.get(currentGestureIndex);
                }
            }

            if (masteredGesture == null) {
                if (levelGestures != null && !levelGestures.isEmpty()) {
                    masteredGesture = levelGestures.get(0);
                } else {
                    masteredGesture = "Unknown";
                }
            }

            resultIntent.putExtra("completed_gesture", masteredGesture);
            resultIntent.putExtra("gesture_mastered", correctDetections >= targetDetections);
            // 🔧 FIX: Use decimal accuracy for comparison
            resultIntent.putExtra("high_accuracy", accuracyDecimal >= 0.8); // 80% as decimal

            Log.d(TAG, "🎯 Returning to lesson with completion data:");
            Log.d(TAG, "   - Practice Mode: " + practiceMode);
            Log.d(TAG, "   - Gesture: " + masteredGesture);
            Log.d(TAG, "   - Mastered: " + (correctDetections >= targetDetections));
            Log.d(TAG, "   - Accuracy: " + accuracyPercentage + "%");
            Log.d(TAG, "   - High Accuracy: " + (accuracyDecimal >= 0.8));

            setResult(Activity.RESULT_OK, resultIntent);
            finish();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in returnToLesson(): " + e.getMessage(), e);

            Intent fallbackIntent = new Intent();
            fallbackIntent.putExtra("practice_completed", true);
            fallbackIntent.putExtra("lesson_id", lessonId);
            fallbackIntent.putExtra("gesture_mastered", true);

            setResult(Activity.RESULT_OK, fallbackIntent);
            finish();
        }
    }

    private void restartPractice() {
        Log.d(TAG, "🔄 restartPractice() called - resetting all states");

        // 🔧 CRITICAL: Reset ALL practice state variables
        currentGestureIndex = 0;
        currentDetections = 0;
        correctDetections = 0;
        totalAttempts = 0;
        isPracticing = false;
        gestureCompleted = false;

        // 🔧 CRITICAL: Stop any running handlers/timers
        stopPracticeFrameCapture();

        // 🔧 CRITICAL: Disconnect WebSocket cleanly
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }

        // 🔧 CRITICAL: Reset UI to initial state
        btnStartPractice.setVisibility(View.VISIBLE);
        btnStartPractice.setEnabled(true); // Make sure it's clickable
        btnFinishPractice.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);

        // 🔧 CRITICAL: Reset practice container and cards
        practiceContainer.setVisibility(View.GONE);
        gestureCard.setBackgroundColor(Color.parseColor("#FFFFFF"));

        // 🔧 CRITICAL: Reset text colors
        if (tvCurrentGesture != null) {
            tvCurrentGesture.setTextColor(Color.parseColor("#000000"));
            tvCurrentGesture.setText("Waiting to start...");
        }

        // 🔧 CRITICAL: Reset progress indicators
        progressIndicator.setProgress(0);
        levelProgress.setProgress(0);

        // 🔧 CRITICAL: Reset instructions and expected gesture
        String expectedGesture = getCurrentExpectedGesture();
        tvInstructions.setText("Ready to practice " + expectedGesture + " again!");
        tvExpectedGesture.setText(expectedGesture);

        // 🔧 CRITICAL: Reset connection status
        updateConnectionStatus(false);
        tvConnectionStatus.setText("🔴 Ready to start practice");

        // 🔧 CRITICAL: Reset timer display
        tvTimer.setText("00:00");

        // 🔧 CRITICAL: Update UI elements
        updateUI();

        Log.d(TAG, "✅ Practice restart complete - UI reset to initial state");
        Log.d(TAG, "   - isPracticing: " + isPracticing);
        Log.d(TAG, "   - gestureCompleted: " + gestureCompleted);
        Log.d(TAG, "   - Start button visible: " + (btnStartPractice.getVisibility() == View.VISIBLE));
        Log.d(TAG, "   - Start button enabled: " + btnStartPractice.isEnabled());
    }

    private void finishPractice() {
        if (isPracticing) {
            showExitDialog();
        } else {
            finish();
        }
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Practice?")
                .setMessage("Your practice progress will be lost. Are you sure?")
                .setPositiveButton("Exit", (dialog, which) -> {
                    stopServerPracticeMode();
                    webSocketManager.disconnect();
                    finish();
                })
                .setNegativeButton("Continue Practicing", null)
                .show();
    }

    private void startTimer() {
        Handler timerHandler = new Handler(Looper.getMainLooper());
        Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPracticing) {
                    long elapsed = System.currentTimeMillis() - practiceStartTime;
                    int seconds = (int) (elapsed / 1000);
                    int minutes = seconds / 60;
                    seconds = seconds % 60;

                    tvTimer.setText(String.format("%02d:%02d", minutes, seconds));
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    // 🔧 ENHANCED: onConnected with proper UI update
    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Log.d(TAG, "✅ WebSocket connected for practice");
            updateConnectionStatus(true);

            if (isPracticing) {
                String expectedGesture = getCurrentExpectedGesture();
                tvConnectionStatus.setText("🟢 Practice server connected - " + lessonId);
                tvInstructions.setText("🟢 Connected! Show the gesture: " + expectedGesture);

                // 🔧 CRITICAL: Make sure expected gesture is displayed correctly
                tvExpectedGesture.setText(expectedGesture);

                Log.d(TAG, "🎯 Connected - Expected gesture UI set to: " + expectedGesture);
            } else {
                tvConnectionStatus.setText("🟢 Ready to practice");
            }
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Log.d(TAG, "🔴 WebSocket disconnected");
            updateConnectionStatus(false);
            if (isPracticing) {
                tvInstructions.setText("🔴 Connection lost. Reconnecting...");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isPracticing) {
                        webSocketManager.connect(Constants.getWebSocketUrl(this), this);
                    }
                }, 2000);
            }
        });
    }

    @Override
    public void onGestureDetected(com.example.asltranslator.models.GestureResponse response) {
        if (!isPracticing || gestureCompleted) {
            Log.d(TAG, "📥 SKIPPING gesture - isPracticing: " + isPracticing + ", gestureCompleted: " + gestureCompleted);
            return;
        }

        runOnUiThread(() -> {
            String expectedGesture = getCurrentExpectedGesture();
            String displayedGesture = tvExpectedGesture.getText().toString();

            Log.d(TAG, "📥 Processing gesture: " + response.getGesture() +
                    ", confidence: " + response.getConfidence() +
                    ", currentDetections: " + currentDetections +
                    ", targetDetections: " + targetDetections);

            // 🔧 CRITICAL DEBUG: Check UI state
            Log.d(TAG, "🎯 UI STATE CHECK:");
            Log.d(TAG, "   - Expected (logic): " + expectedGesture);
            Log.d(TAG, "   - Displayed (UI): " + displayedGesture);
            Log.d(TAG, "   - Current Step Gesture: " + currentStepGesture);
            Log.d(TAG, "   - Level Gestures: " + levelGestures);
            Log.d(TAG, "   - Current Index: " + currentGestureIndex);

            // 🔧 FORCE UI UPDATE if they don't match
            if (!expectedGesture.equals(displayedGesture)) {
                Log.w(TAG, "⚠️ UI MISMATCH! Fixing display from '" + displayedGesture + "' to '" + expectedGesture + "'");
                tvExpectedGesture.setText(expectedGesture);
                tvInstructions.setText("🎯 Show the gesture: " + expectedGesture);
            }

            practiceContainer.setVisibility(View.VISIBLE);
            tvCurrentGesture.setText(response.isHandDetected() ? response.getGesture() : "No hand detected");
            tvConfidence.setText(String.format("Confidence: %.1f%%", response.getConfidence() * 100));

            if (response.isHandDetected() && response.getConfidence() > 0.7) {
                String detectedGesture = response.getGesture();

                totalAttempts++;
                Log.d(TAG, "🎯 Attempt #" + totalAttempts + " - Expected: " + expectedGesture + ", Got: " + detectedGesture);

                if (detectedGesture.equalsIgnoreCase(expectedGesture)) {
                    correctDetections++;
                    currentDetections++;

                    Log.d(TAG, "✅ CORRECT! Progress: " + currentDetections + "/" + targetDetections +
                            " (correctDetections: " + correctDetections + ", gestureCompleted: " + gestureCompleted + ")");

                    gestureCard.setBackgroundColor(Color.parseColor("#4CAF50"));
                    tvCurrentGesture.setTextColor(Color.parseColor("#4CAF50"));
                    tvInstructions.setText("✅ Great! " + currentDetections + "/" + targetDetections + " correct");

                    updateProgress();
                    updateUI();

                    if (currentDetections >= targetDetections) {
                        Log.d(TAG, "🚨 TARGET REACHED! currentDetections: " + currentDetections +
                                ", targetDetections: " + targetDetections +
                                ", gestureCompleted: " + gestureCompleted);

                        if (!gestureCompleted) {
                            Log.d(TAG, "🔥 TRIGGERING COMPLETION SEQUENCE");

                            gestureCompleted = true;
                            isPracticing = false;
                            stopPracticeFrameCapture();

                            tvInstructions.setText("🎉 Gesture mastered! Completing practice...");

                            Log.d(TAG, "🔥 Calling completePractice() NOW");
                            completePractice();
                        } else {
                            Log.w(TAG, "⚠️ Completion already triggered, skipping");
                        }
                    } else {
                        Log.d(TAG, "🔄 Still need " + (targetDetections - currentDetections) + " more correct detections");
                    }
                } else {
                    Log.d(TAG, "❌ INCORRECT - Expected: " + expectedGesture + ", Got: " + detectedGesture);

                    gestureCard.setBackgroundColor(Color.parseColor("#F44336"));
                    tvCurrentGesture.setTextColor(Color.parseColor("#F44336"));
                    tvInstructions.setText("❌ Expected: " + expectedGesture + ", try again");

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!gestureCompleted) {
                            gestureCard.setBackgroundColor(Color.parseColor("#FFFFFF"));
                            tvCurrentGesture.setTextColor(Color.parseColor("#000000"));
                            tvInstructions.setText("🎯 Show the gesture: " + expectedGesture);
                        }
                    }, 1000);

                    updateUI();
                }
            } else {
                Log.d(TAG, "👋 Hand not detected or confidence too low: " + response.getConfidence());
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Log.e(TAG, "WebSocket error: " + error);
            tvConnectionStatus.setText("🔴 Connection error: " + error);
            updateConnectionStatus(false);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (practiceHandler != null) {
            practiceHandler.removeCallbacksAndMessages(null);
        }

        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        if (isPracticing) {
            stopServerPracticeMode();
        }

        Log.d(TAG, "🔄 PracticeActivity destroyed and resources cleaned up");
    }

    @Override
    public void onBackPressed() {
        if (isPracticing) {
            showExitDialog();
        } else {
            super.onBackPressed();
        }
    }
}