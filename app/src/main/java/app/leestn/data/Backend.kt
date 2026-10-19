package app.leestn.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** Talks to the Leestn Cloudflare Worker (see the backend folder). */
object Backend {
    class Tx(val status: String, val progress: Float, val error: String?, val lines: List<TxLine>)

    private fun builder(url: String, key: String): Request.Builder {
        val b = Request.Builder().url(url).header("User-Agent", "Leestn/1.0 (Android podcast app)")
        if (key.isNotBlank()) b.header("Authorization", "Bearer $key")
        return b
    }

    private fun parse(body: String): Tx {
        val j = JSONObject(body)
        val lines = ArrayList<TxLine>()
        j.optJSONArray("lines")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val text = o.optString("text").trim()
                if (text.isNotEmpty()) lines.add(TxLine(o.optInt("t", 0), o.optString("who", ""), text))
            }
        }
        return Tx(j.optString("status", "none"), j.optDouble("progress", 0.0).toFloat(), j.optString("error").ifBlank { null }, lines)
    }

    private fun run(req: Request): Tx {
        Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = try { JSONObject(text).optString("error") } catch (e: Exception) { "" }
                throw IOException(msg.ifBlank { "Server error ${resp.code}" })
            }
            return parse(text)
        }
    }

    fun transcript(base: String, key: String, epId: String): Tx =
        run(builder("$base/v1/transcripts/${java.net.URLEncoder.encode(epId, "UTF-8")}", key).get().build())

    fun requestTranscript(base: String, key: String, epId: String, audioUrl: String, durationSec: Int, language: String = "", force: Boolean = false): Tx {
        val body = JSONObject().put("epId", epId).put("audioUrl", audioUrl).put("durationSec", durationSec).put("language", language).put("force", force).toString()
        return run(builder("$base/v1/transcripts", key).post(body.toRequestBody("application/json".toMediaType())).build())
    }
}
