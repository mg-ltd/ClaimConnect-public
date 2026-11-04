package app.melosome.claimconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.melosome.claimconnect.ai.ApiKeyStore
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The BYOK key round-trips through the real Keystore-backed crypto and is opaque at rest. */
@RunWith(AndroidJUnit4::class)
class ApiKeyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "test_ai_key.enc"

    @After fun cleanup() { File(context.filesDir, fileName).delete() }

    @Test fun roundtripEncryptedAtRest() {
        val store = ApiKeyStore.default(context, fileName)
        assertNull(store.load())
        store.save("test-key-abcdef-1234")
        assertEquals("test-key-abcdef-1234", store.load())
        assertEquals("1234", ApiKeyStore.last4(requireNotNull(store.load())))
        val raw = File(context.filesDir, fileName).readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("test-key-abcdef-1234"))
        store.clear()
        assertNull(store.load())
    }
}
