package app.melosome.claimconnect.model

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class Incident(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val revision: Long = 1,
    val registration: String = "",
    val vehicle: String = "",
    val driver: String = "",
    val company: String = "",
    val date: String = LocalDate.now().toString(),
    val time: String = LocalTime.now().withSecond(0).withNano(0).toString(),
    val timeZone: String = ZoneId.systemDefault().id,
    val location: String = "",
    val notes: String = "",
    val otherParties: String = "",
    val archived: Boolean = false,
) {
    val reference: String get() = "CC-${id.take(8).uppercase()}"
    fun validationErrors(): List<String> = buildList {
        if (registration.isBlank()) add("Enter your vehicle registration.")
        if (driver.isBlank()) add("Enter the driver's name.")
        if (runCatching { LocalDate.parse(date) }.isFailure) add("Choose a valid incident date.")
        if (runCatching { LocalTime.parse(time) }.isFailure) add("Choose a valid incident time.")
        if (location.isBlank()) add("Enter the incident location.")
        if (notes.isBlank()) add("Add a brief account of what happened.")
    }
}

data class Evidence(
    val id: String,
    val incidentId: String,
    val addedAt: Long,
    val source: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val caption: String = "",
)

data class EvidencePack(
    val id: String,
    val incidentId: String,
    val revision: Long,
    val generatedAt: Long,
    val sha256: String,
    val savedAt: Long? = null,
)

data class DriverSettings(
    val driver: String = "",
    val company: String = "",
    val registration: String = "",
    val vehicle: String = "",
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    /** ISO country code of the chosen emergency-number market; blank means automatic from device locale. */
    val emergencyMarket: String = "",
)
data class EmergencyContact(val id: String = UUID.randomUUID().toString(), val label: String, val number: String)
/** Fixed, non-editable national numbers per market. Labels stay in English for v1;
 * the numbers themselves dial the same everywhere. Unknown locales fall back to
 * the Europe-wide 112 number rather than a wrong country's list. */
data class EmergencyNumber(val label: String, val number: String)
data class EmergencyMarket(val countryCode: String, val name: String, val numbers: List<EmergencyNumber>)
object EmergencyMarkets {
    val GB = EmergencyMarket("GB", "United Kingdom", listOf(
        EmergencyNumber("999 · Police, Fire, Ambulance", "999"),
        EmergencyNumber("112 · Emergency (alternative)", "112"),
        EmergencyNumber("101 · Police non-emergency", "101"),
        EmergencyNumber("NHS 111 · Medical advice", "111"),
        EmergencyNumber("National Highways · Road assistance", "03001235000"),
    ))
    val US = EmergencyMarket("US", "United States", listOf(
        EmergencyNumber("911 · Police, Fire, Medical", "911"),
        EmergencyNumber("988 · Crisis lifeline", "988"),
        EmergencyNumber("311 · City services (where available)", "311"),
    ))
    val DE = EmergencyMarket("DE", "Germany", listOf(
        EmergencyNumber("112 · Fire, Rescue", "112"),
        EmergencyNumber("110 · Police", "110"),
    ))
    val FR = EmergencyMarket("FR", "France", listOf(
        EmergencyNumber("112 · European emergency", "112"),
        EmergencyNumber("15 · SAMU (medical)", "15"),
        EmergencyNumber("17 · Police", "17"),
        EmergencyNumber("18 · Fire", "18"),
    ))
    val ES = EmergencyMarket("ES", "Spain", listOf(
        EmergencyNumber("112 · Emergencies", "112"),
        EmergencyNumber("062 · Guardia Civil traffic", "062"),
        EmergencyNumber("091 · National Police", "091"),
    ))
    val IT = EmergencyMarket("IT", "Italy", listOf(
        EmergencyNumber("112 · European emergency", "112"),
        EmergencyNumber("113 · Police", "113"),
        EmergencyNumber("115 · Fire", "115"),
        EmergencyNumber("118 · Medical", "118"),
    ))
    val CA = EmergencyMarket("CA", "Canada", listOf(
        EmergencyNumber("911 · Police, Fire, Medical", "911"),
    ))
    val IE = EmergencyMarket("IE", "Ireland", listOf(
        EmergencyNumber("999 · Police, Fire, Ambulance", "999"),
        EmergencyNumber("112 · Emergency (alternative)", "112"),
    ))
    val AU = EmergencyMarket("AU", "Australia", listOf(
        EmergencyNumber("000 · Police, Fire, Ambulance", "000"),
        EmergencyNumber("112 · Emergency from mobiles", "112"),
    ))
    val NZ = EmergencyMarket("NZ", "New Zealand", listOf(
        EmergencyNumber("111 · Police, Fire, Ambulance", "111"),
        EmergencyNumber("112 · Emergency from mobiles", "112"),
    ))
    val MX = EmergencyMarket("MX", "Mexico", listOf(
        EmergencyNumber("911 · Emergencies", "911"),
    ))
    val IN = EmergencyMarket("IN", "India", listOf(
        EmergencyNumber("112 · Emergency", "112"),
    ))
    val ALL = listOf(GB, US, DE, FR, ES, IT, CA, IE, AU, NZ, MX, IN)
    /** Europe-wide fallback: 112 reaches emergency services across the EU and UK. */
    val FALLBACK = EmergencyMarket("EU", "Europe (112)", listOf(
        EmergencyNumber("112 · Emergency", "112"),
    ))
    fun forCountryCode(code: String): EmergencyMarket =
        ALL.firstOrNull { it.countryCode == code.uppercase() } ?: FALLBACK
}
data class IncidentDetail(val incident: Incident, val evidence: List<Evidence>, val pack: EvidencePack?) {
    val currentPack: EvidencePack? get() = pack?.takeIf { it.revision == incident.revision }
    val status: String get() = when {
        incident.archived -> "Archived"
        currentPack?.savedAt != null -> "Saved copy"
        currentPack != null -> "Pack generated"
        incident.validationErrors().isEmpty() -> "Ready"
        else -> "Draft"
    }
}

object ProductLimits {
    const val MAX_PHOTOS = 10
    const val MAX_PHOTO_BYTES = 15L * 1024 * 1024
    const val MAX_STORAGE_BYTES = 200L * 1024 * 1024
    const val MAX_TEXT_LENGTH = 12000
    const val MAX_IMAGE_PIXELS = 100_000_000L
    const val NOTICE = "This document organises information supplied by the user. ClaimConnect does not determine fault, liability, insurance coverage or claim acceptance."
    const val LOCAL_NOTICE = "On this device only. Save or share your pack to keep a copy. Uninstalling the app or losing this device removes your records."
}

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun photoCount(count: Int): String = "$count ${if (count == 1) "photograph" else "photographs"}"
