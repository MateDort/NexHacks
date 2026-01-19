package com.tapmate.aiagent.audio;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * AudioFormatConverter - Audio format conversion utilities
 *
 * Based on TARS audio conversion pattern (twilio_media_streams.py):
 * - TARS: µ-law (8kHz) ↔ PCM (24kHz)
 * - TapMate: Android AudioRecord/AudioTrack ↔ Gemini PCM
 *
 * Gemini Live API Audio Specs:
 * - Input: 16kHz, LINEAR16 (16-bit PCM), mono, Base64 encoded
 * - Output: 24kHz, LINEAR16 (16-bit PCM), mono, raw bytes or Base64
 *
 * Android Audio Specs:
 * - AudioRecord: 16kHz, ENCODING_PCM_16BIT, mono
 * - AudioTrack: 24kHz, ENCODING_PCM_16BIT, mono
 */
public class AudioFormatConverter {

    private static final String TAG = "AudioFormatConverter";

    // Audio format constants
    public static final int GEMINI_INPUT_SAMPLE_RATE = 16000;   // 16kHz for input
    public static final int GEMINI_OUTPUT_SAMPLE_RATE = 24000;  // 24kHz for output
    public static final int BITS_PER_SAMPLE = 16;               // LINEAR16
    public static final int CHANNELS = 1;                       // Mono

    /**
     * Encode PCM audio for Gemini (Android → Gemini)
     * Like TARS pcm_to_base64()
     *
     * @param pcmData Raw PCM audio from Android AudioRecord (16kHz, 16-bit, mono)
     * @return Base64 encoded string for Gemini
     */
    public static String encodePCMForGemini(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            Log.w(TAG, "Empty PCM data provided for encoding");
            return "";
        }

        try {
            // Gemini expects Base64 encoded PCM
            String base64 = Base64.getEncoder().encodeToString(pcmData);
            Log.v(TAG, String.format("Encoded PCM: %d bytes → %d Base64 chars",
                    pcmData.length, base64.length()));
            return base64;

        } catch (Exception e) {
            Log.e(TAG, "Error encoding PCM for Gemini", e);
            return "";
        }
    }

    /**
     * Decode audio from Gemini (Gemini → Android)
     * Like TARS base64_to_pcm()
     *
     * @param base64Audio Base64 encoded audio from Gemini
     * @return Raw PCM audio for Android AudioTrack (24kHz, 16-bit, mono)
     */
    public static byte[] decodeGeminiAudio(String base64Audio) {
        if (base64Audio == null || base64Audio.isEmpty()) {
            Log.w(TAG, "Empty Base64 audio provided for decoding");
            return new byte[0];
        }

        try {
            byte[] pcmData = Base64.getDecoder().decode(base64Audio);
            Log.v(TAG, String.format("Decoded audio: %d Base64 chars → %d PCM bytes",
                    base64Audio.length(), pcmData.length));
            return pcmData;

        } catch (Exception e) {
            Log.e(TAG, "Error decoding Gemini audio", e);
            return new byte[0];
        }
    }

    /**
     * Resample audio (if needed)
     * Like TARS sample rate conversion
     *
     * Note: Gemini handles 16kHz input and 24kHz output natively,
     * so resampling is usually not needed. This is here for edge cases.
     *
     * @param pcmData PCM audio data
     * @param fromSampleRate Source sample rate
     * @param toSampleRate Target sample rate
     * @return Resampled PCM audio
     */
    public static byte[] resample(byte[] pcmData, int fromSampleRate, int toSampleRate) {
        if (fromSampleRate == toSampleRate) {
            return pcmData;  // No resampling needed
        }

        Log.d(TAG, String.format("Resampling: %d Hz → %d Hz", fromSampleRate, toSampleRate));

        // Convert bytes to 16-bit samples
        short[] samples = bytesToShorts(pcmData);

        // Calculate resampling ratio
        double ratio = (double) toSampleRate / fromSampleRate;
        int newLength = (int) (samples.length * ratio);
        short[] resampled = new short[newLength];

        // Linear interpolation (simple resampling)
        for (int i = 0; i < newLength; i++) {
            double srcIndex = i / ratio;
            int srcIdx1 = (int) Math.floor(srcIndex);
            int srcIdx2 = Math.min(srcIdx1 + 1, samples.length - 1);

            double fraction = srcIndex - srcIdx1;
            resampled[i] = (short) (
                    samples[srcIdx1] * (1 - fraction) +
                    samples[srcIdx2] * fraction
            );
        }

        return shortsToBytes(resampled);
    }

    /**
     * Convert byte array to short array (16-bit PCM)
     */
    private static short[] bytesToShorts(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        return shorts;
    }

    /**
     * Convert short array to byte array (16-bit PCM)
     */
    private static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);
        return bytes;
    }

    /**
     * Calculate RMS (Root Mean Square) for VAD
     * Like TARS VAD threshold calculation
     *
     * @param pcmData PCM audio data (16-bit)
     * @return RMS value
     */
    public static double calculateRMS(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return 0.0;
        }

        short[] samples = bytesToShorts(pcmData);

        long sum = 0;
        for (short sample : samples) {
            sum += sample * sample;
        }

        double mean = (double) sum / samples.length;
        return Math.sqrt(mean);
    }

    /**
     * Normalize audio volume (if needed for better VAD)
     *
     * @param pcmData PCM audio data
     * @param targetRMS Target RMS value
     * @return Normalized PCM audio
     */
    public static byte[] normalizeVolume(byte[] pcmData, double targetRMS) {
        double currentRMS = calculateRMS(pcmData);

        if (currentRMS < 1.0) {
            return pcmData;  // Silence, don't amplify
        }

        double gain = targetRMS / currentRMS;
        short[] samples = bytesToShorts(pcmData);

        for (int i = 0; i < samples.length; i++) {
            int normalized = (int) (samples[i] * gain);
            // Clamp to prevent clipping
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, normalized));
        }

        return shortsToBytes(samples);
    }

    /**
     * Mix two audio streams (for future multi-source audio)
     *
     * @param audio1 First audio stream
     * @param audio2 Second audio stream
     * @return Mixed audio
     */
    public static byte[] mixAudio(byte[] audio1, byte[] audio2) {
        int length = Math.min(audio1.length, audio2.length);
        byte[] mixed = new byte[length];

        short[] samples1 = bytesToShorts(audio1);
        short[] samples2 = bytesToShorts(audio2);
        short[] mixedSamples = new short[length / 2];

        for (int i = 0; i < mixedSamples.length && i < samples1.length && i < samples2.length; i++) {
            int sum = samples1[i] + samples2[i];
            // Clamp to prevent clipping
            mixedSamples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sum));
        }

        return shortsToBytes(mixedSamples);
    }

    /**
     * Get audio format info string for debugging
     */
    public static String getFormatInfo(int sampleRate, int bitsPerSample, int channels) {
        return String.format("%d Hz, %d-bit, %s",
                sampleRate, bitsPerSample, channels == 1 ? "mono" : "stereo");
    }

    /**
     * Validate audio data
     */
    public static boolean isValidPCM(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return false;
        }

        // PCM 16-bit should have even number of bytes
        if (pcmData.length % 2 != 0) {
            Log.w(TAG, "Invalid PCM data: odd number of bytes");
            return false;
        }

        return true;
    }

    /**
     * Calculate audio duration in milliseconds
     */
    public static long calculateDurationMs(int dataLength, int sampleRate, int bitsPerSample, int channels) {
        int bytesPerSample = bitsPerSample / 8;
        int bytesPerSecond = sampleRate * bytesPerSample * channels;
        return (dataLength * 1000L) / bytesPerSecond;
    }

    /**
     * Log audio metrics for debugging (like TARS logging)
     */
    public static void logAudioMetrics(String direction, byte[] pcmData, int sampleRate) {
        if (pcmData == null) {
            Log.w(TAG, String.format("[%s] NULL audio data", direction));
            return;
        }

        double rms = calculateRMS(pcmData);
        long duration = calculateDurationMs(pcmData.length, sampleRate, BITS_PER_SAMPLE, CHANNELS);

        Log.d(TAG, String.format("[%s] %d bytes, %dms, RMS: %.2f, Format: %s",
                direction, pcmData.length, duration, rms,
                getFormatInfo(sampleRate, BITS_PER_SAMPLE, CHANNELS)));
    }
}
