package com.dronzer.kaan

import java.time.LocalDate

/** Something the user can earn. [goal] is how far [Game.progress] has to climb. */
data class Badge(
    val id: String,
    val title: String,
    val how: String,
    val icon: Int,
    val goal: Int,
    /** The full colour illustration of the badge. */
    val art: Int
)

data class Level(val number: Int, val name: String, val from: Int, val next: Int?)

/** A small goal for the day. [done] is true while the day still meets it. */
data class Quest(val title: String, val detail: String, val icon: Int, val done: Boolean)

/**
 * The playful side of Kaan. A day inside the budget keeps the streak alive and earns points.
 * Streaks and a few good habits earn badges. Everything is worked out on the phone.
 */
object Game {

    const val DAY_POINTS = 10
    const val QUIET_BONUS = 5
    const val BADGE_POINTS = 20
    const val QUEST_POINTS = 5

    /** The two loudness quests only count once the day has this much listening. */
    private const val QUEST_SECONDS = 600L

    val badges = listOf(
        Badge("day1", "First calm day", "Finish one day inside your budget.", R.drawable.ic_leaf, 1, R.drawable.badge_day1),
        Badge("streak3", "Three in a row", "Stay inside your budget for 3 days running.", R.drawable.ic_flame, 3, R.drawable.badge_streak3),
        Badge("streak7", "A calm week", "Stay inside your budget for 7 days running.", R.drawable.ic_star, 7, R.drawable.badge_streak7),
        Badge("streak14", "Two steady weeks", "Stay inside your budget for 14 days running.", R.drawable.ic_medal, 14, R.drawable.badge_streak14),
        Badge("streak30", "A month of care", "Stay inside your budget for 30 days running.", R.drawable.ic_trophy, 30, R.drawable.badge_streak30),
        Badge("lower", "Turned it down", "Use the lower to a safe volume button once.", R.drawable.ic_volume, 1, R.drawable.badge_lower),
        Badge("save", "Close call", "Let Kaan catch a sudden jump to full volume.", R.drawable.ic_shield, 1, R.drawable.badge_save),
        Badge("pair", "Know your pair", "Tell Kaan what a Bluetooth device is.", R.drawable.ic_devices, 1, R.drawable.badge_pair)
    )

    private val names = listOf(
        "New listener", "Careful listener", "Steady ears", "Sound keeper", "Ear guardian", "Hearing hero"
    )
    private val steps = listOf(0, 30, 80, 160, 300, 500)

    fun level(points: Int): Level {
        val i = steps.indexOfLast { points >= it }.coerceAtLeast(0)
        return Level(i + 1, names[i], steps[i], steps.getOrNull(i + 1))
    }

    /** How far the user has come toward a badge, never more than its goal. */
    fun progress(store: Store, badge: Badge): Int {
        val raw = when (badge.id) {
            "day1" -> store.goodDays
            "lower" -> store.lowers
            "save" -> store.saves
            "pair" -> store.answered()
            else -> store.bestStreak
        }
        return raw.coerceIn(0, badge.goal)
    }

    /** The three goals for a day. The first is the budget itself, the other two reward quiet listening. */
    fun quests(store: Store, date: String = store.today()): List<Quest> {
        val dose = store.dose(date)
        val secs = store.seconds(date)
        val avg = store.avgDb(date)
        val peak = store.peakDb(date)
        val ref = Dose.refDb(store.sensitive).toInt()
        val enough = secs >= QUEST_SECONDS
        return listOf(
            Quest(
                "Stay inside the budget",
                if (dose < 1.0) "${100 - percent(dose).coerceAtMost(100)}% left" else "Over today",
                R.drawable.ic_shield,
                dose < 1.0
            ),
            Quest(
                "Average at $ref dB or lower",
                when {
                    !enough -> "After 10 min of listening"
                    else -> "About $avg dB so far"
                },
                R.drawable.ic_leaf,
                enough && avg <= ref
            ),
            Quest(
                "Loudest moment under ${ref + 10} dB",
                when {
                    !enough -> "After 10 min of listening"
                    else -> "About $peak dB so far"
                },
                R.drawable.ic_volume,
                enough && peak < ref + 10
            )
        )
    }

    /** Days in a row inside the budget, counting today while it still holds. */
    fun streak(store: Store): Int = if (store.dose() >= 1.0) 0 else store.streakDone + 1

    /** The first streak badge the user has not earned yet. */
    fun nextStreakBadge(store: Store): Badge? {
        val have = store.earned()
        return badges.firstOrNull { it.id.startsWith("streak") && it.id !in have }
    }

    /**
     * Closes every finished day that has not been scored yet. A day inside the budget
     * adds to the streak and earns points. A day over the budget ends the streak.
     */
    fun settle(store: Store) {
        val today = LocalDate.now()
        val last = store.settled?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (last == null) {
            // Days before Kaan was installed do not count.
            store.settled = today.minusDays(1).toString()
            return
        }
        var day = last.plusDays(1)
        if (!day.isBefore(today)) return
        var streak = store.streakDone
        var best = store.bestStreak
        var good = store.goodDays
        var points = store.points
        var gained = 0
        var steps = 0
        while (day.isBefore(today) && steps < 400) {
            val key = day.toString()
            val dose = store.dose(key)
            var add = QUEST_POINTS * quests(store, key).drop(1).count { it.done }
            if (dose < 1.0) {
                streak++
                good++
                add += DAY_POINTS + if (dose < 0.5) QUIET_BONUS else 0
                if (streak > best) best = streak
            } else {
                streak = 0
            }
            points += add
            gained += add
            day = day.plusDays(1)
            steps++
        }
        store.streakDone = streak
        store.bestStreak = best
        store.goodDays = good
        store.points = points
        store.cheer = store.cheer + gained
        store.settled = today.minusDays(1).toString()
    }

    /**
     * Shows good news the user has not seen yet as a sheet over the app: a new badge first,
     * otherwise the points from the days that just ended. Does nothing while the app is closed.
     */
    fun cheer(store: Store) {
        if (!Live.visible || Live.notice != null) return
        val unseen = store.unseen()
        val fresh = badges.filter { it.id in unseen }
        if (fresh.isNotEmpty()) {
            val first = fresh.first()
            val more = fresh.size - 1
            val extra = when (more) {
                0 -> ""
                1 -> " You earned one more badge too."
                else -> " You earned $more more badges too."
            }
            store.clearUnseen()
            store.cheer = 0
            Live.notice = Notice(
                first.icon, "Badge earned: ${first.title}",
                "You earned $BADGE_POINTS points.$extra", Notice.JOY, art = first.art
            )
            return
        }
        val gained = store.cheer
        if (gained > 0) {
            store.cheer = 0
            val streak = store.streakDone
            Live.notice = Notice(
                R.drawable.ic_flame,
                if (streak > 0) "Yesterday stayed inside your budget" else "Points from yesterday",
                if (streak > 1) "That makes $streak days in a row. You earned $gained points."
                else "You earned $gained points.",
                Notice.JOY
            )
        }
    }

    /** Marks every badge whose goal is now met and returns the new ones. */
    fun collect(store: Store): List<Badge> {
        val have = store.earned()
        val fresh = badges.filter { it.id !in have && progress(store, it) >= it.goal }
        fresh.forEach { store.earn(it.id) }
        if (fresh.isNotEmpty()) store.points = store.points + BADGE_POINTS * fresh.size
        return fresh
    }
}
