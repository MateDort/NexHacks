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
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SessionActivity extends Activity {

    private static final String TAG = "SessionActivity";
    private static final int INPUT_SAMPLE_RATE = 16000; // 16kHz for input (Gemini Live input)
    private static final int OUTPUT_SAMPLE_RATE = 24000; // 24kHz for output (Gemini Live output)
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int INPUT_BUFFER_SIZE = AudioRecord.getMinBufferSize(INPUT_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1001;
    
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load config
        com.nexhacks.tapmate.utils.Config.loadEnv(this);
        
        // Initialize components
        mainHandler = new Handler(Looper.getMainLooper());
        accessibilityService = TapMateAccessibilityService.getInstance();
        geminiLiveClient = new GeminiLiveClient();
        
        setContentView(createSessionLayout());
        
        // Request microphone permission
        requestMicrophonePermission();
    }
    
    private void requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.requestMicrophonePermission:PERMISSION_NOT_GRANTED " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"G\",\"location\":\"SessionActivity.java:requestMicrophonePermission\",\"message\":\"Microphone permission not granted, requesting\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            ActivityCompat.requestPermissions(this, 
                new String[]{android.Manifest.permission.RECORD_AUDIO}, 
                PERMISSION_REQUEST_RECORD_AUDIO);
        } else {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.requestMicrophonePermission:PERMISSION_GRANTED " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"G\",\"location\":\"SessionActivity.java:requestMicrophonePermission\",\"message\":\"Microphone permission already granted\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            // Permission already granted, start session
            startGeminiLiveSession();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.onRequestPermissionsResult:PERMISSION_RESULT " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"G\",\"location\":\"SessionActivity.java:onRequestPermissionsResult\",\"message\":\"Permission request result\",\"data\":{\"granted\":" + granted + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
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
            public void onFunctionCall(String functionName, JSONObject args) {
                mainHandler.post(() -> {
                    handleGeminiFunctionCall(functionName, args);
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    Log.e(TAG, "Gemini Live error", e);
                    updateStatus("Error: " + e.getMessage());
                });
            }
        }, accessibilityService != null ? accessibilityService.getScreenState() : "[]");
    }

    private void startAudioCapture() {
        if (isRecording || isMuted) return;
        
        // Check permission first
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.startAudioCapture:NO_PERMISSION " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"H\",\"location\":\"SessionActivity.java:startAudioCapture\",\"message\":\"No microphone permission\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            updateStatus("Microphone permission required");
            return;
        }
        
        try {
            int bufferSize = AudioRecord.getMinBufferSize(INPUT_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.startAudioCapture:BUFFER_SIZE " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"I\",\"location\":\"SessionActivity.java:startAudioCapture\",\"message\":\"Calculated buffer size\",\"data\":{\"bufferSize\":" + bufferSize + ",\"sampleRate\":" + INPUT_SAMPLE_RATE + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.startAudioCapture:INVALID_BUFFER " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"I\",\"location\":\"SessionActivity.java:startAudioCapture\",\"message\":\"Invalid buffer size\",\"data\":{\"bufferSize\":" + bufferSize + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
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
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.startAudioCapture:AUDIO_STATE " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"J\",\"location\":\"SessionActivity.java:startAudioCapture\",\"message\":\"AudioRecord state\",\"data\":{\"state\":" + state + ",\"initialized\":" + (state == AudioRecord.STATE_INITIALIZED) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
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
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.startAudioCapture:RECORDING_STARTED " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"K\",\"location\":\"SessionActivity.java:startAudioCapture\",\"message\":\"Recording started successfully\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            // Start recording thread
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[bufferSize];
                int chunkCount = 0;
                while (isRecording && !isMuted) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.recordingThread:AUDIO_READ " + 
                            "{\"sessionId\":\"debug-session\",\"runId\":\"run6\",\"hypothesisId\":\"N\",\"location\":\"SessionActivity.java:recordingThread\",\"message\":\"Audio read\",\"data\":{\"bytesRead\":" + 
                            bytesRead + ",\"chunkCount\":" + chunkCount + ",\"wsConnected\":" + geminiLiveClient.isConnected() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                        fw.close();
                    } catch (Exception ex) {}
                    // #endregion
                    
                    if (bytesRead > 0) {
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                            // Check if audio data has non-zero bytes
                            boolean hasNonZero = false;
                            for (int i = 0; i < Math.min(buffer.length, 100); i++) {
                                if (buffer[i] != 0) {
                                    hasNonZero = true;
                                    break;
                                }
                            }
                            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.recordingThread:AUDIO_DATA " + 
                                "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H2\",\"location\":\"SessionActivity.java:recordingThread\",\"message\":\"Audio data read\",\"data\":{\"bytesRead\":" + 
                                bytesRead + ",\"hasNonZeroBytes\":" + hasNonZero + ",\"wsConnected\":" + geminiLiveClient.isConnected() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                            fw.close();
                        } catch (Exception ex) {}
                        // #endregion
                        if (geminiLiveClient.isConnected() && !pauseSendingAudio && !isMuted) {
                            // Send audio chunk to Gemini Live (only if not paused and not manually muted)
                            byte[] audioChunk = new byte[bytesRead];
                            System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);
                            geminiLiveClient.sendAudioChunk(audioChunk);
                            chunkCount++;
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.recordingThread:CHUNK_SENT " + 
                                    "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H4\",\"location\":\"SessionActivity.java:recordingThread\",\"message\":\"Audio chunk sent to Gemini\",\"data\":{\"chunkSize\":" + 
                                    bytesRead + ",\"totalChunks\":" + chunkCount + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                fw.close();
                            } catch (Exception ex) {}
                            // #endregion
                        } else {
                            // Log why we're not sending audio
                            if (!geminiLiveClient.isConnected()) {
                                Log.d(TAG, "WebSocket not connected, cannot send audio: bytesRead=" + bytesRead);
                            } else if (pauseSendingAudio) {
                                Log.d(TAG, "Paused sending audio during playback: bytesRead=" + bytesRead);
                            } else if (isMuted) {
                                Log.d(TAG, "Manually muted, not sending audio: bytesRead=" + bytesRead);
                            }
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.recordingThread:WS_NOT_CONNECTED " + 
                                    "{\"sessionId\":\"debug-session\",\"runId\":\"run6\",\"hypothesisId\":\"P\",\"location\":\"SessionActivity.java:recordingThread\",\"message\":\"WebSocket not connected, cannot send audio\",\"data\":{\"bytesRead\":" + 
                                    bytesRead + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                fw.close();
                            } catch (Exception ex) {}
                            // #endregion
                        }
                    } else if (bytesRead < 0) {
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.recordingThread:READ_ERROR " + 
                                "{\"sessionId\":\"debug-session\",\"runId\":\"run6\",\"hypothesisId\":\"L\",\"location\":\"SessionActivity.java:recordingThread\",\"message\":\"Audio read error\",\"data\":{\"bytesRead\":" + bytesRead + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                            fw.close();
                        } catch (Exception ex) {}
                        // #endregion
                        break;
                    }
                }
            });
            recordingThread.start();
            
            updateStatus("Recording...");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio capture", e);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.startAudioCapture:EXCEPTION " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run5\",\"hypothesisId\":\"M\",\"location\":\"SessionActivity.java:startAudioCapture\",\"message\":\"Exception starting audio\",\"data\":{\"error\":\"" + e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
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
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.playAudioChunk:AUDIO_TRACK_INIT " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run14\",\"hypothesisId\":\"AUDIO_FIX\",\"location\":\"SessionActivity.java:playAudioChunk\",\"message\":\"AudioTrack initialized for playback\",\"data\":{\"sampleRate\":" + 
                        OUTPUT_SAMPLE_RATE + ",\"bufferSize\":" + outputBufferSize + ",\"format\":\"PCM_16BIT_MONO\",\"playState\":" + audioTrack.getPlayState() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception ex) {}
                // #endregion
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
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.playAudioChunk:PLAYING " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run14\",\"hypothesisId\":\"AUDIO_FIX\",\"location\":\"SessionActivity.java:playAudioChunk\",\"message\":\"Playing audio chunk\",\"data\":{\"audioSize\":" + 
                    audioData.length + ",\"bytesWritten\":" + written + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio", e);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SessionActivity.playAudioChunk:ERROR " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run13\",\"hypothesisId\":\"AUDIO_FIX\",\"location\":\"SessionActivity.java:playAudioChunk\",\"message\":\"Error playing audio\",\"data\":{\"error\":\"" + 
                    e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
        }
    }

    private void handleGeminiFunctionCall(String functionName, JSONObject args) {
        Log.d(TAG, "Gemini called: " + functionName + " with args: " + args);
        
        if (accessibilityService == null) {
            updateStatus("Accessibility service not available");
            return;
        }
        
        try {
            switch (functionName) {
                case "gui_click":
                    String nodeId = args.optString("node_id", "");
                    if (!nodeId.isEmpty()) {
                        boolean clicked = accessibilityService.performClick(nodeId);
                        updateStatus(clicked ? "Clicked: " + nodeId : "Could not click: " + nodeId);
                    }
                    break;
                    
                case "gui_type":
                    String typeNodeId = args.optString("node_id", "");
                    String text = args.optString("text", "");
                    if (!typeNodeId.isEmpty() && !text.isEmpty()) {
                        boolean typed = accessibilityService.performInput(typeNodeId, text);
                        updateStatus(typed ? "Typed: " + text : "Could not type");
                    }
                    break;
                    
                case "gui_scroll":
                    String direction = args.optString("direction", "DOWN");
                    boolean scrolled = accessibilityService.performScroll(direction);
                    updateStatus(scrolled ? "Scrolled " + direction : "Could not scroll");
                    break;
                    
                case "memory_save":
                    String key = args.optString("key", "");
                    String value = args.optString("value", "");
                    // TODO: Save to database
                    updateStatus("Saved: " + key);
                    break;
                    
                default:
                    updateStatus("Unknown function: " + functionName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling function call", e);
            updateStatus("Error executing function");
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
    }
}
