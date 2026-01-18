package com.nexhacks.tapmate.utils;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsIntegration {
    private static final String TAG = "MapsIntegration";
    private static final String API_KEY = com.nexhacks.tapmate.utils.Config.MAPS_API_KEY;
    
    // Proactive Alert States
    public enum CrosswalkAlertState {
        SAFE,
        WARNING_50_STEPS,
        WARNING_10_STEPS,
        STOP_IMMEDIATELY
    }

    private final OkHttpClient client = new OkHttpClient();

    // 1. Get Walking Directions (Brain Tool)
    public JSONObject getWalkingDirections(String destination) {
        // In a real app, uses Places API to get LatLng from "Walmart", then Directions API
        // For hackathon, we simulate a response or call the real endpoint
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin=current_location&destination=" 
                     + destination + "&mode=walking&key=" + API_KEY;
        
        try {
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                return new JSONObject(response.body().string());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching directions", e);
        }
        return null;
    }

    // 2. Logic: "How far to next intersection?"
    // Input: Current User Location, List of Route Steps
    public CrosswalkAlertState checkCrosswalkProximity(Location userLoc, Location nextStepLoc) {
        if (userLoc == null || nextStepLoc == null) return CrosswalkAlertState.SAFE;

        float distanceMeters = userLoc.distanceTo(nextStepLoc);
        // Approx 1 step = 0.75 meters
        int steps = (int) (distanceMeters / 0.75);

        if (steps <= 5) return CrosswalkAlertState.STOP_IMMEDIATELY;
        if (steps <= 10) return CrosswalkAlertState.WARNING_10_STEPS;
        if (steps <= 50) return CrosswalkAlertState.WARNING_50_STEPS;

        return CrosswalkAlertState.SAFE;
    }
}
