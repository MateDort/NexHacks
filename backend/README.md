# Gemini Bridge Backend Server

Python WebSocket server that bridges Android TapMate app to Gemini Live API using the official SDK.

## Setup

1. **Install dependencies**:
```bash
cd backend
pip install -r requirements.txt
```

2. **Configure API key**:
   - API key is already in `.env`
   - Key: `AIzaSyAYATKqkoY6GnXFAgkM97g52M7FdNnaPj0`

3. **Run server**:
```bash
python gemini_bridge.py
```

Server will start on `0.0.0.0:8765`

## Architecture

```
Android App (TapMate)
    ↓ WebSocket (ws://YOUR_IP:8765)
Python Backend (gemini_bridge.py)
    ↓ Official SDK
Gemini Live API
```

## Message Format

### Android → Backend

**Audio**:
```json
{
  "type": "audio",
  "data": "base64_encoded_pcm_audio"
}
```

**Text**:
```json
{
  "type": "text",
  "text": "Hello Gemini"
}
```

### Backend → Android

**Audio Response**:
```json
{
  "type": "audio",
  "data": "base64_encoded_audio"
}
```

**Transcript**:
```json
{
  "type": "transcript",
  "role": "ai|user",
  "text": "Transcribed text"
}
```

**Ready Signal**:
```json
{
  "type": "ready"
}
```

## Adding Functions

Edit `gemini_bridge.py` and add function registrations:

```python
bridge.register_function(
    {
        "name": "your_function",
        "description": "What it does",
        "parameters": {
            "type": "object",
            "properties": {
                "param1": {"type": "string", "description": "..."}
            }
        }
    },
    async lambda args: {"result": "value"}
)
```

## Testing

1. Start server: `python gemini_bridge.py`
2. Connect Android app to `ws://YOUR_IP:8765`
3. Send audio chunks
4. Receive responses

## Logs

Server logs show:
- ✅ Connection status
- 🎤 Audio chunks sent/received
- 💬 Transcripts (AI and User)
- 🔧 Function calls

## Next Steps for Android

Update `GeminiLiveClient.java`:
- Change URL to `ws://YOUR_SERVER_IP:8765`
- Simplify message format (see above)
- Remove Gemini-specific protocol code
