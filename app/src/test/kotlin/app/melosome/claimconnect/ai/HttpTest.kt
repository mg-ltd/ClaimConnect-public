package app.melosome.claimconnect.ai

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class HttpTest {
    @Test fun postRoundTripsJson() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/echo") { exchange ->
                val body = exchange.requestBody.readAllBytes()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            val status = Http.postJson("http://127.0.0.1:${server.address.port}/echo",
                mapOf("Content-Type" to "application/json"), """{"a":1}""".toByteArray(),
                5_000, 5_000)
            assertEquals(200, status.code)
            assertEquals("""{"a":1}""", status.body.toString(Charsets.UTF_8))
        } finally {
            server.stop(0)
        }
    }

    @Test fun cancellationDisconnectsQuickly() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/slow") { exchange ->
                Thread.sleep(10_000)
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            server.start()
            val started = System.currentTimeMillis()
            val job = async(Dispatchers.IO) {
                Http.postJson("http://127.0.0.1:${server.address.port}/slow", emptyMap(),
                    ByteArray(0), 5_000, 30_000)
            }
            delay(300)
            job.cancel()
            try {
                withTimeout(5_000) { job.await() }
                fail("Cancelled call must not return")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(System.currentTimeMillis() - started < 5_000)
        } finally {
            server.stop(0)
        }
    }
}
