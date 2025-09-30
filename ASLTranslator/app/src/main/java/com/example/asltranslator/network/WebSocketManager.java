package com.example.asltranslator.network;

import android.util.Log;

import com.example.asltranslator.models.GestureResponse;
import com.example.asltranslator.utils.Constants;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Manages WebSocket connections for real-time ASL gesture recognition.
 * Handles frame transmission and server responses in CameraTranslateActivity and PracticeActivity.
 */
public class WebSocketManager extends WebSocketListener {
    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean isConnected = false;
    private WebSocketCallback callback;
    private Gson gson;

    // Reconnection variables
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 2000;

    // Singleton pattern
    /**
     * Returns the singleton instance of WebSocketManager.
     *
     * @return The single instance.
     */
    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    /**
     * Initializes the WebSocket client with Gson for JSON parsing.
     */
    private WebSocketManager() {
        gson = new Gson();
        initializeClient();
    }

    /**
     * Initializes the OkHttpClient with connection settings.
     */
    private void initializeClient() {
        client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Connects to the WebSocket server with the specified URL.
     *
     * @param url     WebSocket URL from Constants.getWebSocketUrl.
     * @param callback Callback for WebSocket events.
     */
    public void connect(String url, WebSocketCallback callback) {
        this.callback = callback;

        if (isConnected) {
            Log.w(TAG, "WebSocket already connected");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Origin", "android-app")
                .build();

        webSocket = client.newWebSocket(request, this);
        Log.d(TAG, "Attempting to connect to: " + url);
    }

    // Frame throttling
    private long lastSentTimestamp = 0;
    private static final long FRAME_INTERVAL_MS = 300; // Adjust as needed (300ms works well)

    /**
     * Sends a Base64-encoded image frame to the server with throttling.
     *
     * @param base64Image Base64-encoded JPEG frame.
     */
    public void sendFrame(String base64Image) {
        long now = System.currentTimeMillis();

        if (now - lastSentTimestamp < FRAME_INTERVAL_MS) {
            Log.d(TAG, "⏳ Frame skipped due to throttling");
            return;
        }
        lastSentTimestamp = now;

        if (!isConnected || webSocket == null) {
            Log.e(TAG, "WebSocket not connected");
            return;
        }

        try {
            JSONObject frameData = new JSONObject();
            // 🔥 FIXED: Send EXACT same format as React Native (NO "source"!)
            frameData.put("frame", "data:image/jpeg;base64," + base64Image);
            frameData.put("timestamp", now);

            webSocket.send(frameData.toString());
            Log.d(TAG, "✅ Frame sent successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending frame", e);
        }
    }

    /**
     * Handles WebSocket connection opening.
     *
     * @param webSocket WebSocket instance.
     * @param response  Server response.
     */
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "WebSocket connected");
        isConnected = true;
        reconnectAttempts = 0;

        if (callback != null) {
            callback.onConnected();
        }
    }

    /**
     * Handles text messages from the server, parsing gesture responses.
     *
     * @param webSocket WebSocket instance.
     * @param text      JSON response with gesture data.
     */
    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "Message received: " + text);

        try {
            GestureResponse response = gson.fromJson(text, GestureResponse.class);

            if (callback != null) {
                callback.onGestureDetected(response);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing message", e);
            if (callback != null) {
                callback.onError("Failed to parse response: " + e.getMessage());
            }
        }
    }

    /**
     * Handles binary messages from the server (currently unused).
     *
     * @param webSocket WebSocket instance.
     * @param bytes     Binary data.
     */
    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        // Handle binary messages if needed
        Log.d(TAG, "Binary message received");
    }

    /**
     * Handles WebSocket closing initiated by the server.
     *
     * @param webSocket WebSocket instance.
     * @param code      Close code.
     * @param reason    Close reason.
     */
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closing: " + reason);
        webSocket.close(1000, null);
        isConnected = false;
    }

    /**
     * Handles WebSocket closure, triggering reconnection if not intentional.
     *
     * @param webSocket WebSocket instance.
     * @param code      Close code.
     * @param reason    Close reason.
     */
    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closed: " + reason);
        isConnected = false;

        if (callback != null) {
            callback.onDisconnected();
        }

        // Attempt reconnection if not manually closed
        if (code != 1000 && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            attemptReconnect();
        }
    }

    /**
     * Handles WebSocket connection failures, triggering reconnection.
     *
     * @param webSocket WebSocket instance.
     * @param t         Throwable error.
     * @param response  Server response, if any.
     */
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket failure", t);
        Log.e(TAG, "WebSocket failure details:");
        Log.e(TAG, "Error: " + t.getMessage());
        Log.e(TAG, "Response: " + (response != null ? response.toString() : "null"));
        Log.e(TAG, "URL attempted: " + Constants.WEBSOCKET_URL);
        isConnected = false;

        if (callback != null) {
            callback.onError("Connection failed: " + t.getMessage());
        }

        // Attempt reconnection
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            attemptReconnect();
        }
    }

    /**
     * Attempts to reconnect to the WebSocket with exponential backoff.
     */
    private void attemptReconnect() {
        reconnectAttempts++;
        Log.d(TAG, "Attempting reconnection " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS);

        // Exponential backoff
        long delay = RECONNECT_DELAY_MS * (long) Math.pow(2, reconnectAttempts - 1);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isConnected) {
                connect(Constants.WEBSOCKET_URL, callback);
            }
        }, delay);
    }

    /**
     * Disconnects the WebSocket connection.
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User initiated disconnect");
            webSocket = null;
        }
        isConnected = false;
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS; // Prevent auto-reconnect
    }

    /**
     * Checks if the WebSocket is currently connected.
     *
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Callback interface for WebSocket events.
     */
    public interface WebSocketCallback {
        void onConnected();
        void onDisconnected();
        void onGestureDetected(GestureResponse response);
        void onError(String error);
    }
}
