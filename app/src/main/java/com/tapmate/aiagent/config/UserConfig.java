package com.tapmate.aiagent.config;

/**
 * User configuration data class containing all personalization settings.
 * Combines TARS-style personality settings with accessibility-specific configurations.
 */
public class UserConfig {

    // === Personality Settings (from TARS) ===

    /**
     * Humor level (0-100).
     * 0 = Very serious, 100 = Maximum humor
     * Default: 70
     */
    private int humorPercentage = 70;

    /**
     * Honesty level (0-100).
     * 0 = Very diplomatic, 100 = Brutally honest
     * Default: 95
     */
    private int honestyPercentage = 95;

    /**
     * Communication style.
     * - "chatty": Verbose, detailed responses
     * - "normal": Balanced responses (default)
     * - "brief": Concise, direct responses
     */
    private String communicationStyle = "normal";

    /**
     * Nationality affects tone and cultural references.
     * Examples: "British", "American", "Australian"
     * Default: "British"
     */
    private String nationality = "British";

    /**
     * Voice selection for Gemini Live.
     * Options: "Puck", "Kore", "Charon"
     * Default: "Puck" (British)
     */
    private String voiceName = "Puck";

    // === Accessibility-Specific Settings ===

    /**
     * Haptic feedback intensity (0-100).
     * 0 = No haptic feedback, 100 = Maximum intensity
     * Default: 70
     */
    private int hapticIntensity = 70;

    /**
     * Screen reader verbosity level.
     * - "minimal": Brief descriptions
     * - "standard": Balanced detail (default)
     * - "detailed": Comprehensive descriptions
     */
    private String screenReaderVerbosity = "standard";

    /**
     * Vision detection sensitivity (0-100).
     * Higher = more sensitive object detection
     * Default: 50
     */
    private int visionSensitivity = 50;

    /**
     * Navigation guidance detail level.
     * - "landmarks": Landmark-based directions
     * - "full": Detailed turn-by-turn (default)
     * - "minimal": Essential directions only
     */
    private String navigationDetailLevel = "full";

    /**
     * Enable orbit mode for object tracking.
     * Default: true
     */
    private boolean orbitModeEnabled = true;

    /**
     * Orbit mode haptic pattern ID.
     * Different patterns for different directional cues.
     * Default: 1
     */
    private int orbitHapticPattern = 1;

    /**
     * Color scheme for UI.
     * - "high-contrast": High contrast colors (default)
     * - "standard": Standard colors
     * - "custom": Custom color scheme
     */
    private String colorScheme = "high-contrast";

    // === Getters and Setters ===

    public int getHumorPercentage() {
        return humorPercentage;
    }

    public void setHumorPercentage(int humorPercentage) {
        this.humorPercentage = Math.max(0, Math.min(100, humorPercentage));
    }

    public int getHonestyPercentage() {
        return honestyPercentage;
    }

    public void setHonestyPercentage(int honestyPercentage) {
        this.honestyPercentage = Math.max(0, Math.min(100, honestyPercentage));
    }

    public String getCommunicationStyle() {
        return communicationStyle;
    }

    public void setCommunicationStyle(String communicationStyle) {
        if ("chatty".equals(communicationStyle) || "normal".equals(communicationStyle) || "brief".equals(communicationStyle)) {
            this.communicationStyle = communicationStyle;
        }
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getVoiceName() {
        return voiceName;
    }

    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    public int getHapticIntensity() {
        return hapticIntensity;
    }

    public void setHapticIntensity(int hapticIntensity) {
        this.hapticIntensity = Math.max(0, Math.min(100, hapticIntensity));
    }

    public String getScreenReaderVerbosity() {
        return screenReaderVerbosity;
    }

    public void setScreenReaderVerbosity(String screenReaderVerbosity) {
        if ("minimal".equals(screenReaderVerbosity) || "standard".equals(screenReaderVerbosity) || "detailed".equals(screenReaderVerbosity)) {
            this.screenReaderVerbosity = screenReaderVerbosity;
        }
    }

    public int getVisionSensitivity() {
        return visionSensitivity;
    }

    public void setVisionSensitivity(int visionSensitivity) {
        this.visionSensitivity = Math.max(0, Math.min(100, visionSensitivity));
    }

    public String getNavigationDetailLevel() {
        return navigationDetailLevel;
    }

    public void setNavigationDetailLevel(String navigationDetailLevel) {
        if ("landmarks".equals(navigationDetailLevel) || "full".equals(navigationDetailLevel) || "minimal".equals(navigationDetailLevel)) {
            this.navigationDetailLevel = navigationDetailLevel;
        }
    }

    public boolean isOrbitModeEnabled() {
        return orbitModeEnabled;
    }

    public void setOrbitModeEnabled(boolean orbitModeEnabled) {
        this.orbitModeEnabled = orbitModeEnabled;
    }

    public int getOrbitHapticPattern() {
        return orbitHapticPattern;
    }

    public void setOrbitHapticPattern(int orbitHapticPattern) {
        this.orbitHapticPattern = orbitHapticPattern;
    }

    public String getColorScheme() {
        return colorScheme;
    }

    public void setColorScheme(String colorScheme) {
        if ("high-contrast".equals(colorScheme) || "standard".equals(colorScheme) || "custom".equals(colorScheme)) {
            this.colorScheme = colorScheme;
        }
    }

    @Override
    public String toString() {
        return "UserConfig{" +
                "humor=" + humorPercentage +
                ", honesty=" + honestyPercentage +
                ", style='" + communicationStyle + '\'' +
                ", nationality='" + nationality + '\'' +
                ", voice='" + voiceName + '\'' +
                ", haptic=" + hapticIntensity +
                ", verbosity='" + screenReaderVerbosity + '\'' +
                ", visionSensitivity=" + visionSensitivity +
                ", navDetail='" + navigationDetailLevel + '\'' +
                ", orbitMode=" + orbitModeEnabled +
                '}';
    }
}
