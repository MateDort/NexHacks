"""
WebSocket bridge server between Android app and Gemini Live API.
Uses official google-genai Python SDK (like TARS) for reliable communication.
"""
import asyncio
import json
import logging
import os
import base64
from typing import Optional, Dict
from dotenv import load_dotenv
import websockets
from google import genai
from google.genai import types

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class GeminiBridge:
    """Bridge between Android WebSocket client and Gemini Live API."""
    
    def __init__(self, api_key: str):
        """Initialize the bridge with Gemini API key."""
        self.api_key = api_key
        self.client = genai.Client(
            http_options={"api_version": "v1beta"},
            api_key=api_key
        )
        # Recommended model for Gemini Live
        self.model = "models/gemini-2.0-flash-exp"
        
        # Gemini session
        self.gemini_session = None
        self._session_context = None
        self.is_connected = False
        
        # Android client
        self.android_websocket = None
        
        # Audio buffering
        self.audio_buffer = bytearray()
        self.buffer_size = 12288  # Approx 384ms (1024 * 12) - balanced for Gemini VAD
        
        # Function registry
        self.function_handlers = {}
        self.function_declarations = []
        
    def register_function(self, declaration: Dict, handler):
        """Register a function that Gemini can call."""
        self.function_declarations.append(declaration)
        self.function_handlers[declaration["name"]] = handler
        logger.info(f"Registered function: {declaration['name']}")
    
    async def start_gemini_session(self, system_instruction: str):
        """Start a Gemini Live API session using official SDK."""
        try:
            # Build configuration
            config = types.LiveConnectConfig(
                speech_config=types.SpeechConfig(
                    voice_config=types.VoiceConfig(
                        prebuilt_voice_config=types.PrebuiltVoiceConfig(
                            voice_name="Puck"
                        )
                    )
                ),
                system_instruction=types.Content(
                    parts=[types.Part(text=system_instruction)]
                ),
                response_modalities=["AUDIO"],
                input_audio_transcription=types.AudioTranscriptionConfig(),
                output_audio_transcription=types.AudioTranscriptionConfig()
            )
            
            # Add tools if we have functions
            if self.function_declarations:
                config.tools = [
                    types.Tool(google_search=types.GoogleSearch()),
                    types.Tool(function_declarations=self.function_declarations)
                ]
            else:
                config.tools = [types.Tool(google_search=types.GoogleSearch())]
            
            # Connect to Gemini Live API
            logger.info(f"Connecting to {self.model}...")
            self._session_context = self.client.aio.live.connect(
                model=self.model,
                config=config
            )
            
            self.gemini_session = await self._session_context.__aenter__()
            self.is_connected = True
            
            logger.info("✅ Connected to Gemini Live API")
            
            # Start receiving responses from Gemini
            asyncio.create_task(self._receive_from_gemini())
            
        except Exception as e:
            logger.error(f"Failed to connect to Gemini: {e}")
            raise
    
    async def _receive_from_gemini(self):
        """Receive responses from Gemini and forward to Android."""
        logger.info("Started Gemini receive loop")
        try:
            while self.is_connected and self.gemini_session:
                try:
                    async for response in self.gemini_session.receive():
                        # Handle audio output in model_turn
                        if response.server_content and response.server_content.model_turn:
                            for part in response.server_content.model_turn.parts:
                                if part.inline_data:
                                    audio_data = part.inline_data.data
                                    logger.debug(f"Received audio from Gemini: {len(audio_data)} bytes")
                                    await self._send_to_android({
                                        "type": "audio",
                                        "data": base64.b64encode(audio_data).decode('utf-8')
                                    })
                                if part.text:
                                    logger.info(f"🎤 AI (Part): {part.text}")
                                    await self._send_to_android({
                                        "type": "transcript",
                                        "role": "ai",
                                        "text": part.text
                                    })
                        
                        # Handle transcriptions and events
                        if response.server_content:
                            if response.server_content.turn_complete:
                                logger.info("Gemini signaled FULL Turn Complete - waiting for user...")
                            
                            if response.server_content.interrupted:
                                logger.warning("⚠️ Gemini reported interruption - stopping playback")
                                await self._send_to_android({"type": "interrupted"})
                            
                            if response.server_content.input_transcription:
                                logger.info(f"🎤 User (Transcribed): {response.server_content.input_transcription}")
                                await self._send_to_android({
                                    "type": "transcript",
                                    "role": "user",
                                    "text": response.server_content.input_transcription
                                })
                            
                            if response.server_content.output_transcription:
                                ai_text = response.server_content.output_transcription.text
                                logger.info(f"🎤 AI (Full Transcript): {ai_text}")
                                await self._send_to_android({
                                    "type": "transcript",
                                    "role": "ai",
                                    "text": ai_text
                                })

                        # Handle Tool Calls
                        if response.tool_call:
                            await self._handle_function_call(response.tool_call)

                    logger.debug("Gemini turn yielded, restarting receive loop...")
                except websockets.exceptions.ConnectionClosedOK:
                    logger.info("Gemini session finished normally (OK)")
                    break
        except Exception as e:
            logger.error(f"Fatal error in Gemini receive loop: {e}", exc_info=True)
        finally:
            self.is_connected = False
            logger.info("Gemini receive loop ended")
    
    async def _handle_function_call(self, tool_call):
        """Execute function calls from Gemini."""
        for func_call in tool_call.function_calls:
            func_name = func_call.name
            func_args = dict(func_call.args) if func_call.args else {}
            call_id = func_call.id
            
            logger.info(f"Function call: {func_name}({func_args}) id={call_id}")
            
            if func_name in self.function_handlers:
                try:
                    # Execute handler (could be sync or async)
                    handler = self.function_handlers[func_name]
                    if asyncio.iscoroutinefunction(handler):
                        result = await handler(func_args)
                    else:
                        result = handler(func_args)
                    
                    # Send result back to Gemini
                    await self.gemini_session.send_tool_response(
                        function_responses=[
                            types.FunctionResponse(
                                id=call_id,
                                name=func_name,
                                response=result
                            )
                        ]
                    )
                except Exception as e:
                    logger.error(f"Error executing {func_name}: {e}")
    
    async def send_audio_to_gemini(self, audio_data: bytes):
        """Send audio from Android to Gemini with buffering."""
        if not self.is_connected or not self.gemini_session:
            return
        
        self.audio_buffer.extend(audio_data)
        
        if len(self.audio_buffer) >= self.buffer_size:
            chunk_to_send = bytes(self.audio_buffer)
            self.audio_buffer.clear()
            
            try:
                # Calculate RMS for debugging
                import math
                import struct
                count = len(chunk_to_send) // 2
                shorts = struct.unpack(f"<{count}h", chunk_to_send)
                rms = math.sqrt(sum(s*s for s in shorts) / count) if count > 0 else 0
                
                if rms > 50:
                    logger.info(f"🎤 Forwarding Audio: {len(chunk_to_send)} bytes (RMS: {rms:.1f})")
                else:
                    logger.debug(f"🔇 Silent/Quiet Forwarded: {len(chunk_to_send)} bytes (RMS: {rms:.1f})")
                
                # Use 'audio' parameter with 'audio/pcm' for maximum compatibility
                await self.gemini_session.send_realtime_input(
                    audio=types.Blob(
                        data=chunk_to_send, 
                        mime_type="audio/pcm;rate=16000"
                    )
                )
            except Exception as e:
                logger.error(f"Error sending audio to Gemini: {e}")
    
    async def _send_to_android(self, message: dict):
        """Send message to Android client."""
        if self.android_websocket:
            try:
                await self.android_websocket.send(json.dumps(message))
            except Exception as e:
                logger.error(f"Error sending to Android: {e}")
    
    async def handle_android_client(self, websocket):
        """Handle WebSocket connection from Android app."""
        self.android_websocket = websocket
        logger.info(f"Android client connected from {websocket.remote_address}")
        
        try:
            # Start Gemini session
            system_instruction = """You are TapMate, an intelligent assistant for visually impaired users.
            Be concise, helpful, and proactive. You can access Google Search and call functions to help the user."""
            
            logger.info("Initializing Gemini session via SDK...")
            await self.start_gemini_session(system_instruction)
            
            # Send ready signal to Android
            logger.info("Sending 'ready' signal to Android client")
            await self._send_to_android({"type": "ready"})
            
            # Send initial greeting to Gemini to "warm up" the session
            logger.info("Sending initial greeting to Gemini...")
            await self.gemini_session.send_client_content(
                turns=[types.Content(parts=[types.Part(text="Hello Gemini! This is TapMate. Please say 'ready' when you can hear me clearly.")])],
                turn_complete=True
            )
            
            # Handle messages from Android
            async for message in websocket:
                try:
                    data = json.loads(message)
                    msg_type = data.get("type")
                    
                    if msg_type == "audio":
                        # Decode base64 audio and send to Gemini
                        audio_b64 = data.get("data")
                        audio_bytes = base64.b64decode(audio_b64)
                        logger.debug(f"📥 Received audio from Android: {len(audio_bytes)} bytes")
                        await self.send_audio_to_gemini(audio_bytes)
                    
                    elif msg_type == "text":
                        # Send text to Gemini (using correct SDK keyword)
                        text = data.get("text")
                        logger.info(f"Forwarding text to Gemini: {text}")
                        await self.gemini_session.send_client_content(
                            turns=[types.Content(parts=[types.Part(text=text)])],
                            turn_complete=True
                        )
                    
                    elif msg_type == "ping":
                        await self._send_to_android({"type": "pong"})
                        
                except json.JSONDecodeError:
                    logger.error("Invalid JSON from Android")
                except Exception as e:
                    logger.error(f"Error handling message from Android: {e}", exc_info=True)
        
        except Exception as e:
            logger.error(f"FATAL error in handle_android_client: {e}", exc_info=True)
            # Send error message to client before closing
            try:
                await self._send_to_android({"type": "error", "message": str(e)})
            except:
                pass
            raise
        finally:
            # Cleanup
            self.is_connected = False
            if self._session_context:
                await self._session_context.__aexit__(None, None, None)
            logger.info("Session ended")


async def start_server(host: str = "0.0.0.0", port: int = 8765):
    """Start the WebSocket server."""
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY not found in environment")
    
    bridge = GeminiBridge(api_key)
    
    # Register example functions (you can add TapMate-specific functions here)
    bridge.register_function(
        {
            "name": "get_time",
            "description": "Get the current time",
            "parameters": {"type": "object", "properties": {}}
        },
        lambda args: {"time": "12:00 PM"}  # Replace with actual implementation
    )
    
    async def handler(websocket):
        await bridge.handle_android_client(websocket)
    
    logger.info(f"Starting WebSocket server on {host}:{port}")
    async with websockets.serve(handler, host, port):
        logger.info("✅ Server ready! Waiting for Android client...")
        await asyncio.Future()  # Run forever


if __name__ == "__main__":
    asyncio.run(start_server())
