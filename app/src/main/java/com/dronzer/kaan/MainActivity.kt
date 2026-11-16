package com.dronzer.kaan

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var billing: Billing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        billing = Billing(this) { Live.beat++ }
        setContent {
            KaanTheme {
                Root(billing = billing, activity = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        billing.start()
        val store = Store(this)
        if (store.onboarded && store.tracking) TrackerService.start(this)
        Live.beat++
    }

    override fun onDestroy() {
        billing.end()
        super.onDestroy()
    }
}

enum class Tab(val label: String, val icon: Int) {
    Today("Today", R.drawable.ic_today),
    History("History", R.drawable.ic_history),
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
                    Tab.Today -> TodayScreen(openPro = openPro)
                    Tab.History -> HistoryScreen(openPro = openPro)
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
    Column(Modifier.fillMaxWidth()) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Tab.values().forEach { tab ->
                val on = tab == current
                val tint by animateColorAsState(if (on) p.accent else p.muted, tween(200), label = "navTint")
                val mark by animateDpAsState(if (on) 22.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "navMark")
                Pressable(
                    onClick = { onTab(tab) },
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
                        Glyph(tab.icon, tint)
                        Spacer(Modifier.height(4.dp))
                        Text(tab.label, style = Type.label, color = tint)
                    }
                }
            }
        }
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
                        Vessel(fill = fill, energy = 0.6f, color = p.accent, modifier = Modifier.fillMaxWidth().height(200.dp))
                        Spacer(Modifier.height(32.dp))
                        Text("Your ears have a daily budget", style = Type.title, color = p.ink)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Loud sound uses it up fast. Quiet sound barely touches it. Kaan shows how much you have used today, so you can turn it down before damage builds up.",
                            style = Type.body, color = p.muted
                        )
                    }
                    1 -> {
                        Glyph(R.drawable.ic_devices, p.accent, size = 88.dp)
                        Spacer(Modifier.height(32.dp))
                        Text("It never listens to the room", style = Type.title, color = p.ink)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Kaan reads your volume level and which earphones are connected. It never opens the microphone, it keeps no audio, and nothing leaves this phone.",
                            style = Type.body, color = p.muted
                        )
                    }
                    else -> {
                        Glyph(R.drawable.ic_bell, p.accent, size = 88.dp)
                        Spacer(Modifier.height(32.dp))
                        Text("One quiet line, two warnings", style = Type.title, color = p.ink)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Kaan keeps one silent notification with today's budget. It speaks up only at 80 percent and when the budget runs out.",
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
