import asyncio
import websockets
import json
import base64
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

async def run_diagnostic():
    uri = "ws://localhost:8765"
    logger.info(f"Connecting to bridge at {uri}...")
    
    try:
        async with websockets.connect(uri) as websocket:
            logger.info("✅ Connected to bridge")
            
            # Wait for 'ready' signal
            while True:
                msg = await websocket.recv()
                data = json.loads(msg)
                logger.info(f"Received from bridge: {data.get('type')}")
                if data.get('type') == 'ready':
                    break
            
            logger.info("Bridge is ready. Sending text message...")
            
            # Send a text message to test connectivity
            test_msg = {
                "type": "text",
                "text": "Hello Gemini! If you can hear me, please tell me the current time and say 'System test successful'."
            }
            await websocket.send(json.dumps(test_msg))
            logger.info(f"Sent: {test_msg['text']}")
            
            # Listen for responses
            audio_count = 0
            transcript_received = False
            
            timeout = 15 # Wait up to 15 seconds
            try:
                while True:
                    msg = await asyncio.wait_for(websocket.recv(), timeout=timeout)
                    data = json.loads(msg)
                    msg_type = data.get('type')
                    
                    if msg_type == 'audio':
                        audio_count += 1
                        if audio_count % 10 == 0:
                            logger.info(f"Received {audio_count} audio chunks...")
                    elif msg_type == 'transcript':
                        logger.info(f"🎤 {data.get('role').upper()}: {data.get('text')}")
                        if data.get('role') == 'ai' or 'successful' in data.get('text').lower():
                            transcript_received = True
                    elif msg_type == 'interrupted':
                        logger.warning("⚠️ Interruption detected!")
                    else:
                        logger.info(f"Received: {data}")
                        
                    # Stop after we get a meaningful response or a long pause
                    if transcript_received and audio_count > 0:
                        # Give it a tiny bit more time to finish streaming
                        await asyncio.sleep(2)
                        break
            except asyncio.TimeoutError:
                logger.warning("Test timed out waiting for response.")
                
            logger.info("--- Diagnostic Result ---")
            logger.info(f"Audio chunks received: {audio_count}")
            logger.info(f"Transcript received: {transcript_received}")
            
            if audio_count > 0 and transcript_received:
                logger.info("✅ FINAL RESULT: CORE SYSTEM IS FUNCTIONAL. The issue is likely in the voice/microphone layer.")
            else:
                logger.error("❌ FINAL RESULT: CORE SYSTEM FAILED. Check Gemini API keys and Bridge configuration.")

    except Exception as e:
        logger.error(f"Connection failed: {e}")

if __name__ == "__main__":
    asyncio.run(run_diagnostic())
