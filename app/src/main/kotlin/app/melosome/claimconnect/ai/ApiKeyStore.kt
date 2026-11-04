package app.melosome.claimconnect.ai

import android.content.Context
import java.io.File
import app.melosome.claimconnect.security.CryptoBox

/**
 * User-supplied Meta API key (BYOK v1), encrypted at rest with the app's
 * Keystore-backed [CryptoBox]. Lives in app-private files — never in plain
 * preferences, logs, screenshots, or backups (backup is disabled
 * manifest-wide and the FileProvider exposes cache paths only). Only the
 * last 4 characters are ever surfaced, for the Settings status line.
 */
class ApiKeyStore(
    private val file: File,
    private val encrypt: (ByteArray) -> ByteArray,
    private val decrypt: (ByteArray) -> ByteArray,
) {
    /** Persists [key] (trimmed). Blank input clears the stored key. */
    fun save(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) { clear(); return }
        require(trimmed.length <= MAX_KEY_LENGTH) { "That key looks too long to be valid." }
        val envelope = encrypt(trimmed.toByteArray(Charsets.UTF_8))
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            java.io.FileOutputStream(tmp).use { it.write(envelope); it.fd.sync() }
            check(tmp.renameTo(file)) { "The API key could not be saved." }
        } finally { tmp.delete() }
    }

    /** The stored key, or null when none is saved or it cannot be read. */
    fun load(): String? = try {
        if (!file.exists()) null
        else String(decrypt(file.readBytes()), Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun clear() { file.delete() }

    companion object {
        const val MAX_KEY_LENGTH = 500
        private const val FILE_NAME = "ai_api_key.enc"
        private const val RECORD_ID = "ai-api-key:v1"

        fun default(context: Context, fileName: String = FILE_NAME): ApiKeyStore {
            val crypto = CryptoBox()
            return ApiKeyStore(File(context.filesDir, fileName),
                { crypto.encrypt(it, RECORD_ID) }, { crypto.decrypt(it, RECORD_ID) })
        }

        /** Last 4 characters for display; empty when blank. */
        fun last4(key: String): String = key.trim().takeLast(4)
    }
}
