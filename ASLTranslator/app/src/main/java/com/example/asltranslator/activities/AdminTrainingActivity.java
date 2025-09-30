package com.example.asltranslator.activities;

import android.Manifest;
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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import com.example.asltranslator.utils.Constants;
import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONException;
import org.json.JSONObject;

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
import okhttp3.ResponseBody;

public class AdminTrainingActivity extends AppCompatActivity {

    private static final String TAG = "AdminTraining";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // Admin-only access
    private static final List<String> ADMIN_EMAILS = Arrays.asList("guylerner10@gmail.com","danielseth1840@gmail.com");

    // Training settings - same as successful TrainingActivity
    private static final long CAPTURE_DELAY_MS = 50;
    private static final int TARGET_SAMPLES = 120;

    // Level-specific gesture mappings - UPDATED WITH DISTINCT GESTURES
    private static final Map<String, List<String>> LEVEL_GESTURES = new HashMap<String, List<String>>() {{
        put("lesson_1", Arrays.asList("Hello", "Thank You", "Yes", "No"));
        put("lesson_2", Arrays.asList("Happy", "Sad", "Angry", "Love"));
        put("lesson_3", Arrays.asList("Eat", "Drink", "Sleep", "Go"));
        put("lesson_4", Arrays.asList("Book", "Phone", "Car", "Home"));
        put("lesson_5", Arrays.asList("What", "Where", "When", "Who"));  // 🔄 Question Words
    }};

    // UI Components
    private PreviewView cameraPreview;
    private TextView tvLevelName, tvGestureName, tvInstructions, tvProgress, tvStatus;
    private ProgressBar progressBar;
    private Button btnToggleCapture, btnNextGesture, btnFinishTraining;
    private ImageButton btnBack;
    private MaterialCardView cardProgress;
    private View connectionIndicator;

    // Camera
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

    // Training State
    private boolean isCollecting = false;
    private String currentLessonId = "";
    private List<String> currentLevelGestures;
    private String currentGesture = "";
    private int currentGestureIndex = 0;
    private int samplesCollected = 0;
    private Handler captureHandler;
    private Runnable captureRunnable;
    private boolean cameraReady = false;

    private String trainingSessionId;
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_training);

        // 🔍 DEBUG: Add these lines first
        Log.d("ADMIN_DEBUG", "=== AdminTrainingActivity onCreate started ===");
        Log.d("ADMIN_DEBUG", "Current user: " + (FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getEmail() : "null"));

        if (!isAdminUser()) {
            Log.d("ADMIN_DEBUG", "❌ Admin check FAILED - showing access denied");
            showAccessDeniedDialog();
            return;
        } else {
            Log.d("ADMIN_DEBUG", "✅ Admin check PASSED - continuing");
        }

        // Get lesson ID from intent
        currentLessonId = getIntent().getStringExtra("lesson_id");
        if (currentLessonId == null) {
            showLevelSelectionDialog();
            return;
        }

        currentLevelGestures = LEVEL_GESTURES.get(currentLessonId);
        if (currentLevelGestures == null) {
            Toast.makeText(this, "Invalid lesson ID", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        initHTTPClient();
        setupClickListeners();
        checkCameraPermission();
    }

    private boolean isAdminUser() {
        Log.d("ADMIN_DEBUG", "🔍 Checking admin status...");

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.d("ADMIN_DEBUG", "❌ No user logged in");
            return false;
        }

        String currentEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        Log.d("ADMIN_DEBUG", "📧 Current email: " + currentEmail);
        Log.d("ADMIN_DEBUG", "📧 Admin emails: " + ADMIN_EMAILS.toString());

        boolean isAdmin = ADMIN_EMAILS.contains(currentEmail);
        Log.d("ADMIN_DEBUG", "🔐 Is admin? " + isAdmin);

        return isAdmin;
    }



    private void showAccessDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🔒 Admin Access Required")
                .setMessage("This feature is restricted to administrators only.\n\nLevel model creation requires special permissions.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showLevelSelectionDialog() {
        String[] levelOptions = {
                "Lesson 1 - Basic Greetings (Hello, Thank You, Yes, No)",
                "Lesson 2 - Emotions & Feelings (Happy, Sad, Angry, Love)",
                "Lesson 3 - Daily Actions (Eat, Drink, Sleep, Go)",
                "Lesson 4 - Common Objects (Book, Phone, Car, Home)",
                "Lesson 5 - Question Words (What, Where, When, Who)"  // 🔄 Updated
        };
        String[] levelIds = {"lesson_1", "lesson_2", "lesson_3", "lesson_4", "lesson_5"};

        new AlertDialog.Builder(this)
                .setTitle("🎯 Select Level to Train")
                .setMessage("Create specialized models for each lesson's unique gestures")
                .setItems(levelOptions, (dialog, which) -> {
                    currentLessonId = levelIds[which];
                    currentLevelGestures = LEVEL_GESTURES.get(currentLessonId);
                    initViews();
                    initHTTPClient();
                    setupClickListeners();
                    checkCameraPermission();
                })
                .setCancelable(false)
                .show();
    }

    private void initViews() {
        if (currentLessonId == null) return;

        cameraPreview = findViewById(R.id.camera_preview);
        tvLevelName = findViewById(R.id.tv_level_name);
        tvGestureName = findViewById(R.id.tv_gesture_name);
        tvInstructions = findViewById(R.id.tv_instructions);
        tvProgress = findViewById(R.id.tv_progress);
        tvStatus = findViewById(R.id.tv_connection_status);
        progressBar = findViewById(R.id.progress_bar);
        btnToggleCapture = findViewById(R.id.btn_start_stop);
        btnNextGesture = findViewById(R.id.btn_next_gesture);
        btnFinishTraining = findViewById(R.id.btn_finish_training);
        btnBack = findViewById(R.id.btn_back);
        cardProgress = findViewById(R.id.card_progress);
        connectionIndicator = findViewById(R.id.connection_indicator);

        // Set level name
        tvLevelName.setText("Admin Training: " + currentLessonId.toUpperCase());

        // Initial UI state
        btnToggleCapture.setEnabled(false);
        btnNextGesture.setEnabled(false);
        btnFinishTraining.setEnabled(false);

        updateConnectionStatus(false);

        tvStatus.setText("📷 Initializing camera...");
        tvInstructions.setText("Admin mode: Creating level-specific model for " + currentLessonId);
    }

    private void initHTTPClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        trainingSessionId = "admin_level_" + currentLessonId + "_" + System.currentTimeMillis();
        Log.d(TAG, "Admin training session: " + trainingSessionId);
    }

    private void setupClickListeners() {

        btnToggleCapture.setOnClickListener(v -> {
            if (cameraReady) {
                toggleCollection();
            } else {
                Toast.makeText(this, "Please wait for camera to initialize", Toast.LENGTH_SHORT).show();
            }
        });

        btnNextGesture.setOnClickListener(v -> nextGesture());
        btnFinishTraining.setOnClickListener(v -> showFinishDialog());
    }

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
                Toast.makeText(this, "Camera permission required for admin training", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();

                cameraReady = true;
                runOnUiThread(() -> {
                    startGestureCollection(currentLevelGestures.get(0));
                    updateConnectionStatus(true);
                });

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to start camera", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        // Same camera settings as successful TrainingActivity
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
        }
    }

    private void startGestureCollection(String gesture) {
        if (isCollecting) {
            stopAutomaticCapture();
            isCollecting = false;
        }

        currentGesture = gesture;
        samplesCollected = 0;
        isCollecting = false;

        tvGestureName.setText(currentGesture);
        tvInstructions.setText("🎯 Admin: Ready to collect '" + gesture + "' for " + currentLessonId);
        updateProgress();

        btnToggleCapture.setText("START COLLECTING");
        btnToggleCapture.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnToggleCapture.setEnabled(cameraReady);

        btnNextGesture.setEnabled(false);
        btnFinishTraining.setEnabled(false);

        tvStatus.setText("⏸️ Ready to collect '" + gesture + "' for level model");
        tvStatus.setTextColor(Color.parseColor("#757575"));

        Log.d(TAG, "🔄 ADMIN RESET: Ready to collect: " + currentGesture + " for " + currentLessonId);
    }

    // Rest of the methods follow the same pattern as TrainingActivity but with admin-specific endpoints...

    private void toggleCollection() {
        isCollecting = !isCollecting;

        if (isCollecting) {
            btnToggleCapture.setText("STOP COLLECTING");
            btnToggleCapture.setBackgroundColor(Color.parseColor("#F44336"));
            tvInstructions.setText("🔴 ADMIN COLLECTING '" + currentGesture + "' for " + currentLessonId);
            tvStatus.setText("🔴 RECORDING FOR LEVEL MODEL");
            tvStatus.setTextColor(Color.parseColor("#4CAF50"));

            btnNextGesture.setEnabled(false);
            btnFinishTraining.setEnabled(false);

            startAutomaticCapture();
            Log.d(TAG, "🔴 Admin started collecting: " + currentGesture);

        } else {
            stopAutomaticCapture();
            btnToggleCapture.setText("START COLLECTING");
            btnToggleCapture.setBackgroundColor(Color.parseColor("#4CAF50"));
            tvStatus.setText("⏸️ PAUSED - " + samplesCollected + "/" + TARGET_SAMPLES);
            tvStatus.setTextColor(Color.parseColor("#757575"));

            checkGestureCompletion();
            Log.d(TAG, "⏸️ Admin paused collecting: " + currentGesture + " (" + samplesCollected + " samples)");
        }
    }

    private void checkGestureCompletion() {
        if (samplesCollected >= TARGET_SAMPLES) {
            tvInstructions.setText("✅ Admin completed " + currentGesture + "! Move to next or finish level model.");
            btnNextGesture.setEnabled(true);
            btnFinishTraining.setEnabled(true);

            cardProgress.setCardBackgroundColor(Color.parseColor("#4CAF50"));
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                cardProgress.setCardBackgroundColor(Color.WHITE);
            }, 1000);

            Log.d(TAG, "✅ Admin gesture '" + currentGesture + "' completed with " + samplesCollected + " samples");
        } else {
            tvInstructions.setText("⏸️ Paused - Need " + (TARGET_SAMPLES - samplesCollected) + " more samples");
            btnNextGesture.setEnabled(false);
            btnFinishTraining.setEnabled(false);
        }
    }

    private void startAutomaticCapture() {
        captureHandler = new Handler(Looper.getMainLooper());
        captureRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCollecting && samplesCollected < TARGET_SAMPLES) {
                    captureTrainingFrame();
                    captureHandler.postDelayed(this, CAPTURE_DELAY_MS);
                } else if (isCollecting && samplesCollected >= TARGET_SAMPLES) {
                    Log.d(TAG, "✅ Admin target reached, auto-stopping collection");
                    toggleCollection();
                }
            }
        };
        captureHandler.post(captureRunnable);
    }

    private void stopAutomaticCapture() {
        if (captureHandler != null && captureRunnable != null) {
            captureHandler.removeCallbacks(captureRunnable);
            captureHandler.removeCallbacksAndMessages(null);
            Log.d(TAG, "🛑 Admin capture callbacks removed");
        }
        captureRunnable = null;
    }

    private void captureTrainingFrame() {
        if (!isCollecting || imageCapture == null || samplesCollected >= TARGET_SAMPLES) {
            return;
        }

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
                            if (samplesCollected < TARGET_SAMPLES) {
                                sendAdminTrainingFrame(Uri.fromFile(outputFile));
                            }
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Error capturing admin frame", exception);
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error capturing admin frame", e);
        }
    }

    private void sendAdminTrainingFrame(Uri imageUri) {
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

            // Send to admin training endpoint
            String url = Constants.getBaseUrl(this) + "/admin/training/add-sample";
            String jsonData = String.format(
                    "{\"session_id\":\"%s\",\"lesson_id\":\"%s\",\"gesture_name\":\"%s\",\"frame\":\"data:image/jpeg;base64,%s\",\"timestamp\":%d}",
                    trainingSessionId, currentLessonId, currentGesture, base64Image, System.currentTimeMillis()
            );

            RequestBody body = RequestBody.create(jsonData, MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody responseBody = response.body();
                    if (response.isSuccessful() && responseBody != null) {
                        try {
                            String bodyString = responseBody.string();
                            JSONObject jsonObject = new JSONObject(bodyString);
                            int samples = jsonObject.getInt("samples");
                            if (samples > 0 && isCollecting && samplesCollected < TARGET_SAMPLES) {
                                runOnUiThread(() -> {
                                    samplesCollected++;
                                    updateProgress();

                                    Log.d(TAG, "📊 Admin HTTP Response: " + currentGesture + " sample #" + samplesCollected);

                                    cardProgress.setCardBackgroundColor(Color.parseColor("#4CAF50"));
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        cardProgress.setCardBackgroundColor(Color.WHITE);
                                    }, 100);

                                    if (samplesCollected >= TARGET_SAMPLES) {
                                        Log.d(TAG, "🎯 Admin reached target samples for " + currentGesture);
                                        toggleCollection();
                                    }
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing admin JSON response: " + e.getMessage());
                        } finally {
                            responseBody.close();
                        }
                    } else {
                        runOnUiThread(() -> {
                            String errorMessage = "Admin server error";
                            Log.e(TAG, "Admin server error: " + response.code());
                            Toast.makeText(AdminTrainingActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        });
                    }
                    response.close();
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to send admin training frame", e);
                    runOnUiThread(() -> {
                        Toast.makeText(AdminTrainingActivity.this, "Admin connection failed. Check server.", Toast.LENGTH_SHORT).show();
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing admin training frame", e);
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
                Log.w(TAG, "Could not delete admin temp file", e);
            }
        }
    }

    private void nextGesture() {
        if (isCollecting) {
            stopAutomaticCapture();
            isCollecting = false;
        }

        currentGestureIndex++;
        if (currentGestureIndex < currentLevelGestures.size()) {
            startGestureCollection(currentLevelGestures.get(currentGestureIndex));
        } else {
            showFinishDialog();
        }
    }

    private void showFinishDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🎯 Train Level Model?")
                .setMessage("This will create a specialized model for " + currentLessonId + " using your collected data.\n\nThis level model will be used for user practice sessions.")
                .setPositiveButton("Create Level Model", (dialog, which) -> finishAdminTraining())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Admin Training?")
                .setMessage("Your collected level data will be lost. Are you sure?")
                .setPositiveButton("Exit", (dialog, which) -> finish())
                .setNegativeButton("Continue", null)
                .show();
    }

    private void finishAdminTraining() {
        stopAutomaticCapture();

        tvInstructions.setText("🧠 Admin: Creating specialized model for " + currentLessonId + "...");
        tvStatus.setText("🚀 Training level-specific model...");
        btnToggleCapture.setEnabled(false);
        btnNextGesture.setEnabled(false);
        btnFinishTraining.setEnabled(false);

        String url = Constants.getBaseUrl(this) + "/admin/training/complete-level";
        String jsonData = String.format("{\"session_id\":\"%s\",\"lesson_id\":\"%s\"}", trainingSessionId, currentLessonId);

        RequestBody body = RequestBody.create(jsonData, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (response.isSuccessful() && responseBody != null) {
                    try {
                        String bodyString = responseBody.string();
                        JSONObject jsonObject = new JSONObject(bodyString);
                        double accuracy = jsonObject.getDouble("accuracy");
                        runOnUiThread(() -> showAdminTrainingComplete(accuracy));
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing admin training completion response: " + e.getMessage());
                        runOnUiThread(() -> showAdminTrainingError("Failed to parse response"));
                    } finally {
                        responseBody.close();
                    }
                } else {
                    runOnUiThread(() -> showAdminTrainingError("Admin training failed on server"));
                }
                response.close();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> showAdminTrainingError("Connection failed: " + e.getMessage()));
            }
        });
    }

    private void showAdminTrainingComplete(double accuracy) {
        new AlertDialog.Builder(this)
                .setTitle("🎉 Level Model Created!")
                .setMessage(String.format("Admin Success!\n\nLevel: %s\nAccuracy: %.1f%%\n\nUsers can now practice with this specialized model for %s gestures.",
                        currentLessonId.toUpperCase(), accuracy * 100, currentLevelGestures.toString()))
                .setPositiveButton("Excellent!", (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showAdminTrainingError(String error) {
        new AlertDialog.Builder(this)
                .setTitle("Admin Training Error")
                .setMessage("Level model creation failed: " + error)
                .setPositiveButton("OK", (dialog, which) -> {
                    btnToggleCapture.setEnabled(cameraReady);
                    btnNextGesture.setEnabled(samplesCollected >= TARGET_SAMPLES);
                    btnFinishTraining.setEnabled(samplesCollected >= TARGET_SAMPLES);
                })
                .show();
    }

    private void updateProgress() {
        int effectiveSamples = Math.min(samplesCollected, TARGET_SAMPLES);
        int percentage = (int) ((effectiveSamples * 100.0) / TARGET_SAMPLES);
        progressBar.setProgress(percentage);

        String gestureProgress = "Gesture " + (currentGestureIndex + 1) + " of " + currentLevelGestures.size();
        tvProgress.setText(effectiveSamples + " / " + TARGET_SAMPLES + " samples (" + percentage + "%) • " + gestureProgress);

        if (samplesCollected >= TARGET_SAMPLES) {
            Log.d(TAG, "✅ Admin target samples reached: " + samplesCollected + "/" + TARGET_SAMPLES);
        }
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            connectionIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
            tvStatus.setText("🟢 Admin ready for level training");
        } else {
            connectionIndicator.setBackgroundColor(Color.parseColor("#F44336"));
            tvStatus.setText("🔴 Admin initializing...");
        }
    }

    private File createTempFile() {
        File outputDir = getCacheDir();
        return new File(outputDir, "admin_training_" + System.currentTimeMillis() + ".jpg");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutomaticCapture();
    }

    @Override
    public void onBackPressed() {
        if (isCollecting) {
            showExitDialog();
        } else {
            super.onBackPressed();
        }
    }
}