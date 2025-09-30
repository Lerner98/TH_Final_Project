package com.example.asltranslator;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class ASLTranslatorApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Enable offline persistence for Firebase
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // Initialize any other app-wide configurations
        initializeAppComponents();
    }

    private void initializeAppComponents() {
        // Initialize crash reporting, analytics, etc.
    }
}