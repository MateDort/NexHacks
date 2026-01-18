package com.tapmate.aiagent.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
        
        // Set status bar color to match app (yellow when idle)
        setStatusBarColor(R.color.effective_yellow);
        
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

    private void setStatusBarColor(int colorResId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, colorResId));
        }
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

        } catch (Exception e) {
            Log.e(TAG, "Error initializing TapMate", e);
        }
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                
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
    }

    private void setupListeners() {
        // Idle layout - handle both tap and swipe
        idleLayout.setOnTouchListener((v, event) -> {
            // First, try to detect swipe
            boolean swipeDetected = gestureDetector.onTouchEvent(event);
            
            // If no swipe and it's a single tap (ACTION_UP), start session
            if (!swipeDetected && event.getAction() == MotionEvent.ACTION_UP) {
                startSession();
                return true;
            }
            
            return swipeDetected;
        });

        // Active layout - allow swipe
        activeLayout.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false; // Let child views handle clicks
        });

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
                Log.w(TAG, "Microphone permission required");
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

            // Change status bar to yellow (mute section color)
            setStatusBarColor(R.color.effective_yellow);

            updateStatus("Listening...");
            Log.i(TAG, "Session started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error starting session", e);
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

            // Reset mute state
            isMuted = false;

            // Change status bar back to yellow
            setStatusBarColor(R.color.effective_yellow);

            updateStatus("Ready");
            Log.i(TAG, "Session stopped successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error stopping session", e);
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;

        if (isMuted) {
            // Muted - show triangle (unmute icon)
            muteIcon.setImageResource(R.drawable.triangle_start);
            muteText.setText("Unmute");
            updateStatus("Muted");
            Log.i(TAG, "Microphone muted");
        } else {
            // Unmuted - show pause lines (mute icon)
            muteIcon.setImageResource(R.drawable.pause_lines);
            muteText.setText("Mute");
            updateStatus("Listening...");
            Log.i(TAG, "Microphone unmuted");
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
                int consecutiveReads = 0;
                
                while (isRecording && !Thread.currentThread().isInterrupted()) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && !isMuted) {
                        // Log every 50th read to avoid spam
                        if (consecutiveReads % 50 == 0) {
                            Log.d(TAG, "✓ Audio receiving: " + bytesRead + " bytes (microphone is working!)");
                        }
                        consecutiveReads++;
                        // TODO: Process audio data (send to Gemini)
                    }
                }
            });
            recordingThread.start();

            Log.i(TAG, "✓ Audio recording started successfully - check logcat for audio data");

        } catch (Exception e) {
            Log.e(TAG, "Error starting audio recording", e);
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
        Log.i(TAG, "Swipe detected - Opening settings (not yet implemented)");
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
                Log.i(TAG, "All permissions granted");
            } else {
                Log.w(TAG, "Some permissions denied");
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
