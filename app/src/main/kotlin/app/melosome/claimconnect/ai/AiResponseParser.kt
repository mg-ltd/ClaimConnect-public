package app.melosome.claimconnect.ai

import app.melosome.claimconnect.ai.JsonParser.Value

/**
 * Strict verification for model replies (see `docs/hackathon/spike/contract.md`):
 * valid envelope, all five keys with the right types, photo indices grounded in
 * the sent photos, and no liability/cost/medical conclusions. Anything else is
 * discarded with a neutral message — never shown as an observation.
 */
object AiResponseParser {
    const val MAX_PHOTOS_PER_REQUEST = 3
    const val UNVERIFIED = "The AI reply could not be verified. Nothing was kept."

    /** Conservative v1 list: fault, liability, speed, injury, cost and price signals. */
    private val BANNED = listOf(
        "fault", "liab", "speed", "injur", "cost", "price", "payout",
        "$", "£", "€", "mph", "km/h", "kmh",
    )

    fun checkPhotos(photos: List<AiPhoto>): String? = when {
        photos.isEmpty() -> "Select at least one photo to analyse."
        photos.size > MAX_PHOTOS_PER_REQUEST -> "Analyse up to 3 photos at a time."
        else -> null
    }

    fun parse(envelopeJson: String, photoCount: Int): AiOutcome {
        val items = try {
            val envelope = JsonParser.parse(envelopeJson) as? Value.Obj ?: throw JsonParser.JsonException("No envelope")
            val choice = (envelope.map["choices"] as? Value.Arr)?.items?.firstOrNull() as? Value.Obj
                ?: throw JsonParser.JsonException("No choice")
            val message = choice.map["message"] as? Value.Obj ?: throw JsonParser.JsonException("No message")
            val content = message.map["content"] as? Value.Str
                ?: throw if ((choice.map["finish_reason"] as? Value.Str)?.text == "length") CutOffException() else JsonParser.JsonException("No content")
            val array = JsonParser.parse(content.text) as? Value.Arr ?: throw JsonParser.JsonException("No array")
            readArray(array, photoCount)
        } catch (_: CutOffException) {
            return AiOutcome.Failed("The AI reply was cut off. Try again.")
        } catch (_: JsonParser.JsonException) {
            return AiOutcome.Rejected(UNVERIFIED)
        } catch (_: IllegalArgumentException) {
            return AiOutcome.Rejected(UNVERIFIED)
        }
        if (items.isEmpty()) return AiOutcome.Rejected(UNVERIFIED)
        return AiOutcome.Suggestions(items)
    }

    private class CutOffException : Exception()

    /**
     * Observed in the step-2 probe: the model echoes the API's image-part
     * naming (`"input_0"`) instead of the requested zero-based integers.
     * Both spellings map to the same index; anything else stays rejected.
     */
    private val INPUT_ID = Regex("^input_(\\d+)$", RegexOption.IGNORE_CASE)
    private fun toPhotoIndex(value: Value): Int {
        val raw = when (value) {
            is Value.Num -> value.raw
            is Value.Str -> INPUT_ID.matchEntire(value.text.trim())?.groupValues?.get(1)
            else -> null
        } ?: throw JsonParser.JsonException("Bad photo id")
        return raw.toIntOrNull() ?: throw JsonParser.JsonException("Bad photo id")
    }

    /** Proxy shape: `{"suggestions": [...]}` with the same item contract. */
    fun parseProxy(bodyJson: String, photoCount: Int): AiOutcome {
        val items = try {
            val root = JsonParser.parse(bodyJson) as? Value.Obj ?: throw JsonParser.JsonException("No body")
            val array = root.map["suggestions"] as? Value.Arr ?: throw JsonParser.JsonException("No suggestions")
            readArray(array, photoCount)
        } catch (_: JsonParser.JsonException) {
            return AiOutcome.Rejected(UNVERIFIED)
        } catch (_: IllegalArgumentException) {
            return AiOutcome.Rejected(UNVERIFIED)
        }
        if (items.isEmpty()) return AiOutcome.Rejected(UNVERIFIED)
        return AiOutcome.Suggestions(items)
    }

    private fun readArray(array: Value.Arr, photoCount: Int): List<AiSuggestion> =
        array.items.mapNotNull { item ->
            readSuggestion(item as? Value.Obj ?: throw JsonParser.JsonException("No object"), photoCount)
        }

    private fun readSuggestion(obj: Value.Obj, photoCount: Int): AiSuggestion? {
        val observation = (obj.map["observation"] as? Value.Str)?.text?.takeIf { it.isNotBlank() }
            ?: throw JsonParser.JsonException("Bad observation")
        val indices = ((obj.map["photo_ids"] as? Value.Arr)?.items ?: throw JsonParser.JsonException("Bad photo_ids"))
            .map { toPhotoIndex(it) }
        if (indices.isEmpty() || indices.any { it < 0 || it >= photoCount }) throw JsonParser.JsonException("Ungrounded")
        val uncertainty = when (val raw = obj.map["uncertainty"] ?: throw JsonParser.JsonException("Bad uncertainty")) {
            is Value.Str -> raw.text
            // Observed in the step-2 probe: the model sometimes scores uncertainty 0-1.
            is Value.Num -> raw.raw
            else -> throw JsonParser.JsonException("Bad uncertainty")
        }
        val missing = when (val raw = obj.map["missing_information"] ?: throw JsonParser.JsonException("Bad missing")) {
            is Value.Str -> raw.text
            is Value.Null -> null
            else -> throw JsonParser.JsonException("Bad missing")
        }
        val version = (obj.map["model_version"] as? Value.Str)?.text?.takeIf { it.isNotBlank() }
            ?: throw JsonParser.JsonException("Bad version")
        // Banned conclusions drop the item, not the whole reply.
        if (BANNED.any { (observation + " " + uncertainty + " " + (missing ?: "")).lowercase().contains(it) }) return null
        return AiSuggestion(observation, indices.distinct(), uncertainty, missing, version)
    }
}
