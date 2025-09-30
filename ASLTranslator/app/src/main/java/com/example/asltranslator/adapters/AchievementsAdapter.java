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
import com.example.asltranslator.models.Achievement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying achievements in RecyclerView with unlock status and dates.
 */
public class AchievementsAdapter extends RecyclerView.Adapter<AchievementsAdapter.AchievementViewHolder> {

    private List<Achievement> achievements;
    private SimpleDateFormat dateFormat;

    public AchievementsAdapter(List<Achievement> achievements) {
        this.achievements = achievements;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    @NonNull
    @Override
    public AchievementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_achievement, parent, false);
        return new AchievementViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AchievementViewHolder holder, int position) {
        Achievement achievement = achievements.get(position);
        holder.bind(achievement);
    }

    @Override
    public int getItemCount() {
        return achievements.size();
    }

    class AchievementViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTitle, tvDescription, tvPoints, tvDate, tvStatus;
        private ImageView ivIcon, ivLock;

        public AchievementViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_achievement_title);
            tvDescription = itemView.findViewById(R.id.tv_achievement_description);
            tvPoints = itemView.findViewById(R.id.tv_achievement_points);
            tvDate = itemView.findViewById(R.id.tv_achievement_date);
            tvStatus = itemView.findViewById(R.id.tv_achievement_status);
            ivIcon = itemView.findViewById(R.id.iv_achievement_icon);
            ivLock = itemView.findViewById(R.id.iv_achievement_lock);
        }

        public void bind(Achievement achievement) {
            tvTitle.setText(achievement.getName());
            tvDescription.setText(achievement.getDescription());
            tvPoints.setText(achievement.getPoints() + " points");

            if (achievement.isUnlocked()) {
                // UNLOCKED STATE

                // Show main icon, hide lock overlay
                ivIcon.setVisibility(View.VISIBLE);
                ivLock.setVisibility(View.GONE);

                // Set consistent unlocked icon (checkmark) - KEEP NATURAL COLORS
                ivIcon.setImageResource(R.drawable.ic_check_circle);

                // Show unlock date
                if (achievement.getUnlockedAt() > 0) {
                    Date unlockDate = new Date(achievement.getUnlockedAt());
                    tvDate.setText("Unlocked: " + dateFormat.format(unlockDate));
                    tvDate.setVisibility(View.VISIBLE);
                } else {
                    tvDate.setVisibility(View.GONE);
                }

                // Hide status text for unlocked (date shows it's unlocked)
                tvStatus.setVisibility(View.GONE);

                // Full opacity for unlocked
                itemView.setAlpha(1.0f);

            } else {
                // LOCKED STATE

                // Hide main icon, show lock overlay
                ivIcon.setVisibility(View.GONE);
                ivLock.setVisibility(View.VISIBLE);

                // Set consistent locked icon color
                ivLock.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.gray));

                // Hide unlock date
                tvDate.setVisibility(View.GONE);

                // REMOVED: Hide redundant "Locked" text since lock icon already shows it's locked
                tvStatus.setVisibility(View.GONE);

                // Reduced opacity for locked
                itemView.setAlpha(0.6f);
            }
        }
    }
}