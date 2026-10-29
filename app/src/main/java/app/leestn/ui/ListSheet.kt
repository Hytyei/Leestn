package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.leestn.data.ListItem
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

/** Bottom sheet to add an episode or a whole show to any list, or to a brand new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(vm: AppViewModel, item: ListItem, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LColors.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Add to list", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 6.dp))
            vm.lists.forEach { l ->
                val on = vm.inList(l.id, item)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) LColors.Accent.copy(alpha = 0.12f) else Color.Transparent).clickable { vm.toggleInList(l.id, item) }.padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(24.dp).clip6().background(if (on) LColors.Accent else Color.Transparent).border(1.5.dp, if (on) LColors.Accent else LColors.Surface3, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Text(l.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${l.items.size}", color = LColors.Text3, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 40) newName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("New list name") },
                    shape = RoundedCornerShape(99.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Accent, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Accent),
                )
                Text(
                    "Create",
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (newName.isBlank()) LColors.Surface3 else LColors.Accent)
                        .clickable(enabled = newName.isNotBlank()) { vm.createList(newName, item); newName = "" }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun Modifier.clip6(): Modifier = this.then(Modifier.clip(RoundedCornerShape(6.dp)))
