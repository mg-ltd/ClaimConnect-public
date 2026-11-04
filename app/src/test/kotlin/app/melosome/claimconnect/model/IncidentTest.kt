package app.melosome.claimconnect.model

import org.junit.Assert.*
import org.junit.Test

class IncidentTest {
    @Test fun incompleteDraftCanExistButCannotBeExported() {
        assertEquals(4, Incident().validationErrors().size)
        assertEquals("Draft", IncidentDetail(Incident(), emptyList(), null).status)
    }
    @Test fun invalidCalendarDatesAndTimesBlockExport() {
        val incident = complete().copy(date = "2026-02-30", time = "25:61")
        assertEquals(listOf("Choose a valid incident date.", "Choose a valid incident time."), incident.validationErrors())
    }
    @Test fun editingInvalidatesThePreviousPackAndItsSavedStatus() {
        val incident = complete()
        val pack = EvidencePack("pack", incident.id, incident.revision, 1, "hash", savedAt = 2)
        assertEquals("Saved copy", IncidentDetail(incident, emptyList(), pack).status)
        val changed = IncidentDetail(incident.copy(revision = incident.revision + 1), emptyList(), pack)
        assertNull(changed.currentPack)
        assertEquals("Ready", changed.status)
    }
    @Test fun knownSha256IdentifiesOriginalBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".toByteArray()))
    }
    @Test fun ukMarketKeepsExistingNumbers() {
        assertEquals(listOf("999", "112", "101", "111", "03001235000"), EmergencyMarkets.GB.numbers.map { it.number })
    }
    @Test fun usMarketStartsWith911() {
        assertEquals("911", EmergencyMarkets.forCountryCode("US").numbers.first().number)
    }
    @Test fun everyMarketNumberIsDiallable() {
        (EmergencyMarkets.ALL + EmergencyMarkets.FALLBACK).forEach { market ->
            assertTrue(market.numbers.isNotEmpty())
            market.numbers.forEach { entry ->
                assertTrue(entry.label.isNotBlank())
                assertTrue(entry.number.all { it.isDigit() || it == '+' })
            }
        }
    }
    @Test fun unknownLocaleFallsBackTo112() {
        assertEquals(listOf("112"), EmergencyMarkets.forCountryCode("XX").numbers.map { it.number })
        assertEquals(EmergencyMarkets.FALLBACK, EmergencyMarkets.forCountryCode(""))
    }
    @Test fun englishSpeakingMarketsReuseFamiliarNumbers() {
        assertEquals("911", EmergencyMarkets.forCountryCode("CA").numbers.first().number)
        assertEquals("999", EmergencyMarkets.forCountryCode("IE").numbers.first().number)
        assertEquals("000", EmergencyMarkets.forCountryCode("AU").numbers.first().number)
        assertEquals("911", EmergencyMarkets.forCountryCode("MX").numbers.first().number)
        assertEquals("112", EmergencyMarkets.forCountryCode("IN").numbers.first().number)
    }
    @Test fun italyHasFourNumbersStartingWith112() {
        assertEquals(listOf("112", "113", "115", "118"), EmergencyMarkets.forCountryCode("IT").numbers.map { it.number })
    }
    @Test fun archivedStatusTakesPrecedenceOverPackStatus() {
        val incident = complete().copy(archived = true)
        val pack = EvidencePack("pack", incident.id, incident.revision, 1, "hash", savedAt = 2)
        assertEquals("Archived", IncidentDetail(incident, emptyList(), pack).status)
    }
    private fun complete() = Incident(registration = "AB12 CDE", driver = "Pilot Driver", location = "Test road", notes = "Simulated incident")
}
