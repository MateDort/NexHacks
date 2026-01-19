package com.tapmate.aiagent.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AudioHandler - Real-time audio capture and playback with Voice Activity
 * Detection (VAD)
 *
 * Features:
 * - Bidirectional audio streaming (capture + playback)
 * - Voice Activity Detection using RMS analysis
 * - Non-blocking queue-based architecture
 * - Automatic silence detection for turn completion
 * - Interrupt handling (user can interrupt AI mid-response)
 *
 * Based on ADA V2's AudioLoop pattern adapted for Android
 */
public class AudioHandler {

    private static final String TAG = "AudioHandler";

    // Audio configuration (Gemini Live API spec)
    public static final int INPUT_SAMPLE_RATE = 16000; // 16kHz input
    public static final int OUTPUT_SAMPLE_RATE = 24000; // 24kHz output
    public static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    public static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int CHUNK_SIZE = 1024; // bytes per chunk

    // VAD configuration (Voice Activity Detection)
    private static final float VAD_THRESHOLD = 1500.0f; // Balanced sensitivity
    private static final int REQUIRED_SPEECH_CHUNKS = 2; // Require 2 consecutive chunks of speech
    private int speechChunkCount = 0;
    private static final long SILENCE_DURATION_MS = 800; // 800ms silence = local turn end

    private final ExecutorService executorService;
    private final BlockingQueue<byte[]> outputQueue; // Audio to send to Gemini
    private final BlockingQueue<byte[]> inputQueue; // Audio received from Gemini

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isSpeaking = new AtomicBoolean(false);

    private long lastSpeechTime = 0;
    private long silenceStartTime = 0;

    // Callbacks
    public interface AudioDataCallback {
        void onAudioChunk(byte[] audioData);
    }

    public interface VADCallback {
        void onSpeechStart();

        void onSpeechEnd();

        void onSilenceDetected();
    }

    private AudioDataCallback audioDataCallback;
    private VADCallback vadCallback;

    public AudioHandler() {
        this.executorService = Executors.newFixedThreadPool(3);
        this.outputQueue = new LinkedBlockingQueue<>();
        this.inputQueue = new LinkedBlockingQueue<>();

        Log.i(TAG, "AudioHandler initialized");
    }

    /**
     * Initialize audio capture (microphone input)
     */
    public boolean initializeAudioCapture() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(
                    INPUT_SAMPLE_RATE,
                    CHANNEL_IN,
                    ENCODING);

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size for audio recording");
                return false;
            }

            // Ensure buffer is large enough
            bufferSize = Math.max(bufferSize, CHUNK_SIZE * 4);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    INPUT_SAMPLE_RATE,
                    CHANNEL_IN,
                    ENCODING,
                    bufferSize);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return false;
            }

            Log.i(TAG, "Audio capture initialized (16kHz mono, buffer: " + bufferSize + " bytes)");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing audio capture", e);
            return false;
        }
    }

    /**
     * Initialize audio playback (speaker output)
     */
    public boolean initializeAudioPlayback() {
        try {
            int bufferSize = AudioTrack.getMinBufferSize(
                    OUTPUT_SAMPLE_RATE,
                    CHANNEL_OUT,
                    ENCODING);

            if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size for audio playback");
                return false;
            }

            // Ensure buffer is large enough
            bufferSize = Math.max(bufferSize, CHUNK_SIZE * 4);

            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(ENCODING)
                            .setSampleRate(OUTPUT_SAMPLE_RATE)
                            .setChannelMask(CHANNEL_OUT)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed");
                return false;
            }

            Log.i(TAG, "Audio playback initialized (24kHz mono, buffer: " + bufferSize + " bytes)");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing audio playback", e);
            return false;
        }
    }

    /**
     * Start audio capture with VAD
     */
    public void startCapture() {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording");
            return;
        }

        if (audioRecord == null && !initializeAudioCapture()) {
            Log.e(TAG, "Failed to initialize audio capture");
            return;
        }

        isRecording.set(true);
        audioRecord.startRecording();

        executorService.execute(() -> {
            Log.i(TAG, "Audio capture started");
            byte[] buffer = new byte[CHUNK_SIZE];

            while (isRecording.get()) {
                try {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0) {
                        // Create a copy to avoid buffer reuse issues
                        byte[] audioChunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                        // Perform VAD analysis
                        float rms = calculateRMS(audioChunk);
                        boolean speechDetected = rms > VAD_THRESHOLD;

                        long currentTime = System.currentTimeMillis();

                        if (speechDetected) {
                            speechChunkCount++;

                            // Log start of speech for debugging
                            if (!isSpeaking.get() && speechChunkCount >= REQUIRED_SPEECH_CHUNKS) {
                                isSpeaking.set(true);
                                lastSpeechTime = currentTime;
                                silenceStartTime = 0;

                                Log.d(TAG, "[VAD] Local speech trigger (RMS: " + rms + ")");
                                if (vadCallback != null) {
                                    vadCallback.onSpeechStart();
                                }
                            }

                            if (isSpeaking.get()) {
                                lastSpeechTime = currentTime;
                            }

                        } else {
                            // Silence detected locally
                            speechChunkCount = 0;
                            if (isSpeaking.get()) {
                                if (silenceStartTime == 0) {
                                    silenceStartTime = currentTime;
                                }

                                long silenceDuration = currentTime - silenceStartTime;

                                if (silenceDuration >= SILENCE_DURATION_MS) {
                                    isSpeaking.set(false);
                                    silenceStartTime = 0;

                                    Log.d(TAG, "[VAD] Local silence confirmed (" + silenceDuration + "ms)");
                                    if (vadCallback != null) {
                                        vadCallback.onSpeechEnd();
                                        vadCallback.onSilenceDetected();
                                    }
                                }
                            }
                        }

                        // ALWAYS stream to bridge for Gemini's internal VAD context
                        outputQueue.offer(audioChunk);

                        if (audioDataCallback != null) {
                            audioDataCallback.onAudioChunk(audioChunk);
                        }

                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord read error: " + bytesRead);
                        break;
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error reading audio", e);
                    break;
                }
            }

            Log.i(TAG, "Audio capture stopped");
        });
    }

    /**
     * Stop audio capture
     */
    public void stopCapture() {
        isRecording.set(false);
        isSpeaking.set(false);

        if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop();
        }

        outputQueue.clear();
        Log.i(TAG, "Audio capture stopped");
    }

    /**
     * Start audio playback
     */
    public void startPlayback() {
        if (isPlaying.get()) {
            Log.w(TAG, "Already playing");
            return;
        }

        if (audioTrack == null && !initializeAudioPlayback()) {
            Log.e(TAG, "Failed to initialize audio playback");
            return;
        }

        isPlaying.set(true);
        audioTrack.play();

        executorService.execute(() -> {
            Log.i(TAG, "Audio playback started");

            while (isPlaying.get()) {
                try {
                    byte[] audioData = inputQueue.take(); // Blocking wait

                    if (audioData != null && audioData.length > 0) {
                        int bytesWritten = audioTrack.write(audioData, 0, audioData.length);
                        Log.d(TAG, "Played audio: " + bytesWritten + " bytes");
                    }

                } catch (InterruptedException e) {
                    Log.d(TAG, "Playback interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error playing audio", e);
                    break;
                }
            }

            Log.i(TAG, "Audio playback stopped");
        });
    }

    /**
     * Stop audio playback
     */
    public void stopPlayback() {
        isPlaying.set(false);

        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause();
            audioTrack.flush(); // Clear buffer
        }

        inputQueue.clear();
        Log.i(TAG, "Audio playback stopped");
    }

    /**
     * Queue audio data for playback (from Gemini)
     */
    public void queueAudioForPlayback(byte[] audioData) {
        if (audioData != null && audioData.length > 0) {
            inputQueue.offer(audioData);
        }
    }

    /**
     * Clear playback queue (for interruption handling)
     */
    public void clearPlaybackQueue() {
        int cleared = inputQueue.size();
        inputQueue.clear();

        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.flush();
        }

        Log.i(TAG, "[INTERRUPT] Cleared " + cleared + " audio chunks from playback queue");
    }

    /**
     * Get next audio chunk to send to Gemini (non-blocking)
     */
    public byte[] pollAudioChunk() {
        return outputQueue.poll();
    }

    /**
     * Check if user is currently speaking
     */
    public boolean isSpeaking() {
        return isSpeaking.get();
    }

    /**
     * Calculate RMS (Root Mean Square) for VAD
     */
    private float calculateRMS(byte[] audioData) {
        long sum = 0;
        int sampleCount = audioData.length / 2; // 16-bit = 2 bytes per sample

        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Convert 2 bytes to 16-bit signed sample (little-endian)
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }

        return (float) Math.sqrt((double) sum / sampleCount);
    }

    /**
     * Set callback for audio data
     */
    public void setAudioDataCallback(AudioDataCallback callback) {
        this.audioDataCallback = callback;
    }

    /**
     * Set callback for VAD events
     */
    public void setVADCallback(VADCallback callback) {
        this.vadCallback = callback;
    }

    /**
     * Shutdown audio handler
     */
    public void shutdown() {
        stopCapture();
        stopPlayback();

        executorService.shutdown();

        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }

        Log.i(TAG, "AudioHandler shutdown");
    }
}
