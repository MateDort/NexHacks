package com.tapmate.aiagent.audio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

/**
 * SpeechRecognizerHelper - Converts audio to text using Android SpeechRecognizer
 *
 * This is a temporary solution until Gemini Live native audio streaming is available.
 */
public class SpeechRecognizerHelper {

    private static final String TAG = "SpeechRecognizer";

    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private RecognitionCallback callback;

    public interface RecognitionCallback {
        void onResult(String transcribedText);
        void onError(int errorCode, String errorMessage);
    }

    public SpeechRecognizerHelper(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    private void initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device");
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);

        // Configure recognizer intent
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
            context.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        Log.i(TAG, "Speech recognizer initialized");
    }

    public void setCallback(RecognitionCallback callback) {
        this.callback = callback;
    }

    public void startListening() {
        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer not initialized");
            return;
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Speech started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Audio level changed
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Received audio buffer
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "Speech ended");
            }

            @Override
            public void onError(int error) {
                String errorMessage = getErrorMessage(error);
                Log.e(TAG, "Speech recognition error: " + errorMessage);
                if (callback != null) {
                    callback.onError(error, errorMessage);
                }

                // Auto-restart on error (except if client canceled)
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    restartListening();
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    String transcribedText = matches.get(0);
                    Log.i(TAG, "Transcribed: " + transcribedText);

                    if (callback != null) {
                        callback.onResult(transcribedText);
                    }
                }

                // Auto-restart for continuous listening
                restartListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    String partialText = matches.get(0);
                    Log.d(TAG, "Partial: " + partialText);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // Event occurred
            }
        });

        speechRecognizer.startListening(recognizerIntent);
        Log.i(TAG, "Started listening");
    }

    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            Log.i(TAG, "Stopped listening");
        }
    }

    private void restartListening() {
        if (speechRecognizer != null) {
            // Small delay before restarting
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    speechRecognizer.startListening(recognizerIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Error restarting listener", e);
                }
            }, 100);
        }
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
            Log.i(TAG, "Speech recognizer destroyed");
        }
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input";
            default:
                return "Unknown error: " + errorCode;
        }
    }
}
