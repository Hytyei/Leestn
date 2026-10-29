package app.leestn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.leestn.data.BadgeProgress
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors
import kotlinx.coroutines.delay

private val ROMAN = listOf("", "I", "II", "III", "IV", "V")

fun tierLabel(tier: Int): String = ROMAN.getOrElse(tier) { "$tier" }

/** Bronze, silver, gold, platinum, diamond. */
fun tierColor(tier: Int): Color = when (tier) {
    1 -> Color(0xFFCD7F32)
    2 -> Color(0xFFC3CCD9)
    3 -> Color(0xFFF5C542)
    4 -> Color(0xFF7FD6FF)
    else -> Color(0xFFB388FF)
}

fun badgeIcon(id: String): ImageVector = when (id) {
    "listener" -> Icons.Rounded.Headphones
    "hours" -> Icons.Rounded.Schedule
    "streak" -> Icons.Rounded.LocalFireDepartment
    "night" -> Icons.Rounded.Bedtime
    "morning" -> Icons.Rounded.WbSunny
    "binge" -> Icons.Rounded.Bolt
    "marathon" -> Icons.Rounded.DirectionsRun
    "critic" -> Icons.Rounded.RateReview
    "ten" -> Icons.Rounded.Stars
    "tough" -> Icons.Rounded.ThumbDown
    "easy" -> Icons.Rounded.ThumbUp
    "side" -> Icons.Rounded.Extension
    "explorer" -> Icons.Rounded.Explore
    "genres" -> Icons.Rounded.Category
    "loyal" -> Icons.Rounded.Favorite
    "complete" -> Icons.Rounded.Verified
    "fresh" -> Icons.Rounded.NewReleases
    "archive" -> Icons.Rounded.History
    "regular" -> Icons.Rounded.CalendarMonth
    else -> Icons.Rounded.Stars
}

/** Round medal: a metal ring by tier with the badge icon inside. Locked badges are grey. */
@Composable
fun BadgeMedal(p: BadgeProgress, size: Dp = 56.dp) {
    val c = if (p.earned) tierColor(p.tier) else LColors.Surface3
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = if (p.earned) 0.55f else 1f))))
            .padding(3.dp).clip(CircleShape).background(LColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(badgeIcon(p.def.id), p.def.name, tint = if (p.earned) c else LColors.Text3.copy(alpha = 0.5f), modifier = Modifier.size(size * 0.46f))
    }
}

/** A short row of the best earned badges, used on profiles. */
@Composable
fun BadgeStrip(badges: List<BadgeProgress>, modifier: Modifier = Modifier) {
    val top = badges.filter { it.earned }.sortedByDescending { it.tier }.take(5)
    if (top.isEmpty()) {
        Text("No badges yet. They unlock as you listen, rate and finish shows.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3, modifier = modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        top.forEachIndexed { i, p ->
            Column(Modifier.width(60.dp).appear(i, 60), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BadgeMedal(p, 52.dp)
                Text(p.def.name, style = MaterialTheme.typography.labelSmall, color = LColors.Text2, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BadgeCard(p: BadgeProgress, modifier: Modifier = Modifier) {
    Column(
        modifier.card(24.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BadgeMedal(p, 46.dp)
            Column(Modifier.weight(1f)) {
                Text(p.def.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (p.earned) "Tier ${tierLabel(p.tier)}${if (p.maxed) " · max" else ""}" else "Locked",
                    style = MaterialTheme.typography.labelSmall, color = if (p.earned) tierColor(p.tier) else LColors.Text3,
                )
            }
        }
        Text(p.def.desc, style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
        val next = p.nextGoal
        if (next != null) {
            ThinBar(p.fraction, color = if (p.earned) tierColor(p.tier + 1) else LColors.Accent)
            Text("${p.value} / $next ${p.def.unit}".trim(), style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
        } else {
            Text("${p.value} ${p.def.unit}".trim(), style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
        }
    }
}

@Composable
fun BadgesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val badges = remember(vm.log.size, vm.log.values.count { it.d != null }) { vm.computeBadges(vm.log.toMap()) }
    val earned = badges.count { it.earned }
    val sorted = badges.sortedWith(compareByDescending<BadgeProgress> { it.earned }.thenByDescending { it.tier }.thenByDescending { it.fraction })
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Your badges", style = MaterialTheme.typography.displaySmall)
                Text("$earned of ${badges.size} earned", style = MaterialTheme.typography.bodyMedium, color = LColors.Text3)
                ThinBar(if (badges.isEmpty()) 0f else earned / badges.size.toFloat(), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
        }
        items(sorted, key = { it.def.id }) { p -> BadgeCard(p, Modifier.animateItem().appear()) }
    }
}

/** Slides down from the top when a new badge tier is unlocked, then goes away by itself. */
@Composable
fun BadgeToast(p: BadgeProgress, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val s = remember(p) { Animatable(0f) }
    LaunchedEffect(p) {
        s.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        delay(4200)
        s.animateTo(0f)
        onDismiss()
    }
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .scale(0.9f + 0.1f * s.value)
            .clip(RoundedCornerShape(24.dp)).background(LColors.Surface2)
            .border(1.dp, tierColor(p.tier).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .pressable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BadgeMedal(p, 48.dp)
        Column(Modifier.weight(1f)) {
            Text("BADGE UNLOCKED", style = MaterialTheme.typography.labelSmall, color = tierColor(p.tier))
            Text("${p.def.name} ${tierLabel(p.tier)}", style = MaterialTheme.typography.titleMedium)
            Text(p.def.desc, style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
