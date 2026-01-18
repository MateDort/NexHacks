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
    public JSONObject getWalkingDirections(String destination, String origin) {
        try {
            // First, try to get place details using Places API Text Search
            String placesUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=" 
                             + java.net.URLEncoder.encode(destination, "UTF-8") 
                             + "&key=" + API_KEY;
            
            Request placesRequest = new Request.Builder().url(placesUrl).build();
            Response placesResponse = client.newCall(placesRequest).execute();
            
            String destCoords = null;
            if (placesResponse.isSuccessful() && placesResponse.body() != null) {
                JSONObject placesJson = new JSONObject(placesResponse.body().string());
                if (placesJson.has("results") && placesJson.getJSONArray("results").length() > 0) {
                    JSONObject firstResult = placesJson.getJSONArray("results").getJSONObject(0);
                    JSONObject location = firstResult.getJSONObject("geometry").getJSONObject("location");
                    destCoords = location.getDouble("lat") + "," + location.getDouble("lng");
                }
            }
            
            // If Places API didn't work, try using destination as-is (might be coordinates or address)
            if (destCoords == null) {
                destCoords = destination;
            }
            
            // Get directions using Directions API
            String originCoords = origin != null ? origin : "37.7749,-122.4194"; // Fallback to SF if no origin
            String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" 
                         + java.net.URLEncoder.encode(originCoords, "UTF-8")
                         + "&destination=" + java.net.URLEncoder.encode(destCoords, "UTF-8")
                         + "&mode=walking&key=" + API_KEY;
            
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject result = new JSONObject(responseBody);
                if (result.has("routes") && result.getJSONArray("routes").length() > 0) {
                    return result;
                } else if (result.has("error_message")) {
                    Log.e(TAG, "Directions API error: " + result.getString("error_message"));
                }
            } else {
                Log.e(TAG, "Directions API HTTP error: " + (response != null ? response.code() : "null"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching directions", e);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " MapsIntegration.getWalkingDirections:ERROR " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"MapsIntegration.java:getWalkingDirections\",\"message\":\"Error in directions\",\"data\":{\"error\":\"" + 
                    e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
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
