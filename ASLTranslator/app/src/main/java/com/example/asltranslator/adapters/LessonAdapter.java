package com.example.asltranslator.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.asltranslator.R;
import com.example.asltranslator.models.Lesson;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

import java.util.List;

/**
 * Displays ASL lessons in a RecyclerView with progress and lock status.
 * Supports interactive lesson selection in MainActivity for learning progression.
 */
public class LessonAdapter extends RecyclerView.Adapter<LessonAdapter.LessonViewHolder> {

    private List<Lesson> lessons;
    private OnLessonClickListener listener;

    public interface OnLessonClickListener {
        void onLessonClick(Lesson lesson);
    }

    public LessonAdapter(List<Lesson> lessons, OnLessonClickListener listener) {
        this.lessons = lessons;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LessonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lesson, parent, false);
        return new LessonViewHolder(view);
    }

    /**
     * Binds lesson data to the view holder, showing title, description, and progress.
     * Drives lesson display and interaction for ASL learning.
     */
    @Override
    public void onBindViewHolder(@NonNull LessonViewHolder holder, int position) {
        Lesson lesson = lessons.get(position);
        holder.bind(lesson, position + 1); // Pass lesson number (1-based)
    }

    @Override
    public int getItemCount() {
        return lessons.size();
    }

    class LessonViewHolder extends RecyclerView.ViewHolder {
        private TextView tvLessonNumber;
        private TextView tvLessonTitle;
        private TextView tvLessonDescription;
        private LinearProgressIndicator progressLesson;
        private TextView tvProgress;
        private ImageView ivStatus;

        public LessonViewHolder(@NonNull View itemView) {
            super(itemView);

            tvLessonNumber = itemView.findViewById(R.id.tv_lesson_number);
            tvLessonTitle = itemView.findViewById(R.id.tv_lesson_title);
            tvLessonDescription = itemView.findViewById(R.id.tv_lesson_description);
            progressLesson = itemView.findViewById(R.id.progress_lesson);
            tvProgress = itemView.findViewById(R.id.tv_progress);
            ivStatus = itemView.findViewById(R.id.iv_status);
        }

        public void bind(Lesson lesson, int lessonNumber) {
            // Set lesson number in circle
            tvLessonNumber.setText(String.valueOf(lessonNumber));

            // Set basic info
            tvLessonTitle.setText(lesson.getTitle());
            tvLessonDescription.setText(lesson.getDescription());

            // 🔑 KEY FIX: Check lock state FIRST before loading progress
            if (lesson.isLocked()) {
                // 🔒 Locked lesson - don't load Firebase data, just show locked state
                setupLockedLesson();
            } else {
                // 🔓 Available lesson - load progress from Firebase
                setupAvailableLesson();
                loadLessonProgress(lesson.getLessonId()); // Use lesson.getLessonId() for accuracy
            }
        }


        /**
         * Loads lesson progress from Firebase for the current user.
         * Tracks ASL learning progress for personalized feedback.
         */
        private void loadLessonProgress(String lessonId) {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                setDefaultProgress();
                return;
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference progressRef = FirebaseDatabase.getInstance().getReference()
                    .child("users")
                    .child(userId)
                    .child("progress")
                    .child(lessonId);

            progressRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Integer progressPercentage = snapshot.child("progressPercentage").getValue(Integer.class);
                        String statusText = snapshot.child("statusText").getValue(String.class);
                        Boolean completed = snapshot.child("completed").getValue(Boolean.class);

                        if (progressPercentage == null) progressPercentage = 0;
                        if (statusText == null) statusText = "📚 Start Learning";
                        if (completed == null) completed = false;

                        updateProgressUI(progressPercentage, statusText, completed);
                    } else {
                        setDefaultProgress();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    setDefaultProgress();
                }
            });
        }


        /**
         * Updates UI with progress data (percentage, status, completion).
         * Visualizes ASL lesson progress for user motivation.
         */
        private void updateProgressUI(int progressPercentage, String statusText, boolean completed) {
            progressLesson.setProgress(progressPercentage);

            if (completed) {
                ivStatus.setImageResource(R.drawable.ic_check_circle);
                ivStatus.setColorFilter(itemView.getContext().getColor(R.color.success));
                tvProgress.setText("✅ Complete (100%)");
                tvProgress.setTextColor(itemView.getContext().getColor(R.color.success));
            } else if (progressPercentage > 0) {
                ivStatus.setImageResource(R.drawable.ic_play_circle);
                ivStatus.setColorFilter(itemView.getContext().getColor(R.color.primary));
                tvProgress.setText(statusText);
                tvProgress.setTextColor(itemView.getContext().getColor(R.color.primary));
            } else {
                ivStatus.setImageResource(R.drawable.ic_play_circle);
                ivStatus.setColorFilter(itemView.getContext().getColor(R.color.primary));
                tvProgress.setText("📚 Start Learning");
                tvProgress.setTextColor(itemView.getContext().getColor(R.color.primary));
            }

            itemView.setAlpha(1f);
            tvLessonNumber.setTextColor(itemView.getContext().getColor(R.color.white));
            tvLessonTitle.setTextColor(itemView.getContext().getColor(R.color.text_primary));
            tvLessonDescription.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
        }

        // 🔧 NEW: Set default progress (no Firebase data)
        private void setDefaultProgress() {
            progressLesson.setProgress(0);
            ivStatus.setImageResource(R.drawable.ic_play_circle);
            ivStatus.setColorFilter(itemView.getContext().getColor(R.color.primary));
            tvProgress.setText("📚 Start Learning");
            tvProgress.setTextColor(itemView.getContext().getColor(R.color.primary));

            itemView.setAlpha(1f);
            tvLessonNumber.setTextColor(itemView.getContext().getColor(R.color.white));
            tvLessonTitle.setTextColor(itemView.getContext().getColor(R.color.text_primary));
            tvLessonDescription.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
        }


        /**
         * Configures UI for locked lessons, disabling interaction.
         * Guides users to complete prior lessons for ASL progression.
         */
        private void setupLockedLesson() {
            ivStatus.setImageResource(R.drawable.ic_lock);
            ivStatus.setColorFilter(itemView.getContext().getColor(R.color.gray));
            itemView.setAlpha(0.6f);
            tvProgress.setText("🔒 Complete previous lesson"); // More informative text
            tvProgress.setTextColor(itemView.getContext().getColor(R.color.gray));
            progressLesson.setProgress(0);
            progressLesson.setVisibility(View.GONE);

            // Disable interaction
            itemView.setClickable(false);
            itemView.setFocusable(false);
            itemView.setBackground(null);

            // Dim all text colors
            tvLessonNumber.setTextColor(itemView.getContext().getColor(R.color.gray));
            tvLessonTitle.setTextColor(itemView.getContext().getColor(R.color.gray));
            tvLessonDescription.setTextColor(itemView.getContext().getColor(R.color.gray));

            // Add lock feedback
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    Lesson lockedLesson = lessons.get(position);
                    // Optionally show a dialog (implement showLevelLockedDialog if needed)
                    // showLevelLockedDialog(lockedLesson);
                }
            });
        }


        /**
         * Configures UI for available lessons, enabling interaction.
         * Encourages users to start or continue ASL learning.
         */
        private void setupAvailableLesson() {
            itemView.setAlpha(1.0f);
            progressLesson.setVisibility(View.VISIBLE);
            itemView.setClickable(true);
            itemView.setFocusable(true);
            itemView.setBackground(ContextCompat.getDrawable(itemView.getContext(),
                    R.drawable.lesson_item_background));

            // Reset text colors
            tvLessonNumber.setTextColor(itemView.getContext().getColor(R.color.white));
            tvLessonTitle.setTextColor(itemView.getContext().getColor(R.color.text_primary));
            tvLessonDescription.setTextColor(itemView.getContext().getColor(R.color.text_secondary));

            // Add click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onLessonClick(lessons.get(position));
                    }
                }
            });
        }
    }
}