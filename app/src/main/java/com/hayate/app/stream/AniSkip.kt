package com.hayate.app.stream

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class SkipSeg(val type: String, val start: Double, val end: Double)

object AniSkip {
    private val cache = ConcurrentHashMap<String, List<SkipSeg>>()
    fun get(malId: Int, ep: Int): List<SkipSeg> {
        val key = "$malId:$ep"
        cache[key]?.let { return it }
        val out: List<SkipSeg> = try {
            val u = "https://api.aniskip.com/v2/skip-times/$malId/$ep" +
                "?types%5B%5D=op&types%5B%5D=ed&types%5B%5D=mixed-op&types%5B%5D=mixed-ed&types%5B%5D=recap&episodeLength=0"
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 6000
            if (c.responseCode != 200) emptyList() else {
                val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results")
                (0 until (arr?.length() ?: 0)).map { i ->
                    val o = arr!!.getJSONObject(i); val iv = o.getJSONObject("interval")
                    SkipSeg(o.getString("skipType"), iv.getDouble("startTime"), iv.getDouble("endTime"))
                }.sortedBy { it.start }
            }
        } catch (e: Exception) { return emptyList() }
        cache[key] = out
        return out
    }
}
