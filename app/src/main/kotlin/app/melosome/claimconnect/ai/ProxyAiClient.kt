package app.melosome.claimconnect.ai

import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-track client: talks to our proxy (key custody, quota, billing
 * server-side), never to Meta directly. The proxy returns already-shaped
 * suggestion JSON; items are still strictly re-validated on-device.
 */
class ProxyAiClient(
    private val baseUrl: String,
    private val installId: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000,
) : AiClient {
    override suspend fun analyze(photos: List<AiPhoto>): AiOutcome = withContext(Dispatchers.IO) {
        AiResponseParser.checkPhotos(photos)?.let { return@withContext AiOutcome.Failed(it) }
        try {
            val parts = photos.joinToString(",") { photo ->
                """{"mime":"image/jpeg","data":"${Base64.getEncoder().encodeToString(photo.jpegBytes)}"}"""
            }
            val status = Http.postJson(
                baseUrl.trimEnd('/') + "/v1/analyze",
                mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                """{"install_id":"${JsonParser.escape(installId)}","photos":[$parts]}""".toByteArray(Charsets.UTF_8),
                connectTimeoutMs, readTimeoutMs,
            )
            if (status.code != 200) return@withContext FailedFor(status)
            AiResponseParser.parseProxy(status.body.toString(Charsets.UTF_8), photos.size)
        } catch (_: java.net.SocketTimeoutException) {
            AiOutcome.Failed("The analysis timed out. Try again when you have a better connection.")
        } catch (_: java.io.IOException) {
            AiOutcome.Failed("Couldn't reach the analysis service. Check your connection and try again.")
        }
    }

    suspend fun quota(): Quota = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(baseUrl.trimEnd('/') + "/v1/quota?install_id=" +
                java.net.URLEncoder.encode(installId, "UTF-8"))
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
            }
            try {
                if (connection.responseCode != 200) return@withContext Quota.Unknown
                val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val map = (JsonParser.parse(body) as? JsonParser.Value.Obj)?.map
                    ?: return@withContext Quota.Unknown
                val remaining = ((map["remaining"] as? JsonParser.Value.Num)?.raw?.toIntOrNull())
                    ?: return@withContext Quota.Unknown
                val allowance = ((map["free_allowance"] as? JsonParser.Value.Num)?.raw?.toIntOrNull())
                    ?: return@withContext Quota.Unknown
                Quota.Known(remaining, allowance, (map["entitled"] as? JsonParser.Value.True) != null)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            Quota.Unknown
        }
    }

    sealed interface Quota {
        data class Known(val remaining: Int, val allowance: Int, val entitled: Boolean) : Quota
        data object Unknown : Quota
    }

    companion object {
        private fun FailedFor(status: Http.Status): AiOutcome.Failed {
            val serverMessage = runCatching {
                ((JsonParser.parse(status.body.toString(Charsets.UTF_8)) as? JsonParser.Value.Obj)
                    ?.map?.get("error") as? JsonParser.Value.Str)?.text
            }.getOrNull()
            return AiOutcome.Failed(
                when (status.code) {
                    429 -> serverMessage ?: "The free allowance is used up for this month."
                    503 -> serverMessage ?: "The analysis service is paused. Try again later."
                    in 500..599 -> "The analysis service failed. Try again shortly."
                    else -> serverMessage ?: "The analysis could not be completed. Try again."
                },
            )
        }
    }
}
