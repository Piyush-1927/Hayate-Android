package com.hayate.core.anilist

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object AniListClient {
    private const val API_URL = "https://graphql.anilist.co"

    /** Blocking call: run it off the main thread. */
    fun query(query: String, variables: JSONObject = JSONObject()): JSONObject =
        send(query, variables, retried = false)

    private fun send(query: String, variables: JSONObject, retried: Boolean): JSONObject {
        val body = JSONObject().put("query", query).put("variables", variables).toString()
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Hayate-Android/0.1")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 429 && !retried) {
                val wait = (conn.getHeaderField("Retry-After")?.toIntOrNull() ?: 5).coerceIn(1, 30)
                Thread.sleep(wait * 1000L)
                return send(query, variables, retried = true)
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("AniList HTTP $code")
            val result = JSONObject(text)
            if (result.has("errors") && result.optJSONObject("data") == null) {
                val msg = result.getJSONArray("errors").optJSONObject(0)?.optString("message")
                throw IOException("AniList error: $msg")
            }
            return result
        } finally {
            conn.disconnect()
        }
    }
}
