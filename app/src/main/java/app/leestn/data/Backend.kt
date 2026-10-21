package app.leestn.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** Talks to the Leestn Cloudflare Worker (see the backend folder). */
object Backend {
    class Tx(val status: String, val progress: Float, val error: String?, val lines: List<TxLine>, val timing: TxEdit? = null)

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
        val timing = j.optJSONObject("timing")?.let { t ->
            val a = HashMap<Int, Int>()
            t.optJSONArray("anchors")?.let { arr ->
                for (i in 0 until arr.length()) { val p = arr.optJSONArray(i) ?: continue; a[p.optInt(0)] = p.optInt(1) }
            }
            val sh = ArrayList<Shift>()
            t.optJSONArray("shifts")?.let { arr ->
                for (i in 0 until arr.length()) { val p = arr.optJSONArray(i) ?: continue; sh.add(Shift(p.optInt(0), p.optInt(1, -1), p.optInt(2))) }
            }
            TxEdit(t.optInt("offset"), a, sh)
        }
        return Tx(j.optString("status", "none"), j.optDouble("progress", 0.0).toFloat(), j.optString("error").ifBlank { null }, lines, timing)
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

    /** Save the timing fix for everyone on the server (an empty fix clears it). */
    fun putTiming(base: String, key: String, epId: String, e: TxEdit) {
        val anchors = org.json.JSONArray()
        e.anchors.forEach { (i, sec) -> anchors.put(org.json.JSONArray().put(i).put(sec)) }
        val shifts = org.json.JSONArray()
        e.shifts.forEach { shifts.put(org.json.JSONArray().put(it.from).put(it.to).put(it.ms)) }
        val body = JSONObject().put("offset", e.offset).put("anchors", anchors).put("shifts", shifts).toString()
        val req = builder("$base/v1/transcripts/${java.net.URLEncoder.encode(epId, "UTF-8")}/timing", key)
            .put(body.toRequestBody("application/json".toMediaType())).build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = try { JSONObject(resp.body?.string().orEmpty()).optString("error") } catch (e: Exception) { "" }
                throw IOException(msg.ifBlank { "Server error ${resp.code}" })
            }
        }
    }
}
