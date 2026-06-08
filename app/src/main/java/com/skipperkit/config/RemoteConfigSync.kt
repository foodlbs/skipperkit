package com.skipperkit.config

import android.util.Log
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * One-shot HTTPS fetch of the raw remote config text. Deliberately conservative:
 *  - HTTPS only (rejects http:// — the config controls which nodes get tapped, so
 *    an attacker-in-the-middle is a real concern; TLS is the floor).
 *  - 8s connect/read timeouts; never blocks indefinitely.
 *  - Response capped at [MAX_BYTES] so a hostile/huge body can't OOM us.
 *
 * Returns the raw body on HTTP 200, or null on any failure. The caller falls back
 * to cached, then bundled config — a failed fetch is a normal, non-fatal path.
 */
object RemoteConfigSync {

    private const val TAG = "SkipperKit"
    private const val TIMEOUT_MS = 8000
    private const val MAX_BYTES = 512 * 1024

    fun fetch(urlString: String): String? {
        if (!urlString.startsWith("https://")) {
            Log.w(TAG, "Refusing non-HTTPS remote config URL")
            return null
        }
        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpsURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            if (conn.responseCode != HttpsURLConnection.HTTP_OK) {
                Log.w(TAG, "Remote config HTTP ${conn.responseCode}")
                return null
            }
            conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                var total = 0
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) {
                        Log.w(TAG, "Remote config exceeded $MAX_BYTES bytes; ignoring")
                        return null
                    }
                    sb.appendRange(buf, 0, n)
                }
                sb.toString()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Remote config fetch failed", t)
            null
        } finally {
            conn?.disconnect()
        }
    }
}
