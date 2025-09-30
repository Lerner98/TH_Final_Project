package com.example.asltranslator.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Manages secure storage of user preferences using EncryptedSharedPreferences.
 * Stores user IDs, first-time flags, WebSocket URLs, and generic settings.
 * Currently used for user ID and server configuration; planned for offline mode and settings UI.
 */
public class PreferenceManager {

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    /**
     * Initializes with EncryptedSharedPreferences, falling back to regular SharedPreferences if encryption fails.
     *
     * @param context Application context.
     */
    public PreferenceManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    Constants.PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            editor = sharedPreferences.edit();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            sharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            editor = sharedPreferences.edit();
        }
    }

    /**
     * Stores the Firebase user ID.
     *
     * @param userId User ID to store.
     */
    public void setUserId(String userId) {
        editor.putString(Constants.KEY_USER_ID, userId).apply();
    }

    /**
     * Retrieves the Firebase user ID for offline use (currently unused).
     *
     * @return Stored user ID or null.
     */
    public String getUserId() {
        return sharedPreferences.getString(Constants.KEY_USER_ID, null);
    }

    /**
     * Sets first-time flag for onboarding.
     *
     * @param isFirstTime True if first run.
     */
    public void setFirstTime(boolean isFirstTime) {
        editor.putBoolean(Constants.KEY_FIRST_TIME, isFirstTime).apply();
    }

    /**
     * Checks if app is running for the first time for onboarding (currently unused).
     *
     * @return True if first run.
     */
    public boolean isFirstTime() {
        return sharedPreferences.getBoolean(Constants.KEY_FIRST_TIME, true);
    }

    /**
     * Stores custom WebSocket URL (currently unused).
     *
     * @param url WebSocket URL to store.
     */
    public void setWebSocketUrl(String url) {
        editor.putString("websocket_url", url).apply();
    }

    /**
     * Retrieves custom WebSocket URL, defaulting to Constants.WEBSOCKET_URL (currently unused).
     *
     * @return Stored WebSocket URL.
     */
    public String getWebSocketUrl() {
        return sharedPreferences.getString("websocket_url", Constants.WEBSOCKET_URL);
    }

    /**
     * Clears all preferences.
     */
    public void clearAll() {
        editor.clear().apply();
    }

    /**
     * Stores a string preference.
     *
     * @param key   Preference key.
     * @param value String value.
     */
    public void setString(String key, String value) {
        editor.putString(key, value).apply();
    }

    /**
     * Retrieves a string preference.
     *
     * @param key          Preference key.
     * @param defaultValue Default value.
     * @return Stored string or default.
     */
    public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    /**
     * Stores a boolean preference for settings like dark mode (currently unused).
     *
     * @param key   Preference key.
     * @param value Boolean value.
     */
    public void setBoolean(String key, boolean value) {
        editor.putBoolean(key, value).apply();
    }

    /**
     * Retrieves a boolean preference for settings like dark mode (currently unused).
     *
     * @param key          Preference key.
     * @param defaultValue Default value.
     * @return Stored boolean or default.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    /**
     * Stores a long preference for timestamps like practice time (currently unused).
     *
     * @param key   Preference key.
     * @param value Long value.
     */
    public void setLong(String key, long value) {
        editor.putLong(key, value).apply();
    }

    /**
     * Retrieves a long preference for timestamps like practice time (currently unused).
     *
     * @param key          Preference key.
     * @param defaultValue Default value.
     * @return Stored long or default.
     */
    public long getLong(String key, long defaultValue) {
        return sharedPreferences.getLong(key, defaultValue);
    }
}
