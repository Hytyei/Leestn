package app.leestn.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

/** Change your photo, display name and bio. Everything is saved on the device, and sent to your server when you are connected. */
@Composable
fun EditProfileScreen(vm: AppViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf(vm.profileName) }
    var bio by remember { mutableStateOf(vm.profileBio) }
    var saved by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setAvatar(uri) }
    val dirty = name.trim() != vm.profileName || bio.trim() != vm.profileBio

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            ScreenTitle("Edit profile", subtitle = "Photo, name and bio")
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { MyAvatar(vm, 112.dp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(if (vm.avatarFile != null) "Change photo" else "Add photo", Icons.Rounded.Edit, primary = true) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (vm.avatarFile != null) ActionPill("Remove", Icons.Rounded.Delete) { vm.setAvatar(null) }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(28.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.connected) Text("@${vm.serverHandle}", style = MaterialTheme.typography.labelMedium, color = LColors.Text3)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40); saved = false }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name") }, shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Accent, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Accent, focusedLabelColor = LColors.Accent),
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it.take(160); saved = false }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5,
                label = { Text("Bio") }, supportingText = { Text("${bio.length}/160") }, shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Accent, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Accent, focusedLabelColor = LColors.Accent),
            )
            vm.profileError?.let { Text(it, color = LColors.Err, style = MaterialTheme.typography.bodySmall) }
            if (!vm.connected) Text("You are not on a server, so only you see this. Join one in Settings to show your profile to friends.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
            ActionPill(if (saved) "Saved" else "Save", Icons.Rounded.Check, primary = dirty, active = saved) {
                vm.saveProfile(name, bio)
                saved = true
            }
        }
    }
}
