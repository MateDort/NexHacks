package com.nexhacks.tapmate.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nexhacks.tapmate.accessibility.TapMateAccessibilityService;
import com.nexhacks.tapmate.gemini.GeminiLiveClient;
import com.nexhacks.tapmate.memory.AppDatabase;
import com.nexhacks.tapmate.memory.MemoryItem;
import com.nexhacks.tapmate.utils.MapsIntegration;
import com.nexhacks.tapmate.utils.LocationService;
import com.nexhacks.tapmate.agents.AgentRegistry;
import com.nexhacks.tapmate.agents.BaseAgent;
import com.nexhacks.tapmate.agents.GUIAgent;
import com.nexhacks.tapmate.agents.MemoryAgent;
import com.nexhacks.tapmate.agents.SearchAgent;
import com.nexhacks.tapmate.agents.NavigationAgent;
import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;

public class SessionActivity extends Activity {

    private static final String TAG = "SessionActivity";
    private static final int INPUT_SAMPLE_RATE = 16000; // 16kHz for input (Gemini Live input)
    private static final int OUTPUT_SAMPLE_RATE = 24000; // 24kHz for output (Gemini Live output)
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int INPUT_BUFFER_SIZE = AudioRecord.getMinBufferSize(INPUT_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1001;
    private static final int PERMISSION_REQUEST_LOCATION = 1002;
    
    private Button stopButton;
    private Button muteButton;
    private TextView statusText;
    private boolean isMuted = false;
    private boolean isRecording = false;
    private boolean isPlayingAudio = false; // Track when we're playing audio to prevent feedback
    private boolean pauseSendingAudio = false; // Pause sending audio to Gemini during playback
    private Handler unmuteHandler = new Handler(Looper.getMainLooper());
    private Runnable unmuteRunnable = null;
    
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private Thread recordingThread;
    private Thread playbackThread;
    private GeminiLiveClient geminiLiveClient;
    private Handler mainHandler;
    private TapMateAccessibilityService accessibilityService;
    private AppDatabase database;
    private ExecutorService executorService;
    private MapsIntegration mapsIntegration;
    private LocationService locationService;
    private AgentRegistry agentRegistry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load config
        com.nexhacks.tapmate.utils.Config.loadEnv(this);
        
        // Initialize components
        mainHandler = new Handler(Looper.getMainLooper());
        accessibilityService = TapMateAccessibilityService.getInstance();
        database = com.nexhacks.tapmate.TapMateApplication.getDatabase();
        executorService = Executors.newSingleThreadExecutor();
        mapsIntegration = new MapsIntegration();
        locationService = new LocationService(this);
        
        // Initialize agent registry
        agentRegistry = new AgentRegistry();
        initializeAgents();
        
        // Initialize Gemini client with agent registry
        geminiLiveClient = new GeminiLiveClient(agentRegistry);
        
        setContentView(createSessionLayout());
        
        // Request permissions
        requestMicrophonePermission();
        requestLocationPermission();
    }
    
    private void initializeAgents() {
        // Create agent callback
        BaseAgent.AgentCallback agentCallback = new BaseAgent.AgentCallback() {
            @Override
            public void onResult(String functionName, String result, String callId) {
                try {
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.agentCallback.onResult:ENTRY " + 
                            "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SessionActivity.java:agentCallback.onResult\",\"message\":\"Agent callback result\",\"data\":{\"functionName\":\"" + 
                            functionName + "\",\"callId\":\"" + (callId != null ? callId : "null") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                        fw.close();
                    } catch (Exception e) {}
                    // #endregion
                    if (!isFinishing() && !isDestroyed()) {
                        updateStatus(result);
                        sendFunctionResultToGemini(functionName, result, callId);
                    }
                } catch (Throwable t) {
                    android.util.Log.e(TAG, "Error in agent callback onResult", t);
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.agentCallback.onResult:ERROR " + 
                            "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SessionActivity.java:agentCallback.onResult\",\"message\":\"Error in callback\",\"data\":{\"functionName\":\"" + 
                            functionName + "\",\"error\":\"" + t.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                        fw.close();
                    } catch (Exception e) {}
                    // #endregion
                }
            }
            
            @Override
            public void onError(String functionName, String error, String callId) {
                try {
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.agentCallback.onError:ENTRY " + 
                            "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SessionActivity.java:agentCallback.onError\",\"message\":\"Agent callback error\",\"data\":{\"functionName\":\"" + 
                            functionName + "\",\"callId\":\"" + (callId != null ? callId : "null") + "\",\"error\":\"" + error + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                        fw.close();
                    } catch (Exception e) {}
                    // #endregion
                    if (!isFinishing() && !isDestroyed()) {
                        updateStatus("Error: " + error);
                        sendFunctionResultToGemini(functionName, error, callId);
                    }
                } catch (Throwable t) {
                    android.util.Log.e(TAG, "Error in agent callback onError", t);
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.agentCallback.onError:ERROR " + 
                            "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SessionActivity.java:agentCallback.onError\",\"message\":\"Error in callback\",\"data\":{\"functionName\":\"" + 
                            functionName + "\",\"error\":\"" + t.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                        fw.close();
                    } catch (Exception e) {}
                    // #endregion
                }
            }
        };
        
        // Create agents
        GUIAgent guiAgent = new GUIAgent(mainHandler, agentCallback, accessibilityService, 
            executorService, this::updateScreenState, this);
        MemoryAgent memoryAgent = new MemoryAgent(mainHandler, agentCallback, database, executorService);
        SearchAgent searchAgent = new SearchAgent(mainHandler, agentCallback, executorService);
        NavigationAgent navigationAgent = new NavigationAgent(mainHandler, agentCallback, 
            mapsIntegration, locationService, executorService);
        
        // Register agents
        agentRegistry.registerAgent(guiAgent);
        agentRegistry.registerAgent(memoryAgent);
        agentRegistry.registerAgent(searchAgent);
        agentRegistry.registerAgent(navigationAgent);
        
        Log.d(TAG, "Initialized " + agentRegistry.getAgentCount() + " agents");
    }
    
    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, 
                            android.Manifest.permission.ACCESS_COARSE_LOCATION}, 
                PERMISSION_REQUEST_LOCATION);
        }
    }
    
    private void requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Microphone permission not granted, requesting");
            ActivityCompat.requestPermissions(this, 
                new String[]{android.Manifest.permission.RECORD_AUDIO}, 
                PERMISSION_REQUEST_RECORD_AUDIO);
        } else {
            Log.d(TAG, "Microphone permission already granted");
            // Permission already granted, start session
            startGeminiLiveSession();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            // Location permission handled
        } else if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            Log.d(TAG, "Permission request result: granted=" + (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED));
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGeminiLiveSession();
            } else {
                updateStatus("Microphone permission denied");
            }
        }
    }

    private void startGeminiLiveSession() {
        updateStatus("Connecting to Gemini Live...");
        
        // Get current screen state
        String screenState = "[]";
        if (accessibilityService != null) {
            screenState = accessibilityService.getScreenState();
        }
        
        // Start Gemini Live session
        geminiLiveClient.startSession(new GeminiLiveClient.GeminiLiveCallback() {
            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    updateStatus("Connected. Listening...");
                    startAudioCapture();
                });
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> {
                    updateStatus("Disconnected");
                    stopAudioCapture();
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    android.util.Log.e(TAG, "Gemini Live error", e);
                    updateStatus("Error: " + e.getMessage());
                    stopAudioCapture();
                });
            }
            
            @Override
            public void onAudioChunk(byte[] audioData) {
                // Play audio response from Gemini
                Log.d(TAG, "onAudioChunk called with " + (audioData != null ? audioData.length : 0) + " bytes");
                // Pause sending audio to Gemini during playback to prevent feedback loop
                if (!isPlayingAudio) {
                    isPlayingAudio = true;
                    pauseSendingAudio = true; // Don't send mic audio while playing back
                    Log.d(TAG, "Paused sending audio to Gemini during playback");
                }
                // Cancel any pending resume since we're still receiving audio
                if (unmuteRunnable != null) {
                    unmuteHandler.removeCallbacks(unmuteRunnable);
                    unmuteRunnable = null;
                }
                playAudioChunk(audioData);
            }

            @Override
            public void onTextResponse(String text) {
                mainHandler.post(() -> {
                    Log.d(TAG, "Gemini text: " + text);
                    updateStatus("Gemini: " + text);
                });
            }

            @Override
            public void onFunctionCall(String functionName, JSONObject args, String callId) {
                Log.d(TAG, "=== FUNCTION CALL RECEIVED ===");
                Log.d(TAG, "Function: " + functionName);
                Log.d(TAG, "Args: " + (args != null ? args.toString() : "null"));
                Log.d(TAG, "Call ID: " + callId);
                
                // #region agent log
                try {
                    android.util.Log.d("SessionActivity", "FUNCTION_CALL_RECEIVED: " + functionName + " id:" + callId + " args: " + (args != null ? args.toString() : "null"));
                } catch (Exception e) {}
                // #endregion
                
                // Safety check: ensure we're still valid
                if (isFinishing() || isDestroyed()) {
                    Log.w(TAG, "Activity finishing, ignoring function call");
                    return;
                }
                
                // Store the function name and call ID so we can send the response back
                lastFunctionCallName = functionName;
                lastFunctionCallId = callId;
                
                mainHandler.post(() -> {
                    handleGeminiFunctionCall(functionName, args, callId);
                });
            }
        }, screenState);
    }

    private void startAudioCapture() {
        if (isRecording || isMuted) return;
        
        // Check permission first
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No microphone permission");
            updateStatus("Microphone permission required");
            return;
        }
        
        try {
            int bufferSize = AudioRecord.getMinBufferSize(INPUT_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            Log.d(TAG, "Calculated buffer size: " + bufferSize + ", sampleRate: " + INPUT_SAMPLE_RATE);
            
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                Log.d(TAG, "Invalid buffer size: " + bufferSize);
                updateStatus("Invalid audio parameters");
                return;
            }
            
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                INPUT_SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            );
            
            int state = audioRecord.getState();
            Log.d(TAG, "AudioRecord state: " + state + ", initialized: " + (state == AudioRecord.STATE_INITIALIZED));
            
            if (state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed, state: " + state);
                updateStatus("Failed to initialize microphone (state: " + state + ")");
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                return;
            }
            
            audioRecord.startRecording();
            isRecording = true;
            Log.d(TAG, "Recording started successfully");
            
            // Start recording thread
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[bufferSize];
                int chunkCount = 0;
                while (isRecording && !isMuted) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    Log.d(TAG, "Audio read: " + bytesRead + " bytes, chunkCount: " + chunkCount + ", wsConnected: " + geminiLiveClient.isConnected());
                    
                    if (bytesRead > 0) {
                        if (geminiLiveClient.isConnected() && !pauseSendingAudio && !isMuted) {
                            // Send audio chunk to Gemini Live (only if not paused and not manually muted)
                            byte[] audioChunk = new byte[bytesRead];
                            System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);
                            geminiLiveClient.sendAudioChunk(audioChunk);
                            chunkCount++;
                            Log.d(TAG, "Audio chunk sent to Gemini: chunkSize=" + bytesRead + ", totalChunks=" + chunkCount);
                        } else {
                            // Log why we're not sending audio
                            if (!geminiLiveClient.isConnected()) {
                                Log.d(TAG, "WebSocket not connected, cannot send audio: bytesRead=" + bytesRead);
                            } else if (pauseSendingAudio) {
                                Log.d(TAG, "Paused sending audio during playback: bytesRead=" + bytesRead);
                            } else if (isMuted) {
                                Log.d(TAG, "Manually muted, not sending audio: bytesRead=" + bytesRead);
                            }
                        }
                    } else if (bytesRead < 0) {
                        Log.d(TAG, "Audio read error: " + bytesRead);
                        break;
                    }
                }
            });
            recordingThread.start();
            
            updateStatus("Recording...");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio capture", e);
            updateStatus("Error: " + e.getMessage());
        }
    }

    private void stopAudioCapture() {
        isRecording = false;
        
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                }
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio capture", e);
            }
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error joining recording thread", e);
            }
            recordingThread = null;
        }
    }

    private void playAudioChunk(byte[] audioData) {
        Log.d(TAG, "playAudioChunk: size=" + (audioData != null ? audioData.length : 0) + ", manuallyMuted=" + isMuted);
        // Only skip playback if manually muted by user, not during auto-pause
        if (isMuted || audioData == null || audioData.length == 0) return;
        
        // Skip very small chunks (likely metadata/headers, not actual audio)
        if (audioData.length < 100) {
            Log.d(TAG, "Skipping small audio chunk (likely metadata): " + audioData.length + " bytes");
            return;
        }
        
        // Ensure audio data length is even (16-bit PCM = 2 bytes per sample)
        if (audioData.length % 2 != 0) {
            Log.w(TAG, "Audio data length is odd, truncating last byte");
            byte[] trimmed = new byte[audioData.length - 1];
            System.arraycopy(audioData, 0, trimmed, 0, trimmed.length);
            audioData = trimmed;
        }
        
        try {
            if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                // Gemini Live outputs audio at 24kHz, 16-bit PCM, mono, little-endian
                int outputBufferSize = AudioTrack.getMinBufferSize(
                    OUTPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AUDIO_FORMAT
                );
                
                if (outputBufferSize == AudioTrack.ERROR_BAD_VALUE || outputBufferSize == AudioTrack.ERROR) {
                    Log.e(TAG, "Invalid buffer size for AudioTrack");
                    return;
                }
                
                // Use larger buffer for smoother playback
                outputBufferSize = Math.max(outputBufferSize * 4, 8192);
                
                audioTrack = new AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    OUTPUT_SAMPLE_RATE, // 24kHz for Gemini Live output
                    AudioFormat.CHANNEL_OUT_MONO,
                    AUDIO_FORMAT, // ENCODING_PCM_16BIT (little-endian by default on Android)
                    outputBufferSize,
                    AudioTrack.MODE_STREAM
                );
                
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack initialization failed, state: " + audioTrack.getState());
                    audioTrack.release();
                    audioTrack = null;
                    return;
                }
                
                audioTrack.play();
                Log.d(TAG, "AudioTrack started, play state: " + audioTrack.getPlayState());
            }
            
            // Check AudioTrack state before writing
            if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                Log.w(TAG, "AudioTrack not playing, state: " + audioTrack.getPlayState() + ", restarting");
                audioTrack.play();
            }
            
            // Inspect first few samples to check format
            if (audioData.length >= 20) {
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < Math.min(20, audioData.length); i++) {
                    hex.append(String.format("%02X ", audioData[i] & 0xFF));
                }
                Log.d(TAG, "First 20 bytes (hex): " + hex.toString());
                
                // Check if data looks like PCM (should have variation)
                boolean allZeros = true;
                boolean allSame = true;
                byte first = audioData[0];
                for (int i = 0; i < Math.min(100, audioData.length); i++) {
                    if (audioData[i] != 0) allZeros = false;
                    if (audioData[i] != first) allSame = false;
                }
                Log.d(TAG, "Audio data check - allZeros: " + allZeros + ", allSame: " + allSame);
            }
            
            // Write audio data directly (AudioTrack handles buffering internally)
            int written = audioTrack.write(audioData, 0, audioData.length);
            if (written < 0) {
                Log.e(TAG, "Error writing audio data, code: " + written);
            } else if (written != audioData.length) {
                Log.w(TAG, "Partial write: " + written + " of " + audioData.length + " bytes");
            } else {
                Log.d(TAG, "Successfully wrote " + written + " bytes to AudioTrack");
            }
            
            // Schedule resume sending audio after playback finishes (estimate: bytes / (sampleRate * bytesPerSample * channels))
            // For 24kHz, 16-bit mono: bytes / (24000 * 2 * 1) = bytes / 48000 seconds
            // Add buffer for safety and to allow for silence detection
            long playbackDurationMs = (audioData.length * 1000L) / (OUTPUT_SAMPLE_RATE * 2) + 500; // +500ms buffer
            unmuteRunnable = () -> {
                if (isPlayingAudio) {
                    isPlayingAudio = false;
                    pauseSendingAudio = false; // Resume sending audio to Gemini
                    Log.d(TAG, "Resumed sending audio to Gemini after playback");
                    unmuteRunnable = null;
                }
            };
            unmuteHandler.postDelayed(unmuteRunnable, playbackDurationMs);
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio", e);
        }
    }

    private void handleGeminiFunctionCall(String functionName, JSONObject args, String callId) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.handleGeminiFunctionCall:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Handling function call\",\"data\":{\"functionName\":\"" + 
                functionName + "\",\"callId\":\"" + (callId != null ? callId : "null") + "\",\"agentRegistryNull\":" + (agentRegistry == null) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error logging function call entry", e);
        }
        // #endregion
        
        if (functionName == null || functionName.isEmpty()) {
            Log.w(TAG, "Empty function name received");
            updateStatus("Invalid function call");
            return;
        }
        
        if (args == null) {
            args = new JSONObject();
        }
        
        Log.d(TAG, "Gemini called: " + functionName + " with args: " + args);
        
        // Use AgentRegistry to route function calls
        if (agentRegistry != null) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.handleGeminiFunctionCall:ROUTING " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Routing to AgentRegistry\",\"data\":{\"functionName\":\"" + 
                    functionName + "\",\"agentCount\":" + agentRegistry.getAgentCount() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            boolean handled = agentRegistry.handleFunctionCall(functionName, args, callId);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.handleGeminiFunctionCall:ROUTED " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Routing result\",\"data\":{\"functionName\":\"" + 
                    functionName + "\",\"handled\":" + handled + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            if (!handled) {
                updateStatus("Unknown function: " + functionName);
                sendFunctionResultToGemini(functionName, "Unknown function: " + functionName, callId);
            }
            return;
        }
        
        // Fallback to old switch statement if no agent registry
        try {
            switch (functionName) {
                case "gui_click":
                    try {
                        if (accessibilityService == null) {
                            updateStatus("Accessibility service not available");
                            sendFunctionResultToGemini("gui_click", "Accessibility service not available", callId);
                            return;
                        }
                        String nodeId = args.optString("node_id", "");
                        if (!nodeId.isEmpty()) {
                            // Run on background thread to avoid blocking
                            executorService.execute(() -> {
                                try {
                                    boolean clicked = accessibilityService.performClick(nodeId);
                                    mainHandler.post(() -> {
                                        String result = clicked ? "Successfully clicked: " + nodeId : "Could not click: " + nodeId;
                                        updateStatus(result);
                                        sendFunctionResultToGemini("gui_click", result, callId);
                                        updateScreenState();
                                    });
                                } catch (Exception e) {
                                    Log.e(TAG, "Error performing click", e);
                                    mainHandler.post(() -> {
                                        String result = "Error clicking: " + e.getMessage();
                                        updateStatus(result);
                                        sendFunctionResultToGemini("gui_click", result, callId);
                                    });
                                }
                            });
                        } else {
                            String result = "No node ID provided";
                            updateStatus(result);
                            sendFunctionResultToGemini("gui_click", result, callId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in gui_click handler", e);
                        String result = "Error: " + e.getMessage();
                        updateStatus(result);
                        sendFunctionResultToGemini("gui_click", result, callId);
                    }
                    break;
                    
                case "gui_type":
                    try {
                        if (accessibilityService == null) {
                            updateStatus("Accessibility service not available");
                            sendFunctionResultToGemini("gui_type", "Accessibility service not available", callId);
                            return;
                        }
                        String typeNodeId = args.optString("node_id", "");
                        String text = args.optString("text", "");
                        if (!typeNodeId.isEmpty() && !text.isEmpty()) {
                            executorService.execute(() -> {
                                try {
                                    boolean typed = accessibilityService.performInput(typeNodeId, text);
                                    mainHandler.post(() -> {
                                        String result = typed ? "Successfully typed: " + text : "Could not type";
                                        updateStatus(result);
                                        sendFunctionResultToGemini("gui_type", result, callId);
                                        updateScreenState();
                                    });
                                } catch (Exception e) {
                                    Log.e(TAG, "Error performing input", e);
                                    mainHandler.post(() -> {
                                        String result = "Error typing: " + e.getMessage();
                                        updateStatus(result);
                                        sendFunctionResultToGemini("gui_type", result, callId);
                                    });
                                }
                            });
                        } else {
                            String result = "Missing node ID or text";
                            updateStatus(result);
                            sendFunctionResultToGemini("gui_type", result, callId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in gui_type handler", e);
                        String result = "Error: " + e.getMessage();
                        updateStatus(result);
                        sendFunctionResultToGemini("gui_type", result, callId);
                    }
                    break;
                    
                case "gui_scroll":
                    try {
                        if (accessibilityService == null) {
                            updateStatus("Accessibility service not available");
                            sendFunctionResultToGemini("gui_scroll", "Accessibility service not available", callId);
                            return;
                        }
                        String direction = args.optString("direction", "DOWN");
                        executorService.execute(() -> {
                            try {
                                boolean scrolled = accessibilityService.performScroll(direction);
                                mainHandler.post(() -> {
                                    String result = scrolled ? "Successfully scrolled " + direction : "Could not scroll";
                                    updateStatus(result);
                                    sendFunctionResultToGemini("gui_scroll", result, callId);
                                    updateScreenState();
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error performing scroll", e);
                                mainHandler.post(() -> {
                                    String result = "Error scrolling: " + e.getMessage();
                                    updateStatus(result);
                                    sendFunctionResultToGemini("gui_scroll", result, callId);
                                });
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in gui_scroll handler", e);
                        String result = "Error: " + e.getMessage();
                        updateStatus(result);
                        sendFunctionResultToGemini("gui_scroll", result, callId);
                    }
                    break;
                    
                case "memory_save":
                    String key = args.optString("key", "");
                    String value = args.optString("value", "");
                    String type = args.optString("type", "GENERAL");
                    long triggerTime = args.optLong("trigger_time", 0);
                    
                    if (!key.isEmpty() && !value.isEmpty()) {
                        executorService.execute(() -> {
                            try {
                                MemoryItem item = new MemoryItem(
                                    type,
                                    value,
                                    new JSONObject().put("key", key).put("value", value).toString(),
                                    System.currentTimeMillis(),
                                    triggerTime
                                );
                                database.memoryDao().insert(item);
                                mainHandler.post(() -> {
                                    String result = "Saved to memory: " + key;
                                    updateStatus(result);
                                    sendFunctionResultToGemini("memory_save", result, callId);
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving memory", e);
                                mainHandler.post(() -> {
                                    String result = "Error saving memory: " + e.getMessage();
                                    updateStatus(result);
                                    sendFunctionResultToGemini("memory_save", result, callId);
                                });
                            }
                        });
                    } else {
                        String result = "Missing key or value for memory save";
                        updateStatus(result);
                        sendFunctionResultToGemini("memory_save", result, callId);
                    }
                    break;
                    
                case "memory_recall":
                    String recallType = args.optString("type", "");
                    executorService.execute(() -> {
                        try {
                            MemoryItem item = null;
                            if (!recallType.isEmpty()) {
                                item = database.memoryDao().getLastItemByType(recallType);
                            }
                            final MemoryItem finalItem = item;
                            mainHandler.post(() -> {
                                if (finalItem != null) {
                                    String result = "I recall: " + finalItem.rawText;
                                    updateStatus("Recalled: " + finalItem.rawText);
                                    // Send recall result back to Gemini as function response
                                    sendFunctionResultToGemini("memory_recall", result, callId);
                                } else {
                                    String result = "No memory found for type: " + recallType;
                                    updateStatus(result);
                                    sendFunctionResultToGemini("memory_recall", result, callId);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error recalling memory", e);
                            mainHandler.post(() -> updateStatus("Error recalling memory"));
                        }
                    });
                    break;
                    
                case "google_search":
                    String query = args.optString("query", "");
                    if (!query.isEmpty()) {
                        executorService.execute(() -> {
                            try {
                                String result = performGoogleSearch(query);
                                mainHandler.post(() -> {
                                    updateStatus("Search result: " + result);
                                    sendFunctionResultToGemini("google_search", result, callId);
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error performing search", e);
                                mainHandler.post(() -> updateStatus("Error searching"));
                            }
                        });
                    }
                    break;
                    
                case "maps_navigation":
                    String destination = args.optString("destination", "");
                    if (!destination.isEmpty()) {
                        executorService.execute(() -> {
                            try {
                                // #region agent log
                                try {
                                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.maps_navigation:START " + 
                                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Getting directions\",\"data\":{\"destination\":\"" + 
                                        destination + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                    fw.close();
                                } catch (Exception ex) {}
                                // #endregion
                                
                                // Get current location first
                                String origin = locationService.getLocationString();
                                if (origin == null) {
                                    origin = "37.7749,-122.4194"; // Fallback
                                }
                                
                                JSONObject directions = mapsIntegration.getWalkingDirections(destination, origin);
                                mainHandler.post(() -> {
                                    if (directions != null) {
                                        String result = "I've started navigation to " + destination;
                                        updateStatus("Navigation to " + destination + " started");
                                        sendFunctionResultToGemini("maps_navigation", result, callId);
                                    } else {
                                        String result = "I couldn't get directions to " + destination + ". Please check your internet connection and try again.";
                                        updateStatus("Could not get directions");
                                        sendFunctionResultToGemini("maps_navigation", result, callId);
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error getting directions", e);
                                // #region agent log
                                try {
                                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.maps_navigation:ERROR " + 
                                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Error getting directions\",\"data\":{\"error\":\"" + 
                                        e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                    fw.close();
                                } catch (Exception ex) {}
                                // #endregion
                                mainHandler.post(() -> updateStatus("Error getting directions: " + e.getMessage()));
                            }
                        });
                    }
                    break;
                    
                case "get_location":
                    executorService.execute(() -> {
                        try {
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.get_location:START " + 
                                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Getting location\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                fw.close();
                            } catch (Exception ex) {}
                            // #endregion
                            
                            Location loc = locationService.getCurrentLocation();
                            mainHandler.post(() -> {
                                if (loc != null) {
                                    String locationStr = "Your location is approximately " + loc.getLatitude() + ", " + loc.getLongitude();
                                    updateStatus(locationStr);
                                    sendFunctionResultToGemini("get_location", locationStr, callId);
                                } else {
                                    String result = "I couldn't determine your location. Please enable location services and grant location permission.";
                                    updateStatus("Could not get location");
                                    sendFunctionResultToGemini("get_location", result, callId);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting location", e);
                            mainHandler.post(() -> updateStatus("Error getting location"));
                        }
                    });
                    break;
                    
                case "weather":
                    String location = args.optString("location", "");
                    executorService.execute(() -> {
                        try {
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.weather:START " + 
                                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H1\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Getting weather via google_search\",\"data\":{\"location\":\"" + 
                                    location + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                fw.close();
                            } catch (Exception ex) {}
                            // #endregion
                            
                            // Use google_search to get weather information
                            String searchQuery = "weather " + location;
                            String result = performGoogleSearch(searchQuery);
                            mainHandler.post(() -> {
                                updateStatus("Weather: " + result);
                                sendFunctionResultToGemini("weather", result, callId);
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting weather", e);
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.weather:ERROR " + 
                                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H1\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Error getting weather\",\"data\":{\"error\":\"" + 
                                    e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                fw.close();
                            } catch (Exception ex) {}
                            // #endregion
                            mainHandler.post(() -> {
                                String result = "I couldn't get the weather information. Please try again.";
                                updateStatus("Error getting weather: " + e.getMessage());
                                sendFunctionResultToGemini("weather", result, callId);
                            });
                        }
                    });
                    break;
                    
                case "gui_open_app":
                    String appName = args.optString("app_name", "");
                    if (!appName.isEmpty()) {
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.gui_open_app:START " + 
                                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Opening app\",\"data\":{\"appName\":\"" + 
                                appName + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                            fw.close();
                        } catch (Exception ex) {}
                        // #endregion
                        
                        boolean opened = openApp(appName);
                        if (opened) {
                            String result = "I've opened the " + appName + " app.";
                            updateStatus("Opening " + appName);
                            sendFunctionResultToGemini("gui_open_app", result, callId);
                            // Wait a bit then update screen state
                            mainHandler.postDelayed(() -> updateScreenState(), 1000);
                        } else {
                            String result = "I couldn't find or open the " + appName + " app. Please make sure it's installed.";
                            updateStatus("Could not open " + appName);
                            sendFunctionResultToGemini("gui_open_app", result, callId);
                        }
                    }
                    break;
                    
                default:
                    updateStatus("Unknown function: " + functionName);
            }
        } catch (Throwable t) {
            // Catch ALL exceptions including runtime exceptions, errors, etc.
            Log.e(TAG, "Error handling function call", t);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                String stackTrace = android.util.Log.getStackTraceString(t);
                String shortStackTrace = stackTrace.length() > 1000 ? stackTrace.substring(0, 1000) + "..." : stackTrace;
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.handleGeminiFunctionCall:EXCEPTION " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H5\",\"location\":\"SessionActivity.java:handleGeminiFunctionCall\",\"message\":\"Exception in function handler\",\"data\":{\"functionName\":\"" + 
                    functionName + "\",\"error\":\"" + (t.getMessage() != null ? t.getMessage().replace("\"", "\\\"") : "null") + "\",\"errorClass\":\"" + t.getClass().getName() + "\",\"stackTrace\":\"" + shortStackTrace.replace("\"", "\\\"").replace("\n", "\\n") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {
                Log.e(TAG, "Error logging exception", ex);
            }
            // #endregion
            try {
                String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                updateStatus("Error: " + errorMsg);
                // Don't send error back to Gemini if it might cause a loop
                // sendTextToGemini("I encountered an error: " + errorMsg);
            } catch (Exception e) {
                Log.e(TAG, "Error updating status after exception", e);
            }
        }
    }
    
    private void updateScreenState() {
        // Update screen state and send to Gemini after a short delay
        mainHandler.postDelayed(() -> {
            if (accessibilityService != null && geminiLiveClient != null && geminiLiveClient.isConnected()) {
                String newScreenState = accessibilityService.getScreenState();
                // Note: Gemini Live doesn't have a direct way to update screen state mid-session
                // This would need to be handled in the next user turn or via a different mechanism
                Log.d(TAG, "Screen state updated");
            }
        }, 500); // Small delay to let UI update
    }
    
    private String performGoogleSearch(String query) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            
            // If query is about weather, use weather API
            if (query.toLowerCase().contains("weather")) {
                String location = query.toLowerCase()
                    .replace("weather", "")
                    .replace("in", "")
                    .replace("what's", "")
                    .replace("the", "")
                    .replace("?", "")
                    .trim();
                
                if (location.isEmpty()) {
                    location = "current location";
                }
                
                String url = "https://wttr.in/" + java.net.URLEncoder.encode(location, "UTF-8") + "?format=j1";
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "curl/7.64.1")
                    .build();
                
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    
                    JSONObject current = json.getJSONArray("current_condition").getJSONObject(0);
                    String temp = current.getString("temp_F") + "°F (" + current.getString("temp_C") + "°C)";
                    String condition = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value");
                    String humidity = current.getString("humidity") + "%";
                    String windSpeed = current.getString("windspeedMiles") + " mph";
                    
                    return String.format("Weather in %s:\nTemperature: %s\nCondition: %s\nHumidity: %s\nWind: %s",
                        location, temp, condition, humidity, windSpeed);
                }
            }
            
            // For general searches, use Gemini API to answer
            String apiKey = com.nexhacks.tapmate.utils.Config.getGeminiApiKey();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;
            
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", "Answer this question concisely in 2-3 sentences: " + query);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);
            
            okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")))
                .build();
            
            okhttp3.Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray candidates = json.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);
                    JSONObject contentObj = candidate.getJSONObject("content");
                    JSONArray partsArray = contentObj.getJSONArray("parts");
                    if (partsArray.length() > 0) {
                        return partsArray.getJSONObject(0).getString("text");
                    }
                }
            }
            
            return "I couldn't find information about: " + query;
            
        } catch (Exception e) {
            Log.e(TAG, "Google Search error", e);
            return "Search error: " + e.getMessage();
        }
    }
    
    private boolean openApp(String appName) {
        try {
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(getAppPackageName(appName));
            if (launchIntent != null) {
                startActivity(launchIntent);
                return true;
            }
            
            // Fallback: Try to open via accessibility service by going to home and searching
            // This is a workaround - ideally we'd have the package name
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error opening app", e);
            return false;
        }
    }
    
    private String getAppPackageName(String appName) {
        // Common app package names
        String lowerName = appName.toLowerCase();
        if (lowerName.contains("messenger") || lowerName.contains("facebook messenger")) {
            return "com.facebook.orca";
        } else if (lowerName.contains("whatsapp")) {
            return "com.whatsapp";
        } else if (lowerName.contains("settings")) {
            return "com.android.settings";
        } else if (lowerName.contains("chrome")) {
            return "com.android.chrome";
        } else if (lowerName.contains("gmail")) {
            return "com.google.android.gm";
        } else if (lowerName.contains("maps")) {
            return "com.google.android.apps.maps";
        } else if (lowerName.contains("camera")) {
            return "com.android.camera2";
        }
        // Try to find by querying installed packages
        PackageManager pm = getPackageManager();
        java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (android.content.pm.ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase();
            if (label.contains(lowerName) || lowerName.contains(label)) {
                return app.packageName;
            }
        }
        return null;
    }
    
    private void sendTextToGemini(String text) {
        // Send text response back to Gemini Live as a function response
        // Note: This is a simplified version - in a real implementation, we'd track which function was called
        // For now, we'll send it as a generic response
        Log.d(TAG, "Sending to Gemini: " + text);
        try {
            JSONObject response = new JSONObject();
            response.put("result", text);
            // We need to track the last function call to send the response properly
            // For now, send as a generic response - this will be improved
            geminiLiveClient.sendFunctionResponse("generic_response", response, lastFunctionCallId);
        } catch (Exception e) {
            Log.e(TAG, "Error sending text to Gemini", e);
        }
    }
    
    private String lastFunctionCallName = null;
    private String lastFunctionCallId = null;
    
    private void sendFunctionResultToGemini(String functionName, String result, String callId) {
        Log.d(TAG, "Sending function result to Gemini: " + functionName + " id:" + callId + " -> " + result);
        try {
            JSONObject response = new JSONObject();
            response.put("result", result);
            geminiLiveClient.sendFunctionResponse(functionName, response, callId);
            // #region agent log
            try {
                android.util.Log.d("SessionActivity", "FUNCTION_RESULT_SENT: " + functionName + " id:" + callId + " -> " + result);
            } catch (Exception e) {}
            // #endregion
        } catch (Exception e) {
            Log.e(TAG, "Error sending function result to Gemini", e);
        }
    }

    private void updateStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
        Log.d(TAG, "Status: " + status);
    }

    private View createSessionLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF1A1A1A);

        // Status text at top
        statusText = new TextView(this);
        statusText.setText("Starting Gemini Live session...");
        statusText.setTextSize(18);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setPadding(20, 40, 20, 20);
        statusText.setGravity(android.view.Gravity.CENTER);
        
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusText.setLayoutParams(statusParams);

        // Stop Button (top half)
        stopButton = new Button(this);
        stopButton.setText("STOP");
        stopButton.setTextSize(40);
        stopButton.setBackgroundColor(0xFFF44336);
        stopButton.setAllCaps(false);
        
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        );
        stopButton.setLayoutParams(stopParams);
        stopButton.setOnClickListener(v -> stopSession());

        // Mute Button (bottom half)
        muteButton = new Button(this);
        muteButton.setText("MUTE");
        muteButton.setTextSize(40);
        muteButton.setBackgroundColor(0xFF2196F3);
        muteButton.setAllCaps(false);
        
        LinearLayout.LayoutParams muteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        );
        muteButton.setLayoutParams(muteParams);
        muteButton.setOnClickListener(v -> toggleMute());

        layout.addView(statusText);
        layout.addView(stopButton);
        layout.addView(muteButton);

        return layout;
    }

    private void toggleMute() {
        boolean wasMuted = isMuted;
        isMuted = !isMuted;
        muteButton.setText(isMuted ? "UNMUTE" : "MUTE");
        muteButton.setBackgroundColor(isMuted ? 0xFF9E9E9E : 0xFF2196F3);
        
        if (isMuted) {
            stopAudioCapture();
            if (audioTrack != null) {
                audioTrack.pause();
                audioTrack.flush();
            }
            updateStatus("Muted");
            isPlayingAudio = false; // Reset playback flag when manually muted
        } else {
            updateStatus("Unmuted - Listening...");
            startAudioCapture();
            if (audioTrack != null) {
                audioTrack.play();
            }
        }
    }

    private void stopSession() {
        isMuted = true;
        stopAudioCapture();
        
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        
        if (geminiLiveClient != null) {
            geminiLiveClient.stopSession();
        }
        
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSession();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
