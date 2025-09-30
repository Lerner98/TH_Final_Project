package com.example.asltranslator.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

public class Constants {

    private static final String TAG = "Constants";

    // 🎯 ENVIRONMENT CONFIGURATION
    private static final String HOME_IP = "10.100.102.11";        // Your home network
    private static final String SCHOOL_IP = "10.57.40.120";      // School network
    private static final String EMULATOR_IP = "10.0.2.2";        // Android emulator
    private static final String PRODUCTION_HOST = "api.asltranslator.app"; // Future cloud

    private static final int DEV_PORT = 8001;
    private static final int PRODUCTION_PORT = 443;

    // 🔧 ENVIRONMENT DETECTION
    private static boolean isRunningOnEmulator() {
        return Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.HARDWARE.contains("goldfish")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.PRODUCT.contains("sdk");
    }

    private static boolean isProductionBuild() {
        return false; // Set to true for production builds
    }

    private static boolean isSchoolDemo(Context context) {
        if (context != null) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("asl_config", Context.MODE_PRIVATE);
                boolean schoolDemo = prefs.getBoolean("school_demo_mode", false);
                // Reset school demo if device is not on school network
                if (schoolDemo) {
                    String deviceIP = getDeviceIP(context);
                    if (deviceIP != null && !deviceIP.startsWith("10.57.40.")) {
                        prefs.edit().putBoolean("school_demo_mode", false).apply();
                        Log.i(TAG, "🏠 Cleared school_demo_mode: not on school network (device IP: " + deviceIP + ")");
                        return false;
                    }
                }
                return schoolDemo;
            } catch (Exception e) {
                Log.w(TAG, "Could not check demo mode: " + e.getMessage());
            }
        }
        return false;
    }

    private static String getDeviceIP(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            return String.format("%d.%d.%d.%d",
                    (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        } catch (Exception e) {
            Log.w(TAG, "Could not get device IP: " + e.getMessage());
            return null;
        }
    }

    private static String getManualIP(Context context) {
        if (context != null) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("asl_config", Context.MODE_PRIVATE);
                return prefs.getString("manual_server_ip", null);
            } catch (Exception e) {
                Log.w(TAG, "Could not get manual IP: " + e.getMessage());
            }
        }
        return null;
    }

    private static String getServerHost(Context context) {
        // Priority: Manual Override > Network Detection > School Demo > Environment > Default
        String manualIP = getManualIP(context);
        if (manualIP != null && !manualIP.trim().isEmpty()) {
            Log.i(TAG, "🔧 Using manual IP: " + manualIP);
            return manualIP.trim();
        }

        // Network-based detection
        if (context != null) {
            String deviceIP = getDeviceIP(context);
            if (deviceIP != null) {
                if (deviceIP.startsWith("10.100.102.")) {
                    Log.i(TAG, "🏠 Detected home network: " + deviceIP + ", using HOME_IP: " + HOME_IP);
                    return HOME_IP;
                } else if (deviceIP.startsWith("10.57.40.")) {
                    Log.i(TAG, "🎓 Detected school network: " + deviceIP + ", using SCHOOL_IP: " + SCHOOL_IP);
                    return SCHOOL_IP;
                }
            }
        }

        // Check school demo mode (only if network detection fails)
        if (isSchoolDemo(context)) {
            Log.i(TAG, "🎓 School demo mode: " + SCHOOL_IP);
            return SCHOOL_IP;
        }

        // Environment-based detection
        if (isProductionBuild()) {
            Log.i(TAG, "🌐 Production mode: " + PRODUCTION_HOST);
            return PRODUCTION_HOST;
        } else if (isRunningOnEmulator()) {
            Log.i(TAG, "📱 Emulator mode: " + EMULATOR_IP);
            return EMULATOR_IP;
        } else {
            Log.i(TAG, "🏠 Defaulting to development mode: " + HOME_IP);
            return HOME_IP;
        }
    }

    private static int getServerPort(Context context) {
        return isProductionBuild() ? PRODUCTION_PORT : DEV_PORT;
    }

    private static String getProtocol(Context context) {
        return isProductionBuild() ? "https" : "http";
    }

    private static String getWebSocketProtocol(Context context) {
        return isProductionBuild() ? "wss" : "ws";
    }

    // 🌐 DYNAMIC URLs
    public static String getBaseUrl(Context context) {
        try {
            return String.format("%s://%s:%d",
                    getProtocol(context),
                    getServerHost(context),
                    getServerPort(context));
        } catch (Exception e) {
            Log.e(TAG, "Error getting base URL, using fallback", e);
            return "http://192.168.1.26:8001";
        }
    }

    public static String getWebSocketUrl(Context context) {
        try {
            return String.format("%s://%s:%d/asl-ws",
                    getWebSocketProtocol(context),
                    getServerHost(context),
                    getServerPort(context));
        } catch (Exception e) {
            Log.e(TAG, "Error getting WebSocket URL, using fallback", e);
            return "ws://192.168.1.26:8001/asl-ws";
        }
    }


    // 🎓 DEMO HELPER METHODS
    public static void enableSchoolDemo(Context context) {
        if (context != null) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("asl_config", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("school_demo_mode", true).apply();
                Log.i(TAG, "🎓 School demo mode ENABLED - using IP: " + SCHOOL_IP);
            } catch (Exception e) {
                Log.e(TAG, "Failed to enable school demo mode", e);
            }
        }
    }

    public static void disableSchoolDemo(Context context) {
        if (context != null) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("asl_config", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("school_demo_mode", false).apply();
                Log.i(TAG, "🏠 School demo mode DISABLED - back to auto-detection");
            } catch (Exception e) {
                Log.e(TAG, "Failed to disable school demo mode", e);
            }
        }
    }

    public static void setManualIP(Context context, String ip) {
        if (context != null) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("asl_config", Context.MODE_PRIVATE);
                if (ip == null || ip.trim().isEmpty()) {
                    prefs.edit().remove("manual_server_ip").apply();
                    Log.i(TAG, "🔧 Manual IP cleared");
                } else {
                    prefs.edit().putString("manual_server_ip", ip.trim()).apply();
                    Log.i(TAG, "🔧 Manual IP set to: " + ip.trim());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set manual IP", e);
            }
        }
    }

    public static String getConfigSummary(Context context) {
        try {
            String deviceIP = getDeviceIP(context);
            return String.format("Host: %s | Base URL: %s | Demo: %s | Device IP: %s",
                    getServerHost(context),
                    getBaseUrl(context),
                    isSchoolDemo(context) ? "SCHOOL" : "AUTO",
                    deviceIP != null ? deviceIP : "unknown");
        } catch (Exception e) {
            return "Config error: " + e.getMessage();
        }
    }

    // 🔙 ALL EXISTING CONSTANTS
    public static final String BASE_URL = "http://10.57.40.120:8001";
    public static final String WEBSOCKET_URL = "ws://10.57.40.120:8001/asl-ws";

    public static final String USERS_NODE = "users";
    public static final String PROGRESS_NODE = "progress";
    public static final String ACHIEVEMENTS_NODE = "achievements";

    public static final String PREF_NAME = "ASLTranslatorPrefs";
    public static final String KEY_FIRST_TIME = "first_time";
    public static final String KEY_USER_ID = "user_id";

    public static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final String KEYSTORE_ALIAS = "ASLTranslatorKey";

    public static final int CAMERA_PERMISSION_CODE = 100;
    public static final String[] ASL_LABELS = {
            "Yes", "No", "I Love You", "Hello", "Thank You"
    };

    public static final int TRAINING_REQUEST_CODE = 200;
    public static final int MIN_SAMPLES_PER_GESTURE = 100;
    public static final int MAX_SAMPLES_PER_GESTURE = 200;
}