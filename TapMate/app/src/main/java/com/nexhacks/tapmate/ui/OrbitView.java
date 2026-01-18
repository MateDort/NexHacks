package com.nexhacks.tapmate.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class OrbitView extends View {

    private Paint backgroundPaint;
    private Paint arrowPaint;
    private Paint textPaint;
    
    // State
    private float bearingToTarget = 0f; // 0-360 degrees
    private float currentHeading = 0f;
    private String statusMessage = "Scanning...";
    private int screenColor = Color.BLACK; // Default Orbit Black

    public OrbitView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.BLACK);

        arrowPaint = new Paint();
        arrowPaint.setColor(Color.GREEN); // Neon Green
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setAntiAlias(true);
        arrowPaint.setShadowLayer(20, 0, 0, Color.GREEN); // Neon Glow effect

        textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(60);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
    }

    public void updateNavigation(float heading, float targetBearing, String msg) {
        this.currentHeading = heading;
        this.bearingToTarget = targetBearing;
        this.statusMessage = msg;
        invalidate(); // Redraw
    }

    public void setAlertState(boolean isRed, boolean isGreen) {
        if (isRed) {
            screenColor = Color.RED;
            arrowPaint.setColor(Color.BLACK); // Contrast
        } else if (isGreen) {
            screenColor = Color.GREEN;
            arrowPaint.setColor(Color.BLACK);
        } else {
            screenColor = Color.BLACK;
            arrowPaint.setColor(Color.GREEN); // Neon
        }
        backgroundPaint.setColor(screenColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;

        // 1. Draw Background
        canvas.drawRect(0, 0, w, h, backgroundPaint);

        // 2. Calculate Arrow Rotation
        // Arrow points to Target relative to Phone Heading
        float rotation = bearingToTarget - currentHeading;

        canvas.save();
        canvas.rotate(rotation, cx, cy);

        // 3. Draw Neon Arrow
        Path arrowPath = new Path();
        arrowPath.moveTo(cx, cy - 300); // Tip
        arrowPath.lineTo(cx - 100, cy + 100); // Bottom Left
        arrowPath.lineTo(cx, cy + 50); // Bottom Center notch
        arrowPath.lineTo(cx + 100, cy + 100); // Bottom Right
        arrowPath.close();

        canvas.drawPath(arrowPath, arrowPaint);
        canvas.restore();

        // 4. Draw Status Text (Always upright)
        canvas.drawText(statusMessage, cx, h - 200, textPaint);
    }
}
