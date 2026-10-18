package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.leestn.data.UpdateState
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

/** Update controls for Settings: check, download, install. */
@Composable
fun UpdateControls(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val u = vm.update) {
            UpdateState.Idle -> ActionPill("Check for updates", Icons.Rounded.SystemUpdate) { vm.checkUpdate(true) }
            UpdateState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
            UpdateState.UpToDate -> {
                Text("You have the latest version.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                ActionPill("Check again", Icons.Rounded.SystemUpdate) { vm.checkUpdate(true) }
            }
            is UpdateState.Available -> {
                Text("Version ${u.rel.version} is available.", style = MaterialTheme.typography.titleSmall)
                if (u.rel.notes.isNotBlank()) Text(u.rel.notes.take(400), style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                ActionPill("Download and install", Icons.Rounded.SystemUpdate, primary = true) { vm.downloadUpdate(u.rel) }
            }
            is UpdateState.Downloading -> {
                ThinBar(u.progress)
                Text("Downloading ${u.rel.version}… ${(u.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
            }
            is UpdateState.Ready -> {
                Text("Ready to install ${u.rel.version}. Android will ask you to confirm.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                ActionPill("Install", Icons.Rounded.SystemUpdate, primary = true) { vm.installUpdate() }
            }
            is UpdateState.Failed -> {
                Text(u.message, style = MaterialTheme.typography.bodySmall, color = LColors.Red)
                ActionPill("Try again", Icons.Rounded.SystemUpdate) { vm.checkUpdate(true) }
            }
        }
    }
}

/** A slim banner at the top of Home when a newer version is available. */
@Composable
fun UpdateBanner(vm: AppViewModel, openSettings: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { vm.checkUpdate(false) }
    val u = vm.update as? UpdateState.Available ?: return
    if (vm.dismissedUpdate == u.rel.version) return
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(LColors.Surface)
            .pressable(onClick = openSettings)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = LColors.Red)
        Column(Modifier.weight(1f)) {
            Text("Update ${u.rel.version} is ready", style = MaterialTheme.typography.titleSmall)
            Text("Tap to read what's new and install", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
        }
        IconButton(onClick = { vm.dismissUpdate(u.rel.version) }) { Icon(Icons.Rounded.Close, "Dismiss") }
    }
}
