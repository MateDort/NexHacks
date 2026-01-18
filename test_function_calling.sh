#!/bin/bash

# Test script for TapMate function calling
# This will help diagnose why function calling isn't working

echo "======================================"
echo "TapMate Function Calling Test Script"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Step 1: Checking if TapMate is running...${NC}"
if adb shell pm list packages | grep -q "com.nexhacks.tapmate"; then
    echo -e "${GREEN}✓ TapMate is installed${NC}"
else
    echo -e "${RED}✗ TapMate is NOT installed${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}Step 2: Clearing logcat...${NC}"
adb logcat -c
echo -e "${GREEN}✓ Logcat cleared${NC}"

echo ""
echo -e "${YELLOW}Step 3: Starting logcat capture in background...${NC}"
adb logcat | grep -E "(GeminiLiveClient|SessionActivity|HYPOTHESIS|executableCode|functionCall)" > /tmp/tapmate_function_test.log &
LOGCAT_PID=$!
echo -e "${GREEN}✓ Logcat capture started (PID: $LOGCAT_PID)${NC}"

echo ""
echo -e "${YELLOW}Step 4: Waiting 5 seconds for app to stabilize...${NC}"
sleep 5

echo ""
echo "======================================"
echo "IMPORTANT: Now perform these actions:"
echo "1. Open TapMate on your phone"
echo "2. Start a voice session"
echo "3. Say: 'What's the weather?'"
echo "4. Wait 10 seconds for response"
echo "5. Press ENTER here when done"
echo "======================================"
read -p "Press ENTER when you've completed the test..."

echo ""
echo -e "${YELLOW}Step 5: Stopping logcat capture...${NC}"
kill $LOGCAT_PID 2>/dev/null
sleep 1
echo -e "${GREEN}✓ Logcat capture stopped${NC}"

echo ""
echo "======================================"
echo "ANALYSIS RESULTS"
echo "======================================"
echo ""

# Analyze the logs
LOG_FILE="/tmp/tapmate_function_test.log"

echo -e "${YELLOW}Checking for executableCode (BAD - means Python code generation):${NC}"
if grep -q "executableCode" "$LOG_FILE"; then
    echo -e "${RED}✗ FOUND executableCode - Gemini is generating Python code instead of calling functions!${NC}"
    echo "  Example:"
    grep -A 2 "executableCode" "$LOG_FILE" | head -5
    EXECUTABLE_CODE_FOUND=true
else
    echo -e "${GREEN}✓ No executableCode found${NC}"
    EXECUTABLE_CODE_FOUND=false
fi

echo ""
echo -e "${YELLOW}Checking for functionCall (GOOD - means proper function calling):${NC}"
if grep -q "functionCall" "$LOG_FILE"; then
    echo -e "${GREEN}✓ FOUND functionCall - Function calling is working!${NC}"
    echo "  Example:"
    grep -A 2 "functionCall" "$LOG_FILE" | head -5
    FUNCTION_CALL_FOUND=true
else
    echo -e "${RED}✗ No functionCall found - Functions are NOT being called${NC}"
    FUNCTION_CALL_FOUND=false
fi

echo ""
echo -e "${YELLOW}Checking for HYPOTHESIS logs:${NC}"
HYPOTHESIS_COUNT=$(grep -c "HYPOTHESIS" "$LOG_FILE")
echo "  Found $HYPOTHESIS_COUNT hypothesis log entries"

echo ""
echo "======================================"
echo "DIAGNOSIS"
echo "======================================"
echo ""

if [ "$EXECUTABLE_CODE_FOUND" = true ]; then
    echo -e "${RED}PROBLEM IDENTIFIED:${NC}"
    echo "Gemini is treating this as a CODE EXECUTION task, not FUNCTION CALLING."
    echo ""
    echo "This happens when:"
    echo "  1. Code execution mode is enabled in the model"
    echo "  2. Tools are not properly registered"
    echo "  3. System instructions mention 'code' or 'execute'"
    echo ""
    echo "FIX NEEDED: Disable code execution and properly configure function calling"
fi

if [ "$FUNCTION_CALL_FOUND" = false ]; then
    echo -e "${RED}PROBLEM: No function calls detected${NC}"
    echo "Possible causes:"
    echo "  1. Tools not sent in setup message"
    echo "  2. Model doesn't support function calling"
    echo "  3. Code execution is overriding function calling"
fi

echo ""
echo "Full log saved to: $LOG_FILE"
echo "View it with: cat $LOG_FILE"
echo ""
echo "======================================"
echo "Test complete!"
echo "======================================"
