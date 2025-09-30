package com.example.asltranslator.adapters;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.asltranslator.R;
import com.example.asltranslator.models.TranslationHistory;

import java.util.List;

/**
 * Displays translation history in a RecyclerView for TranslationHistoryFragment.
 * Currently not shown in UI to avoid clutter; planned for a future TranslationHistoryActivity with detailed accuracy, achievements, and lesson/practice info.
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private List<TranslationHistory> historyList;

    public HistoryAdapter(List<TranslationHistory> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    /**
     * Binds translation history data to the view holder.
     * Shows gesture, timestamp, and confidence for ASL practice feedback.
     */
    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        TranslationHistory history = historyList.get(position);
        holder.bind(history);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        private TextView tvGestureName;
        private TextView tvTimestamp;
        private TextView tvConfidence;
        private ImageView ivGestureIcon;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGestureName = itemView.findViewById(R.id.tv_gesture_name);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvConfidence = itemView.findViewById(R.id.tv_confidence);
            ivGestureIcon = itemView.findViewById(R.id.iv_gesture_icon);
        }

        /**
         * Binds history data to UI elements with formatted timestamp and confidence.
         * Visualizes ASL translation results to track user progress.
         */
        public void bind(TranslationHistory history) {
            tvGestureName.setText(history.getGesture());

            // Format timestamp
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    history.getTimestamp(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            tvTimestamp.setText(timeAgo);

            // Format confidence
            int confidencePercent = Math.round(history.getConfidence() * 100);
            tvConfidence.setText(confidencePercent + "%");

            // Set confidence color
            int color;
            if (confidencePercent >= 80) {
                color = ContextCompat.getColor(itemView.getContext(), R.color.success);
            } else if (confidencePercent >= 60) {
                color = ContextCompat.getColor(itemView.getContext(), R.color.warning);
            } else {
                color = ContextCompat.getColor(itemView.getContext(), R.color.error);
            }
            tvConfidence.setTextColor(color);

            // Set gesture icon based on gesture name
            setGestureIcon(history.getGesture());
        }

        private void setGestureIcon(String gesture) {
            // Map gesture names to icon resources
            int iconRes;
            switch (gesture.toLowerCase()) {
                case "hello":
                    iconRes = R.drawable.ic_lesson_greetings;
                    break;
                case "thank you":
                    iconRes = R.drawable.ic_lesson_phrases;
                    break;
                case "yes":
                case "no":
                    iconRes = R.drawable.ic_gesture_placeholder;
                    break;
                case "i love you":
                    iconRes = R.drawable.ic_lesson_family;
                    break;
                default:
                    iconRes = R.drawable.ic_gesture_placeholder;
                    break;
            }
            ivGestureIcon.setImageResource(iconRes);
        }
    }
}
