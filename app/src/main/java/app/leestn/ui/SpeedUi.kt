package app.leestn.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

/** 1.0 -> "1×", 1.25 -> "1.25×", 1.5 -> "1.5×". */
fun speedText(s: Float): String = "%.2f".format(java.util.Locale.US, s).trimEnd('0').trimEnd('.') + "×"

/** Pick any playback speed from 0.5× to 3× in steps of 0.05. The speed is remembered and used for every episode. */
@Composable
fun SpeedDialog(vm: AppViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = LColors.Surface,
        title = { Text("Playback speed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(speedText(vm.speed), style = MaterialTheme.typography.displaySmall)
                Slider(
                    value = vm.speed, onValueChange = { vm.changeSpeed(it) }, valueRange = 0.5f..3f, steps = 49,
                    colors = SliderDefaults.colors(thumbColor = LColors.Red, activeTrackColor = LColors.Red, inactiveTrackColor = LColors.Surface3),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("−0.05", false) { vm.changeSpeed(vm.speed - 0.05f) }
                    Chip("+0.05", false) { vm.changeSpeed(vm.speed + 0.05f) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)) {
                        Chip(speedText(v), kotlin.math.abs(vm.speed - v) < 0.001f) { vm.changeSpeed(v) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
