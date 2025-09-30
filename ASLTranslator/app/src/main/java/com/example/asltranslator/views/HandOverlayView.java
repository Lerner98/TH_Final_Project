package com.example.asltranslator.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.List;

/**
 * Custom view for drawing hand landmarks overlay on camera preview.
 * Optimized to prevent memory leaks from excessive logging.
 */
public class HandOverlayView extends View {
    private static final String TAG = "HandOverlayView";

    // Performance monitoring
    private long lastLogTime = 0;
    private int frameCount = 0;
    private static final long LOG_INTERVAL_MS = 25000; // Log every 25 seconds as requested

    private Paint landmarkPaint;
    private Paint connectionPaint;
    private Pose currentPose;

    // Hand landmark connections (unchanged)
    private static final int[][] HAND_CONNECTIONS = {
            {PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_THUMB},
            {PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_INDEX},
            {PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_PINKY},
            {PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_THUMB},
            {PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_INDEX},
            {PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_PINKY}
    };

    public HandOverlayView(Context context) {
        super(context);
        init();
    }

    public HandOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HandOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Landmark paint (unchanged)
        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.CYAN);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(10f);

        // Connection paint (unchanged)
        connectionPaint = new Paint();
        connectionPaint.setColor(Color.YELLOW);
        connectionPaint.setStyle(Paint.Style.STROKE);
        connectionPaint.setStrokeWidth(5f);

        Log.d(TAG, "HandOverlayView initialized");
    }

    public void setPose(Pose pose) {
        this.currentPose = pose;
        invalidate(); // Triggers onDraw()
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        frameCount++;

        try {
            // 🔥 FIXED: No more per-frame logging!
            // Only log periodically for performance monitoring
            logPeriodicPerformance();

            if (currentPose == null) {
                return; // 🔥 FIXED: No more "currentPose is null" spam
            }

            // Draw hand connections (same logic, no logging)
            drawHandConnections(canvas);

            // Draw hand landmarks (same logic, no logging)
            drawHandLandmarks(canvas);

        } catch (Exception e) {
            // 🔥 KEEP: Error logging is important for debugging real issues
            Log.e(TAG, "Error in onDraw: " + e.getMessage(), e);
        }
    }

    /**
     * 🆕 NEW: Separate method for drawing connections (cleaner code)
     */
    private void drawHandConnections(Canvas canvas) {
        for (int[] connection : HAND_CONNECTIONS) {
            PoseLandmark start = currentPose.getPoseLandmark(connection[0]);
            PoseLandmark end = currentPose.getPoseLandmark(connection[1]);

            if (isValidLandmark(start) && isValidLandmark(end)) {
                canvas.drawLine(
                        start.getPosition().x,
                        start.getPosition().y,
                        end.getPosition().x,
                        end.getPosition().y,
                        connectionPaint
                );
            }
            // 🔥 FIXED: No more "Skipping line" logging spam
        }
    }

    /**
     * 🆕 NEW: Separate method for drawing landmarks (cleaner code)
     */
    private void drawHandLandmarks(Canvas canvas) {
        List<PoseLandmark> landmarks = currentPose.getAllPoseLandmarks();

        if (landmarks != null) {
            for (PoseLandmark landmark : landmarks) {
                if (isHandLandmark(landmark.getLandmarkType()) && isValidLandmark(landmark)) {
                    canvas.drawCircle(
                            landmark.getPosition().x,
                            landmark.getPosition().y,
                            8f,
                            landmarkPaint
                    );
                }
                // 🔥 FIXED: No more "Drawing landmark" or "Skipping landmark" spam
            }
        }
    }

    /**
     * 🆕 NEW: Helper method to validate landmarks (cleaner code)
     */
    private boolean isValidLandmark(PoseLandmark landmark) {
        return landmark != null &&
                landmark.getPosition() != null &&
                !Float.isNaN(landmark.getPosition().x) &&
                !Float.isNaN(landmark.getPosition().y);
    }

    /**
     * 🆕 NEW: Smart periodic logging as requested (every 25 seconds)
     */
    private void logPeriodicPerformance() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastLogTime >= LOG_INTERVAL_MS) {
            Log.d(TAG, String.format("Performance: %d frames in %d seconds (%.1f FPS)",
                    frameCount,
                    LOG_INTERVAL_MS / 1000,
                    (frameCount * 1000.0f) / LOG_INTERVAL_MS));

            lastLogTime = currentTime;
            frameCount = 0; // Reset counter
        }
    }

    /**
     * Hand landmark detection (unchanged functionality)
     */
    private boolean isHandLandmark(int landmarkType) {
        return landmarkType == PoseLandmark.LEFT_WRIST ||
                landmarkType == PoseLandmark.LEFT_THUMB ||
                landmarkType == PoseLandmark.LEFT_INDEX ||
                landmarkType == PoseLandmark.LEFT_PINKY ||
                landmarkType == PoseLandmark.RIGHT_WRIST ||
                landmarkType == PoseLandmark.RIGHT_THUMB ||
                landmarkType == PoseLandmark.RIGHT_INDEX ||
                landmarkType == PoseLandmark.RIGHT_PINKY;
    }
}