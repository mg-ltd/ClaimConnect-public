package app.melosome.claimconnect.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MetaApiClientTest {
    // Unroutable address: these tests must fail before any network attempt.
    private fun client(key: String?) = MetaApiClient("http://127.0.0.1:9", { key }, "test-model")

    @Test fun missingKeyFailsWithSettingsGuidance(): Unit = runBlocking {
        val outcome = client(null).analyze(listOf(AiPhoto(0, ByteArray(10))))
        assertTrue(outcome is AiOutcome.Failed)
        assertEquals("Add your API key in Settings to run the AI check.", (outcome as AiOutcome.Failed).message)
    }

    @Test fun blankKeyFailsWithSettingsGuidance(): Unit = runBlocking {
        val outcome = client("   ").analyze(listOf(AiPhoto(0, ByteArray(10))))
        assertTrue(outcome is AiOutcome.Failed)
        assertEquals("Add your API key in Settings to run the AI check.", (outcome as AiOutcome.Failed).message)
    }
}
