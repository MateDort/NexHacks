package com.nexhacks.tapmate.agents;

import com.nexhacks.tapmate.accessibility.TapMateAccessibilityService;
import com.nexhacks.tapmate.gemini.GeminiClient;
import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class GUIAgent extends BaseAgent {
    private static final String TAG = "GUIAgent";
    private TapMateAccessibilityService accessibilityService;
    private ExecutorService executorService;
    private Runnable screenStateUpdater;
    private android.content.Context context;
    private GeminiClient geminiClient;
    
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
        this.geminiClient = new GeminiClient();
    }
    
    @Override
    public JSONArray getFunctionDeclarations() {
        try {
            JSONArray funcs = new JSONArray();
            
            // gui_execute_plan - Main GUI agent function that creates a todo list and executes step by step
            JSONObject planParams = new JSONObject();
            planParams.put("goal", new JSONObject()
                .put("type", "STRING")
                .put("description", "The user's goal (e.g., 'Open Messenger', 'Order Uber to bakery', 'Send a message to John')"));
            planParams.put("current_screen_state", new JSONObject()
                .put("type", "STRING")
                .put("description", "Current screen state JSON from accessibility service"));
            funcs.put(createFunctionDeclarationWithTypes("gui_execute_plan",
                "Execute a GUI task by creating a todo list of steps, then executing each step and analyzing the result. " +
                "After each step, analyze the new screen state to determine if the goal is achieved or if more steps are needed. " +
                "This is the main GUI agent function - use this instead of individual click/type/scroll functions.",
                planParams, new String[]{"goal", "current_screen_state"}));
            
            // Keep low-level functions for internal use (not exposed to LLM)
            // gui_click - Internal use only
            funcs.put(createFunctionDeclaration("gui_click", 
                "INTERNAL: Click an element on the screen given its ID or text. Use gui_execute_plan instead.",
                new String[]{"node_id"}, new String[]{"node_id"}));
            
            // gui_type - Internal use only
            funcs.put(createFunctionDeclaration("gui_type",
                "INTERNAL: Type text into an editable field. Use gui_execute_plan instead.",
                new String[]{"node_id", "text"}, new String[]{"node_id", "text"}));
            
            // gui_scroll - Internal use only
            JSONObject scrollParams = new JSONObject();
            scrollParams.put("direction", new JSONObject()
                .put("type", "STRING")
                .put("enum", new JSONArray().put("UP").put("DOWN")));
            funcs.put(createFunctionDeclarationWithTypes("gui_scroll",
                "INTERNAL: Scroll the screen up or down. Use gui_execute_plan instead.",
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
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GUIAgent.handleFunction:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"GUIAgent.java:handleFunction\",\"message\":\"GUIAgent handling function\",\"data\":{\"functionName\":\"" + 
                functionName + "\",\"callId\":\"" + (callId != null ? callId : "null") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error logging", e);
        }
        // #endregion
        switch (functionName) {
            case "gui_execute_plan":
                handleExecutePlan(args, callId);
                return true;
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
        return new String[]{"gui_execute_plan", "gui_click", "gui_type", "gui_scroll", "gui_open_app"};
    }
    
    private void handleExecutePlan(JSONObject args, String callId) {
        String goal = args.optString("goal", "");
        String screenStateJson = args.optString("current_screen_state", "[]");
        
        if (goal.isEmpty()) {
            callback.onError("gui_execute_plan", "No goal provided", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Step 1: Create a todo list using Gemini to plan the steps
                String todoList = createTodoList(goal, screenStateJson);
                
                // Step 2: Execute each step in the todo list
                String result = executeTodoList(goal, todoList, callId);
                
                mainHandler.post(() -> {
                    try {
                        callback.onResult("gui_execute_plan", result, callId);
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error executing plan", e);
                mainHandler.post(() -> callback.onError("gui_execute_plan", "Error: " + e.getMessage(), callId));
            }
        });
    }
    
    private String createTodoList(String goal, String screenStateJson) {
        // Use Gemini (trained model) to create a todo list based on the goal and current screen state
        final AtomicReference<String> resultRef = new AtomicReference<>("");
        final CountDownLatch latch = new CountDownLatch(1);
        
        String planningPrompt = "Given the user's goal: \"" + goal + "\" and the current screen state: " + screenStateJson + 
            ", create a step-by-step todo list to achieve this goal. " +
            "Return ONLY a JSON array of steps, each step should be: {\"action\": \"click|type|scroll|open_app\", \"target\": \"node_id or text\", \"value\": \"text to type if needed\"}. " +
            "Example: [{\"action\":\"click\",\"target\":\"search_button\"},{\"action\":\"type\",\"target\":\"search_input\",\"value\":\"pizza\"}]";
        
        geminiClient.queryAgent(planningPrompt, screenStateJson, new GeminiClient.GeminiCallback() {
            @Override
            public void onResponse(String toolName, JSONObject toolArgs) {
                if (toolName.equals("text_response")) {
                    String text = toolArgs.optString("text", "");
                    resultRef.set(text);
                } else {
                    resultRef.set("{\"steps\":[]}");
                }
                latch.countDown();
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error creating todo list", e);
                resultRef.set("{\"steps\":[]}");
                latch.countDown();
            }
        });
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return resultRef.get();
    }
    
    private String executeTodoList(String goal, String todoList, String callId) {
        // Execute the todo list step by step
        // After each step, analyze the screen state
        // Continue until goal is achieved or max steps reached
        
        int maxSteps = 10;
        int stepCount = 0;
        String currentScreenState = accessibilityService != null ? accessibilityService.getScreenState() : "[]";
        
        // Parse todo list (expecting JSON array of steps)
        JSONArray steps = new JSONArray();
        try {
            // Try to parse as JSON array directly
            if (todoList.trim().startsWith("[")) {
                steps = new JSONArray(todoList);
            } else {
                // Try to extract from text response
                JSONObject parsed = new JSONObject(todoList);
                if (parsed.has("steps")) {
                    steps = parsed.getJSONArray("steps");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing todo list", e);
            // Fallback: create steps from analysis
            steps = createStepsFromAnalysis(goal, currentScreenState);
        }
        
        Log.d(TAG, "Executing plan with " + steps.length() + " steps for goal: " + goal);
        
        for (int i = 0; i < steps.length() && stepCount < maxSteps; i++) {
            try {
                JSONObject step = steps.getJSONObject(i);
                String action = step.optString("action", "");
                String target = step.optString("target", "");
                String value = step.optString("value", "");
                
                Log.d(TAG, "Step " + (i + 1) + ": " + action + " -> " + target);
                
                // Execute the step
                boolean success = executeStep(action, target, value);
                
                if (!success) {
                    Log.w(TAG, "Step " + (i + 1) + " failed, analyzing screen state");
                    // Analyze and potentially replan
                    String analysis = analyzeScreenState(goal, currentScreenState);
                    if (analysis.contains("GOAL_ACHIEVED")) {
                        return "Goal achieved: " + goal;
                    }
                }
                
                // Wait for screen to update
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Update screen state
                if (screenStateUpdater != null) {
                    mainHandler.post(screenStateUpdater);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                currentScreenState = accessibilityService != null ? accessibilityService.getScreenState() : "[]";
                
                // Analyze after each step
                String analysis = analyzeScreenState(goal, currentScreenState);
                if (analysis.contains("GOAL_ACHIEVED") || analysis.contains("SUCCESS")) {
                    return "Goal achieved: " + goal + " (completed in " + (stepCount + 1) + " steps)";
                }
                
                stepCount++;
            } catch (Exception e) {
                Log.e(TAG, "Error executing step " + i, e);
            }
        }
        
        return "Completed " + stepCount + " steps. Goal: " + goal;
    }
    
    private JSONArray createStepsFromAnalysis(String goal, String screenStateJson) {
        // Fallback: use Gemini to create steps from analysis
        JSONArray steps = new JSONArray();
        final AtomicReference<String> resultRef = new AtomicReference<>("[]");
        final CountDownLatch latch = new CountDownLatch(1);
        
        String prompt = "Goal: " + goal + ". Screen: " + screenStateJson + 
            ". Return JSON array of steps: [{\"action\":\"click\",\"target\":\"id\"}]";
        
        geminiClient.queryAgent(prompt, screenStateJson, new GeminiClient.GeminiCallback() {
            @Override
            public void onResponse(String toolName, JSONObject toolArgs) {
                String text = toolArgs.optString("text", "[]");
                resultRef.set(text);
                latch.countDown();
            }
            
            @Override
            public void onError(Exception e) {
                resultRef.set("[]");
                latch.countDown();
            }
        });
        
        try {
            latch.await();
            steps = new JSONArray(resultRef.get());
        } catch (Exception e) {
            Log.e(TAG, "Error creating steps", e);
        }
        
        return steps;
    }
    
    private boolean executeStep(String action, String target, String value) {
        if (accessibilityService == null) {
            return false;
        }
        
        try {
            switch (action.toLowerCase()) {
                case "click":
                    return accessibilityService.performClick(target);
                case "type":
                    return accessibilityService.performInput(target, value);
                case "scroll":
                    return accessibilityService.performScroll(value.isEmpty() ? "DOWN" : value.toUpperCase());
                default:
                    Log.w(TAG, "Unknown action: " + action);
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing step: " + action, e);
            return false;
        }
    }
    
    private String analyzeScreenState(String goal, String screenStateJson) {
        // Use Gemini to analyze the screen state and determine if goal is achieved
        final AtomicReference<String> resultRef = new AtomicReference<>("ANALYZING");
        final CountDownLatch latch = new CountDownLatch(1);
        
        String analysisPrompt = "Analyze if the goal \"" + goal + "\" has been achieved given this screen state: " + 
            screenStateJson.substring(0, Math.min(2000, screenStateJson.length())) + 
            ". Respond with 'GOAL_ACHIEVED' if the goal is complete, or 'CONTINUE' with a brief reason if not.";
        
        geminiClient.queryAgent(analysisPrompt, screenStateJson, new GeminiClient.GeminiCallback() {
            @Override
            public void onResponse(String toolName, JSONObject toolArgs) {
                String text = toolArgs.optString("text", "CONTINUE");
                resultRef.set(text);
                latch.countDown();
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error analyzing screen state", e);
                resultRef.set("CONTINUE");
                latch.countDown();
            }
        });
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return resultRef.get();
    }
    
    private void handleClick(JSONObject args, String callId) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GUIAgent.handleClick:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"GUIAgent.java:handleClick\",\"message\":\"Handling click\",\"data\":{\"callId\":\"" + 
                (callId != null ? callId : "null") + "\",\"nodeId\":\"" + args.optString("node_id", "") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
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
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GUIAgent.handleClick:RESULT " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"GUIAgent.java:handleClick\",\"message\":\"Click result\",\"data\":{\"callId\":\"" + 
                        (callId != null ? callId : "null") + "\",\"clicked\":" + clicked + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
                mainHandler.post(() -> {
                    try {
                        callback.onResult("gui_click", result, callId);
                        if (screenStateUpdater != null) {
                            screenStateUpdater.run();
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
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
                    try {
                        callback.onResult("gui_type", result, callId);
                        if (screenStateUpdater != null) {
                            screenStateUpdater.run();
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
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
                    try {
                        callback.onResult("gui_scroll", result, callId);
                        if (screenStateUpdater != null) {
                            screenStateUpdater.run();
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
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
                    try {
                        callback.onResult("gui_open_app", result, callId);
                        if (opened && screenStateUpdater != null) {
                            // Wait a bit then update screen state
                            mainHandler.postDelayed(screenStateUpdater, 1000);
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
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
