package com.example.asltranslator.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.asltranslator.R;
import com.example.asltranslator.network.WebSocketManager;
import com.example.asltranslator.utils.Constants;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Displays a splash screen with ASL hand animation and preloads WebSocket connection.
 * Ensures seamless server connectivity for ASL features upon app entry.
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long SPLASH_DURATION = 3000; // 3 seconds

    // UI Components
    private ImageView ivAslHand;
    private TextView tvAppName;
    private TextView tvTagline;
    private CircularProgressIndicator progressIndicator;

    // Animation duration constants
    private static final long HAND_ANIMATION_DURATION = 1000;
    private static final long TEXT_FADE_DURATION = 800;
    private static final long PROGRESS_DELAY = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initViews();
        initializeWebSocket();
        startAnimations();
        navigateAfterDelay();
    }

    private void initViews() {
        ivAslHand = findViewById(R.id.iv_asl_hand);
        tvAppName = findViewById(R.id.tv_app_name);
        tvTagline = findViewById(R.id.tv_tagline);
        progressIndicator = findViewById(R.id.progress_circular);

        // Set initial states for animations
        ivAslHand.setAlpha(0f);
        ivAslHand.setScaleX(0.3f);
        ivAslHand.setScaleY(0.3f);

        tvAppName.setAlpha(0f);
        tvTagline.setAlpha(0f);
        progressIndicator.setAlpha(0f);
    }

    /**
     * Initializes WebSocket connection for ASL server communication.
     * Preloads connectivity to ensure seamless feature access in MainActivity.
     */
    private void initializeWebSocket() {
        WebSocketManager.getInstance().connect(
                Constants.getWebSocketUrl(this),
                new WebSocketManager.WebSocketCallback() {
                    @Override
                    public void onConnected() {
                        // Silent logging during splash
                        android.util.Log.d(TAG, "✅ WebSocket connected");
                    }

                    @Override
                    public void onDisconnected() {
                        android.util.Log.d(TAG, "🔌 WebSocket disconnected");
                    }

                    @Override
                    public void onGestureDetected(com.example.asltranslator.models.GestureResponse response) {
                        // Not used in splash
                    }

                    @Override
                    public void onError(String error) {
                        android.util.Log.e(TAG, "❌ WebSocket connection error: " + error);
                    }
                });
    }

    private void startAnimations() {
        // 🎨 Hand Animation - Scale and fade in with bounce
        AnimatorSet handAnimSet = new AnimatorSet();

        ObjectAnimator handFadeIn = ObjectAnimator.ofFloat(ivAslHand, "alpha", 0f, 1f);
        handFadeIn.setDuration(HAND_ANIMATION_DURATION);

        ObjectAnimator handScaleX = ObjectAnimator.ofFloat(ivAslHand, "scaleX", 0.3f, 1.1f, 1f);
        handScaleX.setDuration(HAND_ANIMATION_DURATION);
        handScaleX.setInterpolator(new OvershootInterpolator(1.2f));

        ObjectAnimator handScaleY = ObjectAnimator.ofFloat(ivAslHand, "scaleY", 0.3f, 1.1f, 1f);
        handScaleY.setDuration(HAND_ANIMATION_DURATION);
        handScaleY.setInterpolator(new OvershootInterpolator(1.2f));

        handAnimSet.playTogether(handFadeIn, handScaleX, handScaleY);
        handAnimSet.start();

        // 📝 App Name Animation - Fade in after hand
        ObjectAnimator appNameFade = ObjectAnimator.ofFloat(tvAppName, "alpha", 0f, 1f);
        appNameFade.setDuration(TEXT_FADE_DURATION);
        appNameFade.setStartDelay(400);
        appNameFade.setInterpolator(new AccelerateDecelerateInterpolator());
        appNameFade.start();

        // 🏷 Tagline Animation - Fade in after app name
        ObjectAnimator taglineFade = ObjectAnimator.ofFloat(tvTagline, "alpha", 0f, 1f);
        taglineFade.setDuration(TEXT_FADE_DURATION);
        taglineFade.setStartDelay(700);
        taglineFade.setInterpolator(new AccelerateDecelerateInterpolator());
        taglineFade.start();

        // ⏳ Progress Indicator - Show after other animations
        ObjectAnimator progressFade = ObjectAnimator.ofFloat(progressIndicator, "alpha", 0f, 1f);
        progressFade.setDuration(TEXT_FADE_DURATION);
        progressFade.setStartDelay(PROGRESS_DELAY);
        progressFade.start();

        // 🎭 Subtle continuous pulse for hand (FIXED)
        startHandPulseAnimation();
    }

    private void startHandPulseAnimation() {
        // FIXED: Use individual ObjectAnimators with INFINITE repeat instead of AnimatorSet
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(ivAslHand, "scaleX", 1f, 1.05f, 1f);
        pulseX.setDuration(2000);
        pulseX.setRepeatCount(ObjectAnimator.INFINITE); // ✅ This works for ObjectAnimator
        pulseX.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseX.setStartDelay(HAND_ANIMATION_DURATION + 500);

        ObjectAnimator pulseY = ObjectAnimator.ofFloat(ivAslHand, "scaleY", 1f, 1.05f, 1f);
        pulseY.setDuration(2000);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE); // ✅ This works for ObjectAnimator
        pulseY.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseY.setStartDelay(HAND_ANIMATION_DURATION + 500);

        // Start both animations separately (no AnimatorSet needed for infinite repeat)
        pulseX.start();
        pulseY.start();
    }

    private void navigateAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            navigateToNextScreen();
        }, SPLASH_DURATION);
    }

    private void navigateToNextScreen() {
        try {
            // Check authentication state (offline-safe)
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            Intent intent;

            if (currentUser != null) {
                // User is signed in, go to main screen
                intent = new Intent(this, MainActivity.class);
            } else {
                // User needs to authenticate
                intent = new Intent(this, AuthActivity.class);
            }

            // Smooth transition
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();

        } catch (Exception e) {
            // Fallback: always go to auth if there's any issue
            Intent intent = new Intent(this, AuthActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        // Disable back button during splash
        // Do nothing - let splash complete naturally
        super.onBackPressed();
    }
}