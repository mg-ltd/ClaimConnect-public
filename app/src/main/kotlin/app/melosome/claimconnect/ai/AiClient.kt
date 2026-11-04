package app.melosome.claimconnect.ai

/** One display-copy photo sent for analysis; [index] is its position in the request. */
class AiPhoto(val index: Int, val jpegBytes: ByteArray)

data class AiSuggestion(
    val observation: String,
    val photoIndices: List<Int>,
    val uncertainty: String,
    val missingInformation: String?,
    val modelVersion: String,
)

sealed interface AiOutcome {
    data class Suggestions(val items: List<AiSuggestion>) : AiOutcome
    /** The reply arrived but failed verification; nothing is shown as an observation. */
    data class Rejected(val reason: String) : AiOutcome
    /** No usable reply (no access, offline, timeout, quota). */
    data class Failed(val message: String) : AiOutcome
}

/** Analysis backend. The app codes against this; keyless builds use [SimulatedAiClient]. */
interface AiClient {
    suspend fun analyze(photos: List<AiPhoto>): AiOutcome
}

/**
 * A suggestion the user accepted for the PDF appendix. [photoNumbers] are 1-based
 * positions in the incident's evidence list, resolved at accept time. Held in memory only.
 */
data class AcceptedNote(val text: String, val photoNumbers: List<Int>, val modelVersion: String)
