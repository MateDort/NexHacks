package com.nexhacks.tapmate.agents;

import com.nexhacks.tapmate.utils.MapsIntegration;
import com.nexhacks.tapmate.utils.LocationService;
import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import android.location.Location;

public class NavigationAgent extends BaseAgent {
    private static final String TAG = "NavigationAgent";
    private MapsIntegration mapsIntegration;
    private LocationService locationService;
    private ExecutorService executorService;
    
    public NavigationAgent(Handler mainHandler, AgentCallback callback,
                         MapsIntegration mapsIntegration,
                         LocationService locationService,
                         ExecutorService executorService) {
        super(mainHandler, callback);
        this.mapsIntegration = mapsIntegration;
        this.locationService = locationService;
        this.executorService = executorService;
    }
    
    @Override
    public JSONArray getFunctionDeclarations() {
        JSONArray funcs = new JSONArray();
        
        // maps_navigation
        funcs.put(createFunctionDeclaration("maps_navigation",
            "Get walking directions to a destination using Google Maps.",
            new String[]{"destination"}, new String[]{"destination"}));
        
        // get_location
        funcs.put(createFunctionDeclaration("get_location",
            "Get the user's current GPS location coordinates.",
            new String[]{}, new String[]{}));
        
        return funcs;
    }
    
    @Override
    public boolean handleFunction(String functionName, JSONObject args, String callId) {
        switch (functionName) {
            case "maps_navigation":
                handleMapsNavigation(args, callId);
                return true;
            case "get_location":
                handleGetLocation(callId);
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public String[] getHandledFunctions() {
        return new String[]{"maps_navigation", "get_location"};
    }
    
    private void handleMapsNavigation(JSONObject args, String callId) {
        String destination = args.optString("destination", "");
        if (destination.isEmpty()) {
            callback.onError("maps_navigation", "No destination provided", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Get current location first
                String origin = locationService.getLocationString();
                if (origin == null) {
                    origin = "37.7749,-122.4194"; // Fallback
                }
                
                JSONObject directions = mapsIntegration.getWalkingDirections(destination, origin);
                mainHandler.post(() -> {
                    try {
                        if (directions != null) {
                            String result = "I've started navigation to " + destination;
                            callback.onResult("maps_navigation", result, callId);
                        } else {
                            String result = "I couldn't get directions to " + destination + ". Please check your internet connection and try again.";
                            callback.onResult("maps_navigation", result, callId);
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error getting directions", e);
                mainHandler.post(() -> callback.onError("maps_navigation", "Error getting directions: " + e.getMessage(), callId));
            }
        });
    }
    
    private void handleGetLocation(String callId) {
        executorService.execute(() -> {
            try {
                Location loc = locationService.getCurrentLocation();
                mainHandler.post(() -> {
                    try {
                        if (loc != null) {
                            String locationStr = "Your location is approximately " + loc.getLatitude() + ", " + loc.getLongitude();
                            callback.onResult("get_location", locationStr, callId);
                        } else {
                            String result = "I couldn't determine your location. Please enable location services and grant location permission.";
                            callback.onResult("get_location", result, callId);
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error getting location", e);
                mainHandler.post(() -> callback.onError("get_location", "Error getting location: " + e.getMessage(), callId));
            }
        });
    }
}
