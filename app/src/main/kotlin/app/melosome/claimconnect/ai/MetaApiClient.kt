package app.melosome.claimconnect.ai

import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live Meta client (stdlib `HttpURLConnection`, no new dependencies). BYOK v1:
 * the key is supplied by the user in Settings, held encrypted on-device, and
 * sent only as a `Bearer` header over HTTPS. Wire shape per `spike/contract.md`.
 */
class MetaApiClient(
    private val baseUrl: String,
    private val key: () -> String?,
    private val model: String,
    // 4000: the step-2 probe showed ~300 reasoning tokens before any answer.
    private val maxTokens: Int = 4000,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000,
) : AiClient {
    override suspend fun analyze(photos: List<AiPhoto>): AiOutcome = withContext(Dispatchers.IO) {
        AiResponseParser.checkPhotos(photos)?.let { return@withContext AiOutcome.Failed(it) }
        val bearer = key()?.takeIf { it.isNotBlank() } ?: return@withContext AiOutcome.Failed("Add your API key in Settings to run the AI check.")
        try {
            val status = Http.postJson(
                baseUrl.trimEnd('/') + "/chat/completions",
                mapOf("Authorization" to "Bearer $bearer",
                    "Content-Type" to "application/json", "Accept" to "application/json"),
                requestBody(photos).toByteArray(Charsets.UTF_8),
                connectTimeoutMs, readTimeoutMs,
            )
            if (status.code != 200) return@withContext FailedFor(status.code)
            AiResponseParser.parse(status.body.toString(Charsets.UTF_8), photos.size)
        } catch (_: java.net.SocketTimeoutException) {
            AiOutcome.Failed("The analysis timed out. Try again when you have a better connection.")
        } catch (_: java.io.IOException) {
            AiOutcome.Failed("Couldn't reach the analysis service. Check your connection and try again.")
        }
    }

    private fun requestBody(photos: List<AiPhoto>): String {
        val parts = photos.joinToString(",") { photo ->
            val data = Base64.getEncoder().encodeToString(photo.jpegBytes)
            """{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,$data"}}"""
        }
        return """{"model":"${JsonParser.escape(model)}","messages":[{"role":"user","content":[{"type":"text","text":"${JsonParser.escape(PROMPT)}"},$parts]}],"max_tokens":$maxTokens}"""
    }

    companion object {
        const val PROMPT =
            "Reply with strict JSON only: a list of objects with keys observation, photo_ids, " +
                "uncertainty, missing_information, model_version. Each observation names one visible " +
                "detail in plain words; photo_ids MUST be the zero-based integer indices of the " +
                "sent photos that support it, e.g. [0, 2]; uncertainty is a short sentence. " +
                "Never conclude fault, speed, injury, or costs, and never invent " +
                "facts not visible in the photos."
        private fun FailedFor(code: Int): AiOutcome.Failed = AiOutcome.Failed(
            when (code) {
                429 -> "The API is busy or your key's allowance is used up. Try again later."
                401, 403 -> "The API key was refused. Check your key in Settings and try again."
                in 500..599 -> "The analysis service failed. Try again shortly."
                else -> "The analysis could not be completed. Try again."
            },
        )
    }
}
