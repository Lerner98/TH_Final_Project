package com.example.asltranslator.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import java.io.InputStream;
import java.io.IOException;
import com.example.asltranslator.R;
import com.example.asltranslator.models.GestureResponse;
import com.example.asltranslator.models.TranslationHistory;
import com.example.asltranslator.network.WebSocketManager;
import com.example.asltranslator.utils.Constants;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.ExecutionException;

public class CameraTranslateActivity extends AppCompatActivity
        implements WebSocketManager.WebSocketCallback {

    private static final String TAG = "CameraTranslate";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // Match React Native exactly: 3-second snapshots
    private static final long SNAPSHOT_INTERVAL_MS = 3000;

    // Updated views - removed btnBack, added btnSwitchCamera
    private PreviewView cameraPreview;
    private MaterialCardView cardResult;
    private TextView tvGesture, tvConfidence, tvConnectionStatus;
    private CircularProgressIndicator confidenceIndicator;
    private View connectionIndicator;
    private ImageButton btnTranslate, btnSwitchCamera, btnHistory;

    // Camera - updated with proper selector
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA; // Start with front camera

    // Networking - only WebSocket
    private WebSocketManager webSocketManager;

    // Firebase - minimal for history only
    private DatabaseReference mDatabase;
    private String userId;

    // Translation state - simple boolean
    private boolean isTranslationActive = false;
    private Handler snapshotHandler;
    private Runnable snapshotRunnable;

    // Add these React Native variables:
    private String lastGestureRef = null;
    private java.util.Map<String, Integer> gestureCountRef = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_translate);

        initViews();
        initServices();
        checkCameraPermission();
        setupClickListeners();
        updateTranslationUI();
    }

    private void initViews() {
        // Updated view initialization - removed btnBack, added btnSwitchCamera
        cameraPreview = findViewById(R.id.camera_preview);
        cardResult = findViewById(R.id.card_result);
        tvGesture = findViewById(R.id.tv_gesture);
        tvConfidence = findViewById(R.id.tv_confidence);
        confidenceIndicator = findViewById(R.id.confidence_indicator);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        connectionIndicator = findViewById(R.id.connection_indicator);
        btnTranslate = findViewById(R.id.btn_capture);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);

        // Hide any history elements that might be visible by default
        View historyContainer = findViewById(R.id.history_container);
        if (historyContainer != null) {
            historyContainer.setVisibility(View.GONE);
        }

        // Hide result card initially
        cardResult.setVisibility(View.GONE);
    }

    private void initServices() {
        // Minimal service initialization
        webSocketManager = WebSocketManager.getInstance();

        // Firebase - only for history
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Connect to WebSocket immediately
        connectWebSocket();
    }

    private void setupClickListeners() {
        btnTranslate.setOnClickListener(v -> toggleTranslation());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());

        // Add history button functionality if needed
//        btnHistory.setOnClickListener(v -> {
//            // Toggle history bottom sheet or implement history functionality
//            Toast.makeText(this, "History feature", Toast.LENGTH_SHORT).show();
//        });
    }

    // Add the camera switching functionality
    private void switchCamera() {
        if (cameraProvider != null) {
            // Switch between front and back camera
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                Toast.makeText(this, "Switched to front camera", Toast.LENGTH_SHORT).show();
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                Toast.makeText(this, "Switched to back camera", Toast.LENGTH_SHORT).show();
            }

            // Restart camera with new selector
            bindCameraUseCases();
        }
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
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
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
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        // FORCE much smaller preview and capture
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(240, 320))  // Even smaller!
                .build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        // VERY small capture resolution - like a webcam
        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(240, 320))  // Force tiny resolution
                .setJpegQuality(40)                       // Lower quality for smaller size
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build();

        try {
            cameraProvider.unbindAll();
            // Use the current cameraSelector instead of hardcoded front camera
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            camera.getCameraControl().enableTorch(false);
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
        }
    }

    private void toggleTranslation() {
        isTranslationActive = !isTranslationActive;
        updateTranslationUI();

        Log.d(TAG, isTranslationActive ? "🚀 Starting ASL streaming..." : "🛑 Stopping ASL streaming...");

        if (isTranslationActive) {
            // Reset like React Native
            gestureCountRef.clear();
            lastGestureRef = null;
            startSnapshotLoop();
        } else {
            stopSnapshotLoop();
            cardResult.setVisibility(View.GONE);
        }
    }

    private void startSnapshotLoop() {
        snapshotHandler = new Handler(Looper.getMainLooper());
        snapshotRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTranslationActive) {
                    captureSnapshot();
                    snapshotHandler.postDelayed(this, SNAPSHOT_INTERVAL_MS);
                }
            }
        };
        // Start immediately
        snapshotHandler.post(snapshotRunnable);
    }

    private void stopSnapshotLoop() {
        if (snapshotHandler != null && snapshotRunnable != null) {
            snapshotHandler.removeCallbacks(snapshotRunnable);
        }
    }

    // Exact React Native snapshot approach
    private void captureSnapshot() {
        if (!isTranslationActive || imageCapture == null) return;

        try {
            File outputFile = createTempFile();
            ImageCapture.OutputFileOptions outputFileOptions =
                    new ImageCapture.OutputFileOptions.Builder(outputFile).build();

            Log.d(TAG, "📸 Taking snapshot...");

            imageCapture.takePicture(
                    outputFileOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                            Log.d(TAG, "📸 Snapshot taken: " + outputFile.getPath());
                            processSnapshotFile(Uri.fromFile(outputFile));
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Error capturing snapshot", exception);
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error capturing snapshot", e);
        }
    }

    // Update your processSnapshotFile to EXACTLY match React Native
    private void processSnapshotFile(Uri imageUri) {
        try {
            // 🧠 Step 1: Read image as raw bytes (just like React Native's FileSystem.readAsBase64)
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            inputStream.close();

            // 🧠 Step 2: Encode to Base64 with NO_WRAP (identical to React Native)
            String base64Image = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);

            Log.d(TAG, "📦 Base64 length: " + base64Image.length());
            Log.d(TAG, "📤 Sending to server...");

            // 🧠 Step 3: Send through WebSocket ONCE
            webSocketManager.sendFrame(base64Image);
            Log.d(TAG, "✅ Frame sent");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing snapshot", e);
        } finally {
            // 🧹 Clean up temp file
            try {
                String filePath = imageUri.getPath();
                if (filePath != null) {
                    File tempFile = new File(filePath);
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠ Could not delete temp file", e);
            }
        }
    }

    private File createTempFile() {
        File outputDir = getCacheDir();
        return new File(outputDir, "snapshot_" + System.currentTimeMillis() + ".jpg");
    }

    private void updateTranslationUI() {
        if (isTranslationActive) {
            try {
                btnTranslate.setImageResource(R.drawable.ic_stop);
            } catch (Exception e) {
                btnTranslate.setImageResource(R.drawable.ic_camera);
            }
            tvConnectionStatus.setText("🎥 Processing");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.warning));
        } else {
            try {
                btnTranslate.setImageResource(R.drawable.ic_play_arrow);
            } catch (Exception e) {
                btnTranslate.setImageResource(R.drawable.ic_camera);
            }
            tvConnectionStatus.setText("⏸️ TAP TO START");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.gray));
        }
    }

    private void connectWebSocket() {
        webSocketManager.connect(Constants.getWebSocketUrl(this), this);
    }

    // WebSocket callbacks - minimal implementation
    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Log.d(TAG, "✅ WebSocket connected");
            if (!isTranslationActive) {
                tvConnectionStatus.setText("🟢 Connected");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
            }
            connectionIndicator.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.success));
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Log.d(TAG, "🔴 WebSocket disconnected");
            tvConnectionStatus.setText("🔴 Disconnected");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
            connectionIndicator.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.error));
        });
    }

    @Override
    public void onGestureDetected(GestureResponse response) {
        if (!isTranslationActive) {
            Log.d(TAG, "🚨 Translation not active, ignoring result");
            return;
        }

        runOnUiThread(() -> {
            Log.d(TAG, "📥 Received from server: " + response.getGesture() +
                    ", confidence: " + response.getConfidence());

            if (response.isHandDetected() &&
                    response.getGesture() != null &&
                    !response.getGesture().equals("None") &&
                    !response.getGesture().equals("Unknown")) {

                String newGesture = response.getGesture();
                float newConfidence = response.getConfidence();

                // EXACT React Native logic
                if (newConfidence > 0.4f) {
                    int currentCount = gestureCountRef.getOrDefault(newGesture, 0) + 1;
                    gestureCountRef.put(newGesture, currentCount);

                    // Only show if count >= 2 AND different from last gesture
                    if (currentCount >= 2 && !newGesture.equals(lastGestureRef)) {
                        Log.d(TAG, "✅ Showing gesture: " + newGesture);

                        // Show result
                        cardResult.setVisibility(View.VISIBLE);
                        tvGesture.setText(newGesture);

                        int confidencePercent = Math.round(newConfidence * 100);
                        tvConfidence.setText("Confidence: " + confidencePercent + "%");
                        confidenceIndicator.setProgress(confidencePercent);

                        // Color coding
                        int color;
                        if (confidencePercent >= 80) {
                            color = ContextCompat.getColor(this, R.color.success);
                        } else if (confidencePercent >= 60) {
                            color = ContextCompat.getColor(this, R.color.warning);
                        } else {
                            color = ContextCompat.getColor(this, R.color.error);
                        }
                        confidenceIndicator.setIndicatorColor(color);
                        tvConfidence.setTextColor(color);

                        // Update last gesture
                        lastGestureRef = newGesture;

                        // Reset other gesture counters
                        for (String key : gestureCountRef.keySet()) {
                            if (!key.equals(newGesture)) {
                                gestureCountRef.put(key, 0);
                            }
                        }

                        // 🚫 REMOVED: saveToHistory(response); - No longer saves to history/stats
                        Log.d(TAG, "🚫 Stats disabled for regular camera translation");

                        // Auto-hide
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (cardResult.getVisibility() == View.VISIBLE) {
                                cardResult.setVisibility(View.GONE);
                            }
                        }, 2500);
                    } else {
                        Log.d(TAG, "⏳ Gesture " + newGesture + " count: " + currentCount);
                    }
                }
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Log.e(TAG, "WebSocket error: " + error);
            Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
        });
    }

    // 🚫 OPTIONAL: Keep the method but add a flag to disable it
    private void saveToHistory(GestureResponse response) {
        // 🚫 STATS DISABLED: Regular camera translation should not affect user stats
        Log.d(TAG, "🚫 History saving disabled for regular camera translation");

        // Uncomment below if you want to re-enable history saving later:
        /*
        try {
            String historyId = mDatabase.child("history").child(userId).push().getKey();
            TranslationHistory history = new TranslationHistory(
                    historyId,
                    response.getGesture(),
                    response.getConfidence(),
                    System.currentTimeMillis()
            );
            mDatabase.child("history").child(userId).child(historyId).setValue(history);
        } catch (Exception e) {
            Log.e(TAG, "Error saving history", e);
        }
        */
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSnapshotLoop();
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
    }
}