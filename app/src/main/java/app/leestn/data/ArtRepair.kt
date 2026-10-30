package app.leestn.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * When a show's own cover address fails to load, look the show up in Apple's directory and use its cover instead.
 * The result is remembered for this run. [lastError] holds the most recent load failure so Settings can show it.
 */
object ArtRepair {
    private val fixed = mutableStateMapOf<String, String>()
    private val tried = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var lastError by mutableStateOf<String?>(null)

    /** The address to draw: the repaired one if there is one, else the show's own. */
    fun url(p: Podcast): String = fixed[p.id] ?: p.artworkUrl

    fun failed(p: Podcast, url: String, error: Throwable?) {
        lastError = "${p.name.ifBlank { p.id }}: ${error?.javaClass?.simpleName ?: "error"} ${error?.message ?: ""} ($url)".take(300)
        if (p.name.isBlank() || fixed.containsKey(p.id) || !tried.add(p.id)) return
        scope.launch {
            try {
                val want = p.name.lowercase().filter { it.isLetterOrDigit() }
                val hit = Itunes.search(p.name, 8).firstOrNull { it.name.lowercase().filter { c -> c.isLetterOrDigit() } == want && it.artworkUrl.isNotBlank() }
                if (hit != null && hit.artworkUrl != url) fixed[p.id] = hit.artworkUrl
            } catch (e: Exception) {
                // no repair possible: the placeholder stays
            }
        }
    }
}
