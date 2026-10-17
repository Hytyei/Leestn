package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.leestn.data.Genres
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

/** Genre chips that can be toggled. Used at onboarding and in Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenrePicker(selected: List<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Genres.all.forEachIndexed { i, g ->
            Box(Modifier.appear(i, step = 25, maxDelay = 500)) { Chip(g, g in selected) { onToggle(g) } }
        }
    }
}

/** First-run screen: pick favourite genres so Discover can suggest real shows straight away. */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val picked = remember { mutableStateListOf<String>() }
    Box(Modifier.fillMaxSize().background(LColors.Bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 40.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("leestn", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(LColors.Red))
            }
            Text("What do you listen to?", style = MaterialTheme.typography.displaySmall, modifier = Modifier.appear(1))
            Text(
                "Pick a few favourites. Discover will suggest real shows from them, and you can change this any time in Settings.",
                style = MaterialTheme.typography.bodyLarge, color = LColors.Text2, modifier = Modifier.appear(2),
            )
            Gap(6.dp)
            GenrePicker(picked, onToggle = { g -> if (g in picked) picked.remove(g) else if (picked.size < 8) picked.add(g) })
            Text("${picked.size} of 8 picked", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(LColors.Bg).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionPill(
                if (picked.isEmpty()) "Pick at least one" else "Continue",
                Icons.Rounded.Check, primary = picked.isNotEmpty(),
            ) { if (picked.isNotEmpty()) vm.completeOnboarding(picked.toList()) }
            Text("Skip for now", color = LColors.Text3, style = MaterialTheme.typography.labelLarge, modifier = Modifier.pressable { vm.completeOnboarding(emptyList()) }.padding(8.dp))
        }
    }
}
