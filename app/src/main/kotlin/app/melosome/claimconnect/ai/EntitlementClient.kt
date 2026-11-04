package app.melosome.claimconnect.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Server-side purchase verification. The app sends the Play purchase token to
 * the proxy, which confirms it (simulated accept in the offline build, Play
 * Developer API on probe day) and records the entitlement. A `true` answer is
 * the only thing that unlocks checks.
 */
class EntitlementClient(
    private val baseUrl: String,
    private val installId: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) {
    suspend fun verify(productId: String, purchaseToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val status = Http.postJson(
                baseUrl.trimEnd('/') + "/v1/entitlement",
                mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                """{"install_id":"${JsonParser.escape(installId)}","product_id":"${JsonParser.escape(productId)}","purchase_token":"${JsonParser.escape(purchaseToken)}"}""".toByteArray(Charsets.UTF_8),
                connectTimeoutMs, readTimeoutMs,
            )
            status.code == 200 && parseEntitlement(status.body.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun parseEntitlement(body: String): Boolean = runCatching {
            ((JsonParser.parse(body) as? JsonParser.Value.Obj)?.map?.get("entitled")
                as? JsonParser.Value.True) != null
        }.getOrDefault(false)
    }
}
