package com.tapmate.aiagent.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tapmate.aiagent.R;
import com.tapmate.aiagent.agents.ConfigAgent;
import com.tapmate.aiagent.agents.DatabaseAgent;
import com.tapmate.aiagent.agents.GUIAgent;
import com.tapmate.aiagent.core.TapMateOrchestrator;

/**
 * Main activity for TapMate.
 * Simple UI with large accessible buttons for voice interaction.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TapMateOrchestrator orchestrator;
    private Button btnMic;
    private Button btnSettings;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        btnMic = findViewById(R.id.btn_mic);
        btnSettings = findViewById(R.id.btn_settings);
        tvStatus = findViewById(R.id.tv_status);

        // Initialize TapMate system
        initializeTapMate();

        // Setup button listeners
        setupListeners();

        updateStatus("Ready");
    }

    private void initializeTapMate() {
        try {
            Log.i(TAG, "Initializing TapMate...");

            // Get orchestrator instance
            orchestrator = TapMateOrchestrator.getInstance(this);

            // Initialize
            orchestrator.initialize();

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

    private void setupListeners() {
        btnMic.setOnClickListener(v -> {
            if (orchestrator.isSessionActive()) {
                stopSession();
            } else {
                startSession();
            }
        });

        btnSettings.setOnClickListener(v -> {
            // TODO: Open settings activity
            Toast.makeText(this, "Settings coming soon!", Toast.LENGTH_SHORT).show();
        });
    }

    private void startSession() {
        try {
            Log.i(TAG, "Starting session...");
            orchestrator.startSession();

            btnMic.setText(R.string.stop_session);
            updateStatus("Session active - Listening...");

            Toast.makeText(this, "Session started!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error starting session", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopSession() {
        try {
            Log.i(TAG, "Stopping session...");
            orchestrator.stopSession();

            btnMic.setText(R.string.tap_to_speak);
            updateStatus("Ready");

            Toast.makeText(this, "Session stopped", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error stopping session", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> tvStatus.setText(status));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orchestrator != null && orchestrator.isSessionActive()) {
            orchestrator.stopSession();
        }
    }
}
