package app.melosome.claimconnect.ai

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApiKeyStoreTest {
    @get:Rule val dir = TemporaryFolder()

    private fun store(file: File = File(dir.root, "key.enc")): ApiKeyStore {
        val magic = byteArrayOf(0x4B, 0x45, 0x59) // fake envelope marker
        return ApiKeyStore(file,
            { plain -> magic + plain },
            { envelope ->
                check(envelope.size > magic.size && envelope.copyOfRange(0, 3).contentEquals(magic)) { "bad envelope" }
                envelope.copyOfRange(3, envelope.size)
            })
    }

    @Test fun saveThenLoadRoundtripsAndTrims() {
        val store = store()
        assertNull(store.load())
        store.save("  secret-key-1234  ")
        assertEquals("secret-key-1234", store.load())
    }

    @Test fun saveOverwritesPreviousKey() {
        val store = store()
        store.save("first")
        store.save("second")
        assertEquals("second", store.load())
    }

    @Test fun clearRemovesTheKey() {
        val file = File(dir.root, "key.enc")
        val store = store(file)
        store.save("abc")
        store.clear()
        assertFalse(file.exists())
        assertNull(store.load())
    }

    @Test fun blankSaveClearsInsteadOfStoring() {
        val store = store()
        store.save("abc")
        store.save("   ")
        assertNull(store.load())
    }

    @Test fun overlongKeyIsRejected() {
        try {
            store().save("k".repeat(ApiKeyStore.MAX_KEY_LENGTH + 1))
            fail("expected rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun corruptFileLoadsAsMissing() {
        val file = File(dir.root, "key.enc").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(store(file).load())
    }

    @Test fun last4ShowsFourCharsOnly() {
        assertEquals("1234", ApiKeyStore.last4("secret-key-1234"))
        assertEquals("abc", ApiKeyStore.last4("abc"))
        assertEquals("", ApiKeyStore.last4("   "))
    }
}
