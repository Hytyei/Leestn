package app.leestn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.leestn.data.Episode
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

/** Lets the listener say whether an episode is a side episode, and whether side episodes count in the show rating. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SideLabel(vm: AppViewModel, ep: Episode, modifier: Modifier = Modifier) {
    val side = vm.isSide(ep)
    val counted = ep.pid in vm.countSides
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when {
                side && counted -> "Side episode. Counted in the show rating because you asked for that."
                side && vm.sideSuggested(ep) -> "This looks like a trailer or teaser, so it is left out of the show rating."
                side -> "Side episode. Left out of the show rating and the rating graph."
                else -> "Counts towards the show rating."
            },
            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Side episode", side) { vm.setSideMark(ep.id, !side) }
            if (side) Chip("Count side episodes in this show", counted) { vm.toggleCountSides(ep.pid) }
            if (vm.sideMarks.containsKey(ep.id)) Chip("Reset", false) { vm.setSideMark(ep.id, null) }
        }
    }
}
