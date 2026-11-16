package com.dronzer.kaan

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private fun isDebuggable(ctx: Context): Boolean =
    (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

// ---------------------------------------------------------------- Today

/** Seconds until the daily budget starts again. */
private fun untilMidnight(): Long = (86400 - LocalTime.now().toSecondOfDay()).toLong()

/** The face on the vessel: a wide smile on a quiet day, a frown once the budget is gone. */
private fun mood(dose: Double): Float = when {
    dose >= 1.0 -> -0.9f
    dose >= 0.8 -> -0.15f
    dose >= 0.5 -> 0.55f
    else -> 1f
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
fun TodayScreen(openPro: () -> Unit, openRewards: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }

    // Keeps the "now" line fresh even when background tracking is off.
    LaunchedEffect(Unit) {
        while (true) {
            // The tracker publishes its own readings. This loop only fills in when it is off.
            if (!store.tracking) Live.sample = AudioProbe.read(ctx, store)
            Live.beat++
            delay(2000)
        }
    }

    val beat = Live.beat
    val sample = Live.sample
    val dose = remember(beat) { store.dose() }
    val seconds = remember(beat) { store.seconds() }
    val avg = remember(beat) { store.avgDb() }
    val peak = remember(beat) { store.peakDb() }
    val hours = remember(beat) { store.hours() }
    val tracking = remember(beat) { store.tracking }
    val sensitive = remember(beat) { store.sensitive }
    val streak = remember(beat) { Game.streak(store) }
    val quests = remember(beat) { Game.quests(store) }
    val alertsOn = remember(beat) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    }
    val hour = remember(beat) { LocalTime.now().hour }

    val fraction = dose.toFloat()
    val state = p.state(fraction)
    val listening = sample != null && sample.counts
    val ref = Dose.refDb(sensitive)
    var lowered by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lowered) {
        if (lowered != null) {
            delay(4000)
            lowered = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(14.dp))
        val deep = sceneIsDeep(hour, p.dark)
        val onScene = if (deep) Color(0xFFF2F4FA) else Color(0xFF1D2440)
        Scene(hour = hour, mood = mood(dose), modifier = Modifier.fillMaxWidth().height(188.dp).arrive(0)) {
            Column(Modifier.padding(20.dp)) {
                Text(greeting(hour), style = Type.title, color = onScene)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        dose >= 1.0 -> "Budget used up"
                        dose >= 0.8 -> "Go easy now"
                        dose >= 0.5 -> "Going well"
                        else -> "Your ears are happy"
                    },
                    style = Type.body, color = onScene.copy(alpha = 0.78f)
                )
            }
            Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                StreakMark(streak = streak, onClick = openRewards)
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(Modifier.arrive(1)) {
            Ring(
                fraction = fraction,
                color = state,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(208.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        CountUp(percent(dose), style = Type.big, color = state)
                        Text("%", style = Type.heading, color = state, modifier = Modifier.padding(start = 2.dp, bottom = 9.dp))
                    }
                    Text("of today's budget", style = Type.small, color = p.muted)
                }
            }
            Text(
                when {
                    dose >= 1.0 -> "${percent(dose) - 100}% over. Resets in ${duration(untilMidnight())}"
                    listening -> {
                        val left = Dose.remainingSeconds(dose, sample!!.db, sensitive)
                        "${duration(left).replaceFirstChar { it.uppercase() }} left at this volume"
                    }
                    else -> "${100 - percent(dose)}% left for today"
                },
                style = Type.bodyStrong,
                color = if (dose >= 1.0) p.over else p.ink,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(18.dp))
            Rule()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(
                    if (sample != null && sample.deviceId != null && sample.personal) R.drawable.ic_devices else R.drawable.ic_speaker,
                    p.ink
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sample?.deviceName ?: "Checking audio",
                        style = Type.bodyStrong, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            sample == null -> "One moment"
                            sample.deviceId == null -> "Not counted"
                            !sample.personal -> "Speaker, not counted"
                            !sample.playing -> "Nothing is playing"
                            sample.db <= 0.0 -> "Muted"
                            sample.call -> "Call, about ${sample.db.roundToInt()} dB at your ear"
                            else -> "About ${sample.db.roundToInt()} dB at your ear"
                        },
                        style = Type.small, color = p.muted
                    )
                }
            }
            if (listening) {
                val db = sample!!.db
                val loud = db > ref && !sample.call
                Spacer(Modifier.height(14.dp))
                LevelBar(
                    fraction = ((db - 40.0) / 70.0).toFloat(),
                    color = if (db > ref + 9) p.over else if (db > ref) p.caution else p.calm,
                    icon = R.drawable.ic_volume,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )
                if (loud) {
                    Spacer(Modifier.height(12.dp))
                    val lower: () -> Unit = {
                        val changed = AudioProbe.lowerToSafe(ctx, store, db)
                        lowered = if (changed) "Volume is at a safe step now" else "Already at a safe step"
                        Live.sample = AudioProbe.read(ctx, store)
                        Game.collect(store)
                        Game.cheer(store)
                        Live.beat++
                    }
                    if (dose >= 0.8) SolidButton("Lower to a safe volume", onClick = lower)
                    else OutlineButton("Lower to a safe volume", onClick = lower)
                }
            }
            AnimatedVisibility(
                visible = lowered != null,
                enter = expandVertically(spatial()) + fadeIn(),
                exit = shrinkVertically(spatial()) + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(R.drawable.ic_check, p.accent, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(lowered ?: "", style = Type.small, color = p.ink)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !alertsOn,
            enter = expandVertically(spatial()) + fadeIn(),
            exit = shrinkVertically(spatial()) + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                ActionCard(R.drawable.ic_bell, p.blush, p.over, "Warnings are off", "Allow notifications") {
                    openNotificationSettings(ctx)
                }
            }
        }

        AnimatedVisibility(
            visible = sample != null && sample.ask && sample.deviceId != null,
            enter = expandVertically(spatial()) + fadeIn(),
            exit = shrinkVertically(spatial()) + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                KindQuestion(id = sample?.deviceId ?: "", name = sample?.deviceName ?: "", store = store)
            }
        }

        val update = Updater.state
        AnimatedVisibility(
            visible = update is UpdateState.Available,
            enter = expandVertically(spatial()) + fadeIn(),
            exit = shrinkVertically(spatial()) + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                ActionCard(R.drawable.ic_update, p.lift, p.accent, "A new version is available", "Download and install") {
                    Updater.act(ctx)
                }
            }
        }

        AnimatedVisibility(
            visible = !tracking,
            enter = expandVertically(spatial()) + fadeIn(),
            exit = shrinkVertically(spatial()) + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                ActionCard(R.drawable.ic_today, p.lift, p.accent, "Tracking is off", "Turn tracking on") {
                    TrackerService.start(ctx)
                    Live.beat++
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(Modifier.arrive(2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quests", style = Type.heading, color = p.ink, modifier = Modifier.weight(1f))
                Text("${quests.count { it.done }} of ${quests.size}", style = Type.bodyStrong, color = p.accent)
            }
            Spacer(Modifier.height(6.dp))
            quests.forEach { quest -> QuestRow(quest) }
        }

        Spacer(Modifier.height(14.dp))
        Card(Modifier.arrive(3)) {
            Row(Modifier.fillMaxWidth()) {
                Fact(if (seconds < 60) "0 min" else duration(seconds), "listened", Modifier.weight(1.3f))
                Fact(if (avg > 0) "$avg dB" else "none", "average", Modifier.weight(1f))
                Fact(
                    if (peak > 0) "$peak dB" else "none", "loudest", Modifier.weight(1f),
                    color = if (peak > ref + 9) p.over else p.ink
                )
            }
            Spacer(Modifier.height(20.dp))
            val top = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
            Bars(
                values = hours.map { it.toFloat() / top },
                colors = List(24) { if (it == hour) p.accent else p.calm },
                modifier = Modifier.fillMaxWidth().height(72.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12 am", "6 am", "12 pm", "6 pm", "11 pm").forEach {
                    Text(it, style = Type.label, color = p.muted)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Kaan estimates sound at your ear. It is a guide, not a medical measurement.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** A block with one line of news and one button. */
@Composable
private fun ActionCard(icon: Int, bg: Color, tint: Color, title: String, action: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlock(icon, bg, tint, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = Type.heading, color = p.ink, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        SolidButton(action, onClick = onClick)
    }
}

private fun openNotificationSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** The streak in the corner of Today. The flame sways while a streak is alive. */
@Composable
private fun StreakMark(streak: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val still = remember { reducedMotion(ctx) }
    val loop = rememberInfiniteTransition(label = "flame")
    val sway = loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway"
    )
    Pressable(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        label = "Rewards",
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.clip(Corner).background(p.surface).padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBlock(
                R.drawable.ic_flame,
                if (streak > 0) p.joy else p.bg,
                if (streak > 0) p.accent else p.muted,
                modifier = Modifier.graphicsLayer {
                    if (streak > 0 && !still) {
                        rotationZ = sway.value * 5f
                        val s = 1f + sway.value * 0.04f
                        scaleX = s
                        scaleY = s
                    }
                },
                size = 34.dp,
                glyph = 20.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (streak == 1) "1 day" else "$streak days",
                style = Type.bodyStrong, color = p.ink
            )
        }
    }
}

@Composable
private fun QuestRow(quest: Quest) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (quest.done) p.lift else p.bg, tween(300), label = "questBg")
    val tint by animateColorAsState(if (quest.done) p.accent else p.muted, tween(300), label = "questTint")
    val pop by animateFloatAsState(
        if (quest.done) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 380f), label = "questPop"
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBlock(quest.icon, bg, tint, size = 42.dp, glyph = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(quest.title, style = Type.bodyStrong, color = p.ink)
            Text(quest.detail, style = Type.small, color = p.muted)
        }
        Spacer(Modifier.width(8.dp))
        Glyph(
            R.drawable.ic_check, p.accent,
            modifier = Modifier.graphicsLayer {
                alpha = pop.coerceIn(0f, 1f)
                scaleX = pop
                scaleY = pop
            },
            label = if (quest.done) "On track" else null
        )
    }
}

// ---------------------------------------------------------------- History

@Composable
fun HistoryScreen(openPro: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    val beat = Live.beat
    val pro = remember(beat) { store.isPro }
    var wide by rememberSaveable { mutableStateOf(false) }
    val range = if (wide) 30 else 7
    val days = remember(beat, range) { store.days(range) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("History")

        if (!pro) {
            val today = days.last()
            Card {
                Text("Today", style = Type.small, color = p.muted)
                Text("${percent(today.dose)}% of budget", style = Type.stat, color = p.state(today.dose.toFloat()))
            }
            Spacer(Modifier.height(14.dp))
            Card {
                Glyph(R.drawable.ic_lock, p.ink)
                Spacer(Modifier.height(12.dp))
                Text("See your week and month", style = Type.heading, color = p.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every day charted against your budget.",
                    style = Type.body, color = p.muted
                )
                Spacer(Modifier.height(16.dp))
                SolidButton("See Kaan Pro", onClick = openPro)
            }
            Spacer(Modifier.height(28.dp))
        } else {
            HistoryCharts(days = days, range = range, wide = wide, onWide = { wide = it })
        }
    }
}

@Composable
private fun HistoryCharts(days: List<Day>, range: Int, wide: Boolean, onWide: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Segmented(listOf("7 days", "30 days"), if (wide) 1 else 0, onSelect = { onWide(it == 1) })
        Spacer(Modifier.height(16.dp))

        // The chart tops out at 150 percent so a day over budget still fits.
        Card {
            Bars(
                values = days.map { (it.dose / 1.5).toFloat() },
                colors = days.map { p.state(it.dose.toFloat()) },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                limit = 1f / 1.5f
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(days.first().date.format(DAY), style = Type.label, color = p.muted)
                Text("line marks the daily budget", style = Type.label, color = p.muted)
                Text("today", style = Type.label, color = p.muted)
            }
        }

        val total = days.sumOf { it.dose }
        val over = days.count { it.dose >= 1.0 }
        val secs = days.sumOf { it.seconds }
        val dbSecs = days.sumOf { it.avgDb.toLong() * it.seconds }
        val avg = if (secs > 0) (dbSecs / secs).toInt() else 0

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth()) {
            Fact("${percent(total / range)}%", "of the budget for these $range days", Modifier.weight(1f), color = p.state((total / range).toFloat()))
            Spacer(Modifier.width(16.dp))
            Fact("$over", if (over == 1) "day over budget" else "days over budget", Modifier.weight(1f), color = if (over > 0) p.over else p.ink)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            Fact(if (secs < 60) "0 min" else duration(secs), "listening time", Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Fact(if (avg > 0) "$avg dB" else "none", "average level", Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))
        Text("Day by day", style = Type.heading, color = p.ink)
        Spacer(Modifier.height(8.dp))
        Rule()
        days.reversed().forEach { day ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.date.format(LONG_DAY), style = Type.bodyStrong, color = p.ink)
                    Text(
                        if (day.seconds < 60) "No listening" else "${duration(day.seconds)} at about ${day.avgDb} dB",
                        style = Type.small, color = p.muted
                    )
                }
                Text("${percent(day.dose)}%", style = Type.stat, color = p.state(day.dose.toFloat()))
            }
            Rule()
        }
        Spacer(Modifier.height(28.dp))
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())

// ---------------------------------------------------------------- Earphones

/**
 * Android cannot tell a Bluetooth speaker from Bluetooth earphones.
 * Kaan asks once for each new Bluetooth device. Answering is free for everyone.
 */
@Composable
fun KindQuestion(id: String, name: String, store: Store) {
    val p = LocalPalette.current
    val guessedSpeaker = store.isSpeaker(id)
    Card {
        Text(
            if (guessedSpeaker) "Is $name a speaker?" else "Is $name earphones or a speaker?",
            style = Type.heading, color = p.ink
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (guessedSpeaker) "Kaan guessed speaker and is not counting it."
            else "Only earphones count toward your budget.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The card must go the moment the user answers, not at the next reading.
            fun answer(speaker: Boolean) {
                store.classify(id, speaker)
                val now = Live.sample
                if (now != null && now.deviceId == id) Live.sample = now.copy(ask = false, personal = !speaker)
                Game.collect(store)
                Game.cheer(store)
                Live.beat++
            }
            OutlineButton("Earphones", modifier = Modifier.weight(1f), onClick = { answer(false) })
            OutlineButton("Speaker", modifier = Modifier.weight(1f), onClick = { answer(true) })
        }
    }
}

@Composable
fun EarphonesScreen(openPro: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    val beat = Live.beat
    val pro = remember(beat) { store.isPro }
    val ids = remember(beat) { store.devices() }
    val current = Live.sample?.deviceId
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Earphones")

        if (ids.isEmpty()) {
            Card {
                Glyph(R.drawable.ic_devices, p.ink)
                Spacer(Modifier.height(12.dp))
                Text("No earphones yet", style = Type.heading, color = p.ink)
                Spacer(Modifier.height(4.dp))
                Text("Connect a pair and play something.", style = Type.body, color = p.muted)
            }
        }

        ids.forEach { id ->
            val speaker = remember(beat, id) { store.isSpeaker(id) }
            val cal = remember(beat, id) { store.calibration(id) }
            val trim = remember(beat, id) { store.trim(id) }
            val unanswered = remember(beat, id) { store.needsAnswer(id) }
            val name = remember(beat, id) { store.deviceName(id) }
            val expanded = open == id

            if (unanswered) {
                Column(Modifier.padding(bottom = 12.dp)) { KindQuestion(id, name, store) }
            } else Card(Modifier.padding(bottom = 12.dp)) {
                Pressable(onClick = { open = if (expanded) null else id }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(if (speaker) R.drawable.ic_speaker else R.drawable.ic_devices, if (id == current) p.accent else p.ink)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = Type.bodyStrong, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    append(if (id == current) "Connected now" else AudioProbe.kindLabel(id))
                                    append(
                                        if (speaker) ", speaker, not counted"
                                        else ", about ${store.fullDb(id).roundToInt()} dB at full volume"
                                    )
                                },
                                style = Type.small, color = p.muted
                            )
                        }
                        Text(if (expanded) "Close" else "Edit", style = Type.bodyStrong, color = p.accent)
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(spatial()) + fadeIn(),
                    exit = shrinkVertically(spatial()) + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        if (id.startsWith("bt:")) {
                            Text("What is it", style = Type.small, color = p.muted)
                            Spacer(Modifier.height(6.dp))
                            Segmented(listOf("Earphones", "Speaker"), if (speaker) 1 else 0, onSelect = {
                                store.classify(id, it == 1)
                                Game.collect(store)
                                Game.cheer(store)
                                Live.beat++
                            })
                            Spacer(Modifier.height(14.dp))
                        }
                        if (!speaker && pro) {
                            Text("How loud does it get at full volume", style = Type.small, color = p.muted)
                            Spacer(Modifier.height(6.dp))
                            Segmented(listOf("Quiet", "Standard", "Loud"), cal, onSelect = {
                                store.setCalibration(id, it)
                                Live.beat++
                            })
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Fine tune", style = Type.bodyStrong, color = p.ink)
                                    Text(
                                        "Match the maker's maximum level in dB.",
                                        style = Type.small, color = p.muted
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Stepper(
                                    value = if (trim > 0) "+$trim" else "$trim",
                                    onMinus = {
                                        store.setTrim(id, trim - 1)
                                        Live.beat++
                                    },
                                    onPlus = {
                                        store.setTrim(id, trim + 1)
                                        Live.beat++
                                    }
                                )
                            }
                        }
                        if (!speaker && !pro) {
                            Text("Calibration is part of Kaan Pro.", style = Type.small, color = p.muted)
                            Spacer(Modifier.height(10.dp))
                            OutlineButton("See Kaan Pro", onClick = openPro)
                        }
                        if (id != current) {
                            TextLink("Forget this one", color = p.muted, onClick = {
                                store.forget(id)
                                open = null
                                Live.beat++
                            })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(openPro: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    val beat = Live.beat
    val pro = remember(beat) { store.isPro }
    val tracking = remember(beat) { store.tracking }
    val sensitive = remember(beat) { store.sensitive }
    val near = remember(beat) { store.alertNear }
    val full = remember(beat) { store.alertFull }
    val rest = remember(beat) { store.breakMinutes }
    val auto = remember(beat) { store.autoLower }
    val debugPro = remember(beat) { store.debugPro }
    val guard = remember(beat) { store.guard }
    val theme = Live.theme
    val paid = remember(beat) { store.proCached }
    val trialLeft = remember(beat) { store.trialLeft() }
    val restSteps = listOf(0, 30, 45, 60, 90)

    val precise = remember(beat) { store.precise }
    val micGranted = remember(beat) {
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val measuring = Live.measuring

    fun changed() {
        Live.beat++
        KaanWidget.refresh(ctx)
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        store.precise = granted
        if (granted && store.tracking) TrackerService.start(ctx)
        changed()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Settings")

        Text("Listening standard", style = Type.small, color = p.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(listOf("Adult", "Child or sensitive"), if (sensitive) 1 else 0, onSelect = {
            store.sensitive = it == 1
            changed()
        })
        Spacer(Modifier.height(8.dp))
        Text(
            if (sensitive) "40 hours a week at 75 dB." else "40 hours a week at 80 dB.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(14.dp))
        Rule()

        SettingRow(
            "Background tracking",
            if (tracking) "Counts listening all day." else "Nothing is counted.",
            onClick = {
                if (tracking) TrackerService.stop(ctx) else TrackerService.start(ctx)
                changed()
            }
        ) { Toggle(tracking) }

        SettingRow(
            "Precise mode",
            when {
                !precise -> "Reads the real signal level. Needs the record audio permission. Records nothing."
                !micGranted -> "Permission removed. Tap to allow it."
                !tracking -> "Starts with background tracking."
                measuring -> "Reading the signal."
                else -> "Waiting. This output may not be readable."
            },
            onClick = {
                when {
                    precise && micGranted -> {
                        store.precise = false
                        if (tracking) TrackerService.start(ctx)
                        changed()
                    }
                    micGranted -> {
                        store.precise = true
                        if (tracking) TrackerService.start(ctx)
                        changed()
                    }
                    else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        ) { Toggle(precise && micGranted) }

        SettingRow("Warn at 80 percent", "Pop-up with sound and vibration.", onClick = {
            store.alertNear = !near
            changed()
        }) { Toggle(near) }

        SettingRow("Warn at 100 percent", "Pop-up when the budget runs out.", onClick = {
            store.alertFull = !full
            changed()
        }) { Toggle(full) }

        SettingRow(
            "Sudden volume guard",
            if (!tracking) "Needs background tracking." else "Pulls a sudden jump to full back to 60 percent.",
            onClick = {
                store.guard = !guard
                changed()
            }
        ) { Toggle(guard) }

        SettingRow(
            "Send a test warning",
            "See and feel what a warning is like.",
            onClick = { TrackerService.testAlert(ctx) }
        ) { Glyph(R.drawable.ic_bell, p.accent) }

        if (pro) {
            SettingRow("Ear rest reminder", "Minutes of listening before a break.") {
                val at = restSteps.indexOf(rest).coerceAtLeast(0)
                Stepper(
                    value = if (rest == 0) "Off" else "$rest",
                    onMinus = {
                        store.breakMinutes = restSteps[(at - 1).coerceAtLeast(0)]
                        changed()
                    },
                    onPlus = {
                        store.breakMinutes = restSteps[(at + 1).coerceAtMost(restSteps.lastIndex)]
                        changed()
                    }
                )
            }
            SettingRow("Lower volume at 100 percent", "Turns it down once, on its own.", onClick = {
                store.autoLower = !auto
                changed()
            }) { Toggle(auto) }
        } else {
            SettingRow("Ear rest reminder", "Part of Kaan Pro.", onClick = openPro) { Glyph(R.drawable.ic_lock, p.muted, label = "Locked") }
            SettingRow("Lower volume at 100 percent", "Part of Kaan Pro.", onClick = openPro) { Glyph(R.drawable.ic_lock, p.muted, label = "Locked") }
        }

        SettingRow(
            "Kaan Pro",
            when {
                paid -> "Active."
                trialLeft > 0L -> "Free trial, ${longDuration(trialLeft)} left."
                pro -> "Unlocked for testing."
                else -> "Free trial ended."
            },
            onClick = openPro
        ) { Glyph(R.drawable.ic_crown, p.accent) }

        Spacer(Modifier.height(16.dp))
        Text("Appearance", style = Type.small, color = p.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(listOf("System", "Light", "Dark"), theme, onSelect = {
            store.theme = it
            Live.theme = it
        })
        Spacer(Modifier.height(16.dp))
        Rule()

        SettingRow("Notification settings", "Sound and look of warnings.", onClick = {
            openNotificationSettings(ctx)
        }) { Glyph(R.drawable.ic_next, p.accent) }

        if (isDebuggable(ctx)) {
            SettingRow(
                "Unlock Pro for testing",
                "Debug builds only.",
                onClick = {
                    store.debugPro = !debugPro
                    changed()
                }
            ) { Toggle(debugPro) }
            UpdateRow()
        }

        Spacer(Modifier.height(24.dp))
        Text("Kaan ${BuildConfig.VERSION_NAME}, build ${BuildConfig.VERSION_CODE}, made by Dronzer", style = Type.small, color = p.muted)
        Spacer(Modifier.height(4.dp))
        Text(
            "Kaan estimates sound from your volume setting. It is a guide, not a medical device. See an audiologist if you notice ringing or muffled hearing.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** Debug builds only. Finds, downloads and installs the newest build from GitHub Releases. */
@Composable
private fun UpdateRow() {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val state = Updater.state
    val mine = BuildConfig.VERSION_CODE
    val title = when (state) {
        is UpdateState.Available -> "Update to build ${state.build}"
        is UpdateState.Downloading -> "Update to build ${state.build}"
        is UpdateState.NeedsPermission -> "Update to build ${state.build}"
        is UpdateState.Ready -> "Update to build ${state.build}"
        else -> "Check for update"
    }
    val detail = when (state) {
        is UpdateState.Idle -> "You have build $mine."
        is UpdateState.Checking -> "Checking."
        is UpdateState.UpToDate -> "You have the newest build, $mine."
        is UpdateState.Available -> "A new version is available. Tap to install."
        is UpdateState.Downloading -> "Downloading, ${(state.progress * 100).roundToInt()} percent done."
        is UpdateState.NeedsPermission -> "Allow installs for Kaan, then tap again."
        is UpdateState.Ready -> "Tap to open the installer again."
        is UpdateState.Failed -> "${state.reason} Tap to try again."
    }
    SettingRow(title, detail, onClick = { Updater.act(ctx) }) {
        when (state) {
            is UpdateState.Downloading ->
                Meter(fraction = state.progress, color = p.accent, modifier = Modifier.width(56.dp).height(8.dp))
            is UpdateState.Checking -> Meter(fraction = 0f, color = p.accent, modifier = Modifier.width(56.dp).height(8.dp))
            is UpdateState.UpToDate -> Glyph(R.drawable.ic_check, p.accent)
            is UpdateState.Failed -> Glyph(R.drawable.ic_retry, p.over, label = "Try again")
            else -> Glyph(R.drawable.ic_update, p.accent)
        }
    }
}

// ---------------------------------------------------------------- Pro

object ProPrice {
    /** Shown until Google Play reports the real price for the user's country. Set the same price in Play Console. */
    const val FALLBACK = "\u20B920"
}

@Composable
fun ProScreen(billing: Billing, activity: Activity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    val beat = Live.beat
    val pro = remember(beat) { store.isPro }
    val paid = remember(beat) { store.proCached }
    val trialLeft = remember(beat) { store.trialLeft() }
    val products = remember(beat) { billing.products }
    val monthly = products.firstOrNull()
    val price = monthly?.let { billing.price(it) }?.takeIf { it.isNotBlank() } ?: ProPrice.FALLBACK

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
        ScreenTitle("Kaan Pro", onBack = onBack)

        Card(Modifier.arrive(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBlock(
                    if (paid) R.drawable.ic_crown else R.drawable.ic_gift,
                    p.joy, p.accent, size = 56.dp, glyph = 30.dp
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            paid -> "Pro is active"
                            trialLeft > 0L -> "Your free trial is running"
                            pro -> "Pro is unlocked for testing"
                            else -> "Your free trial has ended"
                        },
                        style = Type.heading, color = p.ink
                    )
                    Text(
                        when {
                            paid -> "Every Pro feature is open."
                            trialLeft > 0L -> "${longDuration(trialLeft).replaceFirstChar { it.uppercase() }} left. No card needed."
                            pro -> "The testing switch keeps Pro open."
                            else -> "Tracking, warnings and rewards stay free."
                        },
                        style = Type.small, color = p.muted
                    )
                }
            }
            if (!paid && trialLeft > 0L) {
                Spacer(Modifier.height(14.dp))
                Meter(
                    fraction = 1f - trialLeft.toFloat() / Store.TRIAL_MS,
                    color = p.joy,
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        listOf(
            Triple(R.drawable.ic_history, "Week and month history", "Every day against your budget."),
            Triple(R.drawable.ic_devices, "Earphone calibration", "A closer estimate for each pair."),
            Triple(R.drawable.ic_moon, "Ear rest reminders", "A nudge after long listening."),
            Triple(R.drawable.ic_down, "Automatic volume drop", "Turns it down when the budget runs out."),
            Triple(R.drawable.ic_today, "Home screen widget", "Today's budget on the home screen.")
        ).forEachIndexed { i, (icon, title, detail) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).arrive(i + 1),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBlock(icon, p.lift, p.accent, size = 44.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = Type.bodyStrong, color = p.ink)
                    Text(detail, style = Type.small, color = p.muted)
                }
            }
            Rule()
        }
        Spacer(Modifier.height(24.dp))

        if (!paid) {
            Text("$price a month", style = Type.title, color = p.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                if (trialLeft > 0L) "After the 2 day free trial. Cancel any time." else "Cancel any time.",
                style = Type.body, color = p.muted
            )
            Spacer(Modifier.height(16.dp))
            if (monthly != null) {
                SolidButton("Subscribe for $price a month", onClick = { billing.buy(activity, monthly) })
            } else {
                Text(
                    "The plan loads when Kaan comes from Google Play.",
                    style = Type.small, color = p.muted
                )
            }
            Spacer(Modifier.height(8.dp))
            TextLink("Restore purchase", onClick = {
                billing.refresh()
                Live.beat++
            })
        }
        Spacer(Modifier.height(28.dp))
    }
}
