#!/bin/bash

# Automated function calling test - taps the button and collects logs

echo "========================================"
echo "Automated TapMate Function Calling Test"
echo "========================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Kill any running TapMate
echo -e "${YELLOW}Step 1: Stopping any running instance...${NC}"
adb shell am force-stop com.nexhacks.tapmate
sleep 1
echo -e "${GREEN}✓ App stopped${NC}"

# Clear logs
echo ""
echo -e "${YELLOW}Step 2: Clearing logcat...${NC}"
adb logcat -c
echo -e "${GREEN}✓ Logcat cleared${NC}"

# Start MainActivity
echo ""
echo -e "${YELLOW}Step 3: Starting TapMate...${NC}"
adb shell am start -n com.nexhacks.tapmate/.ui.MainActivity
sleep 3
echo -e "${GREEN}✓ MainActivity started${NC}"

# Get screen size and calculate tap coordinates (center of screen)
echo ""
echo -e "${YELLOW}Step 4: Getting screen size...${NC}"
SCREEN_SIZE=$(adb shell wm size | grep "Physical size" | cut -d: -f2 | tr -d ' ')
WIDTH=$(echo $SCREEN_SIZE | cut -dx -f1)
HEIGHT=$(echo $SCREEN_SIZE | cut -dx -f2)
TAP_X=$((WIDTH / 2))
TAP_Y=$((HEIGHT / 2))
echo -e "${GREEN}✓ Screen: ${WIDTH}x${HEIGHT}, will tap at (${TAP_X}, ${TAP_Y})${NC}"

# Start logcat in background
echo ""
echo -e "${YELLOW}Step 5: Starting logcat capture...${NC}"
adb logcat > /tmp/tapmate_auto_test.log &
LOGCAT_PID=$!
echo -e "${GREEN}✓ Logcat capture started (PID: $LOGCAT_PID)${NC}"

# Simulate tap on microphone button (center of screen)
echo ""
echo -e "${YELLOW}Step 6: Simulating tap on microphone button...${NC}"
adb shell input tap $TAP_X $TAP_Y
sleep 2
echo -e "${GREEN}✓ Tap simulated${NC}"

# Wait for session to initialize
echo ""
echo -e "${YELLOW}Step 7: Waiting 10 seconds for session to initialize...${NC}"
sleep 10
echo -e "${GREEN}✓ Wait complete${NC}"

# Stop logcat
echo ""
echo -e "${YELLOW}Step 8: Stopping logcat...${NC}"
kill $LOGCAT_PID 2>/dev/null
sleep 1
echo -e "${GREEN}✓ Logcat stopped${NC}"

# Analyze logs
echo ""
echo "========================================"
echo "ANALYSIS"
echo "========================================"
echo ""

LOG_FILE="/tmp/tapmate_auto_test.log"

# Check if SessionActivity started
echo -e "${YELLOW}Checking if SessionActivity started:${NC}"
if grep -q "SessionActivity" "$LOG_FILE"; then
    echo -e "${GREEN}✓ SessionActivity logs found${NC}"
    echo "  Sample:"
    grep "SessionActivity" "$LOG_FILE" | head -3
    SESSION_STARTED=true
else
    echo -e "${RED}✗ No SessionActivity logs found${NC}"
    SESSION_STARTED=false
fi

echo ""

# Check if GeminiLiveClient connected
echo -e "${YELLOW}Checking if GeminiLiveClient connected:${NC}"
if grep -q "GeminiLiveClient" "$LOG_FILE"; then
    echo -e "${GREEN}✓ GeminiLiveClient logs found${NC}"
    echo "  Sample:"
    grep "GeminiLiveClient" "$LOG_FILE" | head -3
    GEMINI_CONNECTED=true
else
    echo -e "${RED}✗ No GeminiLiveClient logs found${NC}"
    GEMINI_CONNECTED=false
fi

echo ""

# Check for function calls
echo -e "${YELLOW}Checking for function calls:${NC}"
if grep -q "functionCall\|FUNCTION_CALL" "$LOG_FILE"; then
    echo -e "${GREEN}✓ Function calls detected!${NC}"
    echo "  Examples:"
    grep -i "functioncall" "$LOG_FILE" | head -3
    FUNCTION_CALLS_FOUND=true
else
    echo -e "${RED}✗ No function calls detected${NC}"
    FUNCTION_CALLS_FOUND=false
fi

echo ""

# Check for executable code (bad)
echo -e "${YELLOW}Checking for executableCode (should NOT appear):${NC}"
if grep -q "executableCode" "$LOG_FILE"; then
    echo -e "${RED}✗ executableCode found - still generating Python code!${NC}"
    grep "executableCode" "$LOG_FILE" | head -3
    EXECUTABLE_CODE_FOUND=true
else
    echo -e "${GREEN}✓ No executableCode found${NC}"
    EXECUTABLE_CODE_FOUND=false
fi

echo ""

# Check for errors
echo -e "${YELLOW}Checking for errors:${NC}"
ERROR_COUNT=$(grep -i "error\|exception\|crash" "$LOG_FILE" | grep -i "tapmate\|sessionactivity\|gemini" | wc -l | tr -d ' ')
if [ "$ERROR_COUNT" -gt 0 ]; then
    echo -e "${RED}✗ Found $ERROR_COUNT errors${NC}"
    echo "  Sample errors:"
    grep -i "error\|exception" "$LOG_FILE" | grep -i "tapmate\|sessionactivity\|gemini" | head -5
else
    echo -e "${GREEN}✓ No errors found${NC}"
fi

echo ""
echo "========================================"
echo "VERDICT"
echo "========================================"
echo ""

if [ "$SESSION_STARTED" = false ]; then
    echo -e "${RED}FAIL: Session did not start${NC}"
    echo "The SessionActivity is not initializing. Check:"
    echo "  1. Is the app installed correctly?"
    echo "  2. Is the tap coordinate correct?"
    echo "  3. Are there permission issues?"
elif [ "$GEMINI_CONNECTED" = false ]; then
    echo -e "${RED}FAIL: GeminiLiveClient did not connect${NC}"
    echo "SessionActivity started but Gemini didn't connect. Check:"
    echo "  1. API key in env file"
    echo "  2. Network connectivity"
    echo "  3. WebSocket initialization"
elif [ "$EXECUTABLE_CODE_FOUND" = true ]; then
    echo -e "${RED}FAIL: Still generating executable code${NC}"
    echo "The fix didn't work - model is still in code execution mode"
elif [ "$FUNCTION_CALLS_FOUND" = true ]; then
    echo -e "${GREEN}SUCCESS: Function calling is working!${NC}"
    echo "✓ Session started"
    echo "✓ Gemini connected"
    echo "✓ Function calls detected"
    echo "✓ No executable code generation"
else
    echo -e "${YELLOW}INCONCLUSIVE: Session started but no activity detected${NC}"
    echo "The session started but we didn't see function calls."
    echo "This might be because:"
    echo "  1. The test didn't trigger any functions (no user speech)"
    echo "  2. The model isn't calling functions when it should"
    echo "  3. Need to wait longer or provide input"
fi

echo ""
echo "Full log saved to: $LOG_FILE"
echo "View with: cat $LOG_FILE | grep -i 'tapmate\|session\|gemini'"
echo ""
