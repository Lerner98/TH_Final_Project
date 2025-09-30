package com.example.asltranslator.models;

import android.os.Parcel;
import android.os.Parcelable;

public class LessonStep implements Parcelable {
    private String title;
    private String instructions;
    private String tips;
    private String videoUrl;
    private String imageUrl;

    public LessonStep(String title, String instructions, String tips) {
        this.title = title;
        this.instructions = instructions;
        this.tips = tips;
    }

    protected LessonStep(Parcel in) {
        title = in.readString();
        instructions = in.readString();
        tips = in.readString();
        videoUrl = in.readString();
        imageUrl = in.readString();
    }

    public static final Creator<LessonStep> CREATOR = new Creator<LessonStep>() {
        @Override
        public LessonStep createFromParcel(Parcel in) {
            return new LessonStep(in);
        }

        @Override
        public LessonStep[] newArray(int size) {
            return new LessonStep[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(instructions);
        dest.writeString(tips);
        dest.writeString(videoUrl);
        dest.writeString(imageUrl);
    }

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getTips() {
        return tips;
    }

    public void setTips(String tips) {
        this.tips = tips;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}