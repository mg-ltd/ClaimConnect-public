package app.melosome.claimconnect.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import app.melosome.claimconnect.model.ProductLimits
import app.melosome.claimconnect.security.CryptoBox

class SecureFiles(context: Context, private val crypto: CryptoBox) {
    private val root = File(context.noBackupFilesDir, "records").apply { mkdirs() }
    private fun folder(incidentId: String) = File(root, incidentId).apply { mkdirs() }
    private fun file(incidentId: String, id: String) = File(folder(incidentId), "$id.cc")
    fun bytesUsed(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun write(incidentId: String, id: String, bytes: ByteArray) {
        check(bytesUsed() + bytes.size + 29 <= ProductLimits.MAX_STORAGE_BYTES) { "Device evidence storage is full. Save packs and delete old incidents to make space." }
        check(root.usableSpace > bytes.size * 2L + 5L * 1024 * 1024) { "Device storage is low. Free some space and try again." }
        val target = file(incidentId, id)
        val temporary = File(target.parentFile, "$id.tmp")
        try {
            FileOutputStream(temporary).use { out ->
                out.write(crypto.encrypt(bytes, "file:$incidentId:$id"))
                out.fd.sync()
            }
            check(temporary.renameTo(target)) { "Unable to save evidence on this device." }
        } finally { temporary.delete() }
    }

    fun read(incidentId: String, id: String): ByteArray = crypto.decrypt(file(incidentId, id).readBytes(), "file:$incidentId:$id")
    fun delete(incidentId: String, id: String) { file(incidentId, id).delete() }
    fun deleteIncident(incidentId: String) { File(root, incidentId).deleteRecursively() }

    /** Same-device backup only: the ciphertext here is already bound to this device's Keystore
     * key, so these copy raw encrypted bytes as-is rather than decrypting/re-encrypting. */
    fun rawBytes(incidentId: String, id: String): ByteArray = file(incidentId, id).readBytes()
    fun writeRawBytes(incidentId: String, id: String, bytes: ByteArray) { file(incidentId, id).writeBytes(bytes) }

    /** Recover from a process ending between durable file writes and the Room transaction. */
    fun reconcile(retained: Map<String, Set<String>>) {
        root.listFiles()?.forEach { directory ->
            val ids = retained[directory.name]
            if (ids == null) directory.deleteRecursively()
            else directory.listFiles()?.forEach { entry ->
                if (entry.extension != "cc" || entry.nameWithoutExtension !in ids) entry.delete()
            }
        }
    }
}
