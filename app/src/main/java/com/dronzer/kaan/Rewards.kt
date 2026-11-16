package com.dronzer.kaan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Streak, level and badges. The playful record of how the user has treated their ears. */
@Composable
fun RewardsScreen() {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val store = remember { Store(ctx) }
    val beat = Live.beat
    val streak = remember(beat) { Game.streak(store) }
    val best = remember(beat) { maxOf(store.bestStreak, streak) }
    val points = remember(beat) { store.points }
    val earned = remember(beat) { store.earned() }
    val week = remember(beat) { store.days(7) }
    val next = remember(beat) { Game.nextStreakBadge(store) }
    val level = Game.level(points)
    val firstDay = remember {
        val start = store.firstRun
        if (start == 0L) LocalDate.now() else Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val still = remember { reducedMotion(ctx) }
    val loop = rememberInfiniteTransition(label = "flame")
    val sway = loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway"
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Rewards")

        Card(Modifier.arrive(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBlock(
                    R.drawable.ic_flame,
                    if (streak > 0) p.joy else p.bg,
                    if (streak > 0) p.accent else p.muted,
                    modifier = Modifier.graphicsLayer {
                        if (streak > 0 && !still) {
                            rotationZ = sway.value * 4f
                            val s = 1f + sway.value * 0.03f
                            scaleX = s
                            scaleY = s
                        }
                    },
                    size = 76.dp,
                    glyph = 40.dp
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        CountUp(streak, style = Type.big, color = p.ink)
                        Text(
                            if (streak == 1) "day" else "days",
                            style = Type.heading, color = p.ink,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                    Text(
                        when {
                            streak == 0 -> "A new streak starts tomorrow."
                            else -> "in a row. Best $best."
                        },
                        style = Type.small, color = p.muted
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEachIndexed { i, day ->
                    DayMark(
                        day = day,
                        counted = !day.date.isBefore(firstDay),
                        today = i == week.lastIndex,
                        modifier = Modifier.weight(1f).arrive(i + 1)
                    )
                }
            }

            if (next != null) {
                val have = Game.progress(store, next)
                Spacer(Modifier.height(18.dp))
                Text(
                    if (next.goal - have == 1) "1 day to ${next.title}" else "${next.goal - have} days to ${next.title}",
                    style = Type.bodyStrong, color = p.ink
                )
                Spacer(Modifier.height(8.dp))
                Meter(
                    fraction = have.toFloat() / next.goal,
                    color = p.joy,
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(Modifier.arrive(2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBlock(R.drawable.ic_star, p.lift, p.accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Level ${level.number}", style = Type.small, color = p.muted)
                    Text(level.name, style = Type.heading, color = p.ink)
                }
                CountUp(points, style = Type.stat, color = p.accent)
            }
            Spacer(Modifier.height(14.dp))
            val to = level.next
            Meter(
                fraction = if (to == null) 1f else (points - level.from).toFloat() / (to - level.from),
                color = p.accent,
                modifier = Modifier.fillMaxWidth().height(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (to == null) "Top level reached." else "${to - points} points to ${Game.level(to).name}.",
                style = Type.small, color = p.muted
            )
        }

        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Badges", style = Type.heading, color = p.ink, modifier = Modifier.weight(1f))
            Text("${earned.size} of ${Game.badges.size}", style = Type.bodyStrong, color = p.accent)
        }
        Spacer(Modifier.height(12.dp))
        Game.badges.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { badge ->
                    BadgeTile(
                        badge = badge,
                        has = badge.id in earned,
                        progress = Game.progress(store, badge),
                        modifier = Modifier.weight(1f).arrive(row + 3)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** One day of the week: a tick when it stayed inside the budget, a cross when it went over. */
@Composable
private fun DayMark(day: Day, counted: Boolean, today: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(11.dp)
    val over = day.dose >= 1.0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val box = Modifier.size(36.dp).clip(shape)
        // Solid marks: orange for a day inside the budget, red for a day over it.
        Box(
            when {
                !counted -> box.border(1.5.dp, p.line, shape)
                over -> box.background(p.over)
                else -> box.background(p.accent)
            },
            contentAlignment = Alignment.Center
        ) {
            if (counted) {
                Glyph(
                    if (over) R.drawable.ic_cross else R.drawable.ic_check,
                    p.onAccent,
                    size = 18.dp,
                    label = if (over) "Over budget" else "Inside budget"
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (today) "Today" else day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
            style = Type.label,
            color = if (today) p.ink else p.muted,
            maxLines = 1
        )
    }
}

@Composable
private fun BadgeTile(badge: Badge, has: Boolean, progress: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Pressable(
        onClick = {
            Live.notice = Notice(
                badge.icon,
                badge.title,
                if (has) "You earned this one. ${badge.how}" else badge.how,
                if (has) Notice.JOY else Notice.CALM,
                art = badge.art
            )
        },
        modifier = modifier.clip(CardShape).background(p.surface),
        label = badge.title
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            BadgeArt(badge.art, earned = has, size = 68.dp)
            Spacer(Modifier.height(12.dp))
            Text(badge.title, style = Type.bodyStrong, color = p.ink, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            when {
                has -> Text("Earned", style = Type.small, color = p.accent)
                badge.goal > 1 -> {
                    Text("$progress of ${badge.goal} days", style = Type.small, color = p.muted)
                    Spacer(Modifier.height(8.dp))
                    Meter(
                        fraction = progress.toFloat() / badge.goal,
                        color = p.joy,
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                }
                else -> Text("Not yet", style = Type.small, color = p.muted)
            }
        }
    }
}
