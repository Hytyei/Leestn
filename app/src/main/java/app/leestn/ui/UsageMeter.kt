package app.leestn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors
import java.text.DateFormat
import java.util.Date

private val Amber = Color(0xFFFFB020)

/** Minutes as "1 h 20 min" or "45 min". */
fun minutesText(m: Double): String {
    val t = Math.round(m).toInt()
    return if (t >= 60) "${t / 60} h ${t % 60} min" else "$t min"
}

/**
 * Shows how much of today's free Whisper time the server has left.
 * The server counts the audio it was asked to transcribe, so this is an estimate and cannot see
 * other Workers AI use in the same Cloudflare account.
 * [needMin] is the length of the episode about to be transcribed. When it does not fit, a warning shows.
 */
@Composable
fun UsageMeter(vm: AppViewModel, needMin: Double? = null, modifier: Modifier = Modifier) {
    LaunchedEffect(vm.connected) { if (vm.connected) vm.loadUsage() }
    val u = vm.usage ?: return
    val used = if (u.limitMin > 0) (u.usedMin / u.limitMin).toFloat() else 1f
    val tooLong = needMin != null && needMin > u.remainingMin
    val color = when {
        tooLong || used >= 1f -> LColors.Red
        used >= 0.8f -> Amber
        else -> LColors.Good
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Free transcription today", style = MaterialTheme.typography.labelLarge)
            Text("${minutesText(u.remainingMin)} left", style = MaterialTheme.typography.labelLarge, color = color)
        }
        ThinBar(used, color = color)
        val resets = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(u.resetsAt))
        Text(
            "${minutesText(u.usedMin)} of ${minutesText(u.limitMin.toDouble())} used. Resets at $resets. This is an estimate.",
            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
        )
        if (tooLong && needMin != null) {
            Text(
                "This episode is about ${minutesText(needMin)} long, more than is left today. Try again after the reset, or choose a shorter episode.",
                style = MaterialTheme.typography.bodySmall, color = LColors.Red,
            )
        } else if (used >= 1f) {
            Text("Today's free allowance is used up. Transcripts work again after the reset.", style = MaterialTheme.typography.bodySmall, color = LColors.Red)
        }
    }
}
