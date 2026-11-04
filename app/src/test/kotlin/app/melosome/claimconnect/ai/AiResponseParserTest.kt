package app.melosome.claimconnect.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiResponseParserTest {
    private fun envelope(content: String): String =
        """{"choices":[{"message":{"content":"""" + JsonParser.escape(content) + """"}}]}"""

    @Test fun validReplyMapsToSuggestions() {
        val outcome = AiResponseParser.parse(envelope(
            """[{"observation":"Cracked bumper.","photo_ids":[0,1],"uncertainty":"Edge is dark.","missing_information":null,"model_version":"m1"}]"""), 2)
        val items = (outcome as AiOutcome.Suggestions).items
        assertEquals(1, items.size)
        assertEquals(listOf(0, 1), items.single().photoIndices)
        assertEquals("m1", items.single().modelVersion)
        assertNull(items.single().missingInformation)
    }
    @Test fun malformedRepliesAreRejected() {
        listOf("not json", "{}", """{"choices":[]}""",
            envelope("not json"), envelope("{}"),
            envelope("""[{"observation":"x"}]""")).forEach { bad ->
            assertTrue(bad, AiResponseParser.parse(bad, 2) is AiOutcome.Rejected)
        }
    }
    @Test fun ungroundedPhotoIdsAreRejected() {
        listOf("[5]", "[]", "[-1]", """["a"]""").forEach { ids ->
            val outcome = AiResponseParser.parse(envelope(
                """[{"observation":"x","photo_ids":$ids,"uncertainty":"u","missing_information":null,"model_version":"m"}]"""), 2)
            assertTrue(ids, outcome is AiOutcome.Rejected)
        }
    }
    @Test fun bannedConclusionsDropTheItem() {
        val outcome = AiResponseParser.parse(envelope(
            """[{"observation":"The other driver was at fault.","photo_ids":[0],"uncertainty":"u","missing_information":null,"model_version":"m"},
                {"observation":"A scratch on the door.","photo_ids":[1],"uncertainty":"u","missing_information":null,"model_version":"m"}]"""), 2)
        val items = (outcome as AiOutcome.Suggestions).items
        assertEquals(1, items.size)
        assertEquals("A scratch on the door.", items.single().observation)
    }
    @Test fun costAndSpeedSignalsAreRejected() {
        listOf("repair will cost £500", "doing 40 mph", "the driver is injured").forEach { text ->
            val outcome = AiResponseParser.parse(envelope(
                """[{"observation":"$text","photo_ids":[0],"uncertainty":"u","missing_information":null,"model_version":"m"}]"""), 1)
            assertTrue(text, outcome is AiOutcome.Rejected)
        }
    }
    @Test fun proxyShapeMapsToSuggestions() {
        val outcome = AiResponseParser.parseProxy(
            """{"suggestions":[{"observation":"Cracked bumper.","photo_ids":[0],"uncertainty":"u","missing_information":null,"model_version":"p1"}]}""", 1)
        assertEquals("p1", (outcome as AiOutcome.Suggestions).items.single().modelVersion)
    }
    @Test fun proxyShapeRejectsBadItems() {
        assertTrue(AiResponseParser.parseProxy("""{"suggestions":[]}""", 1) is AiOutcome.Rejected)
        assertTrue(AiResponseParser.parseProxy("""{"nope":[]}""", 1) is AiOutcome.Rejected)
        assertTrue(AiResponseParser.parseProxy(
            """{"suggestions":[{"observation":"At fault.","photo_ids":[0],"uncertainty":"u","missing_information":null,"model_version":"p"}]}""", 1) is AiOutcome.Rejected)
    }
    @Test fun observedProbeShapeParses() {
        // Exact content shape from the step-2 probe: string "input_N" ids, numeric uncertainty.
        val outcome = AiResponseParser.parse(envelope(
            """[{"observation":"The image is a very small square.","photo_ids":["input_0"],"uncertainty":0.95,"missing_information":"Higher resolution is missing","model_version":"muse-spark"}]"""), 1)
        val item = (outcome as AiOutcome.Suggestions).items.single()
        assertEquals(listOf(0), item.photoIndices)
        assertEquals("0.95", item.uncertainty)
        assertEquals("muse-spark", item.modelVersion)
    }
    @Test fun inputIdsMapToIndicesAndOtherStringsReject() {
        val ok = AiResponseParser.parse(envelope(
            """[{"observation":"x","photo_ids":["input_1"],"uncertainty":"u","missing_information":null,"model_version":"m"}]"""), 2)
        assertEquals(listOf(1), (ok as AiOutcome.Suggestions).items.single().photoIndices)
        assertTrue(AiResponseParser.parse(envelope(
            """[{"observation":"x","photo_ids":["photo 1"],"uncertainty":"u","missing_information":null,"model_version":"m"}]"""), 2) is AiOutcome.Rejected)
        assertTrue(AiResponseParser.parse(envelope(
            """[{"observation":"x","photo_ids":["input_9"],"uncertainty":"u","missing_information":null,"model_version":"m"}]"""), 2) is AiOutcome.Rejected)
    }
    @Test fun cutOffReplyIsRetryableNotRejected() {
        val outcome = AiResponseParser.parse(
            """{"choices":[{"finish_reason":"length","message":{"content":null}}]}""", 1)
        assertEquals(AiOutcome.Failed("The AI reply was cut off. Try again."), outcome)
        assertTrue(AiResponseParser.parse(
            """{"choices":[{"finish_reason":"stop","message":{"content":null}}]}""", 1) is AiOutcome.Rejected)
    }
    @Test fun photoCountIsEnforced() {
        assertNotNull(AiResponseParser.checkPhotos(emptyList()))
        assertNotNull(AiResponseParser.checkPhotos(List(4) { AiPhoto(it, ByteArray(0)) }))
        assertNull(AiResponseParser.checkPhotos(List(3) { AiPhoto(it, ByteArray(0)) }))
    }
    @Test fun simulatorExercisesRealValidation() = runBlocking {
        assertTrue(SimulatedAiClient(SimulatedAiClient.Scenario.SUPPORTED, 0).analyze(listOf(AiPhoto(0, ByteArray(0)))) is AiOutcome.Suggestions)
        assertTrue(SimulatedAiClient(SimulatedAiClient.Scenario.AMBIGUOUS, 0).analyze(listOf(AiPhoto(0, ByteArray(0)))) is AiOutcome.Suggestions)
        assertTrue(SimulatedAiClient(SimulatedAiClient.Scenario.ADVERSARIAL_FAULT, 0).analyze(listOf(AiPhoto(0, ByteArray(0)))) is AiOutcome.Rejected)
        assertTrue(SimulatedAiClient(SimulatedAiClient.Scenario.MALFORMED, 0).analyze(listOf(AiPhoto(0, ByteArray(0)))) is AiOutcome.Rejected)
        assertTrue(SimulatedAiClient(SimulatedAiClient.Scenario.EMPTY, 0).analyze(listOf(AiPhoto(0, ByteArray(0)))) is AiOutcome.Rejected)
    }
}
