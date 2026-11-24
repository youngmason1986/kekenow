package com.blackempire.kekenow.service;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Wrapper for Android SpeechRecognizer + TextToSpeech.
 *
 * Usage:
 *   1. Call init(context) once (after Activity onCreate).
 *   2. Call startListening(listener) to begin voice capture.
 *   3. Call speak(text) to announce something.
 *   4. Call shutdown() in onDestroy.
 */
public class VoiceService {

    public interface VoiceListener {
        void onResult(String text);
        void onTimeout();
        void onError(int errorCode);
    }

    private static final String TAG = "KekeNow";
    private static final long TIMEOUT_MS = 8_000L;

    private Context context;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private VoiceListener pendingListener;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    public void init(Context ctx) {
        context = ctx.getApplicationContext();
        timeoutHandler = new Handler(Looper.getMainLooper());
        initTts();
    }

    private void initTts() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Prefer en_NG → en_GB → ENGLISH
                if (tts.isLanguageAvailable(new Locale("en", "NG")) >= TextToSpeech.LANG_AVAILABLE) {
                    tts.setLanguage(new Locale("en", "NG"));
                } else if (tts.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) {
                    tts.setLanguage(Locale.UK);
                } else {
                    tts.setLanguage(Locale.ENGLISH);
                }
                ttsReady = true;
                Log.d(TAG, "TTS initialised");
            } else {
                Log.e(TAG, "TTS init failed with status " + status);
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  SpeechRecognizer
    // ------------------------------------------------------------------ //

    /**
     * Returns true if on-device speech recognition is available on this device.
     */
    public boolean isRecognitionAvailable() {
        return context != null && SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void startListening(VoiceListener listener) {
        if (context == null) {
            Log.e(TAG, "VoiceService.startListening called before init()");
            return;
        }
        pendingListener = listener;

        if (recognizer != null) {
            recognizer.destroy();
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(buildRecognitionListener());

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Try en-NG; if unavailable the recogniser falls back to the system default
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-NG");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-NG");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        recognizer.startListening(intent);

        // 8-second outer watchdog
        timeoutRunnable = () -> {
            Log.d(TAG, "VoiceService: 8 s timeout fired");
            cancelListening();
            if (pendingListener != null) {
                pendingListener.onTimeout();
                pendingListener = null;
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    public void cancelListening() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (recognizer != null) {
            recognizer.cancel();
        }
    }

    private RecognitionListener buildRecognitionListener() {
        return new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Log.d(TAG, "STT: ready"); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partial) {}
            @Override public void onEvent(int type, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                clearTimeout();
                ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && pendingListener != null) {
                    pendingListener.onResult(matches.get(0));
                } else if (pendingListener != null) {
                    pendingListener.onTimeout();
                }
                pendingListener = null;
            }

            @Override
            public void onError(int error) {
                clearTimeout();
                Log.e(TAG, "STT error code: " + error);
                if (pendingListener != null) {
                    pendingListener.onError(error);
                    pendingListener = null;
                }
            }
        };
    }

    private void clearTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    // ------------------------------------------------------------------ //
    //  TextToSpeech
    // ------------------------------------------------------------------ //

    /**
     * Speaks the given text, interrupting any current speech (QUEUE_FLUSH).
     * Silent no-op if TTS is not yet initialised.
     */
    public void speak(String text) {
        if (ttsReady && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "keke_" + System.currentTimeMillis());
        }
    }

    /**
     * Queues the given text without interrupting current speech (QUEUE_ADD).
     */
    public void speakQueued(String text) {
        if (ttsReady && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "keke_" + System.currentTimeMillis());
        }
    }

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    public void shutdown() {
        cancelListening();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
    }
}
