package app.melosome.claimconnect.data

import org.json.JSONObject
import app.melosome.claimconnect.model.*
import app.melosome.claimconnect.security.CryptoBox

class RecordCodec(private val crypto: CryptoBox) {
    private fun encode(id: String, json: JSONObject) = crypto.encrypt(json.toString().toByteArray(Charsets.UTF_8), id)
    private fun decode(id: String, bytes: ByteArray) = JSONObject(String(crypto.decrypt(bytes, id), Charsets.UTF_8))

    fun row(i: Incident) = IncidentRow(i.id, i.updatedAt, encode("incident:${i.id}", JSONObject()
        .put("id", i.id).put("createdAt", i.createdAt).put("updatedAt", i.updatedAt).put("revision", i.revision)
        .put("registration", i.registration).put("vehicle", i.vehicle).put("driver", i.driver).put("company", i.company)
        .put("date", i.date).put("time", i.time).put("timeZone", i.timeZone)
        .put("location", i.location).put("notes", i.notes).put("otherParties", i.otherParties).put("archived", i.archived)))
    fun incident(r: IncidentRow): Incident = decode("incident:${r.id}", r.payload).let { j ->
        Incident(j.getString("id"), j.getLong("createdAt"), j.getLong("updatedAt"), j.getLong("revision"),
            j.getString("registration"), j.getString("vehicle"), j.getString("driver"), j.getString("company"),
            j.getString("date"), j.getString("time"), j.getString("timeZone"), j.getString("location"),
            j.getString("notes"), j.getString("otherParties"), j.optBoolean("archived", false))
    }
    fun row(e: Evidence) = EvidenceRow(e.id, e.incidentId, e.addedAt, encode("evidence:${e.id}", JSONObject()
        .put("id", e.id).put("incidentId", e.incidentId).put("addedAt", e.addedAt).put("source", e.source)
        .put("originalName", e.originalName).put("mimeType", e.mimeType).put("sizeBytes", e.sizeBytes)
        .put("sha256", e.sha256).put("caption", e.caption)))
    fun evidence(r: EvidenceRow): Evidence = decode("evidence:${r.id}", r.payload).let { j ->
        Evidence(j.getString("id"), j.getString("incidentId"), j.getLong("addedAt"), j.getString("source"),
            j.getString("originalName"), j.getString("mimeType"), j.getLong("sizeBytes"), j.getString("sha256"), j.getString("caption"))
    }
    fun row(p: EvidencePack) = PackRow(p.id, p.incidentId, p.generatedAt, encode("pack:${p.id}", JSONObject()
        .put("id", p.id).put("incidentId", p.incidentId).put("revision", p.revision)
        .put("generatedAt", p.generatedAt).put("sha256", p.sha256).put("savedAt", p.savedAt)))
    fun pack(r: PackRow): EvidencePack = decode("pack:${r.id}", r.payload).let { j ->
        EvidencePack(j.getString("id"), j.getString("incidentId"), j.getLong("revision"), j.getLong("generatedAt"),
            j.getString("sha256"), if(j.isNull("savedAt")) null else j.getLong("savedAt"))
    }
    fun row(s: DriverSettings) = SettingsRow(payload = encode("settings:driver", JSONObject()
        .put("driver", s.driver).put("company", s.company).put("registration", s.registration).put("vehicle", s.vehicle)
        .put("emergencyMarket", s.emergencyMarket)
        .put("emergencyContacts", org.json.JSONArray().apply { s.emergencyContacts.forEach { contact ->
            put(JSONObject().put("id", contact.id).put("label", contact.label).put("number", contact.number))
        } })))
    fun settings(r: SettingsRow) = decode("settings:driver", r.payload).let { j ->
        val contacts = j.optJSONArray("emergencyContacts")?.let { array ->
            (0 until array.length()).map { index -> array.getJSONObject(index).let { c ->
                EmergencyContact(c.getString("id"), c.getString("label"), c.getString("number"))
            } }
        } ?: emptyList()
        // optString keeps records written before the market choice existed readable.
        DriverSettings(j.getString("driver"), j.getString("company"), j.getString("registration"), j.getString("vehicle"), contacts,
            j.optString("emergencyMarket", ""))
    }
}
