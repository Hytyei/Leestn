package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val FounderGold = Color(0xFFFFC043)

/** Gold verified check shown after the name of the founder. Draws nothing for anyone else. */
@Composable
fun FounderBadge(role: String, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    if (role != "founder") return
    Icon(Icons.Rounded.Verified, "Verified founder", tint = FounderGold, modifier = modifier.size(size))
}

/** "Verified founder" pill for profile headers. */
@Composable
fun FounderTag(role: String, modifier: Modifier = Modifier) {
    if (role != "founder") return
    Row(
        modifier.clip(RoundedCornerShape(99.dp)).background(FounderGold.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Rounded.Verified, null, tint = FounderGold, modifier = Modifier.size(14.dp))
        Text("VERIFIED FOUNDER", style = MaterialTheme.typography.labelSmall, color = FounderGold)
    }
}
