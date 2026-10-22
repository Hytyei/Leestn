package app.leestn.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Things the person does to transcripts on this device: timing fixes, pasted text, display choices. */
class TxLocal(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("leestn_tx", Context.MODE_PRIVATE)
    private val dir = File(ctx.filesDir, "tx").apply { mkdirs() }

    var timings: Boolean
        get() = prefs.getBoolean("timings", true)
        set(v) { prefs.edit().putBoolean("timings", v).apply() }

    /** Which transcript to show when there are two: "paste" or "orig". */
    fun source(epId: String): String? = prefs.getString("src_" + key(epId), null)
    fun setSource(epId: String, v: String) { prefs.edit().putString("src_" + key(epId), v).apply() }

    fun edit(epId: String, source: String): TxEdit {
        val s = prefs.getString("edit_${key(epId)}_$source", null) ?: return TxEdit()
        return try {
            val j = JSONObject(s)
            val a = HashMap<Int, Int>()
            val arr = j.optJSONArray("a") ?: JSONArray()
            for (i in 0 until arr.length()) { val p = arr.getJSONArray(i); a[p.getInt(0)] = p.getInt(1) }
            val sh = ArrayList<Shift>()
            val sa = j.optJSONArray("s") ?: JSONArray()
            for (i in 0 until sa.length()) { val p = sa.getJSONArray(i); sh.add(Shift(p.getInt(0), p.getInt(1), p.getInt(2))) }
            TxEdit(j.optInt("o"), a, sh)
        } catch (e: Exception) { TxEdit() }
    }

    fun saveEdit(epId: String, source: String, e: TxEdit) {
        val k = "edit_${key(epId)}_$source"
        if (e.isEmpty) { prefs.edit().remove(k).apply(); return }
        val arr = JSONArray()
        e.anchors.forEach { (i, s) -> arr.put(JSONArray().put(i).put(s)) }
        val sa = JSONArray()
        e.shifts.forEach { sa.put(JSONArray().put(it.from).put(it.to).put(it.ms)) }
        prefs.edit().putString(k, JSONObject().put("o", e.offset).put("a", arr).put("s", sa).toString()).apply()
    }

    /** Lines the person highlighted by hand in plain-text mode. */
    fun highlights(epId: String, source: String): Set<Int> {
        val s = prefs.getString("hl_${key(epId)}_$source", null) ?: return emptySet()
        return try { val a = JSONArray(s); (0 until a.length()).map { a.getInt(it) }.toSet() } catch (e: Exception) { emptySet() }
    }

    fun saveHighlights(epId: String, source: String, set: Set<Int>) {
        val k = "hl_${key(epId)}_$source"
        if (set.isEmpty()) { prefs.edit().remove(k).apply(); return }
        val a = JSONArray(); set.sorted().forEach { a.put(it) }
        prefs.edit().putString(k, a.toString()).apply()
    }

    private fun pasteFile(epId: String) = File(dir, key(epId) + ".txt")
    fun paste(epId: String): String? = pasteFile(epId).takeIf { it.exists() }?.readText()
    fun savePaste(epId: String, text: String) { pasteFile(epId).writeText(text) }
    fun deletePaste(epId: String) { pasteFile(epId).delete() }

    private fun key(epId: String): String =
        MessageDigest.getInstance("SHA-1").digest(epId.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
}
