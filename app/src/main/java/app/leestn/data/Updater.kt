package app.leestn.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A published release of Leestn on GitHub that carries an APK. */
data class AppRelease(val tag: String, val version: String, val notes: String, val apkUrl: String, val size: Long, val sha256: String?)

/**
 * In-app updates from the GitHub releases of Hytyei/Leestn.
 * It always looks at the newest releases, pre-releases included, and offers the highest version.
 * The repository must be public, or friends' phones cannot read its releases.
 */
object Updater {
    const val REPO = "Hytyei/Leestn"
    private const val API = "https://api.github.com/repos/$REPO/releases?per_page=15"

    /** Highest-version release that has an .apk file, or null when there is none. */
    fun latest(): AppRelease? {
        val req = Request.Builder().url(API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Leestn-Android-Updater")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.code == 404) throw IOException("Can't read the Leestn releases. The GitHub repository must be public.")
            if (resp.code == 403 || resp.code == 429) throw IOException("GitHub is limiting requests right now. Try again in a few minutes.")
            if (!resp.isSuccessful) throw IOException("GitHub error ${resp.code}")
            val list = JSONArray(resp.body?.string().orEmpty())
            var best: AppRelease? = null
            for (i in 0 until list.length()) {
                val r = list.getJSONObject(i)
                if (r.optBoolean("draft")) continue
                val tag = r.optString("tag_name")
                val assets = r.optJSONArray("assets") ?: continue
                var apk: org.json.JSONObject? = null
                for (k in 0 until assets.length()) {
                    val a = assets.getJSONObject(k)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) { apk = a; break }
                }
                if (apk == null) continue
                val digest = apk.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
                val rel = AppRelease(tag, tag.removePrefix("v"), r.optString("body"), apk.optString("browser_download_url"), apk.optLong("size"), digest)
                if (best == null || Version.isNewer(rel.version, best.version)) best = rel
            }
            return best
        }
    }

    fun installedVersion(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    /** Downloads the APK into the app's cache, reporting progress 0..1. Old downloads are removed first. */
    fun download(ctx: Context, rel: AppRelease, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "leestn-${rel.version}.apk")
        val req = Request.Builder().url(rel.apkUrl).header("User-Agent", "Leestn-Android-Updater").build()
        val md = MessageDigest.getInstance("SHA-256")
        Http.client.newBuilder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (${resp.code})")
            val body = resp.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: rel.size
            var done = 0L
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (rel.sha256 != null) {
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(rel.sha256, ignoreCase = true)) { out.delete(); throw IOException("The download is damaged. Try again.") }
        }
        return out
    }

    /** Checks the file is Leestn, newer than what is installed, and signed with the same key. Returns an error text, or null when fine. */
    @Suppress("DEPRECATION")
    fun problem(ctx: Context, apk: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags) ?: return "The downloaded file isn't a valid app."
        if (info.packageName != ctx.packageName) return "The downloaded file is a different app."
        val mine = try { pm.getPackageInfo(ctx.packageName, flags) } catch (e: Exception) { return null }
        fun sigs(p: android.content.pm.PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures)
                ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }?.toSet() ?: emptySet()
        val a = sigs(info)
        val b = sigs(mine)
        if (a.isNotEmpty() && b.isNotEmpty() && a.intersect(b).isEmpty()) {
            return "This update is signed with a different key than the installed app, so Android won't install it over the top. Uninstall Leestn once and install the new APK by hand; after that, updates work."
        }
        return null
    }

    fun canInstall(ctx: Context): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens Android's "allow from this app" screen. */
    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hands the APK to Android's installer. The person still taps Install. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val rel: AppRelease) : UpdateState
    data class Downloading(val rel: AppRelease, val progress: Float) : UpdateState
    data class Ready(val rel: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
