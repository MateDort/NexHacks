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

        layout.addView(micButton);

        return layout;
    }
}
