package app.melosome.claimconnect.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Locked golden outcomes for the offline demo corpus. Each scenario runs the
 * canned bytes through the real verification path, so these tests fail if
 * validation, grounding, or the keyword guard regresses.
 */
class AiGoldenTest {
    private val photo = listOf(AiPhoto(0, ByteArray(0)))

    private fun run(scenario: SimulatedAiClient.Scenario): AiOutcome = runBlocking {
        SimulatedAiClient(scenario, 0).analyze(photo)
    }

    @Test fun supportedObservationIsKeptVerbatim() {
        val items = (run(SimulatedAiClient.Scenario.SUPPORTED) as AiOutcome.Suggestions).items
        assertEquals(1, items.size)
        assertEquals("The front bumper shows a visible crack on the left side.", items.single().observation)
        assertEquals(listOf(0), items.single().photoIndices)
        assertEquals("sim-1", items.single().modelVersion)
    }
    @Test fun ambiguousObservationSurfacesUncertainty() {
        val items = (run(SimulatedAiClient.Scenario.AMBIGUOUS) as AiOutcome.Suggestions).items
        assertEquals("A light-coloured mark is visible on the door.", items.single().observation)
        assertTrue(items.single().uncertainty.contains("unclear"))
        assertNotNull(items.single().missingInformation)
    }
    @Test fun faultClaimIsRejectedWhole() {
        val outcome = run(SimulatedAiClient.Scenario.ADVERSARIAL_FAULT)
        assertTrue(outcome is AiOutcome.Rejected)
        assertEquals(AiResponseParser.UNVERIFIED, (outcome as AiOutcome.Rejected).reason)
    }
    @Test fun malformedAndEmptyAreRejectedWhole() {
        assertTrue(run(SimulatedAiClient.Scenario.MALFORMED) is AiOutcome.Rejected)
        assertTrue(run(SimulatedAiClient.Scenario.EMPTY) is AiOutcome.Rejected)
    }
}
