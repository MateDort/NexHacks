package com.tapmate.aiagent.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tapmate.aiagent.R;
import com.tapmate.aiagent.agents.ConfigAgent;
import com.tapmate.aiagent.agents.DatabaseAgent;
import com.tapmate.aiagent.agents.GUIAgent;
import com.tapmate.aiagent.core.TapMateOrchestrator;

/**
 * MainActivity - Full-screen effective color design for visually impaired users
 *
 * IDLE state: Full-screen yellow + purple triangle (start)
 * ACTIVE state: Top half yellow + purple pause lines (mute), bottom half red + purple rectangle (stop)
 * Swipe right: Go to settings
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private TapMateOrchestrator orchestrator;
    private GestureDetector gestureDetector;

    // UI elements
    private LinearLayout idleLayout;
    private LinearLayout activeLayout;
    private FrameLayout btnMute;
    private FrameLayout btnStop;
    private ImageView muteIcon;
    private TextView muteText;
    private TextView tvStatus;

    // Audio recording
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private boolean isMuted = false;
    private Thread recordingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        idleLayout = findViewById(R.id.idle_layout);
        activeLayout = findViewById(R.id.active_layout);
        btnMute = findViewById(R.id.btn_mute);
        btnStop = findViewById(R.id.btn_stop);
        muteIcon = findViewById(R.id.mute_icon);
        muteText = findViewById(R.id.mute_text);
        tvStatus = findViewById(R.id.tv_status);

        // Initialize TapMate system
        initializeTapMate();

        // Setup gesture detector for swipe
        setupGestureDetector();

        // Setup button listeners
        setupListeners();

        // Request permissions
        requestPermissions();

        updateStatus("Ready");
    }

    private void initializeTapMate() {
        try {
            Log.i(TAG, "Initializing TapMate...");

            // Get orchestrator instance
            orchestrator = TapMateOrchestrator.getInstance(this);

            // Register sub-agents
            orchestrator.registerSubAgent(new ConfigAgent(this));
            orchestrator.registerSubAgent(new DatabaseAgent(this));
            orchestrator.registerSubAgent(new GUIAgent(this));

            Log.i(TAG, "TapMate initialized successfully");
            Toast.makeText(this, "TapMate ready!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing TapMate", e);
            Toast.makeText(this, "Error initializing: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) &&
                    Math.abs(diffX) > SWIPE_THRESHOLD &&
                    Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) {
                        // Swipe right - open settings
                        openSettings();
                        return true;
                    }
                }
                return false;
            }
        });

        // Apply gesture detector to main container
        findViewById(R.id.main_container).setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });
    }

    private void setupListeners() {
        // Idle layout - tap anywhere to start
        idleLayout.setOnClickListener(v -> startSession());

        // Mute button
        btnMute.setOnClickListener(v -> toggleMute());

        // Stop button
        btnStop.setOnClickListener(v -> stopSession());
    }

    private void startSession() {
        try {
            Log.i(TAG, "Starting session...");

            // Check microphone permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show();
                requestPermissions();
                return;
            }

            // Start orchestrator
            orchestrator.startSession();

            // Start audio recording
            startAudioRecording();

            // Update UI to active state
            idleLayout.setVisibility(View.GONE);
            activeLayout.setVisibility(View.VISIBLE);

            updateStatus("Listening...");
            Toast.makeText(this, "Session started - I'm listening", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error starting session", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopSession() {
        try {
            Log.i(TAG, "Stopping session...");

            // Stop audio recording
            stopAudioRecording();

            // Stop orchestrator
            orchestrator.stopSession();

            // Update UI to idle state
            activeLayout.setVisibility(View.GONE);
            idleLayout.setVisibility(View.VISIBLE);

            updateStatus("Ready");
            Toast.makeText(this, "Session stopped", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error stopping session", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;

        if (isMuted) {
            // Muted - show rectangle (unmute icon)
            muteIcon.setImageResource(R.drawable.rectangle_stop);
            muteText.setText("Unmute");
            updateStatus("Muted");
            Toast.makeText(this, "Microphone muted", Toast.LENGTH_SHORT).show();
        } else {
            // Unmuted - show pause lines (mute icon)
            muteIcon.setImageResource(R.drawable.pause_lines);
            muteText.setText("Mute");
            updateStatus("Listening...");
            Toast.makeText(this, "Microphone active", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAudioRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }

        try {
            int bufferSize = AudioRecord.getMinBufferSize(
                16000, // 16kHz sample rate
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No microphone permission");
                return;
            }

            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            );

            audioRecord.startRecording();
            isRecording = true;

            // Start recording thread
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[bufferSize];
                while (isRecording && !Thread.currentThread().isInterrupted()) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && !isMuted) {
                        // TODO: Process audio data (send to Gemini)
                        // For now, just log that we're receiving audio
                        Log.d(TAG, "Audio data received: " + bytesRead + " bytes");
                    }
                }
            });
            recordingThread.start();

            Log.i(TAG, "Audio recording started");

        } catch (Exception e) {
            Log.e(TAG, "Error starting audio recording", e);
            Toast.makeText(this, "Microphone error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopAudioRecording() {
        isRecording = false;

        if (recordingThread != null) {
            recordingThread.interrupt();
            recordingThread = null;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        Log.i(TAG, "Audio recording stopped");
    }

    private void openSettings() {
        // TODO: Create SettingsActivity
        Toast.makeText(this, "Settings - Coming soon!", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Opening settings (not yet implemented)");
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> tvStatus.setText(status));
    }

    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.VIBRATE
        };

        boolean needsPermission = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needsPermission = true;
                break;
            }
        }

        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions denied - app may not work fully", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudioRecording();
        if (orchestrator != null && orchestrator.isSessionActive()) {
            orchestrator.stopSession();
        }
    }
}
