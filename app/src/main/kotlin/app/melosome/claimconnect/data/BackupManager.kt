package app.melosome.claimconnect.data

import androidx.room.withTransaction
import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class RestoreSummary(val incidentsAdded: Int, val incidentsSkipped: Int, val evidenceAdded: Int, val packsAdded: Int)

private const val FORMAT = "claimconnect.backup.v1"

/**
 * Same-device backup/restore only. Row payloads and evidence/pack files are copied as the opaque
 * ciphertext already at rest, encrypted with this device's non-exportable Android Keystore key
 * (see [app.melosome.claimconnect.security.CryptoBox]) — nothing is ever decrypted during export.
 * The resulting file cannot be opened on another device, or on this device after the app's
 * storage is cleared or the app is uninstalled, because that key cannot be recreated or moved.
 * A portable, passphrase-protected backup that survives those cases is a possible follow-up
 * (see docs/design/ROADMAP.md).
 */
class BackupManager(private val db: ClaimDatabase, private val files: SecureFiles) {
    private val dao = db.dao()

    suspend fun export(resolver: ContentResolver, destination: Uri) {
        val incidents = db.withTransaction { dao.allIncidents() }
        val output = resolver.openOutputStream(destination, "wt")
            ?: throw IllegalStateException("The destination could not be opened. Please choose another location.")
        output.use { out ->
            ZipOutputStream(out).use { zip ->
                entry(zip, "manifest.json") { JSONObject().put("format", FORMAT).put("exportedAt", System.currentTimeMillis()).toString().toByteArray() }
                entry(zip, "incidents.json") { JSONArray(incidents.map { row(it.id, it.updatedAt, it.payload) }).toString().toByteArray() }
                val evidenceRows = incidents.flatMap { dao.evidence(it.id) }
                entry(zip, "evidence.json") { JSONArray(evidenceRows.map { row(it.id, it.addedAt, it.payload, it.incidentId) }).toString().toByteArray() }
                val packRows = incidents.mapNotNull { dao.pack(it.id) }
                entry(zip, "packs.json") { JSONArray(packRows.map { row(it.id, it.generatedAt, it.payload, it.incidentId) }).toString().toByteArray() }
                dao.settings()?.let { settings -> entry(zip, "settings.json") { row(settings.id, 0L, settings.payload).toString().toByteArray() } }
                for (e in evidenceRows) entry(zip, "files/${e.incidentId}/${e.id}.cc") { files.rawBytes(e.incidentId, e.id) }
                for (p in packRows) entry(zip, "files/${p.incidentId}/${p.id}.cc") { files.rawBytes(p.incidentId, p.id) }
            }
        }
    }

    suspend fun restore(resolver: ContentResolver, source: Uri): RestoreSummary {
        val entries = linkedMapOf<String, ByteArray>()
        val input = resolver.openInputStream(source)
            ?: throw IllegalStateException("The backup file could not be opened.")
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var next = zip.nextEntry
                while (next != null) {
                    entries[next.name] = zip.readBytes()
                    zip.closeEntry()
                    next = zip.nextEntry
                }
            }
        }
        val manifest = entries["manifest.json"]?.let { JSONObject(String(it)) }
            ?: throw IllegalArgumentException("This file is not a ClaimConnect backup.")
        require(manifest.optString("format") == FORMAT) { "This backup was made by a different app version and cannot be restored here." }
        var incidentsAdded = 0; var incidentsSkipped = 0; var evidenceAdded = 0; var packsAdded = 0
        jsonArray(entries["incidents.json"]).forEachObject { r ->
            val id = r.getString("id")
            if (dao.incident(id) != null) { incidentsSkipped++ }
            else { dao.insertIncident(IncidentRow(id, r.getLong("timestamp"), payload(r))); incidentsAdded++ }
        }
        jsonArray(entries["evidence.json"]).forEachObject { r ->
            val id = r.getString("id"); val incidentId = r.getString("incidentId")
            if (dao.incident(incidentId) == null || dao.evidenceRow(id) != null) return@forEachObject
            dao.insertEvidence(EvidenceRow(id, incidentId, r.getLong("timestamp"), payload(r)))
            entries["files/$incidentId/$id.cc"]?.let { files.writeRawBytes(incidentId, id, it) }
            evidenceAdded++
        }
        jsonArray(entries["packs.json"]).forEachObject { r ->
            val id = r.getString("id"); val incidentId = r.getString("incidentId")
            if (dao.incident(incidentId) == null || dao.packRow(id) != null) return@forEachObject
            dao.insertPack(PackRow(id, incidentId, r.getLong("timestamp"), payload(r)))
            entries["files/$incidentId/$id.cc"]?.let { files.writeRawBytes(incidentId, id, it) }
            packsAdded++
        }
        entries["settings.json"]?.takeIf { dao.settings() == null }?.let {
            dao.saveSettings(SettingsRow(payload = payload(JSONObject(String(it)))))
        }
        return RestoreSummary(incidentsAdded, incidentsSkipped, evidenceAdded, packsAdded)
    }

    private fun row(id: String, timestamp: Long, payload: ByteArray, incidentId: String? = null) = JSONObject().apply {
        put("id", id); put("timestamp", timestamp); put("payload", Base64.encodeToString(payload, Base64.NO_WRAP))
        if (incidentId != null) put("incidentId", incidentId)
    }
    private fun payload(r: JSONObject): ByteArray = Base64.decode(r.getString("payload"), Base64.NO_WRAP)
    private fun jsonArray(bytes: ByteArray?): JSONArray = bytes?.let { JSONArray(String(it)) } ?: JSONArray()
    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) { for (i in 0 until length()) action(getJSONObject(i)) }
    private fun entry(zip: ZipOutputStream, name: String, bytes: () -> ByteArray) {
        zip.putNextEntry(ZipEntry(name)); zip.write(bytes()); zip.closeEntry()
    }
}
