package com.tapmate.aiagent.core;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.tapmate.aiagent.agents.GUIAgent;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentAccessibilityService - Core accessibility service for TapMate
 *
 * Provides GUI control capabilities for visually impaired users:
 * - Screen reading and element detection
 * - Touch simulation (click, long press, swipe)
 * - Text input via accessibility
 * - Navigation controls (back, home)
 *
 * Implements GUIAgent.GUIActionListener to receive commands from the trained model.
 */
public class AgentAccessibilityService extends AccessibilityService implements GUIAgent.GUIActionListener {

    private static final String TAG = "AgentAccessibilityService";
    private static AgentAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        GUIAgent.setActionListener(this);
        Log.i(TAG, "AgentAccessibilityService created and registered with GUIAgent");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        GUIAgent.setActionListener(null);
        Log.i(TAG, "AgentAccessibilityService destroyed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Listen for accessibility events (window changes, focus changes, etc.)
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Window changed: " + event.getPackageName());
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
    }

    public static AgentAccessibilityService getInstance() {
        return instance;
    }

    // ======================== GUIActionListener Implementation ========================

    @Override
    public boolean clickOnElement(String nodeId) {
        try {
            Log.i(TAG, "Attempting to click on: " + nodeId);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "Root node is null");
                return false;
            }

            AccessibilityNodeInfo target = findNodeByIdentifier(root, nodeId);
            if (target == null) {
                Log.e(TAG, "Target node not found: " + nodeId);
                root.recycle();
                return false;
            }

            boolean success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!success) {
                // Try gesture-based click
                Rect bounds = new Rect();
                target.getBoundsInScreen(bounds);
                success = performTapGesture(bounds.centerX(), bounds.centerY());
            }

            target.recycle();
            root.recycle();

            Log.i(TAG, "Click " + (success ? "successful" : "failed") + " on: " + nodeId);
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error clicking element", e);
            return false;
        }
    }

    @Override
    public boolean typeText(String text) {
        try {
            Log.i(TAG, "Attempting to type text: " + text);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "Root node is null");
                return false;
            }

            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) {
                Log.e(TAG, "No focused input field found");
                root.recycle();
                return false;
            }

            // Set text via accessibility
            android.os.Bundle bundle = new android.os.Bundle();
            bundle.putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);

            focused.recycle();
            root.recycle();

            Log.i(TAG, "Type text " + (success ? "successful" : "failed"));
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error typing text", e);
            return false;
        }
    }

    @Override
    public boolean scrollDown() {
        try {
            Log.i(TAG, "Attempting to scroll down");
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "Root node is null");
                return false;
            }

            AccessibilityNodeInfo scrollable = findScrollableNode(root);
            if (scrollable == null) {
                Log.e(TAG, "No scrollable node found");
                root.recycle();
                return false;
            }

            boolean success = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            scrollable.recycle();
            root.recycle();

            Log.i(TAG, "Scroll down " + (success ? "successful" : "failed"));
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error scrolling down", e);
            return false;
        }
    }

    @Override
    public boolean scrollUp() {
        try {
            Log.i(TAG, "Attempting to scroll up");
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "Root node is null");
                return false;
            }

            AccessibilityNodeInfo scrollable = findScrollableNode(root);
            if (scrollable == null) {
                Log.e(TAG, "No scrollable node found");
                root.recycle();
                return false;
            }

            boolean success = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            scrollable.recycle();
            root.recycle();

            Log.i(TAG, "Scroll up " + (success ? "successful" : "failed"));
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error scrolling up", e);
            return false;
        }
    }

    @Override
    public boolean goHome() {
        try {
            Log.i(TAG, "Attempting to go home");
            return performGlobalAction(GLOBAL_ACTION_HOME);
        } catch (Exception e) {
            Log.e(TAG, "Error going home", e);
            return false;
        }
    }

    @Override
    public boolean goBack() {
        try {
            Log.i(TAG, "Attempting to go back");
            return performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            Log.e(TAG, "Error going back", e);
            return false;
        }
    }

    @Override
    public boolean performSwipe(String direction) {
        try {
            Log.i(TAG, "Attempting to swipe " + direction);
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;

            int startX, startY, endX, endY;

            switch (direction.toLowerCase()) {
                case "left":
                    startX = width * 3 / 4;
                    startY = height / 2;
                    endX = width / 4;
                    endY = height / 2;
                    break;
                case "right":
                    startX = width / 4;
                    startY = height / 2;
                    endX = width * 3 / 4;
                    endY = height / 2;
                    break;
                case "up":
                    startX = width / 2;
                    startY = height * 3 / 4;
                    endX = width / 2;
                    endY = height / 4;
                    break;
                case "down":
                    startX = width / 2;
                    startY = height / 4;
                    endX = width / 2;
                    endY = height * 3 / 4;
                    break;
                default:
                    Log.e(TAG, "Unknown swipe direction: " + direction);
                    return false;
            }

            return performSwipeGesture(startX, startY, endX, endY, 300);

        } catch (Exception e) {
            Log.e(TAG, "Error performing swipe", e);
            return false;
        }
    }

    @Override
    public boolean performLongPress(String nodeId) {
        try {
            Log.i(TAG, "Attempting to long press on: " + nodeId);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "Root node is null");
                return false;
            }

            AccessibilityNodeInfo target = findNodeByIdentifier(root, nodeId);
            if (target == null) {
                Log.e(TAG, "Target node not found: " + nodeId);
                root.recycle();
                return false;
            }

            boolean success = target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            target.recycle();
            root.recycle();

            Log.i(TAG, "Long press " + (success ? "successful" : "failed") + " on: " + nodeId);
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error performing long press", e);
            return false;
        }
    }

    @Override
    public String readScreen() {
        try {
            Log.i(TAG, "Attempting to read screen");
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "Root node is null");
                return "";
            }

            StringBuilder content = new StringBuilder();
            extractTextFromNode(root, content);
            root.recycle();

            String result = content.toString().trim();
            Log.i(TAG, "Screen content read: " + result.length() + " characters");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error reading screen", e);
            return "";
        }
    }

    // ======================== Helper Methods ========================

    private AccessibilityNodeInfo findNodeByIdentifier(AccessibilityNodeInfo root, String identifier) {
        if (root == null) return null;

        // Try to find by text
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(identifier);
        if (nodes != null && !nodes.isEmpty()) {
            return nodes.get(0);
        }

        // Try to find by viewId
        nodes = root.findAccessibilityNodeInfosByViewId(identifier);
        if (nodes != null && !nodes.isEmpty()) {
            return nodes.get(0);
        }

        // Try to find by content description
        return findNodeByContentDescription(root, identifier);
    }

    private AccessibilityNodeInfo findNodeByContentDescription(AccessibilityNodeInfo node, String description) {
        if (node == null) return null;

        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.toString().contains(description)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeByContentDescription(child, description);
                if (result != null) {
                    return result;
                }
                child.recycle();
            }
        }

        return null;
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if (node.isScrollable()) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findScrollableNode(child);
                if (result != null) {
                    return result;
                }
                child.recycle();
            }
        }

        return null;
    }

    private void extractTextFromNode(AccessibilityNodeInfo node, StringBuilder content) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            content.append(text).append(" ");
        }

        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            content.append(contentDesc).append(" ");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractTextFromNode(child, content);
                child.recycle();
            }
        }
    }

    private boolean performTapGesture(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));

        return dispatchGesture(builder.build(), null, null);
    }

    private boolean performSwipeGesture(int startX, int startY, int endX, int endY, long duration) {
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));

        return dispatchGesture(builder.build(), null, null);
    }
}
