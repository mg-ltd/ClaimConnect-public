package app.melosome.claimconnect.speech

import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test

class SpeechTranscriberTest {
    @Test fun keptTranscriptFillsBlankNotes() {
        assertEquals("dictated words", SpeechTranscriber.appendTranscript("", "dictated words"))
        assertEquals("dictated words", SpeechTranscriber.appendTranscript("   ", "dictated words"))
    }
    @Test fun keptTranscriptAppendsWithAParagraphBreak() {
        assertEquals("typed start\n\ndictated words",
            SpeechTranscriber.appendTranscript("typed start", "dictated words"))
        assertEquals("typed start\n\ndictated words",
            SpeechTranscriber.appendTranscript("typed start\n", "dictated words"))
    }
    @Test fun silenceAndNoMatchInviteARetry() {
        assertEquals("Didn't catch that. Dictate again or type instead.",
            SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals("Didn't catch that. Dictate again or type instead.",
            SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }
    @Test fun networkErrorsPointAtConnectivity() {
        val message = "Can't reach transcription. Check your connection or type instead."
        assertEquals(message, SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(message, SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals(message, SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_SERVER))
    }
    @Test fun deniedPermissionKeepsTypingAvailable() {
        assertEquals("Microphone denied. Type your account instead.",
            SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }
    @Test fun missingLanguagePackPointsAtSystemSettings() {
        val message = "No speech recognition for this language. Download it in system settings, or type instead."
        assertEquals(message, SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertEquals(message, SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }
    @Test fun anyEnglishVariantRequestsUSPack() {
        assertEquals("en-US", SpeechTranscriber.recognitionLanguageTag(java.util.Locale.UK))
        assertEquals("en-US", SpeechTranscriber.recognitionLanguageTag(java.util.Locale.US))
        assertEquals("en-US", SpeechTranscriber.recognitionLanguageTag(java.util.Locale("en", "AU")))
    }
    @Test fun otherLanguagesPassThroughUnchanged() {
        assertEquals("fr-FR", SpeechTranscriber.recognitionLanguageTag(java.util.Locale.FRANCE))
        assertEquals("de-DE", SpeechTranscriber.recognitionLanguageTag(java.util.Locale.GERMANY))
    }
    @Test fun exactLocaleWinsWhenSupported() {
        assertEquals("en-GB", SpeechTranscriber.bestLanguageTag(
            java.util.Locale.UK, listOf("en-US", "en-GB", "fr-FR"), "en-US"))
    }
    @Test fun englishFallsBackToUSWhenLocaleMissing() {
        assertEquals("en-US", SpeechTranscriber.bestLanguageTag(
            java.util.Locale.UK, listOf("en-US", "fr-FR"), "fr-FR"))
    }
    @Test fun sameLanguageEntryBeatsUnrelatedPreference() {
        assertEquals("fr-CA", SpeechTranscriber.bestLanguageTag(
            java.util.Locale.FRANCE, listOf("en-US", "fr-CA"), "en-US"))
    }
    @Test fun preferenceWinsWhenNoLanguageMatch() {
        assertEquals("de-DE", SpeechTranscriber.bestLanguageTag(
            java.util.Locale.FRANCE, listOf("en-US", "de-DE"), "de-DE"))
    }
    @Test fun emptySupportListFallsBackToDefault() {
        assertNull(SpeechTranscriber.bestLanguageTag(java.util.Locale.UK, emptyList(), "en-US"))
        assertNull(SpeechTranscriber.bestLanguageTag(java.util.Locale.UK, null, null))
    }
    @Test fun tagsMatchDespiteSeparatorsAndCase() {
        assertEquals("EN_us", SpeechTranscriber.bestLanguageTag(
            java.util.Locale.UK, listOf("EN_us"), null))
    }
    @Test fun unknownErrorsStayNeutral() {
        assertEquals("Transcription isn't available right now. Type your account instead.",
            SpeechTranscriber.messageForError(SpeechRecognizer.ERROR_CLIENT))
        assertEquals("Transcription isn't available right now. Type your account instead.",
            SpeechTranscriber.messageForError(999))
    }
}
