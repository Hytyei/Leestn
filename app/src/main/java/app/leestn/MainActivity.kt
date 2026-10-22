package app.leestn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import app.leestn.ui.Motion
import app.leestn.ui.BadgeToast
import app.leestn.ui.BadgesScreen
import app.leestn.ui.MiniPlayer
import app.leestn.ui.NavIcon
import app.leestn.ui.NowPlayingSheet
import app.leestn.ui.OnboardingScreen
import app.leestn.ui.SettingsScreen
import app.leestn.ui.DownloadsScreen
import app.leestn.ui.pressable
import app.leestn.ui.showOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.lifecycle.viewmodel.compose.viewModel
import app.leestn.data.Catalog
import app.leestn.data.Dates
import app.leestn.data.Kind
import app.leestn.state.AppViewModel
import app.leestn.ui.ActionPill
import app.leestn.ui.DiscoverScreen
import app.leestn.ui.EditProfileScreen
import app.leestn.ui.EpisodeScreen
import app.leestn.ui.FriendScreen
import app.leestn.ui.FriendsScreen
import app.leestn.ui.HomeScreen
import app.leestn.ui.ListDetailScreen
import app.leestn.ui.ListsScreen
import app.leestn.ui.NextUpItem
import app.leestn.ui.PlayButton
import app.leestn.ui.Poster
import app.leestn.ui.ProfileScreen
import app.leestn.ui.RecapScreen
import app.leestn.ui.ShowScreen
import app.leestn.ui.theme.LColors
import app.leestn.ui.theme.LeestnTheme
import androidx.compose.material.icons.rounded.Check

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val incoming = mutableStateOf<String?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming.value = routeFrom(intent)
        setContent {
            LeestnTheme {
                LeestnApp(vm, onFirstPlay = ::askNotifications, incoming = incoming.value, onIncomingHandled = { incoming.value = null })
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = routeFrom(intent)
    }

    /** Widgets open the app with leestn://open/<route>, for example ep:ID. */
    private fun routeFrom(i: android.content.Intent?): String? =
        i?.data?.takeIf { it.scheme == "leestn" }?.lastPathSegment
}

private class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Rounded.Home),
    Tab("discover", "Discover", Icons.Rounded.Explore),
    Tab("lists", "Lists", Icons.Rounded.Bookmarks),
    Tab("friends", "Friends", Icons.Rounded.Groups),
    Tab("profile", "Profile", Icons.Rounded.Person),
)
private val TAB_ROUTES = TABS.map { it.route }

/** Routes: a tab name, or "kind:id" for details (show, ep, list, person) and plain "recap". */
@Composable
fun LeestnApp(vm: AppViewModel, onFirstPlay: () -> Unit = {}, incoming: String? = null, onIncomingHandled: () -> Unit = {}) {
    if (!vm.onboarded) {
        OnboardingScreen(vm)
        return
    }
    val ctx = LocalContext.current
    // Back stack kept as a saveable string list so rotation and process death keep your place.
    val stack = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        mutableStateListOf("home")
    }
    val route = stack.last()
    fun push(r: String) { if (stack.last() != r) stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun goTab(r: String) { stack.clear(); stack.add(r) }
    fun openEp(id: String) { push("ep:$id") }
    fun openShow(id: String) { push("show:$id") }
    /** Open something a friend listened to. The show may be new to this device, so it is registered from the feed address first. */
    fun openRemote(epId: String, showId: String, name: String, art: String, feedUrl: String) {
        if (!vm.adoptShow(showId, name, art, feedUrl)) {
            Toast.makeText(ctx, "That show has no feed address, so it can't be opened.", Toast.LENGTH_SHORT).show()
            return
        }
        if (epId.isNotBlank() && Catalog.episode(epId) != null) push("ep:$epId") else push("show:$showId")
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    LaunchedEffect(incoming) {
        val r = incoming ?: return@LaunchedEffect
        when {
            r in TAB_ROUTES -> goTab(r)
            r.startsWith("ep:") -> openEp(r.removePrefix("ep:"))
            r.startsWith("show:") -> openShow(r.removePrefix("show:"))
        }
        onIncomingHandled()
    }

    LaunchedEffect(vm.playing) { if (vm.playing) onFirstPlay() }

    // Direction for the screen transition: deeper = forward, shallower = back.
    var lastSize by remember { mutableIntStateOf(stack.size) }
    val forward = stack.size >= lastSize
    SideEffect { lastSize = stack.size }

    val onTab = route in TAB_ROUTES
    val inRecap = route == "recap"
    val meta = vm.nowMeta
    val showMini = meta != null && !inRecap
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = LColors.Bg,
        bottomBar = {
            AnimatedVisibility(
                visible = !inRecap,
                enter = slideInVertically(tween(Motion.Normal)) { it } + fadeIn(tween(Motion.Normal)),
                exit = slideOutVertically(tween(Motion.Normal)) { it } + fadeOut(tween(Motion.Quick)),
            ) {
                Column(Modifier.background(LColors.Bg)) {
                    AnimatedVisibility(
                        visible = showMini,
                        enter = slideInVertically(tween(Motion.Normal)) { it / 2 } + fadeIn(tween(Motion.Normal)),
                        exit = slideOutVertically(tween(Motion.Quick)) { it / 2 } + fadeOut(tween(Motion.Quick)),
                    ) {
                        if (meta != null) MiniPlayer(vm, meta) { playerOpen = true }
                    }
                    HorizontalDivider(color = LColors.Line)
                    NavigationBar(containerColor = LColors.Bg, tonalElevation = 0.dp) {
                        val selected = when {
                            onTab -> route
                            route.startsWith("show:") || route.startsWith("ep:") -> stack.lastOrNull { it in TAB_ROUTES } ?: "home"
                            route.startsWith("list:") -> "lists"
                            route.startsWith("person:") -> "friends"
                            route == "badges" || route == "editprofile" || route == "settings" -> "profile"
                            route == "downloads" -> "lists"
                            else -> "home"
                        }
                        TABS.forEach { t ->
                            NavigationBarItem(
                                selected = selected == t.route,
                                onClick = { goTab(t.route) },
                                icon = { NavIcon(t.icon, t.label, selected == t.route) },
                                label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LColors.Red, selectedTextColor = LColors.Red,
                                    unselectedIconColor = LColors.Text3, unselectedTextColor = LColors.Text3,
                                    indicatorColor = LColors.RedSoft,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).let { if (inRecap) it else it.statusBarsPadding() }) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val bothTabs = initialState in TAB_ROUTES && targetState in TAB_ROUTES
                    when {
                        bothTabs || targetState == "recap" || initialState == "recap" ->
                            fadeIn(tween(Motion.Normal, delayMillis = 60)) togetherWith fadeOut(tween(Motion.Quick))
                        forward ->
                            (fadeIn(tween(Motion.Normal, delayMillis = 40)) + slideInHorizontally(tween(Motion.Slow, easing = Motion.Ease)) { it / 7 }) togetherWith
                                (fadeOut(tween(Motion.Quick)) + slideOutHorizontally(tween(Motion.Normal, easing = Motion.Ease)) { -it / 12 })
                        else ->
                            (fadeIn(tween(Motion.Normal, delayMillis = 40)) + slideInHorizontally(tween(Motion.Slow, easing = Motion.Ease)) { -it / 12 }) togetherWith
                                (fadeOut(tween(Motion.Quick)) + slideOutHorizontally(tween(Motion.Normal, easing = Motion.Ease)) { it / 7 })
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "screens",
            ) { r ->
                val id = r.substringAfter(':', "")
                when {
                    r == "home" -> HomeScreen(
                        vm, openShow = ::openShow, openEp = ::openEp,
                        openItem = { openRemote(it.epId, it.showId, it.showName, it.showArt, it.feedUrl) }, openPerson = { push("person:$it") },
                        goFriends = { goTab("friends") }, goDiscover = { goTab("discover") },
                        openSettings = { push("settings") },
                    )
                    r == "discover" -> DiscoverScreen(vm) { openShow(it) }
                    r == "lists" -> ListsScreen(vm, openList = { push("list:$it") }, openDownloads = { push("downloads") })
                    r == "friends" -> FriendsScreen(
                        vm, openItem = { openRemote(it.epId, it.showId, it.showName, it.showArt, it.feedUrl) },
                        openPerson = { push("person:$it") }, openSettings = { push("settings") },
                    )
                    r == "profile" -> ProfileScreen(
                        vm, openEp = ::openEp, openRecap = { push("recap") }, openSettings = { push("settings") },
                        openBadges = { push("badges") }, openEditProfile = { push("editprofile") },
                    )
                    r == "editprofile" -> EditProfileScreen(vm, onBack = ::pop)
                    r == "settings" -> SettingsScreen(vm, onBack = ::pop, openDownloads = { push("downloads") })
                    r == "downloads" -> DownloadsScreen(vm, onBack = ::pop, openEp = ::openEp)
                    r == "recap" -> RecapScreen(vm) { pop() }
                    r.startsWith("show:") -> ShowScreen(vm, id, onBack = ::pop) { openEp(it) }
                    r.startsWith("ep:") -> EpisodeScreen(
                        vm, id, onBack = ::pop, openShow = ::openShow, openEp = ::openEp, openPerson = { push("person:$it") },
                        openSettings = { push("settings") },
                    )
                    r.startsWith("list:") -> ListDetailScreen(vm, id, onBack = ::pop, openEp = ::openEp, openShow = ::openShow)
                    r.startsWith("person:") -> FriendScreen(vm, id, onBack = ::pop, openRemote = ::openRemote)
                    r == "badges" -> BadgesScreen(vm, onBack = ::pop)
                }
            }
        }
    }

    vm.newBadge?.let { nb ->
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            BadgeToast(nb, onDismiss = { vm.dismissBadge() }, onOpen = { vm.dismissBadge(); goTab("profile"); push("badges") })
        }
    }

    if (playerOpen && meta != null) {
        NowPlayingSheet(
            vm, meta, onClose = { playerOpen = false },
            openEpisode = { openEp(it) }, openShow = { openShow(it) },
        )
    }

    vm.finishedId?.let { fid -> FinishedSheet(vm, fid, openEp = { vm.dismissFinished(); openEp(it) }) { vm.dismissFinished() } }
}

/** Shown when an episode ends: it is logged automatically, you can rate it, and see what to hear next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishedSheet(vm: AppViewModel, id: String, openEp: (String) -> Unit, onDismiss: () -> Unit) {
    val ep = Catalog.episode(id)
    if (ep == null) { onDismiss(); return }
    val entry = vm.log[id]
    val nu = remember(id) { Catalog.nextUp(ep) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LColors.Surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("FINISHED · LOGGED ${Dates.short(entry?.d ?: Dates.today()).uppercase()}", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
            Text(ep.title, style = MaterialTheme.typography.headlineSmall)
            Text("How was it?", color = LColors.Text3)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (n in 1..10) {
                    val on = entry?.r != null && n <= entry.r
                    val bg by animateColorAsState(if (on) LColors.Red else LColors.Surface3, tween(Motion.Quick), label = "fin")
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(bg).pressable { vm.rate(id, n) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", color = if (on) Color.White else LColors.Text3, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Text("What to listen to next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
            nu.nextMain?.let { nm ->
                NextUpItem(vm, nm, main = true, hint = if (ep.kind == Kind.SIDE) "Back to the main story" else if (nm.cat.id != ep.cat.id) "${nm.cat.name} begins" else "The story continues") { openEp(nm.id) }
            }
            nu.side.forEach { s ->
                NextUpItem(vm, s, main = false, hint = if (ep.kind == Kind.MAIN) "Optional, fits right after ${ep.code}" else "More side content for this point") { openEp(s.id) }
            }
            if (nu.nextMain == null && nu.side.isEmpty()) Text("That is the end of the story so far.", color = LColors.Text3)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ActionPill("Done", Icons.Rounded.Check, onClick = onDismiss) }
        }
    }
}
