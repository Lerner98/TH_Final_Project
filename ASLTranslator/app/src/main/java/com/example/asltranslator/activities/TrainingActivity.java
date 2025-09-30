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
import com.example.asltranslator.utils.AchievementManager;
import com.example.asltranslator.utils.Constants;
import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class TrainingActivity extends AppCompatActivity {

    private static final String TAG = "TrainingActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // Training settings - same as original train_model.py
    private static final long CAPTURE_DELAY_MS = 50; // 50ms delay between captures
    private static final int TARGET_SAMPLES = 120;   // 120 samples per gesture

    // UI Components
    private PreviewView cameraPreview;
    private TextView tvGestureName, tvInstructions, tvProgress, tvStatus;
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
    private String currentGesture = "";
    private int currentGestureIndex = 0;
    private int samplesCollected = 0;
    private Handler captureHandler;
    private Runnable captureRunnable;
    private boolean cameraReady = false;

    // ASL gestures - using Constants or fallback
    private String[] gestures = {"Hello", "Thank You", "Yes", "No", "I Love You"};
    private String trainingSessionId;

    // HTTP client
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_training);

        // Use Constants if available, otherwise use fallback
        try {
            gestures = Constants.ASL_LABELS;
        } catch (Exception e) {
            Log.w(TAG, "Using fallback gesture list");
        }

        initViews();
        initHTTPClient();
        setupClickListeners();
        checkCameraPermission();
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.camera_preview);
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

        // Initial UI state
        btnToggleCapture.setEnabled(false); // Disabled until camera ready
        btnNextGesture.setEnabled(false);
        btnFinishTraining.setEnabled(false);

        updateConnectionStatus(false);

        // Set initial text
        tvStatus.setText("📷 Initializing camera...");
        tvInstructions.setText("Please wait while camera initializes...");
    }

    private void initHTTPClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        trainingSessionId = "android_session_" + System.currentTimeMillis();
        Log.d(TAG, "Training session: " + trainingSessionId);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            if (isCollecting) {
                showExitDialog();
            } else {
                finish();
            }
        });

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
                Toast.makeText(this, "Camera permission required for training", Toast.LENGTH_LONG).show();
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

                // Camera is ready, enable UI
                cameraReady = true;
                runOnUiThread(() -> {
                    startGestureCollection(gestures[0]);
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
        // Same camera settings as CameraTranslateActivity
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
        // 🔥 FIXED: Force stop any ongoing collection first
        if (isCollecting) {
            stopAutomaticCapture();
            isCollecting = false;
        }

        // Reset all state COMPLETELY
        currentGesture = gesture;
        samplesCollected = 0;  // 🔥 CRITICAL: Reset BEFORE setting isCollecting
        isCollecting = false; // Explicitly set to false

        // Update UI
        tvGestureName.setText(currentGesture);
        tvInstructions.setText("🎯 Ready to collect '" + gesture + "' - Position your hand and tap START!");
        updateProgress();

        // Set button to START state
        btnToggleCapture.setText("START COLLECTING");
        btnToggleCapture.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
        btnToggleCapture.setEnabled(cameraReady);

        // Disable other buttons until collection starts
        btnNextGesture.setEnabled(false);
        btnFinishTraining.setEnabled(false);

        // Update status
        tvStatus.setText("⏸️ Ready to collect '" + gesture + "'");
        tvStatus.setTextColor(Color.parseColor("#757575")); // Gray

        Log.d(TAG, "🔄 RESET: Ready to collect: " + currentGesture + " (Collection: " + isCollecting + ", Samples: " + samplesCollected + ")");
    }

    private void toggleCollection() {
        isCollecting = !isCollecting;

        if (isCollecting) {
            // Start collecting
            btnToggleCapture.setText("STOP COLLECTING");
            btnToggleCapture.setBackgroundColor(Color.parseColor("#F44336")); // Red
            tvInstructions.setText("🔴 COLLECTING '" + currentGesture + "' - Hold sign steady!");
            tvStatus.setText("🔴 RECORDING");
            tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green

            // Disable other buttons while collecting
            btnNextGesture.setEnabled(false);
            btnFinishTraining.setEnabled(false);

            startAutomaticCapture();
            Log.d(TAG, "🔴 Started collecting: " + currentGesture);

        } else {
            // Stop collecting
            stopAutomaticCapture();
            btnToggleCapture.setText("START COLLECTING");
            btnToggleCapture.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
            tvStatus.setText("⏸️ PAUSED - " + samplesCollected + "/" + TARGET_SAMPLES);
            tvStatus.setTextColor(Color.parseColor("#757575")); // Gray

            // 🔥 FIXED: Check samples AFTER stopping collection
            checkGestureCompletion();

            Log.d(TAG, "⏸️ Paused collecting: " + currentGesture + " (" + samplesCollected + " samples)");
        }
    }

    // 🔥 NEW METHOD: Separate method to check completion
    private void checkGestureCompletion() {
        if (samplesCollected >= TARGET_SAMPLES) {
            // Gesture is complete
            tvInstructions.setText("✅ Completed " + currentGesture + "! Move to next gesture or finish training.");
            btnNextGesture.setEnabled(true);
            btnFinishTraining.setEnabled(true);

            // Visual feedback for completion
            cardProgress.setCardBackgroundColor(Color.parseColor("#4CAF50"));
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                cardProgress.setCardBackgroundColor(Color.WHITE);
            }, 1000); // Keep green longer for completion

            Log.d(TAG, "✅ Gesture '" + currentGesture + "' completed with " + samplesCollected + " samples");
        } else {
            // Need more samples
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
                // Check BEFORE capturing to prevent overshoot
                if (isCollecting && samplesCollected < TARGET_SAMPLES) {
                    captureTrainingFrame();
                    // Schedule next capture
                    captureHandler.postDelayed(this, CAPTURE_DELAY_MS);
                } else if (isCollecting && samplesCollected >= TARGET_SAMPLES) {
                    // Auto-stop when target reached - exactly like original train_model.py
                    Log.d(TAG, "✅ Target reached, auto-stopping collection");
                    toggleCollection();
                }
            }
        };
        captureHandler.post(captureRunnable);
    }

    private void stopAutomaticCapture() {
        // 🔥 FIXED: More thorough cleanup
        if (captureHandler != null && captureRunnable != null) {
            captureHandler.removeCallbacks(captureRunnable);
            captureHandler.removeCallbacksAndMessages(null); // Remove ALL pending callbacks
            Log.d(TAG, "🛑 All capture callbacks removed");
        }

        // Clear references
        captureRunnable = null;
    }

    private void captureTrainingFrame() {
        // Double-check before capturing to prevent overshoot
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
                            // Final check before sending to prevent overshoot
                            if (samplesCollected < TARGET_SAMPLES) {
                                sendTrainingFrame(Uri.fromFile(outputFile));
                            }
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Error capturing frame", exception);
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error capturing frame", e);
        }
    }

    private void sendTrainingFrame(Uri imageUri) {
        try {
            // Read and encode image - same as CameraTranslateActivity
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            inputStream.close();

            String base64Image = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);

            // Send to server
            String url = Constants.getBaseUrl(this) + "/training/add-sample";
            String jsonData = String.format(
                    "{\"session_id\":\"%s\",\"gesture_name\":\"%s\",\"frame\":\"data:image/jpeg;base64,%s\",\"timestamp\":%d}",
                    trainingSessionId, currentGesture, base64Image, System.currentTimeMillis()
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
                        String bodyString = null;
                        try {
                            bodyString = responseBody.string();
                            JSONObject jsonObject = new JSONObject(bodyString);
                            int samples = jsonObject.getInt("samples");
                            if (samples > 0 && isCollecting && samplesCollected < TARGET_SAMPLES) {
                                runOnUiThread(() -> {
                                    samplesCollected++;
                                    updateProgress();

                                    Log.d(TAG, "📊 HTTP Response: " + currentGesture + " sample #" + samplesCollected + " (isCollecting: " + isCollecting + ")");

                                    cardProgress.setCardBackgroundColor(Color.parseColor("#4CAF50"));
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        cardProgress.setCardBackgroundColor(Color.WHITE);
                                    }, 100);

                                    if (samplesCollected >= TARGET_SAMPLES) {
                                        Log.d(TAG, "🎯 Reached target samples for " + currentGesture + ", auto-stopping");
                                        toggleCollection();
                                    }
                                });
                            } else {
                                Log.d(TAG, "⚠️ Ignored response for " + currentGesture + " sample #" + samples + " - not collecting or target reached");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing JSON response: " + e.getMessage());
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading response body: " + e.getMessage());
                        } finally {
                            if (responseBody != null) {
                                responseBody.close();
                            }
                        }
                    } else {
                        runOnUiThread(() -> {
                            String errorMessage = responseBody != null ? "Server error" : "No response body";
                            try {
                                if (responseBody != null) {
                                    errorMessage = responseBody.string();
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "Error reading error body: " + e.getMessage());
                            }
                            Log.e(TAG, "Server error: " + response.code() + " - " + errorMessage);
                            Toast.makeText(TrainingActivity.this, "Server error: " + errorMessage, Toast.LENGTH_SHORT).show();
                        });
                    }
                    response.close();
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to send training frame", e);
                    runOnUiThread(() -> {
                        Toast.makeText(TrainingActivity.this, "Connection failed. Check server.", Toast.LENGTH_SHORT).show();
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing training frame", e);
        } finally {
            // Clean up temp file
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

    private void nextGesture() {
        // 🔥 FIXED: Ensure we're completely stopped before moving to next gesture
        if (isCollecting) {
            stopAutomaticCapture();
            isCollecting = false;
        }

        // Only proceed if the current gesture is complete or user chooses to move on
        if (samplesCollected >= TARGET_SAMPLES || currentGestureIndex + 1 < gestures.length) {
            currentGestureIndex++;
            if (currentGestureIndex < gestures.length) {
                startGestureCollection(gestures[currentGestureIndex]);
            } else {
                showFinishDialog();
            }
        } else {
            // Prevent moving to next gesture if not enough samples
            Toast.makeText(this, "Collect " + TARGET_SAMPLES + " samples before proceeding!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFinishDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Start Training?")
                .setMessage("This will train the AI model with your Android camera data. This may take several minutes.")
                .setPositiveButton("Train Model", (dialog, which) -> finishTraining())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Training?")
                .setMessage("Your collected data will be lost. Are you sure?")
                .setPositiveButton("Exit", (dialog, which) -> finish())
                .setNegativeButton("Continue", null)
                .show();
    }

    private void finishTraining() {
        stopAutomaticCapture();

        // 🚨 DEFENSIVE ALERT - Show warning before starting training
        new AlertDialog.Builder(this)
                .setTitle("⏳ Training Started")
                .setMessage("Please wait while the server processes and builds your model.\n\nDo NOT close the app! You will be prompted when it finishes.")
                .setPositiveButton("OK, I'll Wait", (dialog, which) -> {
                    // Continue with original training logic after user acknowledges

                    tvInstructions.setText("🧠 Training AI model with your Android camera data...");
                    tvStatus.setText("🚀 Running training script...");
                    btnToggleCapture.setEnabled(false);
                    btnNextGesture.setEnabled(false);
                    btnFinishTraining.setEnabled(false);

                    String url = Constants.getBaseUrl(this) + "/training/complete";
                    String jsonData = String.format("{\"session_id\":\"%s\"}", trainingSessionId);

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
                                    runOnUiThread(() -> showTrainingComplete(accuracy));
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error parsing training completion response: " + e.getMessage());
                                } catch (IOException e) {
                                    Log.e(TAG, "Error reading training completion response: " + e.getMessage());
                                } finally {
                                    if (responseBody != null) {
                                        responseBody.close();
                                    }
                                }
                            } else {
                                runOnUiThread(() -> {
                                    String errorMessage = responseBody != null ? "Server error" : "No response body";
                                    try {
                                        if (responseBody != null) {
                                            errorMessage = responseBody.string();
                                        }
                                    } catch (IOException e) {
                                        Log.e(TAG, "Error reading error body: " + e.getMessage());
                                    }
                                    showTrainingError("Training failed: " + errorMessage);
                                });
                            }
                            response.close();
                        }

                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            runOnUiThread(() -> showTrainingError("Connection failed: " + e.getMessage()));
                        }
                    });
                })
                .setCancelable(false)
                .show();
    }

    private void showTrainingComplete(double accuracy) {
        // 🆕 USE CENTRALIZED ACHIEVEMENT SYSTEM
        AchievementManager.getInstance().unlockTrainerAchievement(this);

        new AlertDialog.Builder(this)
                .setTitle("Training Complete! 🎉")
                .setMessage(String.format("Your AI model has been trained using your Android camera data!\n\nAccuracy: %.1f%%\n\n🏆 Achievement Unlocked: AI Trainer!\n\nThe model is now ready for translation.", accuracy * 100))
                .setPositiveButton("Awesome!", (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showTrainingError(String error) {
        new AlertDialog.Builder(this)
                .setTitle("Training Error")
                .setMessage("Training failed: " + error)
                .setPositiveButton("OK", (dialog, which) -> {
                    btnToggleCapture.setEnabled(cameraReady);
                    btnNextGesture.setEnabled(samplesCollected >= TARGET_SAMPLES);
                    btnFinishTraining.setEnabled(samplesCollected >= TARGET_SAMPLES);
                })
                .show();
    }

    private void updateProgress() {
        // Cap at 100% and target samples - exactly like original train_model.py
        int effectiveSamples = Math.min(samplesCollected, TARGET_SAMPLES);
        int percentage = (int) ((effectiveSamples * 100.0) / TARGET_SAMPLES);
        progressBar.setProgress(percentage);

        String gestureProgress = "Gesture " + (currentGestureIndex + 1) + " of " + gestures.length;
        tvProgress.setText(effectiveSamples + " / " + TARGET_SAMPLES + " samples (" + percentage + "%) • " + gestureProgress);

        // Log when target reached
        if (samplesCollected >= TARGET_SAMPLES) {
            Log.d(TAG, "✅ Target samples reached: " + samplesCollected + "/" + TARGET_SAMPLES);
        }
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            connectionIndicator.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
            tvStatus.setText("🟢 Ready for training");
        } else {
            connectionIndicator.setBackgroundColor(Color.parseColor("#F44336")); // Red
            tvStatus.setText("🔴 Initializing...");
        }
    }

    private File createTempFile() {
        File outputDir = getCacheDir();
        return new File(outputDir, "training_" + System.currentTimeMillis() + ".jpg");
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