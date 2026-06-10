package com.skipperkit.contribute

import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * POSTs a contribution payload. HTTPS-only, 8 s timeouts, response body
 * ignored. Callers must invoke off the main thread (Dispatchers.IO).
 */
object ContributionSender {

    enum class Result { SENT, RATE_LIMITED, FAILED }

    private const val TIMEOUT_MS = 8_000

    fun send(urlString: String, jsonBody: String): Result {
        if (!urlString.startsWith("https://")) return Result.FAILED
        return runCatching {
            val conn = URL(urlString).openConnection() as HttpsURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                when {
                    conn.responseCode in 200..299 -> Result.SENT
                    conn.responseCode == 429 -> Result.RATE_LIMITED
                    else -> Result.FAILED
                }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(Result.FAILED)
    }
}
