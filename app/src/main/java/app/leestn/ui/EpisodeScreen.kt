package app.leestn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.DownloadDone
import app.leestn.data.Dl
import app.leestn.data.Downloads
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import app.leestn.state.TxState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.leestn.data.Catalog
import app.leestn.data.Dates
import app.leestn.data.Episode
import app.leestn.data.Kind
import app.leestn.data.ListItem
import app.leestn.state.AppViewModel
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import app.leestn.data.TxEditing
import app.leestn.data.TxEdit
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import app.leestn.ui.theme.LColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val TX_START = 8 // number of LazyColumn items before the first transcript line

private fun highlight(text: String, q: String): AnnotatedString {
    if (q.isEmpty()) return AnnotatedString(text)
    val lo = text.lowercase()
    return buildAnnotatedString {
        var i = 0
        while (true) {
            val j = lo.indexOf(q, i)
            if (j < 0) break
            append(text.substring(i, j))
            withStyle(SpanStyle(background = LColors.RedSoft, color = LColors.Red)) { append(text.substring(j, j + q.length)) }
            i = j + q.length
        }
        append(text.substring(i))
    }
}

/** Own composable so only the slider recomposes on every player tick. */
@Composable
private fun SeekRow(vm: AppViewModel, ep: Episode) {
    val current = vm.playingId == ep.id
    val total = if (current && vm.durationSec > 0) vm.durationSec else ep.seconds
    val t = if (current) vm.t else (if (vm.log[ep.id]?.d != null) 0f else (vm.prog[ep.id] ?: 0).toFloat())
    var drag by remember { mutableStateOf<Float?>(null) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Dates.clock((drag ?: t).toInt()), style = MaterialTheme.typography.labelSmall, color = LColors.Text3, modifier = Modifier.width(44.dp))
        Slider(
            value = (drag ?: t).coerceIn(0f, maxOf(total, 1).toFloat()),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { if (current) vm.seekTo(it) else vm.play(ep.id, it.toInt()) }
                drag = null
            },
            valueRange = 0f..maxOf(total, 1).toFloat(),
            enabled = total > 0,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LColors.Red, inactiveTrackColor = LColors.Surface3),
        )
        Text(if (total > 0) Dates.clock(total) else "–", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
    }
}

@Composable
private fun RateBox(n: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) LColors.Red else LColors.Surface3, tween(Motion.Quick), label = "rate")
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(bg).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("$n", color = if (on) Color.White else LColors.Text3, style = MaterialTheme.typography.labelMedium) }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EpisodeScreen(vm: AppViewModel, epId: String, onBack: () -> Unit, openShow: (String) -> Unit, openEp: (String) -> Unit, openPerson: (String) -> Unit, openSettings: () -> Unit = {}) {
    val ep = Catalog.episode(epId)
    if (ep == null) {
        Column(Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("This episode isn't loaded yet. Open its show to refresh the feed.", color = LColors.Text3)
        }
        return
    }
    val p = showOf(ep.pid)
    val epTint = rememberArtTint(p.artworkUrl)
    val entry = vm.log[epId]
    val isCurrent = vm.playingId == epId
    val tx = vm.transcripts[epId]
    LaunchedEffect(epId) { vm.loadTranscript(epId); vm.loadTxLocal(epId) }
    // Which transcript is shown: the one the person pasted, or the original (publisher's or generated).
    val pastedText = vm.pasted[epId]
    val useCustom = pastedText != null && vm.txSource[epId] != "orig"
    val txSourceKey = if (useCustom) "paste" else "orig"
    val baseLines = if (useCustom) remember(pastedText) { TxEditing.parsePasted(pastedText ?: "") } else ((tx as? TxState.Ready)?.lines ?: emptyList())
    val edit = vm.txEdits["$epId|$txSourceKey"]
    val fixedLines = remember(baseLines, edit) { TxEditing.apply(baseLines, edit) }
    val lines = if (vm.showTimings) fixedLines else remember(fixedLines) { fixedLines.map { it.copy(t = -1) } }
    val synced = vm.showTimings && lines.any { it.t >= 0 }
    // Plain-text mode (no timings shown, or text that has none): tap a line to highlight it by hand.
    val plainMode = lines.isNotEmpty() && !synced
    val marks = vm.txHighlights["$epId|$txSourceKey"] ?: emptySet()
    var syncMode by rememberSaveable(epId) { mutableStateOf(false) }
    var selLine by rememberSaveable(epId) { mutableStateOf(-1) } // line chosen for moving, -1 = whole transcript
    var tailScope by rememberSaveable(epId) { mutableStateOf(false) } // move the chosen line and everything after it
    var amount by rememberSaveable(epId) { mutableStateOf("1") } // seconds to move by
    var pasteDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    val activeIdx by remember(epId, lines) { derivedStateOf { if (synced && vm.playingId == epId) lines.indexOfLast { it.t >= 0 && it.t <= vm.t } else -1 } }
    var query by rememberSaveable(epId) { mutableStateOf("") }
    var follow by rememberSaveable { mutableStateOf(true) }
    var listSheet by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var more by rememberSaveable(epId) { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val listState = rememberLazyListState()

    LaunchedEffect(activeIdx, follow, vm.playing) {
        if (follow && q.isEmpty() && activeIdx >= 0 && vm.playing && isCurrent) listState.animateScrollToItem(TX_START + activeIdx, scrollOffset = -(listState.layoutInfo.viewportSize.height * 0.28f).toInt())
    }

    Box(Modifier.fillMaxSize()) {
    // colour from the cover washes over the top and fades away as you scroll
    Box(
        Modifier.fillMaxWidth().height(360.dp)
            .graphicsLayer { alpha = if (listState.firstVisibleItemIndex > 1) 0f else (1f - listState.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(listOf(epTint.copy(alpha = 0.38f), Color.Transparent))),
    )
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Column(Modifier.padding(bottom = 14.dp)) { // 0
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        } }
        item { Column(Modifier.padding(bottom = 14.dp).appear()) { // 1
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val artPop by animateFloatAsState(if (vm.playing && isCurrent) 1f else 0.94f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "epArt")
                Poster(p, Modifier.width(160.dp).graphicsLayer { scaleX = artPop; scaleY = artPop }.glow(epTint, 26.dp, if (vm.playing && isCurrent) 0.5f else 0.25f).pressable { openShow(p.id) }, corner = 24.dp)
                Gap(6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CodeChip(ep)
                    KindTag(ep.kind)
                }
                Text(ep.title, style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(
                    "${p.name} · ${ep.cat.name} · ${Dates.full(ep.date)} · ${Dates.dur(ep.min)}",
                    style = MaterialTheme.typography.labelSmall, color = LColors.Text3, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (ep.afterId != null) {
                    Catalog.episode(ep.afterId)?.let { Text("Best after ${it.code}", style = MaterialTheme.typography.labelSmall, color = LColors.Side) }
                }
            }
        } }
        item { Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp).animateContentSize(tween(Motion.Normal))) { // 2 show notes
            val text = ep.description.ifBlank { ep.blurb }
            if (text.isNotBlank()) Column(Modifier.fillMaxWidth().card(24.dp).padding(16.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = LColors.Text2, maxLines = if (more) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                if (text.length > 220) {
                    Text(if (more) "Show less" else "Show more", color = LColors.Red, style = MaterialTheme.typography.labelLarge, modifier = Modifier.pressable { more = !more }.padding(vertical = 6.dp))
                }
            }
        } }
        item { Column(Modifier.padding(bottom = 14.dp)) { // 3
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeekRow(vm, ep)
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val playingNow = vm.playing && isCurrent
                    val loading = vm.buffering && isCurrent
                    ActionPill(
                        if (loading) "Loading" else if (playingNow) "Pause" else if (isCurrent && vm.t > 0f) "Resume" else "Play",
                        if (playingNow) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, primary = true,
                    ) { if (isCurrent) vm.togglePlay() else vm.play(epId) }
                    ActionPill("Speed ${speedText(vm.speed)}", Icons.Rounded.Speed) { speedDialog = true }
                    val wish = vm.inWishlist(ListItem.Ep(epId))
                    ActionPill(if (wish) "In wishlist" else "Wishlist", if (wish) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, active = wish) {
                        vm.toggleInList("wishlist", ListItem.Ep(epId))
                    }
                    ActionPill("List", Icons.Rounded.Bookmarks) { listSheet = true }
                    val dl = Downloads.state[epId]
                    ActionPill(
                        when (dl) { is Dl.Done -> "Downloaded"; is Dl.Progress -> "${(dl.frac * 100).toInt()}%"; Dl.Queued -> "Queued"; null -> "Download" },
                        if (dl is Dl.Done) Icons.Rounded.DownloadDone else Icons.Rounded.Download, active = dl != null,
                    ) { if (dl == null) vm.download(ep) else vm.removeDownload(epId) }
                }
                val err = vm.playerError
                if (isCurrent && err != null) {
                    Text("Playback problem: $err", color = LColors.Err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        } }
        item { Column(Modifier.padding(bottom = 14.dp)) { // 4 rating
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(24.dp).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your rating", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(entry?.r?.toString() ?: "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("/10", style = MaterialTheme.typography.labelMedium, color = LColors.Text3, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (n in 1..10) {
                        RateBox(n, entry?.r != null && n <= entry.r, Modifier.weight(1f)) { vm.rate(epId, n) }
                    }
                }
                SideLabel(vm, ep)
                if (entry?.r != null && entry.d == null) {
                    Text("Rated but not logged. Add a listened date to count it in your stats.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                }
            }
        } }
        item { Column(Modifier.padding(bottom = 14.dp)) { // 5 listened
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(24.dp).padding(14.dp).animateContentSize(tween(Motion.Normal)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Listened", style = MaterialTheme.typography.titleMedium)
                if (entry?.d != null) {
                    Text("Logged on ${Dates.full(entry.d)}", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Not logged yet. Finishing the episode logs it for you.", style = MaterialTheme.typography.bodyMedium, color = LColors.Text3)
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry?.d == null) {
                        ActionPill("Log today", Icons.Rounded.PlayArrow, primary = true) { vm.logListen(epId, Dates.today()) }
                        ActionPill("Pick date", Icons.Rounded.Bookmarks) { picker = true }
                    } else {
                        ActionPill("Change date", Icons.Rounded.Bookmarks) { picker = true }
                        ActionPill("Remove", Icons.Rounded.BookmarkBorder) { vm.unlog(epId) }
                    }
                }
            }
        } }
        item { Column(Modifier.padding(bottom = 14.dp)) { // 6 what comes next
            val nu = remember(epId, p) { Catalog.nextUp(ep) }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What comes next", style = MaterialTheme.typography.titleLarge)
                nu.nextMain?.let { nm ->
                    NextUpItem(vm, nm, main = true, hint = if (ep.kind == Kind.SIDE) "Back to the main story" else if (nm.cat.id != ep.cat.id) "${nm.cat.name} begins" else "The story continues") { openEp(nm.id) }
                }
                nu.side.forEach { s ->
                    NextUpItem(vm, s, main = false, hint = if (ep.kind == Kind.MAIN) "Optional, fits right after ${ep.code}" else "More side content for this point") { openEp(s.id) }
                }
                if (nu.nextMain == null && nu.side.isEmpty()) Text("Nothing queued after this one.", color = LColors.Text3)
            }
        } }
        item { Column(Modifier.padding(bottom = 14.dp)) { // 7 transcript header and status
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Transcript", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (!vm.showTimings && lines.isNotEmpty()) "Timings are hidden. Tap a line to highlight it, or turn Timings on to follow playback."
                            else if (lines.isNotEmpty() && !synced) "This transcript has no timestamps, so it can't follow playback. Tap a line to highlight it, or use Sync to add timings."
                            else if (useCustom) "Your pasted transcript. Tap a line to jump there."
                            else if (tx is TxState.Ready && tx.generated) "Generated by speech recognition, so expect small mistakes. Tap a line to jump there."
                            else "Follows playback. Tap a line to jump there.",
                            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
                        )
                    }
                }
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (synced && !syncMode) Chip("Follow", follow) { follow = !follow }
                    if (!useCustom && tx is TxState.Ready && tx.generated && vm.connected && vm.canTranscribe) Chip("Redo", false) { vm.regenerateTranscript(epId) }
                    if (lines.isNotEmpty()) Chip("Timings", vm.showTimings) { vm.changeShowTimings(!vm.showTimings); if (!vm.showTimings) { syncMode = false; selLine = -1 } }
                    if (lines.isNotEmpty() && vm.showTimings) Chip("Sync", syncMode) { syncMode = !syncMode; if (!syncMode) selLine = -1 }
                    Chip(if (pastedText != null) "Edit pasted text" else "Paste text", false) { pasteDialog = true }
                    if (plainMode && marks.isNotEmpty()) Chip("Clear highlights", false) { vm.clearHighlights(epId, txSourceKey) }
                    if (pastedText != null && tx is TxState.Ready) {
                        Chip("Original", !useCustom) { vm.chooseTxSource(epId, "orig") }
                        Chip("Pasted", useCustom) { vm.chooseTxSource(epId, "paste") }
                    }
                }
                if (syncMode && vm.showTimings && lines.isNotEmpty()) SyncPanel(
                    vm = vm, epId = epId, isCurrent = isCurrent, source = txSourceKey, edit = edit ?: TxEdit(),
                    untimed = baseLines.none { it.t >= 0 }, anchorCount = (edit ?: TxEdit()).anchors.size,
                    selLine = selLine, tailScope = tailScope, onTailScope = { tailScope = it }, onClearSel = { selLine = -1 },
                    amount = amount, onAmount = { amount = it },
                )
                val searchField: @Composable () -> Unit = {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search transcript") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        shape = RoundedCornerShape(99.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Red, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Red),
                    )
                }
                if (useCustom) searchField() else when (tx) {
                    null, TxState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { SkeletonBox(Modifier.fillMaxWidth(if (it == 2) 0.6f else 1f).height(14.dp), 4.dp) }
                    }
                    TxState.None -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "The publisher hasn't shared a transcript for this episode. Join a Leestn server and you can generate one with speech recognition. Only pressing Generate on an episode uses the server's quota.",
                            style = MaterialTheme.typography.bodyMedium, color = LColors.Text3,
                        )
                        ActionPill("Set up or join a server", Icons.Rounded.Subtitles, onClick = openSettings)
                    }
                    is TxState.Offer -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            tx.note ?: "No transcript from the publisher. Leestn can generate one for everyone with speech recognition. It takes a few minutes and keeps going if you leave this page.",
                            style = MaterialTheme.typography.bodyMedium, color = LColors.Text3,
                        )
                        val needMin = (ep.durationSec) / 60.0
                        if (!vm.canTranscribe) {
                            Text("Transcription is invite only. Enter the invite key in Settings to unlock it.", style = MaterialTheme.typography.bodyMedium, color = LColors.Text2)
                            ActionPill("Open Settings", Icons.Rounded.Subtitles, onClick = openSettings)
                        } else UsageMeter(vm, needMin = needMin.takeIf { it > 0 })
                        val left = vm.usage?.remainingMin
                        val fits = vm.canTranscribe && (left == null || needMin <= left)
                        if (fits) ActionPill("Generate transcript", Icons.Rounded.Subtitles, primary = true) { vm.generateTranscript(epId) }
                    }
                    is TxState.Generating -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThinBar(tx.progress)
                        Text(
                            if (tx.progress > 0f) "Generating transcript… ${(tx.progress * 100).toInt()}%" else "Queued. Generating transcript…",
                            style = MaterialTheme.typography.bodyMedium, color = LColors.Text3,
                        )
                    }
                    is TxState.Failed -> ErrorState(if (tx.fromServer) "Transcription failed. ${tx.message}" else "Couldn't load the transcript. ${tx.message}", onRetry = { vm.retryTranscript(epId) }, modifier = Modifier.padding(0.dp))
                    is TxState.Ready -> searchField()
                }
            }
        } }
        // transcript lines: always TX_START leading items, filtered view hides non-matching lines
        lines.forEachIndexed { i, line ->
            val hit = q.isEmpty() || (line.who + " " + line.text).lowercase().contains(q)
            item(key = "tx$i") {
                if (hit) {
                    val active = i == activeIdx
                    val past = activeIdx >= 0 && i < activeIdx
                    val chosen = syncMode && vm.showTimings && i == selLine
                    val marked = plainMode && !(syncMode && vm.showTimings) && i in marks
                    val bg by animateColorAsState(if (active || chosen || marked) LColors.Surface2 else Color.Transparent, tween(Motion.Normal), label = "txBg")
                    val bar by animateColorAsState(if (chosen) LColors.Side else if (active || marked) LColors.Red else Color.Transparent, tween(Motion.Normal), label = "txBar")
                    val a by animateFloatAsState(if (active || marked) 1f else if (past) 0.45f else 0.85f, tween(Motion.Normal), label = "txAlpha")
                    val glowA by animateFloatAsState(if (active) 1f else 0f, tween(Motion.Slow), label = "txGlow")
                    val pop by animateFloatAsState(if (active) 1.025f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "txPop")
                    Row(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(IntrinsicSize.Min)
                            .graphicsLayer { scaleX = pop; scaleY = pop; transformOrigin = TransformOrigin(0f, 0.5f) }
                            .alpha(a)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .drawBehind { drawRect(Brush.horizontalGradient(listOf(epTint.copy(alpha = 0.28f * glowA), Color.Transparent))) }
                            .clickable(enabled = line.t >= 0 || (syncMode && vm.showTimings) || plainMode) {
                                if (syncMode && vm.showTimings) selLine = if (selLine == i) -1 else i
                                else if (plainMode) vm.toggleHighlight(epId, txSourceKey, i)
                                else if (isCurrent) { vm.seekTo(line.t.toFloat()); if (!vm.playing) vm.togglePlay() } else vm.play(epId, line.t)
                            },
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
                        if (line.t >= 0) {
                            Text(Dates.clock(line.t), style = MaterialTheme.typography.labelSmall, color = LColors.Text3, modifier = Modifier.width(54.dp).padding(start = 10.dp, top = 12.dp))
                        } else {
                            Box(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = 10.dp).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (line.who.isNotBlank()) Text(line.who, style = MaterialTheme.typography.labelLarge, color = LColors.Text3, fontSize = 12.sp)
                            Text(highlight(line.text, q), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp))
                        }
                        if (syncMode && vm.showTimings) {
                            if (line.t >= 0) Box(
                                Modifier.padding(end = 6.dp, top = 8.dp).align(Alignment.Top).clip(RoundedCornerShape(99.dp)).background(LColors.Surface3)
                                    .pressable { if (isCurrent) { vm.seekTo(line.t.toFloat()); if (!vm.playing) vm.togglePlay() } else vm.play(epId, line.t) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) { Text("Play", style = MaterialTheme.typography.labelMedium, color = LColors.Text2) }
                            val pinned = edit?.anchors?.containsKey(i) == true
                            Box(
                                Modifier.padding(end = 8.dp, top = 8.dp).align(Alignment.Top).clip(RoundedCornerShape(99.dp))
                                    .background(if (pinned) LColors.Red else LColors.Surface3)
                                    .pressable(enabled = isCurrent) {
                                        val cur = edit ?: TxEdit()
                                        vm.saveTxEdit(epId, txSourceKey, cur.copy(anchors = cur.anchors + (i to vm.t.toInt())))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) { Text(if (pinned) "Set ✓" else "Set", style = MaterialTheme.typography.labelMedium, color = if (pinned) Color.White else LColors.Text2) }
                        }
                    }
                }
            }
        }
    }

    }

    if (speedDialog) SpeedDialog(vm) { speedDialog = false }
    if (pasteDialog) PasteDialog(
        initial = pastedText ?: "",
        onSave = { vm.savePasted(epId, it); pasteDialog = false },
        onRemove = if (pastedText != null) ({ vm.removePasted(epId); pasteDialog = false }) else null,
        onClose = { pasteDialog = false },
    )
    if (listSheet) AddToListSheet(vm, ListItem.Ep(epId)) { listSheet = false }
    if (picker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(entry?.d ?: Dates.today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        vm.logListen(epId, Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    picker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}


/** Controls shown while fixing timings by hand. Every change is saved on this device straight away. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SyncPanel(
    vm: AppViewModel, epId: String, isCurrent: Boolean, source: String, edit: TxEdit, untimed: Boolean, anchorCount: Int,
    selLine: Int, tailScope: Boolean, onTailScope: (Boolean) -> Unit, onClearSel: () -> Unit, amount: String, onAmount: (String) -> Unit,
) {
    val seconds = amount.replace(',', '.').toDoubleOrNull()
    val ms = if (seconds != null && seconds > 0) Math.min(3_600_000.0, Math.round(seconds * 1000.0).toDouble()).toInt() else 0
    fun move(sign: Int) {
        if (ms == 0) return
        val (from, to) = when {
            selLine < 0 -> 0 to -1
            tailScope -> selLine to -1
            else -> selLine to selLine
        }
        vm.saveTxEdit(epId, source, edit.shifted(from, to, sign * ms))
    }
    Column(
        Modifier.fillMaxWidth().card(24.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Fix the timing", style = MaterialTheme.typography.titleSmall)
        Text(
            if (untimed) "This text has no timings. Play the episode and tap Set on the line you are hearing. Set at least two lines, far apart (for example near the start and near the end); the lines between are spread evenly."
            else "Move by an amount you choose, or play the episode and tap Set on the line you are hearing now. Two Sets far apart also fix drifting.",
            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
        )
        if (!isCurrent) Text("Start playing this episode first, so Set knows the position.", style = MaterialTheme.typography.bodySmall, color = LColors.Err)

        // move by any amount, earlier or later
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = amount, onValueChange = { onAmount(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)) },
                singleLine = true, label = { Text("Seconds") }, isError = ms == 0, modifier = Modifier.width(112.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Red, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Red),
            )
            Chip("◀ Earlier", false) { move(-1) }
            Chip("Later ▶", false) { move(1) }
        }

        // what the move applies to
        if (selLine >= 0) {
            Text("Moving line ${selLine + 1} only. Tap the line again to go back to the whole transcript.", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("This line only", !tailScope) { onTailScope(false) }
                Chip("This line and after", tailScope) { onTailScope(true) }
                Chip("Whole transcript", false) { onClearSel() }
            }
        } else {
            Text("Moving the whole transcript. Tap a line to move only that line.", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
        }

        val whole = edit.shifts.filter { it.from == 0 && it.to == -1 }.sumOf { it.ms } / 1000.0
        Text(
            "Whole transcript moved ${if (whole > 0) "+" else ""}${"%.1f".format(whole)} s · ${edit.shifts.count { !(it.from == 0 && it.to == -1) }} partial moves · $anchorCount line${if (anchorCount == 1) "" else "s"} set",
            style = MaterialTheme.typography.labelSmall, color = LColors.Text3,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Reset all", false) { vm.saveTxEdit(epId, source, TxEdit()); onClearSel() }
        }
        Text(
            if (vm.timingIsShared(epId, source)) "Saved for everyone on your server, as well as on this phone." else "Saved on this phone only.",
            style = MaterialTheme.typography.labelSmall, color = LColors.Good,
        )
        vm.timingSaveError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = LColors.Err) }
    }
}

@Composable
private fun PasteDialog(initial: String, onSave: (String) -> Unit, onRemove: (() -> Unit)?, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = LColors.Surface,
        title = { Text("Paste a transcript") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Paste the text. Plain text works (blank lines or new lines split it into paragraphs). Lines that start with a time, like 0:12 or [01:02:03], or WebVTT/SRT files, keep their timings. Without timings, use Sync to add them.",
                    style = MaterialTheme.typography.bodySmall, color = LColors.Text2,
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text("Paste here…") }, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Red, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Red),
                )
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove pasted text", color = LColors.Err) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
