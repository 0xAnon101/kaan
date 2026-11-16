package com.dronzer.kaan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow

/** One reading of what the phone is playing and where. */
data class Sample(
    val deviceId: String?,
    val deviceName: String,
    val personal: Boolean,
    val playing: Boolean,
    val db: Double,
    val volume: Float,
    /** The audio is speech: a call, or an app that marks its audio as spoken word. */
    val speech: Boolean = false,
    val call: Boolean = false,
    /** A Bluetooth device the user has not yet confirmed as earphones or a speaker. */
    val ask: Boolean = false,
    /** How far the current volume step sits below full output, in dB. Zero or negative. */
    val below: Double = 0.0,
    /** True when the level comes from the real signal strength, not from the volume step alone. */
    val measured: Boolean = false
) {
    /** True when this reading adds to the hearing dose. */
    val counts: Boolean get() = personal && playing && db > 0.0
}

/**
 * Something Kaan wants to say right now, shown as a sheet over the app.
 * The same news also goes out as a notification for when the app is closed.
 */
data class Notice(
    val icon: Int,
    val title: String,
    val text: String,
    val tone: Int = CALM,
    /** Offer the lower to a safe volume button. */
    val lower: Boolean = false,
    /** Offer to undo the volume guard. */
    val undo: Boolean = false,
    /** A full colour illustration to show in place of the glyph. 0 for none. */
    val art: Int = 0
) {
    companion object {
        const val CALM = 0
        const val WARN = 1
        const val OVER = 2
        const val JOY = 3
    }
}

/** What the screens observe. The tracker and the Today screen both write here, on the main thread. */
object Live {
    var sample by mutableStateOf<Sample?>(null)
    var beat by mutableIntStateOf(0)

    /** True while precise mode is reading the signal. */
    var measuring by mutableStateOf(false)

    /** True while the app is on screen. The tracker only raises a sheet when someone can see it. */
    var visible = false

    var notice by mutableStateOf<Notice?>(null)

    /** 0 follows the phone, 1 is light, 2 is dark. */
    var theme by mutableIntStateOf(0)

    fun say(notice: Notice) {
        if (visible) this.notice = notice
    }
}

/**
 * Dose maths from the WHO and ITU safe listening standard (H.870).
 * A week allows 40 hours at 80 dB, or 40 hours at 75 dB for sensitive ears.
 * Every 3 dB louder halves the safe time.
 */
object Dose {
    private const val DAILY_SECONDS = 40.0 * 3600.0 / 7.0

    fun refDb(sensitive: Boolean): Double = if (sensitive) 75.0 else 80.0

    /** Fraction of the daily budget used by one second at this level. */
    fun perSecond(db: Double, sensitive: Boolean): Double =
        10.0.pow((db - refDb(sensitive)) / 10.0) / DAILY_SECONDS

    /** Seconds of budget left today at this level. */
    fun remainingSeconds(dose: Double, db: Double, sensitive: Boolean): Long {
        if (dose >= 1.0) return 0L
        val rate = perSecond(db, sensitive)
        return if (rate <= 0.0) Long.MAX_VALUE else ((1.0 - dose) / rate).toLong()
    }
}

/**
 * A first guess at what a Bluetooth audio device is, from its name alone.
 * Android reports a Bluetooth speaker and Bluetooth earphones as the same kind of output,
 * so Kaan guesses, then asks the user once to confirm.
 */
object Guess {
    const val UNKNOWN = 0
    const val EARPHONES = 1
    const val SPEAKER = 2

    private val speakerParts = listOf(
        "speaker", "soundbar", "sound bar", "boombox", "soundlink", "party", "stereo",
        "carplay", "homepod", "roku", "chromecast", "bravia", "handsfree", "hands-free",
        "xtreme", "stanmore", "emberton", "megaboom", "wonderboom"
    )
    private val earParts = listOf(
        "bud", "pod", "ear", "headphone", "headset", "neckband", "airdopes", "rockerz",
        "bullets", "tws", "wf-", "wh-"
    )
    private val speakerWords = Regex("\\b(car|tv|home|echo|nest|sync|flip|charge|clip|go|auto|dot|hub)\\b")

    fun of(name: String): Int {
        val n = name.lowercase()
        return when {
            speakerParts.any { it in n } -> SPEAKER
            earParts.any { it in n } -> EARPHONES
            speakerWords.containsMatchIn(n) -> SPEAKER
            else -> UNKNOWN
        }
    }
}

object AudioProbe {

    /** Bluetooth SCO. Calls over Bluetooth use this hidden stream for their volume. */
    private const val STREAM_BLUETOOTH_SCO = 6

    /** How far below music the model places speech at the same volume step. An assumption, not a measurement. */
    private const val SPEECH_OFFSET_DB = 5.0

    private val BLUETOOTH = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET
    )
    private val WIRED = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES
    )
    private val PERSONAL = BLUETOOTH + WIRED + AudioDeviceInfo.TYPE_USB_HEADSET

    fun read(ctx: Context, store: Store): Sample {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val call = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
        val device = currentOutput(am, call)

        val stream = when {
            !call -> AudioManager.STREAM_MUSIC
            device?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> STREAM_BLUETOOTH_SCO
            else -> AudioManager.STREAM_VOICE_CALL
        }
        val index = runCatching { am.getStreamVolume(stream) }.getOrDefault(0)
        val max = runCatching { am.getStreamMaxVolume(stream) }.getOrDefault(15).coerceAtLeast(1)
        val volume = index.toFloat() / max
        val playing = call || am.isMusicActive
        val speech = call || speechOnly(am)

        if (device == null || device.type !in PERSONAL) {
            return Sample(null, "Phone speaker", false, playing, 0.0, volume, speech, call)
        }

        val name = nameOf(device)
        val id = idOf(device.type, name)
        store.remember(id, name)
        val personal = !store.isSpeaker(id)
        val below = attenuation(am, stream, device.type, index, max)
        val db = estimateDb(am, stream, device.type, index, max, store.fullDb(id), speech)
        return Sample(id, name, personal, playing, db, volume, speech, call, store.needsAnswer(id), below)
    }

    /**
     * Estimated level at the ear. Android reports how far each volume step sits below full
     * output. Kaan adds that to the full output level set for this pair of earphones,
     * then lowers it for speech, which carries less energy than music at the same step.
     */
    fun estimateDb(
        am: AudioManager, stream: Int, type: Int, index: Int, max: Int, full: Double, speech: Boolean
    ): Double {
        if (index <= 0) return 0.0
        val content = if (speech) SPEECH_OFFSET_DB else 0.0
        return (full + attenuation(am, stream, type, index, max) - content).coerceIn(30.0, 115.0)
    }

    /** dB below full output for this volume step, from Android's own volume curve. */
    private fun attenuation(am: AudioManager, stream: Int, type: Int, index: Int, max: Int): Double {
        val linear = -50.0 * (1.0 - index.toDouble() / max)
        val curve = runCatching { am.getStreamVolumeDb(stream, index, type) }.getOrDefault(Float.NaN)
        val usable = !curve.isNaN() && !curve.isInfinite() && curve <= 0f && !(curve == 0f && index < max)
        return if (usable) curve.toDouble() else linear
    }

    /**
     * Precise mode. Replaces the volume-only estimate with one built on the measured signal.
     * The full output level of a pair is defined for music at REFERENCE_DBFS. A quieter or
     * louder signal moves the result by the same number of dB.
     * Bluetooth earphones apply the volume themselves, so the phone's signal is still at full
     * strength and the volume step is added. Wired and USB outputs already carry the volume.
     */
    fun refine(sample: Sample, rmsDbfs: Double, store: Store): Sample {
        val id = sample.deviceId ?: return sample
        if (sample.call || sample.db <= 0.0) return sample
        if (rmsDbfs < SILENCE_DBFS) return sample.copy(db = 0.0, speech = false, measured = true)
        val volume = if (id.startsWith("bt:")) sample.below else 0.0
        val db = (store.fullDb(id) + (rmsDbfs - REFERENCE_DBFS) + volume).coerceIn(30.0, 115.0)
        return sample.copy(db = db, speech = false, measured = true)
    }

    /** Signal level of typical loud music. The loudness classes are defined at this level. An assumption. */
    private const val REFERENCE_DBFS = -12.0

    /** Below this the output is treated as silent. */
    const val SILENCE_DBFS = -80.0

    /**
     * Lowers the media volume to the highest step that stays at or under the safe reference level.
     * [nowDb] is the level Kaan shows right now. In precise mode it can sit above the safe level
     * even when the volume step alone looks fine, so Kaan then steps down by the difference.
     * Returns true when the volume changed.
     */
    fun lowerToSafe(ctx: Context, store: Store, nowDb: Double = 0.0): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val device = currentOutput(am, false) ?: return false
        if (device.type !in PERSONAL) return false
        val full = store.fullDb(idOf(device.type, nameOf(device)))
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        val ref = Dose.refDb(store.sensitive)
        var target = 1
        for (i in max downTo 1) {
            if (estimateDb(am, stream, device.type, i, max, full, false) <= ref) {
                target = i
                break
            }
        }
        val current = am.getStreamVolume(stream)
        if (current <= target && nowDb > ref && current > 1) {
            // About 3 dB a step is a fair guess across phones.
            val down = kotlin.math.ceil((nowDb - ref) / 3.0).toInt().coerceAtLeast(1)
            target = (current - down).coerceAtLeast(1)
        }
        if (current <= target) return false
        val ok = runCatching { am.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI) }.isSuccess
        if (ok) store.lowers = store.lowers + 1
        return ok
    }

    /** Bluetooth media and Bluetooth calls reach the same earphones through different outputs. One id covers both. */
    fun idOf(type: Int, name: String): String = when (type) {
        in BLUETOOTH -> "bt:$name"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb:$name"
        else -> "wired:$name"
    }

    fun kindLabel(id: String): String = when (id.substringBefore(':')) {
        "bt" -> "Bluetooth"
        "usb" -> "USB headset"
        else -> "Wired"
    }

    private fun nameOf(device: AudioDeviceInfo): String {
        val given = device.productName?.toString()?.takeIf { it.isNotBlank() }
        // Wired earphones report the phone's own model as their name.
        if (device.type in WIRED) return "Wired earphones"
        return given ?: if (device.type in BLUETOOTH) "Bluetooth audio" else "USB headset"
    }

    /** True when every app playing media marks its audio as spoken word. */
    private fun speechOnly(am: AudioManager): Boolean {
        val media = runCatching { am.activePlaybackConfigurations }.getOrDefault(emptyList())
            .map { it.audioAttributes }
            .filter {
                it.usage == AudioAttributes.USAGE_MEDIA ||
                    it.usage == AudioAttributes.USAGE_GAME ||
                    it.usage == AudioAttributes.USAGE_UNKNOWN
            }
        return media.isNotEmpty() && media.all { it.contentType == AudioAttributes.CONTENT_TYPE_SPEECH }
    }

    private fun currentOutput(am: AudioManager, call: Boolean): AudioDeviceInfo? {
        if (call && Build.VERSION.SDK_INT >= 31) {
            runCatching { am.communicationDevice }.getOrNull()?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = AudioAttributes.Builder()
                .setUsage(if (call) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                .setContentType(if (call) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val routed = runCatching { am.getAudioDevicesForAttributes(attrs) }.getOrNull()
            if (!routed.isNullOrEmpty()) return routed[0]
        }
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outs.firstOrNull { it.type in PERSONAL }
            ?: outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }
}
