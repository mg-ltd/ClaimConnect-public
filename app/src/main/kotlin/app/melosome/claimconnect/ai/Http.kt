package app.melosome.claimconnect.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cancellable stdlib HTTP POST. Disconnects the connection when the caller is
 * cancelled. Blocking: callers must invoke this on Dispatchers.IO.
 */
object Http {
    class Status(val code: Int, val body: ByteArray)

    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): Status = suspendCancellableCoroutine { continuation ->
        var connection: HttpURLConnection? = null
        continuation.invokeOnCancellation { runCatching { connection?.disconnect() } }
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (continuation.isActive) continuation.resume(Status(code, bytes))
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resumeWithException(e)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
