package app.melosome.claimconnect.ai

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EntitlementTest {
    private fun server(routes: Map<String, Pair<Int, String>>): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        routes.forEach { (path, answer) ->
            server.createContext(path) { exchange ->
                exchange.requestBody.readAllBytes()
                val body = answer.second.toByteArray()
                exchange.sendResponseHeaders(answer.first, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        return server
    }

    @Test fun verificationAcceptsOnlyTrue() = runBlocking {
        val server = server(mapOf("/v1/entitlement" to (200 to """{"entitled":true}""")))
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertTrue(EntitlementClient(base, "install-1").verify("ai_unlock", "token-123"))
        } finally {
            server.stop(0)
        }
    }

    @Test fun verificationRejectsFalseAndBrokenReplies() = runBlocking {
        val cases = mapOf(
            "no" to (200 to """{"entitled":false}"""),
            "bad" to (200 to "not json"),
            "err" to (500 to """{"error":"x"}"""),
        )
        val server = server(mapOf("/v1/entitlement" to cases.getValue("no")))
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertFalse(EntitlementClient(base, "install-1").verify("ai_unlock", "token-123"))
        } finally {
            server.stop(0)
        }
        assertFalse(EntitlementClient.parseEntitlement("not json"))
        assertFalse(EntitlementClient.parseEntitlement("""{"entitled":false}"""))
        assertFalse(EntitlementClient.parseEntitlement("""{"entitled":"yes"}"""))
        assertTrue(EntitlementClient.parseEntitlement("""{"entitled":true}"""))
    }

    @Test fun quotaParsesKnownAndUnknown() = runBlocking {
        val good = server(mapOf("/v1/quota" to (200 to
            """{"month":"2026-11","free_allowance":10,"used":7,"remaining":3,"entitled":false}""")))
        try {
            val quota = ProxyAiClient("http://127.0.0.1:${good.address.port}", "install-1").quota()
            assertEquals(ProxyAiClient.Quota.Known(3, 10, false), quota)
        } finally {
            good.stop(0)
        }
        val bad = server(mapOf("/v1/quota" to (200 to "not json")))
        try {
            assertEquals(ProxyAiClient.Quota.Unknown,
                ProxyAiClient("http://127.0.0.1:${bad.address.port}", "install-1").quota())
        } finally {
            bad.stop(0)
        }
        assertEquals(ProxyAiClient.Quota.Unknown,
            ProxyAiClient("http://127.0.0.1:1", "install-1").quota())
    }
}
