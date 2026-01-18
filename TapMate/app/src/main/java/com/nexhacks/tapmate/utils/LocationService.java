package com.nexhacks.tapmate.utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

public class LocationService {
    private static final String TAG = "LocationService";
    private Context context;
    private LocationManager locationManager;
    
    public LocationService(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    public Location getCurrentLocation() {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " LocationService.getCurrentLocation:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"LocationService.java:getCurrentLocation\",\"message\":\"Getting current location\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " LocationService.getCurrentLocation:NO_PERMISSION " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"LocationService.java:getCurrentLocation\",\"message\":\"No location permission\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            Log.w(TAG, "Location permission not granted");
            return null;
        }
        
        try {
            Location lastKnownLocation = null;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lastKnownLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " LocationService.getCurrentLocation:SUCCESS " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"LocationService.java:getCurrentLocation\",\"message\":\"Location retrieved\",\"data\":{\"hasLocation\":" + 
                    (lastKnownLocation != null) + ",\"lat\":" + (lastKnownLocation != null ? lastKnownLocation.getLatitude() : 0) + ",\"lng\":" + (lastKnownLocation != null ? lastKnownLocation.getLongitude() : 0) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            return lastKnownLocation;
        } catch (Exception e) {
            Log.e(TAG, "Error getting location", e);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " LocationService.getCurrentLocation:ERROR " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"LocationService.java:getCurrentLocation\",\"message\":\"Error getting location\",\"data\":{\"error\":\"" + 
                    e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            return null;
        }
    }
    
    public String getLocationString() {
        Location loc = getCurrentLocation();
        if (loc != null) {
            return loc.getLatitude() + "," + loc.getLongitude();
        }
        return null;
    }
}
