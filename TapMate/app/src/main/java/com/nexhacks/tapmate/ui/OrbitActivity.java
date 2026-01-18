package com.nexhacks.tapmate.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import com.nexhacks.tapmate.vision.OvershootClient;

public class OrbitActivity extends Activity implements SensorEventListener {

    private OrbitView orbitView;
    private SensorManager sensorManager;
    private Vibrator vibrator;
    private OvershootClient visionClient;

    private float currentHeading = 0f;
    private boolean isOrbitActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        orbitView = new OrbitView(this, null);
        setContentView(orbitView);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        visionClient = new OvershootClient();

        // Start sensors (Compass)
        Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        
        // Start Vision Loop
        startVisionLoop();
    }

    private void startVisionLoop() {
        new Thread(() -> {
            while (isOrbitActive) {
                try {
                    // 1. Capture Frame (Mocked here, real app uses CameraX)
                    Bitmap frame = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888); 
                    
                    // 2. Call Overshoot Vision
                    visionClient.detectObject(frame, "traffic_light", new OvershootClient.VisionCallback() {
                        @Override
                        public void onDetection(String objectName, String attributes, float confidence) {
                            runOnUiThread(() -> handleDetection(objectName, attributes));
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e("Orbit", "Vision Error", e);
                        }
                    });

                    Thread.sleep(500); // 500ms loop
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleDetection(String objectName, String attributes) {
        if ("traffic_light".equals(objectName)) {
            if (attributes.contains("Red")) {
                orbitView.setAlertState(true, false);
                orbitView.updateNavigation(currentHeading, 0, "STOP! Light is Red.");
                vibrateWarning();
            } else if (attributes.contains("Green")) {
                orbitView.setAlertState(false, true);
                orbitView.updateNavigation(currentHeading, 0, "Safe to Cross.");
            }
        }
    }
    
    private void vibrateWarning() {
        if (vibrator != null) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            
            // Azimuth (Heading) in degrees
            currentHeading = (float) Math.toDegrees(orientation[0]);
            if (currentHeading < 0) currentHeading += 360;
            
            // Mock Target: North (0 degrees)
            orbitView.updateNavigation(currentHeading, 0f, "Walk Forward");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
