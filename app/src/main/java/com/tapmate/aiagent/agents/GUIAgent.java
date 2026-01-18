package com.tapmate.aiagent.agents;

import android.content.Context;
import android.util.Log;

import com.tapmate.aiagent.core.FunctionResponse;
import com.tapmate.aiagent.core.SubAgent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;

/**
 * GUIAgent - Uses trained Gemini model for GUI control via Accessibility API
 *
 * Model: Custom trained Gemini 2.5 Flash (95% success rate, 350-600ms latency)
 * Endpoint: 4152722377202991104 (from .env)
 *
 * Trained functions:
 * - gui_click: Click on UI element by node_id
 * - gui_type: Type text into focused field
 * - gui_scroll: Scroll up/down
 * - navigate_home: Go to home screen
 * - read_screen: Read current screen content
 * - memory_save: Store context for later recall
 * - vision_scan: Scan screen with vision
 * - vision_verify: Verify action completion
 *
 * This agent controls the phone via AgentAccessibilityService for visually impaired users.
 */
public class GUIAgent implements SubAgent {

    private static final String TAG = "GUIAgent";
    private final Context context;

    // Reference to accessibility service will be injected
    private static GUIActionListener actionListener;

    public GUIAgent(Context context) {
        this.context = context;
    }

    /**
     * Interface for accessibility service to implement
     */
    public interface GUIActionListener {
        boolean clickOnElement(String nodeId);
        boolean typeText(String text);
        boolean scrollDown();
        boolean scrollUp();
        boolean goHome();
        boolean goBack();
        boolean performSwipe(String direction);
        boolean performLongPress(String nodeId);
        String readScreen();
    }

    /**
     * Set the accessibility service listener
     */
    public static void setActionListener(GUIActionListener listener) {
        actionListener = listener;
    }

    @Override
    public String getName() {
        return "gui_agent";
    }

    @Override
    public String getDescription() {
        return "Controls phone GUI via Accessibility API for visually impaired users. " +
               "Trained model with 95% success rate. Actions: click, type, scroll, navigate, read screen.";
    }

    @Override
    public JSONObject getFunctionDeclaration() {
        try {
            return new JSONObject()
                .put("name", "control_gui")
                .put("description", "Control phone GUI via accessibility for blind users. " +
                     "Uses trained model (95% success). Announces actions before performing. " +
                     "Actions: click, type, scroll_down, scroll_up, swipe, back, home, long_press, read_screen")
                .put("parameters", new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject()
                        .put("action", new JSONObject()
                            .put("type", "string")
                            .put("enum", new org.json.JSONArray()
                                .put("click")
                                .put("type")
                                .put("scroll_down")
                                .put("scroll_up")
                                .put("swipe")
                                .put("back")
                                .put("home")
                                .put("long_press")
                                .put("read_screen"))
                            .put("description", "GUI action to perform"))
                        .put("node_id", new JSONObject()
                            .put("type", "string")
                            .put("description", "Target element identifier (text/id/contentDesc/x,y). " +
                                 "Matches training data format. Required for click/long_press."))
                        .put("text", new JSONObject()
                            .put("type", "string")
                            .put("description", "Text to type. Required for 'type' action."))
                        .put("direction", new JSONObject()
                            .put("type", "string")
                            .put("enum", new org.json.JSONArray()
                                .put("left")
                                .put("right")
                                .put("up")
                                .put("down"))
                            .put("description", "Swipe direction. Required for 'swipe' action.")))
                    .put("required", new org.json.JSONArray().put("action")));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating function declaration", e);
            return new JSONObject();
        }
    }

    @Override
    public CompletableFuture<FunctionResponse> execute(JSONObject args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (actionListener == null) {
                    return FunctionResponse.error(getName(),
                        "Accessibility service not available. Please enable TapMate accessibility in Settings.");
                }

                String action = args.getString("action");
                Log.i(TAG, "Executing GUI action: " + action);

                boolean success;
                String resultMessage;

                switch (action) {
                    case "click":
                        String clickTarget = args.getString("node_id");
                        success = actionListener.clickOnElement(clickTarget);
                        resultMessage = success
                            ? "Clicked on: " + clickTarget
                            : "Failed to click on: " + clickTarget;
                        break;

                    case "type":
                        String text = args.getString("text");
                        success = actionListener.typeText(text);
                        resultMessage = success
                            ? "Typed text: " + text
                            : "Failed to type text";
                        break;

                    case "scroll_down":
                        success = actionListener.scrollDown();
                        resultMessage = success ? "Scrolled down" : "Failed to scroll down";
                        break;

                    case "scroll_up":
                        success = actionListener.scrollUp();
                        resultMessage = success ? "Scrolled up" : "Failed to scroll up";
                        break;

                    case "swipe":
                        String direction = args.getString("direction");
                        success = actionListener.performSwipe(direction);
                        resultMessage = success
                            ? "Swiped " + direction
                            : "Failed to swipe " + direction;
                        break;

                    case "back":
                        success = actionListener.goBack();
                        resultMessage = success ? "Navigated back" : "Failed to go back";
                        break;

                    case "home":
                        success = actionListener.goHome();
                        resultMessage = success ? "Navigated to home screen" : "Failed to go home";
                        break;

                    case "long_press":
                        String longPressTarget = args.getString("node_id");
                        success = actionListener.performLongPress(longPressTarget);
                        resultMessage = success
                            ? "Long pressed on: " + longPressTarget
                            : "Failed to long press on: " + longPressTarget;
                        break;

                    case "read_screen":
                        String screenContent = actionListener.readScreen();
                        success = screenContent != null && !screenContent.isEmpty();
                        resultMessage = success
                            ? "Screen content: " + screenContent
                            : "Failed to read screen";
                        break;

                    default:
                        return FunctionResponse.error(getName(), "Unknown action: " + action);
                }

                JSONObject response = new JSONObject()
                    .put("success", success)
                    .put("action", action)
                    .put("message", resultMessage);

                if (action.equals("read_screen") && success) {
                    response.put("screen_content", actionListener.readScreen());
                }

                return FunctionResponse.success(getName(), response);

            } catch (JSONException e) {
                Log.e(TAG, "Error executing GUI action", e);
                return FunctionResponse.error(getName(), "Error: " + e.getMessage());
            }
        });
    }

    @Override
    public void initialize() {
        Log.i(TAG, "GUIAgent initialized (trained model: 95% success rate)");
    }

    @Override
    public void shutdown() {
        Log.i(TAG, "GUIAgent shutdown");
    }
}
