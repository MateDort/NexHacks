package com.nexhacks.tapmate.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

public class MainActivity extends Activity {

    private Button micButton;
    private Button settingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load environment config
        com.nexhacks.tapmate.utils.Config.loadEnv(this);
        setContentView(createHomeLayout());
    }

    private View createHomeLayout() {
        RelativeLayout layout = new RelativeLayout(this);
        layout.setBackgroundColor(0xFF1A1A1A); // Dark background

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // Create Mic Button (75% of screen, centered)
        micButton = new Button(this);
        micButton.setText("🎤");
        micButton.setTextSize(80);
        micButton.setBackgroundColor(0xFF4CAF50); // Green color
        micButton.setAllCaps(false);
        
        RelativeLayout.LayoutParams micParams = new RelativeLayout.LayoutParams(
            (int) (screenWidth * 0.75f),
            (int) (screenHeight * 0.75f)
        );
        micParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        micButton.setLayoutParams(micParams);
        
        micButton.setOnClickListener(v -> {
            // Start session activity
            Intent intent = new Intent(MainActivity.this, SessionActivity.class);
            startActivity(intent);
        });

        // Create Settings Button (top left corner)
        settingsButton = new Button(this);
        settingsButton.setText("⚙️");
        settingsButton.setTextSize(30);
        settingsButton.setBackgroundColor(0xFF757575); // Gray color
        settingsButton.setAllCaps(false);
        
        RelativeLayout.LayoutParams settingsParams = new RelativeLayout.LayoutParams(
            (int) (screenWidth * 0.15f),
            (int) (screenHeight * 0.1f)
        );
        settingsParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        settingsParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        settingsParams.setMargins(20, 20, 0, 0);
        settingsButton.setLayoutParams(settingsParams);
        
        settingsButton.setOnClickListener(v -> {
            // TODO: Open settings activity or dialog
            // For now, just show a toast or placeholder
            android.widget.Toast.makeText(this, "Settings (coming soon)", android.widget.Toast.LENGTH_SHORT).show();
        });

        layout.addView(micButton);
        layout.addView(settingsButton);

        return layout;
    }
}
