package app.melosome.claimconnect.ai

import kotlinx.coroutines.delay

/**
 * Keyless stand-in for the Meta API used until probe day. Every scenario runs
 * through the real [AiResponseParser], so the verification path under test is
 * identical to production; only the bytes are canned.
 */
class SimulatedAiClient(
    private val scenario: Scenario = Scenario.SUPPORTED,
    private val delayMs: Long = 500,
) : AiClient {
    enum class Scenario { SUPPORTED, AMBIGUOUS, ADVERSARIAL_FAULT, MALFORMED, EMPTY }

    override suspend fun analyze(photos: List<AiPhoto>): AiOutcome {
        AiResponseParser.checkPhotos(photos)?.let { return AiOutcome.Failed(it) }
        delay(delayMs)
        return AiResponseParser.parse(envelope(scenario, photos.size), photos.size)
    }

    companion object {
        fun envelope(scenario: Scenario, photoCount: Int): String {
            val last = (photoCount - 1).coerceAtLeast(0)
            val content = when (scenario) {
                Scenario.SUPPORTED -> """[{"observation":"The front bumper shows a visible crack on the left side.","photo_ids":["input_0"],"uncertainty":0.2,"missing_information":"Which side of the car is shown in photo 1?","model_version":"sim-1"}]"""
                Scenario.AMBIGUOUS -> """[{"observation":"A light-coloured mark is visible on the door.","photo_ids":[0],"uncertainty":"It is unclear whether this is damage, dirt, or a reflection.","missing_information":"Can you add a closer photo of the mark?","model_version":"sim-1"}]"""
                Scenario.ADVERSARIAL_FAULT -> """[{"observation":"The other driver was at fault and speeding.","photo_ids":[$last],"uncertainty":"None.","missing_information":null,"model_version":"sim-1"}]"""
                Scenario.MALFORMED -> """[{"observation":"Broken,"photo_ids":[]}]"""
                Scenario.EMPTY -> """[]"""
            }
            return """{"choices":[{"message":{"content":"""" + JsonParser.escape(content) + """"}}]}"""
        }
    }
}
