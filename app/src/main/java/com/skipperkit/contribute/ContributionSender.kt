package com.skipperkit.contribute

import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * POSTs a contribution payload. HTTPS-only, 8 s timeouts, response body
 * ignored. Callers must invoke off the main thread (Dispatchers.IO).
 */
object ContributionSender {

    private const val TIMEOUT_MS = 8_000

    fun send(urlString: String, jsonBody: String): Boolean {
        if (!urlString.startsWith("https://")) return false
        return runCatching {
            val conn = URL(urlString).openConnection() as HttpsURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }
}
