#!/bin/bash

# Validates that function responses are properly returned
# Analyzes existing test logs to check response quality

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================"
echo "Function Response Validator"
echo "========================================"
echo ""

LOG_FILE="/tmp/tapmate_function_test.log"

if [ ! -f "$LOG_FILE" ]; then
    echo -e "${RED}Error: No log file found at $LOG_FILE${NC}"
    echo "Run test_function_calling.sh first to generate logs"
    exit 1
fi

echo "Analyzing log file: $LOG_FILE"
echo ""

# Extract all function calls and their responses
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Function Calls Detected${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Find all toolCall entries
FUNCTION_COUNT=0
while IFS= read -r line; do
    if echo "$line" | grep -q "toolCall function:"; then
        FUNCTION_COUNT=$((FUNCTION_COUNT + 1))
        FUNCTION_NAME=$(echo "$line" | sed -n 's/.*toolCall function: \([^ ]*\).*/\1/p')
        FUNCTION_ID=$(echo "$line" | sed -n 's/.*id: \([^ ]*\).*/\1/p')
        
        echo -e "${GREEN}${FUNCTION_COUNT}. Function: ${FUNCTION_NAME}${NC}"
        echo "   Call ID: ${FUNCTION_ID}"
        
        # Look for execution
        if grep -q "toolCall function executed: ${FUNCTION_NAME}" "$LOG_FILE"; then
            echo -e "   ${GREEN}✓ Executed${NC}"
        else
            echo -e "   ${RED}✗ NOT executed${NC}"
        fi
        
        # Look for response
        RESPONSE_LINE=$(grep -A 20 "Function response JSON:.*${FUNCTION_ID}" "$LOG_FILE" | grep '"response"' | head -1)
        if [ ! -z "$RESPONSE_LINE" ]; then
            echo -e "   ${GREEN}✓ Response sent${NC}"
            
            # Extract and display response content
            RESPONSE=$(echo "$RESPONSE_LINE" | sed 's/.*"response":\s*{\(.*\)}.*/\1/' | head -c 200)
            echo "   Response: ${RESPONSE}..."
            
            # Validate response has content
            if echo "$RESPONSE" | grep -q "result\|error\|data"; then
                echo -e "   ${GREEN}✓ Response has valid content${NC}"
            else
                echo -e "   ${YELLOW}⚠ Response may be empty${NC}"
            fi
        else
            echo -e "   ${RED}✗ No response found${NC}"
        fi
        
        echo ""
    fi
done < "$LOG_FILE"

if [ $FUNCTION_COUNT -eq 0 ]; then
    echo -e "${YELLOW}No function calls detected in log${NC}"
    echo ""
    echo "Possible reasons:"
    echo "  - No voice input provided during test"
    echo "  - Functions not being called by Gemini"
    echo "  - Wrong log file being analyzed"
    exit 1
fi

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Response Quality Analysis${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check response completeness
RESPONSES_SENT=$(grep -c "Function response JSON:" "$LOG_FILE" || echo 0)
RESPONSES_WITH_CONTENT=$(grep "Function response JSON:" "$LOG_FILE" | grep -c '"result"\|"error"\|"data"' || echo 0)

echo "Total functions called: ${FUNCTION_COUNT}"
echo "Responses sent: ${RESPONSES_SENT}"
echo "Responses with content: ${RESPONSES_WITH_CONTENT}"
echo ""

if [ $RESPONSES_SENT -eq $FUNCTION_COUNT ]; then
    echo -e "${GREEN}✓ All functions sent responses${NC}"
else
    echo -e "${RED}✗ Some functions did not send responses${NC}"
    MISSING=$((FUNCTION_COUNT - RESPONSES_SENT))
    echo "   Missing responses: ${MISSING}"
fi

echo ""

if [ $RESPONSES_WITH_CONTENT -eq $RESPONSES_SENT ]; then
    echo -e "${GREEN}✓ All responses have content${NC}"
elif [ $RESPONSES_WITH_CONTENT -gt 0 ]; then
    echo -e "${YELLOW}⚠ Some responses may be empty${NC}"
else
    echo -e "${RED}✗ No responses have valid content${NC}"
fi

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Gemini's Response${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if Gemini sent audio back after receiving function response
AUDIO_RESPONSES=$(grep -c "Extracted audio from serverContent" "$LOG_FILE" || echo 0)
echo "Audio responses from Gemini: ${AUDIO_RESPONSES}"

if [ $AUDIO_RESPONSES -gt 0 ]; then
    echo -e "${GREEN}✓ Gemini is sending audio responses${NC}"
else
    echo -e "${YELLOW}⚠ No audio responses detected${NC}"
fi

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Sample Responses${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Show a few sample responses
grep "Function response JSON:" "$LOG_FILE" | head -3 | while IFS= read -r line; do
    echo "$line" | sed 's/^.*Function response JSON: //' | python3 -m json.tool 2>/dev/null || echo "$line"
    echo ""
done

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Verdict${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

SUCCESS_RATE=$((RESPONSES_WITH_CONTENT * 100 / FUNCTION_COUNT))

if [ $SUCCESS_RATE -eq 100 ]; then
    echo -e "${GREEN}🎉 PERFECT! All functions returned valid responses!${NC}"
elif [ $SUCCESS_RATE -ge 80 ]; then
    echo -e "${GREEN}✓ GOOD! Most functions returned valid responses (${SUCCESS_RATE}%)${NC}"
elif [ $SUCCESS_RATE -ge 50 ]; then
    echo -e "${YELLOW}⚠ PARTIAL: Some functions returned responses (${SUCCESS_RATE}%)${NC}"
else
    echo -e "${RED}✗ POOR: Most functions did not return valid responses (${SUCCESS_RATE}%)${NC}"
fi

echo ""
