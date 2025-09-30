package com.example.asltranslator.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.asltranslator.R;
import com.example.asltranslator.activities.PracticeActivity;
import com.example.asltranslator.models.LessonStep;
import com.google.android.material.button.MaterialButton;

/**
 * Displays ASL lesson content (title, instructions, tips, demo images) and initiates practice sessions.
 * Supports interactive learning by showing gesture visuals and guiding users to practice specific signs.
 */
public class LessonContentFragment extends Fragment {

    private static final String TAG = "LessonContentFragment";
    private static final String ARG_LESSON_STEP = "lesson_step";

    private TextView tvStepTitle, tvInstructions, tvTips;
    private ImageView ivGestureDemo, ivPlayButton;
    private MaterialButton btnPracticeNow;

    private LessonStep lessonStep;

    /**
     * Creates a new instance with a specific lesson step.
     *
     * @param step The lesson step to display.
     * @return A new LessonContentFragment instance.
     */
    public static LessonContentFragment newInstance(LessonStep step) {
        LessonContentFragment fragment = new LessonContentFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_LESSON_STEP, step);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            lessonStep = getArguments().getParcelable(ARG_LESSON_STEP);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lesson_content, container, false);
    }

    /**
     * Initializes views, populates lesson content, and sets up click listeners.
     * Drives the core display of ASL gesture instructions for effective learning.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        populateContent();
        setupClickListeners();
    }

    private void initViews(View view) {
        tvStepTitle = view.findViewById(R.id.tv_step_title);
        tvInstructions = view.findViewById(R.id.tv_instructions);
        tvTips = view.findViewById(R.id.tv_tips);
        ivGestureDemo = view.findViewById(R.id.iv_gesture_demo);
        ivPlayButton = view.findViewById(R.id.iv_play_button);
        btnPracticeNow = view.findViewById(R.id.btn_practice_now);
    }

    /**
     * Populates UI with lesson step details (title, instructions, tips, demo image).
     * Enhances ASL learning by providing clear, visual, and textual guidance.
     */
    private void populateContent() {
        if (lessonStep == null) return;

        tvStepTitle.setText("How to sign '" + lessonStep.getTitle() + "'");
        tvInstructions.setText(formatInstructions(lessonStep.getInstructions()));
        tvTips.setText(lessonStep.getTips());

        loadDemoImage(lessonStep.getTitle());
        setupMultipleImages();

        // 🆕 Add instruction text for gestures with multiple images
        String gestureName = lessonStep.getTitle().toLowerCase();
        if ("hello".equals(gestureName) || "sleep".equals(gestureName) || "book".equals(gestureName) ||
                "where".equals(gestureName) || "who".equals(gestureName)) {
            tvTips.setText(lessonStep.getTips() + "\n\n💡 Tip: Tap the image to see different variations of this sign!");
        }

        if (lessonStep.getVideoUrl() != null && !lessonStep.getVideoUrl().isEmpty()) {
            ivPlayButton.setVisibility(View.VISIBLE);
        }
    }

    private String formatInstructions(String instructions) {
        String[] steps = instructions.split(",");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < steps.length; i++) {
            formatted.append((i + 1)).append(". ").append(steps[i].trim());
            if (i < steps.length - 1) {
                formatted.append("\n");
            }
        }
        return formatted.toString();
    }

    private void loadDemoImage(String gestureName) {
        int imageRes;
        switch (gestureName.toLowerCase()) {
            // Lesson 1 - Basic Greetings
            case "hello":
                imageRes = R.drawable.asl_hello;
                break;
            case "thank you":
                imageRes = R.drawable.asl_thankyou;
                break;
            case "yes":
                imageRes = R.drawable.asl_yes;
                break;
            case "no":
                imageRes = R.drawable.asl_no;
                break;

            // Lesson 2 - Emotions
            case "happy":
                imageRes = R.drawable.asl_happy;
                break;
            case "sad":
                imageRes = R.drawable.asl_sad;
                break;
            case "angry":
                imageRes = R.drawable.asl_angry;
                break;
            case "love":
                imageRes = R.drawable.asl_love;
                break;

            // Lesson 3 - Actions
            case "eat":
                imageRes = R.drawable.asl_eat;
                break;
            case "drink":
                imageRes = R.drawable.asl_drink;
                break;
            case "sleep":
                imageRes = R.drawable.asl_sleep;
                break;
            case "go":
                imageRes = R.drawable.asl_go;
                break;

            // Lesson 4 - Objects
            case "book":
                imageRes = R.drawable.asl_book;
                break;
            case "phone":
                imageRes = R.drawable.asl_phone;
                break;
            case "car":
                imageRes = R.drawable.asl_car;
                break;
            case "home":
                imageRes = R.drawable.asl_home;
                break;

            // Lesson 5 - Question Words
            case "what":
                imageRes = R.drawable.asl_what;
                break;
            case "where":
                imageRes = R.drawable.asl_where;  // First Where image
                break;
            case "when":
                imageRes = R.drawable.asl_when;
                break;
            case "who":
                imageRes = R.drawable.asl_who;  // First Who image
                break;

            // Default fallback
            default:
                imageRes = R.drawable.demo_default;
                break;
        }
        ivGestureDemo.setImageResource(imageRes);
    }


    /**
     * Sets up clickable images for gestures with multiple variations.
     * Enhances learning by showing diverse ASL sign representations.
     */
    private void setupMultipleImages() {
        String gestureName = lessonStep.getTitle().toLowerCase();

        if ("hello".equals(gestureName)) {
            setupClickableImages("hello", R.drawable.asl_hello, R.drawable.asl_hello2);
        } else if ("sleep".equals(gestureName)) {
            setupClickableImages("sleep", R.drawable.asl_sleep, R.drawable.asl_sleep2);
        } else if ("book".equals(gestureName)) {
            setupClickableImages("book", R.drawable.asl_book, R.drawable.asl_book2);
        } else if ("where".equals(gestureName)) {  // 🆕 Added Where support
            setupClickableImages("where", R.drawable.asl_where, R.drawable.asl_where2);
        } else if ("who".equals(gestureName)) {  // 🆕 Added Who support
            setupClickableImages("who", R.drawable.asl_who, R.drawable.asl_who2);
        }
    }

    // 🆕 NEW: Generic method to handle multiple images for any gesture
    private void setupClickableImages(String gestureName, int image1, int image2) {
        ivGestureDemo.setOnClickListener(v -> {
            Object currentTag = ivGestureDemo.getTag();
            if (currentTag != null && currentTag.equals(gestureName + "2")) {
                // Switch back to first image
                ivGestureDemo.setImageResource(image1);
                ivGestureDemo.setTag(gestureName + "1");
            } else {
                // Switch to second image
                ivGestureDemo.setImageResource(image2);
                ivGestureDemo.setTag(gestureName + "2");
            }
        });

        // Set initial tag
        ivGestureDemo.setTag(gestureName + "1");

        // Add visual indicator that image is clickable
        ivGestureDemo.setAlpha(0.9f);
    }

    private void setupClickListeners() {
        // 🔥 FIXED: Simple click listener - no ActivityResultLauncher
        btnPracticeNow.setOnClickListener(v -> {
            Log.d(TAG, "🎯 Practice button clicked for gesture: " + lessonStep.getTitle());
            openPracticeMode();
        });

        ivPlayButton.setOnClickListener(v -> {
            // TODO: Implement video playback
            Log.d(TAG, "🎬 Play button clicked for video");
        });
    }

    /**
     * Opens PracticeActivity for the current gesture.
     * Enables hands-on ASL practice to reinforce learning.
     */
    private void openPracticeMode() {
        if (getActivity() != null) {
            String lessonId = getActivity().getIntent().getStringExtra("lesson_id");

            if (lessonId != null && lessonStep != null) {
                Log.d(TAG, "🚀 Opening PracticeActivity for lesson: " + lessonId +
                        ", gesture: " + lessonStep.getTitle());

                Intent intent = new Intent(getActivity(), PracticeActivity.class);
                intent.putExtra("lesson_id", lessonId);
                intent.putExtra("practice_mode", PracticeActivity.PRACTICE_MODE_SINGLE);
                intent.putExtra("current_gesture", lessonStep.getTitle()); // This should be the fragment's gesture

                // 🔥 KEY FIX: Use getActivity().startActivityForResult()
                // This ensures LearningActivity handles the result, not the fragment
                getActivity().startActivityForResult(intent, 101); // Use same request code as LearningActivity

            } else {
                Log.e(TAG, "❌ Failed to start PracticeActivity: lessonId or lessonStep is null");
            }
        } else {
            Log.e(TAG, "❌ Failed to start PracticeActivity: getActivity() is null");
        }
    }

}