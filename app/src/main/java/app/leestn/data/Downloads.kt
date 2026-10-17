package app.leestn.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Progress 0..1 while downloading. */
sealed class Dl {
    class Progress(val frac: Float) : Dl()
    object Queued : Dl()
    object Done : Dl()
}

/** Offline copies of episodes. Files live in app storage, so uninstalling the app removes them. */
object Downloads {
    private var dir: File? = null

    /** Episode id to state. Absent means not downloaded. Safe to read from composables. */
    val state = mutableStateMapOf<String, Dl>()

    fun init(ctx: Context) {
        val d = File(ctx.filesDir, "downloads")
        d.mkdirs()
        dir = d
        d.listFiles()?.forEach { f ->
            when {
                f.name.endsWith(".part") -> f.delete()
                f.name.endsWith(".audio") -> state[f.name.removeSuffix(".audio")] = Dl.Done
            }
        }
    }

    private fun file(id: String) = File(dir ?: error("Downloads not initialised"), "$id.audio")
    private fun part(id: String) = File(dir ?: error("Downloads not initialised"), "$id.part")

    fun isDone(id: String) = state[id] is Dl.Done

    fun localUri(id: String): Uri? = if (isDone(id) && file(id).exists()) Uri.fromFile(file(id)) else null

    fun sizeBytes(): Long = dir?.listFiles()?.filter { it.name.endsWith(".audio") }?.sumOf { it.length() } ?: 0L

    fun doneIds(): List<String> = state.filterValues { it is Dl.Done }.keys.toList()

    fun enqueue(ctx: Context, ep: Episode, wifiOnly: Boolean) {
        if (ep.audioUrl.isBlank() || state.containsKey(ep.id)) return
        state[ep.id] = Dl.Queued
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("id" to ep.id, "url" to ep.audioUrl))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("dl")
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("dl-${ep.id}", ExistingWorkPolicy.KEEP, req)
    }

    fun remove(ctx: Context, id: String) {
        WorkManager.getInstance(ctx).cancelUniqueWork("dl-$id")
        file(id).delete()
        part(id).delete()
        state.remove(id)
    }

    fun removeAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag("dl")
        dir?.listFiles()?.forEach { it.delete() }
        state.clear()
    }

    internal fun download(id: String, url: String) {
        val target = file(id)
        val tmp = part(id)
        Http.client.newCall(Request.Builder().url(url).header("User-Agent", "Leestn/1.0 (Android podcast app)").build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            var done = 0L
            var lastReport = 0L
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (!state.containsKey(id)) throw java.util.concurrent.CancellationException("Removed")
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            state[id] = Dl.Progress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        target.delete()
        if (!tmp.renameTo(target)) throw IOException("Couldn't save the file")
        state[id] = Dl.Done
    }

    internal fun failed(id: String) {
        part(id).delete()
        state.remove(id)
    }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val url = inputData.getString("url") ?: return Result.failure()
        return try {
            Downloads.download(id, url)
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount >= 3) { Downloads.failed(id); Result.failure() } else Result.retry()
        } catch (e: Exception) {
            Downloads.failed(id)
            Result.failure()
        }
    }

}
