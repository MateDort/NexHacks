package com.nexhacks.tapmate.agents;

import com.nexhacks.tapmate.accessibility.TapMateAccessibilityService;
import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;
import android.util.Log;
import java.util.concurrent.ExecutorService;

public class GUIAgent extends BaseAgent {
    private static final String TAG = "GUIAgent";
    private TapMateAccessibilityService accessibilityService;
    private ExecutorService executorService;
    private Runnable screenStateUpdater;
    private android.content.Context context;
    
    public GUIAgent(Handler mainHandler, AgentCallback callback,
                   TapMateAccessibilityService accessibilityService,
                   ExecutorService executorService,
                   Runnable screenStateUpdater,
                   android.content.Context context) {
        super(mainHandler, callback);
        this.accessibilityService = accessibilityService;
        this.executorService = executorService;
        this.screenStateUpdater = screenStateUpdater;
        this.context = context;
    }
    
    @Override
    public JSONArray getFunctionDeclarations() {
        try {
            JSONArray funcs = new JSONArray();
            
            // gui_click
            funcs.put(createFunctionDeclaration("gui_click", 
                "Click an element on the screen given its ID or text.",
                new String[]{"node_id"}, new String[]{"node_id"}));
            
            // gui_type
            funcs.put(createFunctionDeclaration("gui_type",
                "Type text into an editable field.",
                new String[]{"node_id", "text"}, new String[]{"node_id", "text"}));
            
            // gui_scroll
            JSONObject scrollParams = new JSONObject();
            scrollParams.put("direction", new JSONObject()
                .put("type", "STRING")
                .put("enum", new JSONArray().put("UP").put("DOWN")));
            funcs.put(createFunctionDeclarationWithTypes("gui_scroll",
                "Scroll the screen up or down.",
                scrollParams, new String[]{"direction"}));
            
            // gui_open_app
            funcs.put(createFunctionDeclaration("gui_open_app",
                "Open an app on the phone by name. Use this when the user asks to open an app that's not currently visible on screen.",
                new String[]{"app_name"}, new String[]{"app_name"}));
            
            return funcs;
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error creating function declarations", e);
            return new JSONArray();
        }
    }
    
    @Override
    public boolean handleFunction(String functionName, JSONObject args, String callId) {
        switch (functionName) {
            case "gui_click":
                handleClick(args, callId);
                return true;
            case "gui_type":
                handleType(args, callId);
                return true;
            case "gui_scroll":
                handleScroll(args, callId);
                return true;
            case "gui_open_app":
                handleOpenApp(args, callId);
                return true;
            default:
                return false; // Not handled by this agent
        }
    }
    
    @Override
    public String[] getHandledFunctions() {
        return new String[]{"gui_click", "gui_type", "gui_scroll", "gui_open_app"};
    }
    
    private void handleClick(JSONObject args, String callId) {
        if (accessibilityService == null) {
            callback.onError("gui_click", "Accessibility service not available", callId);
            return;
        }
        
        String nodeId = args.optString("node_id", "");
        if (nodeId.isEmpty()) {
            callback.onError("gui_click", "No node ID provided", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                boolean clicked = accessibilityService.performClick(nodeId);
                String result = clicked ? "Successfully clicked: " + nodeId : "Could not click: " + nodeId;
                mainHandler.post(() -> {
                    callback.onResult("gui_click", result, callId);
                    if (screenStateUpdater != null) {
                        screenStateUpdater.run();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error performing click", e);
                mainHandler.post(() -> callback.onError("gui_click", "Error: " + e.getMessage(), callId));
            }
        });
    }
    
    private void handleType(JSONObject args, String callId) {
        if (accessibilityService == null) {
            callback.onError("gui_type", "Accessibility service not available", callId);
            return;
        }
        
        String typeNodeId = args.optString("node_id", "");
        String text = args.optString("text", "");
        if (typeNodeId.isEmpty() || text.isEmpty()) {
            callback.onError("gui_type", "Missing node ID or text", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                boolean typed = accessibilityService.performInput(typeNodeId, text);
                String result = typed ? "Successfully typed: " + text : "Could not type";
                mainHandler.post(() -> {
                    callback.onResult("gui_type", result, callId);
                    if (screenStateUpdater != null) {
                        screenStateUpdater.run();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error performing input", e);
                mainHandler.post(() -> callback.onError("gui_type", "Error: " + e.getMessage(), callId));
            }
        });
    }
    
    private void handleScroll(JSONObject args, String callId) {
        if (accessibilityService == null) {
            callback.onError("gui_scroll", "Accessibility service not available", callId);
            return;
        }
        
        String direction = args.optString("direction", "DOWN");
        executorService.execute(() -> {
            try {
                boolean scrolled = accessibilityService.performScroll(direction);
                String result = scrolled ? "Successfully scrolled " + direction : "Could not scroll";
                mainHandler.post(() -> {
                    callback.onResult("gui_scroll", result, callId);
                    if (screenStateUpdater != null) {
                        screenStateUpdater.run();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error performing scroll", e);
                mainHandler.post(() -> callback.onError("gui_scroll", "Error: " + e.getMessage(), callId));
            }
        });
    }
    
    private void handleOpenApp(JSONObject args, String callId) {
        String appName = args.optString("app_name", "");
        if (appName.isEmpty()) {
            callback.onError("gui_open_app", "No app name provided", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                boolean opened = openApp(appName);
                String result = opened ? "I've opened the " + appName + " app." : 
                    "I couldn't find or open the " + appName + " app. Please make sure it's installed.";
                mainHandler.post(() -> {
                    callback.onResult("gui_open_app", result, callId);
                    if (opened && screenStateUpdater != null) {
                        // Wait a bit then update screen state
                        mainHandler.postDelayed(screenStateUpdater, 1000);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error opening app", e);
                mainHandler.post(() -> callback.onError("gui_open_app", "Error: " + e.getMessage(), callId));
            }
        });
    }
    
    private boolean openApp(String appName) {
        try {
            if (context == null) {
                return false;
            }
            
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.Intent intent = pm.getLaunchIntentForPackage(appName.toLowerCase().replace(" ", ""));
            
            if (intent == null) {
                // Try to find by name
                java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
                for (android.content.pm.ApplicationInfo app : apps) {
                    String label = pm.getApplicationLabel(app).toString();
                    if (label.toLowerCase().contains(appName.toLowerCase())) {
                        intent = pm.getLaunchIntentForPackage(app.packageName);
                        break;
                    }
                }
            }
            
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error opening app: " + appName, e);
            return false;
        }
    }
}
