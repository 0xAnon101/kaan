package com.dronzer.kaan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun TodayScreen(openPro: () -> Unit) {
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

    val fraction = dose.toFloat()
    val state = p.state(fraction)
    val listening = sample != null && sample.counts
    val ref = Dose.refDb(sensitive)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_mark, p.accent, size = 24.dp)
            Spacer(Modifier.width(10.dp))
            Text("Kaan", style = Type.heading, color = p.ink)
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            CountUp(percent(dose), style = Type.hero, color = state)
            Text("%", style = Type.title, color = state, modifier = Modifier.padding(start = 4.dp, bottom = 18.dp))
        }
        Text(
            when {
                dose >= 1.0 -> "Today's safe listening is used up"
                dose >= 0.8 -> "of today's safe listening used. Go easy from here"
                else -> "of today's safe listening used"
            },
            style = Type.body, color = p.muted
        )

        Spacer(Modifier.height(20.dp))
        Vessel(
            fill = fraction,
            energy = if (listening) ((sample!!.db - 55.0) / 45.0).toFloat() else 0f,
            color = state,
            modifier = Modifier.fillMaxWidth().height(170.dp)
        )

        Spacer(Modifier.height(14.dp))
        Card {
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
                            sample.deviceId == null -> "Speakers do not count toward your budget"
                            !sample.personal -> "Marked as a speaker. Not counted"
                            !sample.playing -> "Connected. Nothing is playing"
                            sample.db <= 0.0 -> "Muted"
                            sample.measured -> "Measured signal, about ${sample.db.roundToInt()} dB at your ear"
                            sample.call -> "On a call, about ${sample.db.roundToInt()} dB at your ear"
                            sample.speech -> "Speech, about ${sample.db.roundToInt()} dB at your ear"
                            else -> "About ${sample.db.roundToInt()} dB at your ear"
                        },
                        style = Type.small, color = p.muted
                    )
                }
            }
            if (listening) {
                val db = sample!!.db
                val left = Dose.remainingSeconds(dose, db, sensitive)
                Spacer(Modifier.height(16.dp))
                Meter(
                    fraction = ((db - 40.0) / 70.0).toFloat(),
                    color = if (db > ref + 9) p.over else if (db > ref) p.caution else p.accent,
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        left <= 0L -> "No budget left at this volume today"
                        else -> "${duration(left).replaceFirstChar { it.uppercase() }} left at this volume"
                    },
                    style = Type.bodyStrong, color = p.ink
                )
                if (db > ref && !sample.call) {
                    TextLink("Lower to a safe volume", onClick = {
                        AudioProbe.lowerToSafe(ctx, store)
                        Live.beat++
                    })
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

        AnimatedVisibility(
            visible = !tracking,
            enter = expandVertically(spatial()) + fadeIn(),
            exit = shrinkVertically(spatial()) + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                Card {
                    Text("Tracking is off", style = Type.heading, color = p.ink)
                    Spacer(Modifier.height(4.dp))
                    Text("Kaan only counts listening while tracking runs in the background.", style = Type.small, color = p.muted)
                    Spacer(Modifier.height(14.dp))
                    SolidButton("Turn tracking on", onClick = {
                        TrackerService.start(ctx)
                        Live.beat++
                    })
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth()) {
            Fact(if (seconds < 60) "0 min" else duration(seconds), "listened", Modifier.weight(1.3f))
            Fact(if (avg > 0) "$avg dB" else "none", "average", Modifier.weight(1f))
            Fact(
                if (peak > 0) "$peak dB" else "none", "loudest", Modifier.weight(1f),
                color = if (peak > ref + 9) p.over else p.ink
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Through the day", style = Type.heading, color = p.ink)
        Spacer(Modifier.height(12.dp))
        val top = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val nowHour = LocalTime.now().hour
        Bars(
            values = hours.map { it.toFloat() / top },
            colors = List(24) { if (it == nowHour) p.ink else p.accent },
            modifier = Modifier.fillMaxWidth().height(84.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12 am", "6 am", "12 pm", "6 pm", "11 pm").forEach {
                Text(it, style = Type.label, color = p.muted)
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Kaan estimates sound at your ear. It is a guide, not a medical measurement.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(28.dp))
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
                    "Kaan Pro charts every day against your budget, counts the days you went over and shows your weekly total.",
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
            if (guessedSpeaker) "Kaan guessed speaker from the name and is not counting it."
            else "Earphones count toward your budget. Speakers and car stereos do not.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Earphones", modifier = Modifier.weight(1f), onClick = {
                store.classify(id, false)
                Live.beat++
            })
            OutlineButton("Speaker", modifier = Modifier.weight(1f), onClick = {
                store.classify(id, true)
                Live.beat++
            })
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
        Text(
            "Kaan remembers each pair you connect. Tell it which ones are speakers, and the count stays honest.",
            style = Type.body, color = p.muted
        )
        Spacer(Modifier.height(18.dp))

        if (ids.isEmpty()) {
            Card {
                Glyph(R.drawable.ic_devices, p.ink)
                Spacer(Modifier.height(12.dp))
                Text("No earphones yet", style = Type.heading, color = p.ink)
                Spacer(Modifier.height(4.dp))
                Text("Connect a pair and play something. It appears here.", style = Type.body, color = p.muted)
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
                                        "If the maker lists a maximum level in dB, step until the figure above matches it.",
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
                            Text("Setting how loud a pair runs is part of Kaan Pro.", style = Type.small, color = p.muted)
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
            if (sensitive) "A week allows 40 hours at 75 dB. Louder sound uses the budget faster."
            else "A week allows 40 hours at 80 dB. Louder sound uses the budget faster.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(14.dp))
        Rule()

        SettingRow(
            "Background tracking",
            if (tracking) "Kaan counts listening all day." else "Off. Nothing is counted.",
            onClick = {
                if (tracking) TrackerService.stop(ctx) else TrackerService.start(ctx)
                changed()
            }
        ) { Toggle(tracking) }

        SettingRow(
            "Precise mode",
            when {
                !precise -> "Reads how loud the audio signal is, not just the volume step. Android asks for the record audio permission. Kaan records and stores nothing."
                !micGranted -> "Android removed the permission. Tap to allow it again."
                !tracking -> "On. Starts when background tracking is on."
                measuring -> "On. Kaan is reading the signal."
                else -> "On, but waiting. Open Kaan once after a restart, or this output cannot be read."
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

        SettingRow("Warn at 80 percent", "One notification when the budget runs low.", onClick = {
            store.alertNear = !near
            changed()
        }) { Toggle(near) }

        SettingRow("Warn at 100 percent", "One notification when the budget runs out.", onClick = {
            store.alertFull = !full
            changed()
        }) { Toggle(full) }

        if (pro) {
            SettingRow("Ear rest reminder", "Minutes of unbroken listening before Kaan suggests a break.") {
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
            SettingRow("Lower volume at 100 percent", "Kaan turns the volume down once when the budget runs out.", onClick = {
                store.autoLower = !auto
                changed()
            }) { Toggle(auto) }
        } else {
            SettingRow("Ear rest reminder", "Part of Kaan Pro.", onClick = openPro) { Glyph(R.drawable.ic_lock, p.muted, label = "Locked") }
            SettingRow("Lower volume at 100 percent", "Part of Kaan Pro.", onClick = openPro) { Glyph(R.drawable.ic_lock, p.muted, label = "Locked") }
        }

        SettingRow(
            "Kaan Pro",
            if (pro) "Active on this phone." else "History, earphone calibration, rest reminders, widget.",
            onClick = openPro
        ) { Glyph(R.drawable.ic_next, p.accent) }

        SettingRow("Notification settings", "Change how Kaan's alerts look and sound.", onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Glyph(R.drawable.ic_next, p.accent) }

        if (isDebuggable(ctx)) {
            SettingRow(
                "Unlock Pro for testing",
                "Debug builds only. Stays on until you switch it off. Never ships in a release build.",
                onClick = {
                    store.debugPro = !debugPro
                    changed()
                }
            ) { Toggle(debugPro) }
        }

        Spacer(Modifier.height(24.dp))
        Text("Kaan 1.0, made by Dronzer", style = Type.small, color = p.muted)
        Spacer(Modifier.height(4.dp))
        Text(
            "Kaan estimates sound from your volume setting. It is a guide, not a medical device. See an audiologist if you notice ringing or muffled hearing.",
            style = Type.small, color = p.muted
        )
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------- Pro

@Composable
fun ProScreen(billing: Billing, activity: Activity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    val beat = Live.beat
    val pro = remember(beat) { store.isPro }
    val products = remember(beat) { billing.products }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
        ScreenTitle("Kaan Pro", onBack = onBack)

        listOf(
            Triple(R.drawable.ic_history, "Week and month history", "Every day charted against your budget."),
            Triple(R.drawable.ic_devices, "Earphone calibration", "Set how loud each pair runs for a closer estimate."),
            Triple(R.drawable.ic_rest, "Ear rest reminders", "A nudge after long unbroken listening."),
            Triple(R.drawable.ic_down, "Automatic volume drop", "Kaan lowers the volume when the budget runs out."),
            Triple(R.drawable.ic_today, "Home screen widget", "Today's budget without opening the app.")
        ).forEach { (icon, title, detail) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(icon, p.accent, size = 28.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = Type.bodyStrong, color = p.ink)
                    Text(detail, style = Type.small, color = p.muted)
                }
            }
            Rule()
        }
        Spacer(Modifier.height(24.dp))

        when {
            pro -> Text("Pro is active on this phone.", style = Type.bodyStrong, color = p.accent)

            products.isEmpty() -> Text(
                "Plans load when Kaan is installed from Google Play. In a debug build, use the testing switch in Settings.",
                style = Type.body, color = p.muted
            )

            else -> products.forEachIndexed { i, product ->
                val period = if (product.productId == Billing.YEARLY) "yearly" else "monthly"
                val label = "Start $period at ${billing.price(product)}"
                if (i == 0) {
                    SolidButton(label, onClick = { billing.buy(activity, product) })
                } else {
                    OutlineButton(label, onClick = { billing.buy(activity, product) })
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (!pro) {
            Spacer(Modifier.height(8.dp))
            TextLink("Restore purchase", onClick = {
                billing.refresh()
                Live.beat++
            })
            Text(
                "Google Play handles payment and renewal. Cancel any time in your Play subscriptions.",
                style = Type.small, color = p.muted
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
