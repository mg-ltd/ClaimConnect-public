package app.melosome.claimconnect.speech

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface TranscriptEvent {
    data class Partial(val text: String) : TranscriptEvent
    data class Final(val text: String) : TranscriptEvent
    data class Error(val message: String) : TranscriptEvent
}

/**
 * One-shot native speech recognition for the incident account. Prefers on-device
 * recognition, streams partials, and reports a single final transcript or error
 * through [onEvent]. The caller owns the lifecycle: [cancel] or [destroy] when
 * the UI goes away. Audio is handled by the device recognizer; only kept text
 * ever reaches the incident record.
 */
class SpeechTranscriber(private val context: Context, private val onEvent: (TranscriptEvent) -> Unit) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var languageTag: String? = null
    private var preferOffline = true

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onEvent(TranscriptEvent.Error(messageForError(SpeechRecognizer.ERROR_NO_MATCH)))
                else onEvent(TranscriptEvent.Final(text))
            }
            override fun onPartialResults(partial: Bundle) {
                partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { onEvent(TranscriptEvent.Partial(it)) }
            }
            override fun onError(error: Int) {
                // The offline pack for a supported language may be missing while the
                // online model works: retry once without the offline preference
                // before surfacing the error.
                if ((error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) && preferOffline) {
                    preferOffline = false
                    runCatching { listen() }.onFailure { onEvent(TranscriptEvent.Error(messageForError(error))) }
                } else onEvent(TranscriptEvent.Error(messageForError(error)))
            }
            override fun onReadyForSpeech(params: Bundle) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle) = Unit
        })
    }

    /**
     * Starts one dictation session. Asks the recognizer which languages it
     * supports first so the request matches the device instead of assuming
     * en-US; falls back to the legacy default when the query yields nothing.
     */
    suspend fun start(locale: Locale = Locale.getDefault()) {
        val details = queryLanguageDetails()
        languageTag = bestLanguageTag(locale, details?.supported, details?.preference)
            ?: recognitionLanguageTag(locale)
        preferOffline = true
        listen()
    }

    private fun listen() {
        // EXTRA_PREFER_OFFLINE is a compile-time constant: safe on older devices, ignored there.
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    /** Asks the recognizer for its supported languages; null on any failure. */
    private suspend fun queryLanguageDetails(): LanguageDetails? = withTimeoutOrNull(2000) {
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val bundle = getResultExtras(false)
                    cont.resume(bundle?.let {
                        LanguageDetails(
                            supported = it.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES).orEmpty(),
                            preference = it.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE),
                        )
                    })
                }
            }
            // Ordered broadcast: the result receiver runs last and collects the extras.
            // (One-shot result receivers are never registered, so no unregister.)
            runCatching {
                context.sendOrderedBroadcast(
                    Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS), null, receiver,
                    Handler(Looper.getMainLooper()), 0, null, null,
                )
            }.onFailure { cont.resume(null) }
        }
    }

    /** Stops listening and delivers final results through [onEvent]. */
    fun stop() { runCatching { recognizer.stopListening() } }

    /** Discards this session without results. */
    fun cancel() { runCatching { recognizer.cancel() } }

    fun destroy() { runCatching { recognizer.destroy() } }

    /** Languages reported by the recognizer's GET_LANGUAGE_DETAILS broadcast. */
    data class LanguageDetails(val supported: List<String>, val preference: String?)

    companion object {
        fun isAvailable(context: Context): Boolean =
            runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /**
         * BCP-47 tag sent to the recognizer. Any English variant requests en-US,
         * whose offline pack is the most widely installed; other languages pass
         * through unchanged.
         */
        fun recognitionLanguageTag(locale: Locale = Locale.getDefault()): String =
            if (locale.language == Locale.ENGLISH.language) Locale.US.toLanguageTag()
            else locale.toLanguageTag()

        /**
         * Best tag for [locale] given the recognizer's [supported] list: the
         * exact locale when supported, else en-US for any English locale, else
         * any same-language entry, else the recognizer's own preference. Null
         * means "no usable match" and the caller falls back to
         * [recognitionLanguageTag].
         */
        fun bestLanguageTag(locale: Locale, supported: List<String>?, preference: String?): String? {
            if (supported.isNullOrEmpty()) return null
            fun norm(tag: String) = tag.replace('_', '-').lowercase()
            val byNorm = supported.associateBy { norm(it) }
            val exact = byNorm[norm(locale.toLanguageTag())]
            if (exact != null) return exact
            if (locale.language == Locale.ENGLISH.language) {
                byNorm["en-us"]?.let { return it }
            }
            byNorm.entries.firstOrNull { (key, _) ->
                key == locale.language.lowercase() || key.startsWith(locale.language.lowercase() + "-")
            }?.let { return it.value }
            if (preference != null) byNorm[norm(preference)]?.let { return it }
            return null
        }

        /** Appends a kept transcript to existing notes with a paragraph break. */
        fun appendTranscript(notes: String, transcript: String): String =
            if (notes.isBlank()) transcript else notes.trimEnd() + "\n\n" + transcript

        fun messageForError(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Didn't catch that. Dictate again or type instead."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                "Can't reach transcription. Check your connection or type instead."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone denied. Type your account instead."
            // Added in API 33 but compile-time constants, so safe to reference on minSdk 30.
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "No speech recognition for this language. Download it in system settings, or type instead."
            else -> "Transcription isn't available right now. Type your account instead."
        }
    }
}
