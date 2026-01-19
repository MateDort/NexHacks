package com.tapmate.aiagent.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tapmate.aiagent.BuildConfig;
import com.tapmate.aiagent.audio.AudioHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import android.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * GeminiLiveClient - Real Gemini Multimodal Live API with bidirectional audio
 * streaming
 *
 * Features:
 * - WebSocket connection to Gemini Live API with auto-reconnection
 * - Bidirectional audio streaming (send PCM, receive PCM)
 * - Voice Activity Detection (VAD) for turn management
 * - Interruption handling (user can interrupt AI mid-response)
 * - Function calling support with async execution
 * - Audio transcription (input and output)
 * - Exponential backoff reconnection strategy
 * - System instruction with personalization
 *
 * Based on ADA V2 implementation pattern adapted for Android/Java
 */
public class GeminiLiveClient {

    private static final String TAG = "GeminiLiveClient";
    // Backend bridge URL - change to your server IP when deployed
    // Emulator: ws://10.0.2.2:8765 | Real device: ws://YOUR_IP:8765
    private static final String WS_URL = "ws://10.0.0.205:8765";

    // Reconnection configuration
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final int MAX_RETRY_DELAY_MS = 10000; // 10 seconds

    // No API key needed - backend handles authentication
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Map<String, FunctionHandler> functionHandlers = new HashMap<>();
    private final List<JSONObject> registeredFunctions = new ArrayList<>();

    private WebSocket webSocket;
    private OkHttpClient httpClient;
    private final AtomicBoolean isSessionActive = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private String currentSystemInstruction = "";

    // Audio handling
    private AudioHandler audioHandler;
    private final AtomicBoolean isAudioStreamingActive = new AtomicBoolean(false);

    // Callbacks
    public interface FunctionHandler {
        CompletableFuture<JSONObject> handle(JSONObject args);
    }

    public interface AudioResponseCallback {
        void onAudioData(byte[] audioData);
    }

    public interface TranscriptCallback {
        void onTranscript(String text, boolean isUser);
    }

    public interface SetupCompleteCallback {
        void onSetupComplete();
    }

    private AudioResponseCallback audioResponseCallback;
    private TranscriptCallback transcriptCallback;
    private SetupCompleteCallback setupCompleteCallback;

    public GeminiLiveClient(AudioHandler audioHandler) {
        this.executor = Executors.newFixedThreadPool(4); // More threads for concurrent operations
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Initialize HTTP client with longer timeouts for WebSocket
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // Keep-alive
                .build();

        // Use provided audio handler
        this.audioHandler = audioHandler;
        setupAudioHandlerCallbacks();

        Log.i(TAG, "GeminiLiveClient initialized - will connect to Python backend");
    }

    /**
     * Setup audio handler callbacks for VAD and streaming
     */
    private void setupAudioHandlerCallbacks() {
        // VAD callbacks
        audioHandler.setVADCallback(new AudioHandler.VADCallback() {
            @Override
            public void onSpeechStart() {
                Log.d(TAG, "[VAD] User started speaking");

                // Interrupt AI if it's currently speaking
                if (isAudioStreamingActive.get()) {
                    Log.i(TAG, "[INTERRUPT] User interrupted AI - clearing playback queue");
                    audioHandler.clearPlaybackQueue();
                }
            }

            @Override
            public void onSpeechEnd() {
                Log.d(TAG, "[VAD] User stopped speaking");
            }

            @Override
            public void onSilenceDetected() {
                Log.d(TAG, "[VAD] Silence detected");
                // Manual turn_complete is now handled automatically by Gemini VAD on the
                // backend
            }
        });

        // Start audio streaming task
        startAudioStreamingLoop();
    }

    /**
     * Continuous audio streaming loop (sends captured audio to Gemini)
     */
    private void startAudioStreamingLoop() {
        executor.execute(() -> {
            Log.i(TAG, "Audio streaming loop started");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (isSessionActive.get()) {
                        byte[] audioChunk = audioHandler.pollAudioChunk();

                        if (audioChunk != null) {
                            sendAudioData(audioChunk);
                        }
                    }

                    // Small sleep to prevent busy-waiting
                    Thread.sleep(10);

                } catch (InterruptedException e) {
                    Log.d(TAG, "Audio streaming loop interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in audio streaming loop", e);
                }
            }

            Log.i(TAG, "Audio streaming loop stopped");
        });
    }

    public void startSession(String systemInstruction, List<JSONObject> functionDeclarations) {
        Log.i(TAG, "Starting Gemini Live session...");

        this.currentSystemInstruction = systemInstruction;
        this.registeredFunctions.clear();
        this.registeredFunctions.addAll(functionDeclarations);

        // Enable reconnection and reset attempts
        shouldReconnect.set(true);
        reconnectAttempts.set(0);

        // Start connection (with reconnection support)
        connectWithRetry(false);
    }

    /**
     * Connect to Gemini Live API with exponential backoff retry
     */
    private void connectWithRetry(boolean isReconnect) {
        if (!shouldReconnect.get()) {
            Log.d(TAG, "Reconnection disabled - not connecting");
            return;
        }

        // Set session active
        isSessionActive.set(true);

        executor.execute(() -> {
            int retryDelay = INITIAL_RETRY_DELAY_MS;

            while (shouldReconnect.get() && reconnectAttempts.get() < MAX_RECONNECT_ATTEMPTS) {
                try {
                    Log.i(TAG, "[CONNECT] Attempt " + (reconnectAttempts.get() + 1) + "/" + MAX_RECONNECT_ATTEMPTS);

                    // Build WebSocket URL
                    // Connect to Python backend bridge
                    String wsUrl = WS_URL;

                    // Create WebSocket request
                    Request request = new Request.Builder()
                            .url(wsUrl)
                            .build();

                    // Connect WebSocket
                    webSocket = httpClient.newWebSocket(request, createWebSocketListener(isReconnect));

                    // Wait for connection to establish or fail
                    Thread.sleep(2000);

                    // Check if connection successful
                    if (isSessionActive.get()) {
                        Log.i(TAG, "[CONNECT] Successfully connected");
                        reconnectAttempts.set(0);
                        return; // Success
                    }

                } catch (Exception e) {
                    Log.e(TAG, "[RECONNECT] Connection failed: " + e.getMessage());
                }

                // Exponential backoff
                reconnectAttempts.incrementAndGet();

                if (reconnectAttempts.get() < MAX_RECONNECT_ATTEMPTS) {
                    Log.i(TAG, "[RETRY] Reconnecting in " + retryDelay + "ms...");
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException e) {
                        break;
                    }
                    retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY_MS);
                }
            }

            Log.e(TAG, "[RECONNECT] Max reconnection attempts reached");
            isSessionActive.set(false);
        });
    }

    /**
     * Create WebSocket listener with all event handlers
     */
    private WebSocketListener createWebSocketListener(boolean isReconnect) {
        return new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.i(TAG, "WebSocket connected" + (isReconnect ? " (RECONNECTED)" : ""));

                // Check if session is still active (prevent race condition with stopSession)
                if (!isSessionActive.get()) {
                    Log.w(TAG, "Session was stopped before WebSocket fully opened");
                    ws.close(1000, "Session stopped");
                    return;
                }

                // Backend bridge handles setup automatically on connection
                Log.i(TAG, "Connected to bridge - waiting for 'ready' signal");

                // Initialize audio but DON'T start yet - wait for setupComplete
                if (!audioHandler.initializeAudioCapture() || !audioHandler.initializeAudioPlayback()) {
                    Log.e(TAG, "Failed to initialize audio");
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    // Log full message for debugging (first occurrence only to avoid spam)
                    if (text.length() < 1000) {
                        Log.d(TAG, "Received message: " + text);
                    } else {
                        Log.d(TAG, "Received large message (" + text.length() + " chars): " + text.substring(0, 500)
                                + "...");
                    }
                    handleServerMessage(new JSONObject(text));
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing server message", e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                // Gemini sends JSON in binary frames too
                byte[] data = bytes.toByteArray();

                // Check if this is JSON (starts with '{')
                if (data.length > 0 && data[0] == '{') {
                    try {
                        String jsonText = bytes.utf8();
                        Log.d(TAG, "Received BINARY JSON: " + jsonText.substring(0, Math.min(300, jsonText.length())));
                        handleServerMessage(new JSONObject(jsonText));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing binary JSON", e);
                    }
                } else {
                    // Raw audio data (PCM) from Gemini
                    Log.d(TAG, "✓ Received raw PCM audio: " + data.length + " bytes");

                    // Queue for playback via AudioHandler
                    audioHandler.queueAudioForPlayback(data);

                    if (audioResponseCallback != null) {
                        audioResponseCallback.onAudioData(data);
                    }
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.i(TAG, "WebSocket closing: " + reason);
                isSessionActive.set(false);

                // Attempt reconnection if enabled
                if (shouldReconnect.get()) {
                    Log.i(TAG, "[RECONNECT] Attempting to reconnect...");
                    mainHandler.postDelayed(() -> connectWithRetry(true), 1000);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String errorMsg = "WebSocket error: " + t.getMessage();
                if (response != null) {
                    errorMsg += " | Response: " + response.code() + " " + response.message();
                }
                Log.e(TAG, errorMsg, t);
                isSessionActive.set(false);

                // Attempt reconnection if enabled
                if (shouldReconnect.get()) {
                    Log.i(TAG, "[RECONNECT] Attempting to reconnect after failure...");
                    mainHandler.postDelayed(() -> connectWithRetry(true), 2000);
                }
            }
        };
    }

    private void handleServerMessage(JSONObject message) throws JSONException {
        String type = message.optString("type", "unknown");

        // Handle READY signal from backend
        if ("ready".equals(type)) {
            Log.i(TAG, "✓ Backend bridge ready - starting audio capture/playback");

            // Start audio streaming (after bridge is ready)
            if (!isAudioStreamingActive.get()) {
                audioHandler.startCapture();
                audioHandler.startPlayback();
                isAudioStreamingActive.set(true);
                Log.i(TAG, "Audio capture and playback started");
            }

            if (setupCompleteCallback != null) {
                setupCompleteCallback.onSetupComplete();
            }
            return;
        }

        // Handle AUDIO response from backend
        if ("audio".equals(type)) {
            if (message.has("data")) {
                String base64Audio = message.getString("data");
                byte[] audioData = Base64.decode(base64Audio, Base64.DEFAULT);
                Log.d(TAG, "Received audio: " + audioData.length + " bytes");
                audioHandler.queueAudioForPlayback(audioData);

                if (audioResponseCallback != null) {
                    audioResponseCallback.onAudioData(audioData);
                }
            }
            return;
        }

        // Handle TRANSCRIPT response from backend
        if ("transcript".equals(type)) {
            String role = message.optString("role", "unknown");
            String text = message.optString("text", "");
            boolean isUser = "user".equals(role);

            Log.i(TAG, (isUser ? "User: " : "AI: ") + text);
            if (transcriptCallback != null) {
                transcriptCallback.onTranscript(text, isUser);
            }
            return;
        }

        // Handle INTERRUPTED signal from backend
        if ("interrupted".equals(type)) {
            Log.i(TAG, "⚠️ Received 'interrupted' signal from backend - clearing playback");
            audioHandler.clearPlaybackQueue();
            return;
        }

        // Check for ERROR messages
        if ("error".equals(type) || message.has("error")) {
            Log.e(TAG, "=== BACKEND ERROR ===");
            Log.e(TAG, message.toString());
        }
    }

    private void handleFunctionCall(JSONObject toolCall) throws JSONException {
        // Functions are now handled on the backend for simplicity in this bridge setup
        Log.i(TAG, "Function call received (handling is on backend)");
    }

    public void stopSession() {
        Log.i(TAG, "Stopping session");

        // Disable reconnection
        shouldReconnect.set(false);
        isSessionActive.set(false);
        isAudioStreamingActive.set(false);

        // Stop audio handler
        if (audioHandler != null) {
            audioHandler.stopCapture();
            audioHandler.stopPlayback();
        }

        // Close WebSocket
        if (webSocket != null) {
            webSocket.close(1000, "Session ended");
            webSocket = null;
        }

        Log.i(TAG, "Session stopped");
    }

    public boolean isSessionActive() {
        return isSessionActive.get();
    }

    /**
     * Send raw audio data to Python backend (PCM 16-bit mono 16kHz)
     * Backend will forward to Gemini using official SDK
     */
    public void sendAudioData(byte[] audioData) {
        if (!isSessionActive.get() || webSocket == null) {
            return;
        }

        try {
            // Encode audio as base64
            String base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP);

            // Simple message format for Python backend
            JSONObject message = new JSONObject();
            message.put("type", "audio");
            message.put("data", base64Audio);

            webSocket.send(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending audio data", e);
        }
    }

    /**
     * Register a function that Gemini can call
     */
    public void registerFunction(JSONObject functionDeclaration, FunctionHandler handler) {
        try {
            String functionName = functionDeclaration.getString("name");
            functionHandlers.put(functionName, handler);

            Log.i(TAG, "Registered function: " + functionName);
        } catch (JSONException e) {
            Log.e(TAG, "Error registering function", e);
        }
    }

    /**
     * Get audio handler instance
     */
    public AudioHandler getAudioHandler() {
        return audioHandler;
    }

    /**
     * Check if audio streaming is active
     */
    public boolean isAudioStreamingActive() {
        return isAudioStreamingActive.get();
    }

    public void setAudioResponseCallback(AudioResponseCallback callback) {
        this.audioResponseCallback = callback;
    }

    public void setTranscriptCallback(TranscriptCallback callback) {
        this.transcriptCallback = callback;
    }

    public void setSetupCompleteCallback(SetupCompleteCallback callback) {
        this.setupCompleteCallback = callback;
    }

    public void shutdown() {
        stopSession();

        // Shutdown audio handler
        if (audioHandler != null) {
            audioHandler.shutdown();
        }

        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        // Cleanup HTTP client
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }

        Log.i(TAG, "GeminiLiveClient shutdown");
    }
}
