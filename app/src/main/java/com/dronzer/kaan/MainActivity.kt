package com.dronzer.kaan

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var billing: Billing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        // The two day free trial starts the first time the app opens.
        store.startClock()
        Live.theme = store.theme
        TrackerService.createChannels(this)
        billing = Billing(this) { Live.beat++ }
        setContent {
            KaanTheme {
                Root(billing = billing, activity = this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Live.visible = true
    }

    override fun onResume() {
        super.onResume()
        billing.start()
        val store = Store(this)
        if (store.onboarded && store.tracking) TrackerService.start(this)
        // Debug builds look for a newer build on GitHub. Release builds do nothing here.
        Updater.autoCheck(this)
        // Score the days that ended while the app was closed, then celebrate what is new.
        Game.settle(store)
        Game.collect(store)
        if (store.onboarded) Game.cheer(store)
        Live.beat++
    }

    override fun onStop() {
        Live.visible = false
        Live.notice = null
        super.onStop()
    }

    override fun onDestroy() {
        billing.end()
        super.onDestroy()
    }
}

enum class Tab(val label: String, val icon: Int) {
    Today("Today", R.drawable.ic_today),
    History("History", R.drawable.ic_history),
    Rewards("Rewards", R.drawable.ic_trophy),
    Earphones("Earphones", R.drawable.ic_devices),
    Settings("Settings", R.drawable.ic_tune)
}

@Composable
private fun Root(billing: Billing, activity: Activity) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    var onboarded by remember { mutableStateOf(store.onboarded) }
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var showPro by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = onboarded && (showPro || tab != Tab.Today)) {
        if (showPro) showPro = false else tab = Tab.Today
    }

    val mode = when {
        !onboarded -> 0
        showPro -> 2
        else -> 1
    }

    // Status and navigation bar icons follow the chosen appearance.
    LaunchedEffect(p.dark) {
        val style = if (p.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        (activity as? ComponentActivity)?.enableEdgeToEdge(style, style)
    }

    Box(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding()) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(140))
            },
            label = "root"
        ) { m ->
            when (m) {
                0 -> Onboarding(onDone = { onboarded = true })
                2 -> ProScreen(billing = billing, activity = activity, onBack = { showPro = false })
                else -> Main(tab = tab, onTab = { tab = it }, openPro = { showPro = true })
            }
        }
        NoticeSheet(openRewards = {
            showPro = false
            tab = Tab.Rewards
        })
    }
}

@Composable
private fun Main(tab: Tab, onTab: (Tab) -> Unit, openPro: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                // Moving right in the bar slides the new screen in from the right.
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(spatial()) { it / 7 * dir } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(spatial()) { -it / 7 * dir } + fadeOut(tween(120)))
            },
            label = "tab"
        ) { t ->
            Box(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
                when (t) {
                    Tab.Today -> TodayScreen(openPro = openPro, openRewards = { onTab(Tab.Rewards) })
                    Tab.History -> HistoryScreen(openPro = openPro)
                    Tab.Rewards -> RewardsScreen()
                    Tab.Earphones -> EarphonesScreen(openPro = openPro)
                    Tab.Settings -> SettingsScreen(openPro = openPro)
                }
            }
        }
        NavBar(tab, onTab)
    }
}

@Composable
private fun NavBar(current: Tab, onTab: (Tab) -> Unit) {
    val p = LocalPalette.current
    val view = LocalView.current
    Column(Modifier.fillMaxWidth()) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Tab.values().forEach { tab ->
                val on = tab == current
                val tint by animateColorAsState(if (on) p.accent else p.muted, tween(200), label = "navTint")
                val mark by animateDpAsState(if (on) 22.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "navMark")
                // The chosen icon hops up a little and settles.
                val lift = animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.42f, stiffness = 420f), label = "navLift")
                Pressable(
                    onClick = {
                        if (!on) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onTab(tab)
                    },
                    modifier = Modifier.weight(1f),
                    role = Role.Tab,
                    label = tab.label,
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.padding(top = 0.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.height(3.dp).width(mark).clip(RoundedCornerShape(2.dp)).background(p.accent))
                        Spacer(Modifier.height(9.dp))
                        Glyph(
                            tab.icon, tint,
                            modifier = Modifier.graphicsLayer {
                                val v = lift.value
                                translationY = -2.dp.toPx() * v
                                scaleX = 1f + 0.1f * v
                                scaleY = 1f + 0.1f * v
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        // Only the open tab shows its name. The space stays, so nothing jumps.
                        Text(
                            tab.label, style = Type.label, color = tint, maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = lift.value.coerceIn(0f, 1f) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Notices

/**
 * Kaan's news, raised over whatever screen is open: a budget warning, the volume guard,
 * a rest reminder or a reward. Good news comes with confetti. It buzzes once as it appears.
 */
@Composable
private fun NoticeSheet(openRewards: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val view = LocalView.current
    val store = remember { Store(ctx) }
    val still = remember { reducedMotion(ctx) }
    val notice = Live.notice
    var last by remember { mutableStateOf<Notice?>(null) }
    var plays by remember { mutableIntStateOf(0) }
    val pop = remember { Animatable(1f) }

    LaunchedEffect(notice) {
        if (notice != null) {
            last = notice
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (notice.tone == Notice.JOY) plays++
            if (!still) {
                pop.snapTo(0.5f)
                pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 260f))
            }
        }
    }
    BackHandler(enabled = notice != null) { Live.notice = null }
    val show = notice ?: last

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = notice != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close"
                    ) { Live.notice = null }
            )
        }
        AnimatedVisibility(
            visible = notice != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (still) fadeIn(tween(180))
            else slideInVertically(spring(dampingRatio = 0.72f, stiffness = 320f)) { it } + fadeIn(tween(160)),
            exit = if (still) fadeOut(tween(140))
            else slideOutVertically(spatial()) { it } + fadeOut(tween(140))
        ) {
            if (show != null) {
                val joy = show.tone == Notice.JOY
                val blockTint = when (show.tone) {
                    Notice.JOY -> p.accent
                    Notice.CALM -> p.accent
                    Notice.WARN -> p.caution
                    else -> p.over
                }
                Column(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(p.bg)
                        .border(1.5.dp, p.line, CardShape)
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        if (joy) Rays(play = plays, color = p.joy, modifier = Modifier.fillMaxSize())
                        val grow = Modifier.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        if (show.art != 0) BadgeArt(show.art, earned = true, modifier = grow, size = 88.dp)
                        else Glyph(show.icon, blockTint, modifier = grow, size = 56.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(show.title, style = Type.title, color = p.ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(show.text, style = Type.body, color = p.muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    if (show.lower) {
                        SolidButton("Lower to a safe volume", onClick = {
                            AudioProbe.lowerToSafe(ctx, store, Live.sample?.db ?: 0.0)
                            Game.collect(store)
                            Live.notice = null
                            Live.beat++
                        })
                        Spacer(Modifier.height(10.dp))
                    }
                    if (show.undo) {
                        OutlineButton("Go back to full volume", onClick = {
                            TrackerService.send(ctx, TrackerService.ACTION_UNDO_GUARD)
                            Live.notice = null
                        })
                        Spacer(Modifier.height(10.dp))
                    }
                    if (joy) {
                        SolidButton("See my rewards", onClick = {
                            Live.notice = null
                            openRewards()
                        })
                        Spacer(Modifier.height(10.dp))
                    }
                    if (show.lower || show.undo || joy) {
                        OutlineButton(if (joy) "Close" else "Leave it", onClick = { Live.notice = null })
                    } else {
                        SolidButton("Got it", onClick = { Live.notice = null })
                    }
                }
            }
        }
        Confetti(play = plays, modifier = Modifier.fillMaxSize())
    }
}

// ---------------------------------------------------------------- First run

@Composable
private fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    var step by rememberSaveable { mutableIntStateOf(0) }

    fun finish() {
        store.onboarded = true
        TrackerService.start(ctx)
        Live.beat++
        onDone()
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_mark, p.accent, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Text("Kaan", style = Type.heading, color = p.ink)
            Spacer(Modifier.weight(1f))
            Text("${step + 1} of 3", style = Type.small, color = p.muted)
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spatial()) { it / 4 * dir } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(spatial()) { -it / 4 * dir } + fadeOut(tween(120)))
            },
            label = "onboarding"
        ) { s ->
            Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                when (s) {
                    0 -> {
                        var fill by remember { mutableFloatStateOf(0f) }
                        LaunchedEffect(Unit) {
                            delay(250)
                            fill = 0.62f
                        }
                        Ring(fraction = fill, color = p.calm, modifier = Modifier.size(200.dp)) {
                            Text("62%", style = Type.big, color = p.calm)
                        }
                        Spacer(Modifier.height(32.dp))
                        Text("Your ears have a daily budget", style = Type.title, color = p.ink)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Loud sound uses it up fast. Kaan shows how much is left today.",
                            style = Type.body, color = p.muted
                        )
                    }
                    1 -> {
                        IconBlock(R.drawable.ic_devices, p.lift, p.accent, size = 112.dp, glyph = 60.dp)
                        Spacer(Modifier.height(32.dp))
                        Text("It never listens to the room", style = Type.title, color = p.ink)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Kaan reads your volume and your earphones. No microphone, no audio kept, nothing leaves this phone.",
                            style = Type.body, color = p.muted
                        )
                    }
                    else -> {
                        IconBlock(R.drawable.ic_bell, p.joy, p.accent, size = 112.dp, glyph = 60.dp)
                        Spacer(Modifier.height(32.dp))
                        Text("Quiet until it matters", style = Type.title, color = p.ink)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "A warning pops up at 80 percent and when the budget runs out. Every Pro feature is free for 2 days.",
                            style = Type.body, color = p.muted
                        )
                    }
                }
            }
        }

        SolidButton(
            label = if (step < 2) "Continue" else "Allow notifications and start",
            onClick = {
                when {
                    step < 2 -> step++
                    Build.VERSION.SDK_INT >= 33 -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> finish()
                }
            }
        )
        if (step == 2) {
            TextLink(
                "Start without notifications",
                onClick = { finish() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            Spacer(Modifier.height(44.dp))
        }
    }
}
