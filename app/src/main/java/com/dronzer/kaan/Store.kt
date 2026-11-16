package com.dronzer.kaan

import android.content.Context
import java.time.LocalDate

data class Day(
    val date: LocalDate,
    val dose: Double,
    val seconds: Long,
    val avgDb: Int,
    val peakDb: Int
)

/**
 * Everything Kaan knows lives in one SharedPreferences file on the phone.
 * No account, no server, no database to host.
 */
class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("kaan", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = p.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = p.edit().putBoolean(key, v).apply()

    var onboarded: Boolean
        get() = bool("onboarded", false)
        set(v) = setBool("onboarded", v)

    var tracking: Boolean
        get() = bool("tracking", false)
        set(v) = setBool("tracking", v)

    /** The stricter 75 dB allowance for children and sensitive ears. */
    var sensitive: Boolean
        get() = bool("sensitive", false)
        set(v) = setBool("sensitive", v)

    var alertNear: Boolean
        get() = bool("alertNear", true)
        set(v) = setBool("alertNear", v)

    var alertFull: Boolean
        get() = bool("alertFull", true)
        set(v) = setBool("alertFull", v)

    /** Pro. Minutes of unbroken listening before a rest reminder. 0 means off. */
    var breakMinutes: Int
        get() = p.getInt("breakMinutes", 0)
        set(v) = p.edit().putInt("breakMinutes", v).apply()

    /** Pro. Lower the volume once when the daily budget runs out. */
    var autoLower: Boolean
        get() = bool("autoLower", false)
        set(v) = setBool("autoLower", v)

    /** Precise mode. Reads the real signal level. Needs the record audio permission. */
    var precise: Boolean
        get() = bool("precise", false)
        set(v) = setBool("precise", v)

    /** Last entitlement Google Play reported. Lets Pro work offline. */
    var proCached: Boolean
        get() = bool("pro", false)
        set(v) = setBool("pro", v)

    /** Only reachable from debug builds. Stays on until switched off. */
    var debugPro: Boolean
        get() = bool("debugPro", false)
        set(v) = setBool("debugPro", v)

    /** Paid, unlocked for testing, or still inside the free trial. */
    val isPro: Boolean get() = proCached || debugPro || trialActive

    /** Stops a sudden jump to full volume on earphones. On for everyone. */
    var guard: Boolean
        get() = bool("guard", true)
        set(v) = setBool("guard", v)

    /** Appearance. 0 follows the phone, 1 is light, 2 is dark. */
    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) = p.edit().putInt("theme", v.coerceIn(0, 2)).apply()

    // ------------------------------------------------------------ trial

    /** When Kaan first ran on this phone, in milliseconds. 0 until the app has been opened. */
    val firstRun: Long get() = p.getLong("firstRun", 0L)

    /** Starts the trial clock the first time the app opens. Later calls change nothing. */
    fun startClock() {
        if (firstRun == 0L) p.edit().putLong("firstRun", System.currentTimeMillis()).apply()
    }

    /** Milliseconds of free trial left. Every Pro feature is open for the first two days. */
    fun trialLeft(): Long {
        val start = firstRun
        if (start == 0L) return TRIAL_MS
        return (start + TRIAL_MS - System.currentTimeMillis()).coerceIn(0L, TRIAL_MS)
    }

    val trialActive: Boolean get() = trialLeft() > 0L

    /** True once Kaan has told the user that the trial ended. */
    var trialEndSeen: Boolean
        get() = bool("trialEndSeen", false)
        set(v) = setBool("trialEndSeen", v)

    // ------------------------------------------------------------ rewards

    private fun int(key: String) = p.getInt(key, 0)
    private fun setInt(key: String, v: Int) = p.edit().putInt(key, v).apply()

    /** Finished days in a row inside the budget. Today is not part of it yet. */
    var streakDone: Int
        get() = int("streakDone")
        set(v) = setInt("streakDone", v)

    var bestStreak: Int
        get() = int("bestStreak")
        set(v) = setInt("bestStreak", v)

    /** Finished days inside the budget since Kaan was installed. */
    var goodDays: Int
        get() = int("goodDays")
        set(v) = setInt("goodDays", v)

    var points: Int
        get() = int("points")
        set(v) = setInt("points", v)

    /** Times the user lowered the volume with Kaan's button. */
    var lowers: Int
        get() = int("lowers")
        set(v) = setInt("lowers", v)

    /** Times the volume guard caught a jump to full volume. */
    var saves: Int
        get() = int("saves")
        set(v) = setInt("saves", v)

    /** The last day Kaan closed and scored, as a date string. Null before the first run. */
    var settled: String?
        get() = p.getString("settled", null)
        set(v) = p.edit().putString("settled", v).apply()

    fun earned(): Set<String> = p.getStringSet("badges", emptySet()) ?: emptySet()

    /** Badges earned while nobody was looking. Kaan celebrates them when the app is next on screen. */
    fun unseen(): Set<String> = p.getStringSet("unseenBadges", emptySet()) ?: emptySet()

    fun earn(id: String) {
        p.edit()
            .putStringSet("badges", earned() + id)
            .putStringSet("unseenBadges", unseen() + id)
            .apply()
    }

    fun clearUnseen() = p.edit().remove("unseenBadges").apply()

    /** Points from finished days that the user has not been shown yet. */
    var cheer: Int
        get() = int("cheer")
        set(v) = setInt("cheer", v)

    /** Bluetooth devices the user has confirmed as earphones or a speaker. */
    fun answered(): Int = devices().count { it.startsWith("bt:") && bool("dev:$it:answered", false) }

    // ------------------------------------------------------------ days

    fun today(): String = LocalDate.now().toString()

    private fun k(date: String, name: String) = "d:$date:$name"

    /** Fraction of the daily budget used. 1.0 is the whole budget. */
    fun dose(date: String = today()): Double = p.getLong(k(date, "dose"), 0L) / NANO

    fun seconds(date: String = today()): Long = p.getLong(k(date, "secs"), 0L)

    fun avgDb(date: String = today()): Int {
        val secs = seconds(date)
        return if (secs == 0L) 0 else (p.getLong(k(date, "dbsecs"), 0L) / secs).toInt()
    }

    fun peakDb(date: String = today()): Int = p.getInt(k(date, "peak"), 0)

    /** Dose per hour of the day, 24 values. */
    fun hours(date: String = today()): LongArray {
        val out = LongArray(24)
        val raw = p.getString(k(date, "hours"), null) ?: return out
        raw.split(',').forEachIndexed { i, s -> if (i < 24) out[i] = s.toLongOrNull() ?: 0L }
        return out
    }

    /** Adds one stretch of listening to today. */
    fun record(db: Double, secs: Int, doseAdd: Double, hour: Int) {
        val date = today()
        val add = (doseAdd * NANO).toLong()
        val hrs = hours(date)
        hrs[hour.coerceIn(0, 23)] += add
        p.edit()
            .putLong(k(date, "dose"), p.getLong(k(date, "dose"), 0L) + add)
            .putLong(k(date, "secs"), seconds(date) + secs)
            .putLong(k(date, "dbsecs"), p.getLong(k(date, "dbsecs"), 0L) + (db * secs).toLong())
            .putInt(k(date, "peak"), maxOf(peakDb(date), db.toInt()))
            .putString(k(date, "hours"), hrs.joinToString(","))
            .apply()
    }

    fun flag(name: String): Boolean = bool(k(today(), "f:$name"), false)

    fun setFlag(name: String) = setBool(k(today(), "f:$name"), true)

    /** The last [n] days, oldest first, today last. */
    fun days(n: Int): List<Day> {
        val now = LocalDate.now()
        return (n - 1 downTo 0).map { back ->
            val d = now.minusDays(back.toLong())
            val key = d.toString()
            Day(d, dose(key), seconds(key), avgDb(key), peakDb(key))
        }
    }

    /** Drops day records older than 90 days. */
    fun prune() {
        val cutoff = LocalDate.now().minusDays(90).toString()
        val old = p.all.keys.filter { it.startsWith("d:") && it.length >= 12 && it.substring(2, 12) < cutoff }
        if (old.isEmpty()) return
        val e = p.edit()
        old.forEach { e.remove(it) }
        e.apply()
    }

    // ------------------------------------------------------------ earphones

    fun devices(): List<String> = (p.getStringSet("devices", emptySet()) ?: emptySet()).sorted()

    /** Records a device the first time it shows up and makes a first guess at what it is. */
    fun remember(id: String, name: String) {
        val known = p.getStringSet("devices", emptySet()) ?: emptySet()
        if (id in known) {
            if (deviceName(id) != name) p.edit().putString("dev:$id:name", name).apply()
            return
        }
        val e = p.edit().putStringSet("devices", known + id).putString("dev:$id:name", name)
        if (id.startsWith("bt:")) {
            when (Guess.of(name)) {
                Guess.SPEAKER -> e.putBoolean("dev:$id:speaker", true)
                Guess.EARPHONES -> e.putBoolean("dev:$id:answered", true)
            }
        }
        e.apply()
    }

    fun forget(id: String) {
        val known = p.getStringSet("devices", emptySet()) ?: emptySet()
        val e = p.edit().putStringSet("devices", known - id)
        listOf("name", "speaker", "cal", "trim", "answered", "asked").forEach { e.remove("dev:$id:$it") }
        e.apply()
    }

    fun deviceName(id: String): String = p.getString("dev:$id:name", null) ?: "Earphones"

    /** A Bluetooth speaker or car stereo. Kaan does not count it. */
    fun isSpeaker(id: String): Boolean = bool("dev:$id:speaker", false)

    /** The user's answer to "earphones or speaker". Also clears the open question. */
    fun classify(id: String, speaker: Boolean) {
        p.edit().putBoolean("dev:$id:speaker", speaker).putBoolean("dev:$id:answered", true).apply()
    }

    /** A Bluetooth device whose kind the user has not confirmed. */
    fun needsAnswer(id: String): Boolean = id.startsWith("bt:") && !bool("dev:$id:answered", false)

    /** True once Kaan has sent its one notification about this device. */
    fun asked(id: String): Boolean = bool("dev:$id:asked", false)

    fun setAsked(id: String) = setBool("dev:$id:asked", true)

    /** 0 quiet, 1 standard, 2 loud. */
    fun calibration(id: String): Int = p.getInt("dev:$id:cal", 1)

    fun setCalibration(id: String, v: Int) = p.edit().putInt("dev:$id:cal", v.coerceIn(0, 2)).apply()

    /** Fine adjustment in dB on top of the loudness class, from -8 to 8. */
    fun trim(id: String): Int = p.getInt("dev:$id:trim", 0)

    fun setTrim(id: String, v: Int) = p.edit().putInt("dev:$id:trim", v.coerceIn(-8, 8)).apply()

    /** Estimated level at the ear with this pair at full volume. */
    fun fullDb(id: String): Double = when (calibration(id)) {
        0 -> 94.0
        2 -> 106.0
        else -> 100.0
    } + trim(id)

    companion object {
        private const val NANO = 1_000_000_000.0
        const val TRIAL_MS = 2L * 24L * 60L * 60L * 1000L
    }
}
