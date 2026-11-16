package com.dronzer.kaan

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Samples the media volume every few seconds while audio plays through earphones
 * and adds the estimated dose to today's total. It never touches the microphone.
 * It also watches for a sudden jump to full volume and pulls it back.
 */
class TrackerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = Runnable { tick() }
    private lateinit var store: Store
    private var lastStatus = ""
    private var continuous = 0
    private var silence = 0
    private var foreground = false
    private var ticking = false
    private val meter = LevelMeter()
    private var blindTicks = 0
    private var day = ""
    private val poller = object : Runnable {
        override fun run() {
            meter.poll()
            handler.postDelayed(this, 500L)
        }
    }

    // Volume guard. Remembers the last volume step and where a fast rise began.
    private var volIndex = -1
    private var volTime = 0L
    private var volDevice: String? = null
    private var riseFrom = -1
    private var riseStart = 0L
    private var guardHoldUntil = 0L
    private var guardFrom = -1
    private var watching = false
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onVolume()
        }
    }
    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onVolume()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        createChannels(this)
        store.prune()
        watchVolume()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_LOWER -> {
                AudioProbe.lowerToSafe(this, store, Live.sample?.db ?: 0.0)
                manager().cancel(ALERT_ID)
                Live.beat++
            }
            ACTION_UNDO_GUARD -> {
                undoGuard()
                manager().cancel(GUARD_ID)
            }
            ACTION_EARPHONES, ACTION_SPEAKER -> {
                intent?.getStringExtra(EXTRA_ID)?.let { store.classify(it, action == ACTION_SPEAKER) }
                manager().cancel(ASK_ID)
                Live.sample = Live.sample?.copy(ask = false)
                Live.beat++
            }
            ACTION_STOP -> {
                store.tracking = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Volume and device choices arrive while the service already runs. Only real starts set it up.
        if (action == null || !foreground) {
            if (!goForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            foreground = true
        }
        syncMeter()
        // Start the sampling loop once. Later starts must not add an extra reading.
        if (!ticking) {
            ticking = true
            handler.post(ticker)
        }
        return START_STICKY
    }

    private fun micAllowed(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Android 14 and later only grant the microphone service type while the app is on screen.
     * After a restart Kaan runs without it until the user opens the app once.
     */
    private fun goForeground(): Boolean {
        val n = statusNotification(statusText(Live.sample))
        if (Build.VERSION.SDK_INT < 34) return runCatching { startForeground(STATUS_ID, n) }.isSuccess
        if (store.precise && micAllowed()) {
            val both = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (runCatching { startForeground(STATUS_ID, n, both) }.isSuccess) return true
        }
        return runCatching { startForeground(STATUS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }.isSuccess
    }

    /** Starts or stops the signal meter to match the setting and the permission. */
    private fun syncMeter() {
        val want = store.precise && micAllowed()
        if (want && !meter.running) meter.start()
        if (!want && meter.running) meter.stop()
        blindTicks = 0
        Live.measuring = meter.running
        handler.removeCallbacks(poller)
        if (meter.running) handler.post(poller)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(poller)
        if (watching) {
            runCatching { unregisterReceiver(volumeReceiver) }
            runCatching { contentResolver.unregisterContentObserver(volumeObserver) }
            watching = false
        }
        meter.stop()
        Live.measuring = false
        super.onDestroy()
    }

    private fun tick() {
        var sample = AudioProbe.read(this, store)
        val rms = if (meter.running) meter.take() else null
        if (rms != null) {
            // A long run of "silence" while music plays means the meter cannot see this output.
            // Kaan then falls back to the volume estimate so it never undercounts.
            val blind = sample.playing && sample.db > 0.0 && rms < AudioProbe.SILENCE_DBFS
            blindTicks = if (blind) blindTicks + 1 else 0
            if (blindTicks < BLIND_LIMIT) sample = AudioProbe.refine(sample, rms, store)
            Live.measuring = blindTicks < BLIND_LIMIT
        }
        Live.sample = sample

        // Each output keeps its own volume. A new output starts the volume guard afresh.
        if (sample.deviceId != volDevice) {
            volDevice = sample.deviceId
            volIndex = -1
            riseFrom = -1
        }

        if (sample.counts) {
            val add = Dose.perSecond(sample.db, store.sensitive) * TICK_SECONDS
            store.record(sample.db, TICK_SECONDS, add, LocalTime.now().hour)
            continuous += TICK_SECONDS
            silence = 0
        } else {
            silence += TICK_SECONDS
            if (silence >= 300) continuous = 0
        }
        Live.beat++

        newDay()
        checkAlerts(sample)
        checkRest()
        askAbout(sample)
        checkRewards()
        // The events above normally catch a volume jump. This is the fallback.
        onVolume()
        // Debug builds only. Tells the user when a newer build is on GitHub.
        Updater.autoCheck(this, notify = true)

        val text = statusText(sample)
        if (text != lastStatus) {
            lastStatus = text
            manager().notify(STATUS_ID, statusNotification(text))
            KaanWidget.refresh(this)
        }
        handler.postDelayed(ticker, TICK_SECONDS * 1000L)
    }

    /** Scores the days that ended since the last look. */
    private fun newDay() {
        val today = LocalDate.now().toString()
        if (today == day) return
        day = today
        Game.settle(store)
    }

    // ------------------------------------------------------------ alerts

    private fun checkAlerts(sample: Sample) {
        val dose = store.dose()
        val listening = sample.counts && !sample.call
        if (dose >= 1.0 && !store.flag("full")) {
            store.setFlag("full")
            val lowered = store.isPro && store.autoLower && AudioProbe.lowerToSafe(this, store, sample.db)
            if (store.alertFull) {
                alert(
                    "Today's hearing budget is used up",
                    if (lowered) "Kaan lowered the volume to a level that is safe for longer. A fresh budget starts at midnight."
                    else "Lower the volume or give your ears a rest. A fresh budget starts at midnight.",
                    offerLower = !lowered && listening,
                    tone = Notice.OVER
                )
            }
        } else if (dose >= 1.5 && store.alertFull) {
            // One more reminder at 150 percent, then at every further 50 percent, up to 300.
            val step = (dose / 0.5).toInt().coerceAtMost(6)
            val name = "over$step"
            if (!store.flag(name)) {
                store.setFlag(name)
                alert(
                    "You are at ${step * 50} percent of today's budget",
                    "Your ears need a rest. Stop for a while, or lower the volume.",
                    offerLower = listening && sample.db > Dose.refDb(store.sensitive),
                    tone = Notice.OVER
                )
            }
        } else if (dose >= 0.8 && dose < 1.0 && !store.flag("near") && store.alertNear) {
            store.setFlag("near")
            val left = if (sample.counts) Dose.remainingSeconds(dose, sample.db, store.sensitive) else -1L
            alert(
                "80 percent of today's hearing budget used",
                if (left >= 0) "About ${duration(left)} left at this volume. Lower it to make the budget last."
                else "Keep the volume low for the rest of the day.",
                offerLower = listening && sample.db > Dose.refDb(store.sensitive),
                tone = Notice.WARN
            )
        }
    }

    private fun checkRest() {
        val every = store.breakMinutes
        if (!store.isPro || every <= 0 || continuous < every * 60) return
        continuous = 0
        val text = "You have listened for $every minutes without a break. Five quiet minutes help."
        val n = loud("Time for an ear rest", text).build()
        post(REST_ID, n)
        Live.say(Notice(R.drawable.ic_moon, "Time for an ear rest", text, Notice.CALM))
    }

    /** Asks once, the first time an unconfirmed Bluetooth device plays audio. */
    private fun askAbout(sample: Sample) {
        val id = sample.deviceId ?: return
        if (!sample.ask || !sample.playing || store.asked(id)) return
        store.setAsked(id)
        val guessedSpeaker = store.isSpeaker(id)
        val name = sample.deviceName
        val n = loud(
            if (guessedSpeaker) "Is $name a speaker?" else "Is $name earphones or a speaker?",
            if (guessedSpeaker) "Kaan is not counting it. Tell Kaan if it sits in or on your ears."
            else "Kaan counts earphones toward your hearing budget. Speakers do not count."
        )
            .addAction(choice(ACTION_EARPHONES, 4, id, R.drawable.ic_devices, "Earphones"))
            .addAction(choice(ACTION_SPEAKER, 5, id, R.drawable.ic_speaker, "Speaker"))
            .build()
        post(ASK_ID, n)
    }

    /** Hands out new badges, and says once that the free trial is over. */
    private fun checkRewards() {
        Game.collect(store).forEach { badge ->
            val n = Notification.Builder(this, CH_QUIET)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(getColor(R.color.brand))
                .setContentTitle("Badge earned: ${badge.title}")
                .setContentText("Open Kaan to see your rewards.")
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
            // The app celebrates on screen when it is open. The notification is for when it is not.
            if (!Live.visible) post(BADGE_ID, n)
        }
        Game.cheer(store)
        if (!store.trialActive && !store.trialEndSeen && store.firstRun > 0L) {
            store.trialEndSeen = true
            if (!store.isPro) {
                val n = Notification.Builder(this, CH_QUIET)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setColor(getColor(R.color.brand))
                    .setContentTitle("Your free trial of Kaan Pro has ended")
                    .setContentText("Tracking and warnings stay free. Open Kaan to keep Pro.")
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .build()
                post(TRIAL_ID, n)
            }
            Live.beat++
            KaanWidget.refresh(this)
        }
    }

    private fun choice(action: String, code: Int, id: String, icon: Int, label: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, code,
            Intent(this, TrackerService::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    /** A notification that pops up on screen with a sound and a vibration. */
    private fun loud(title: String, text: String): Notification.Builder =
        Notification.Builder(this, CH_WARN)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(getColor(R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .setAutoCancel(true)

    private fun lowerAction(): Notification.Action {
        val lower = PendingIntent.getService(
            this, 2,
            Intent(this, TrackerService::class.java).setAction(ACTION_LOWER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_down), "Lower to a safe volume", lower
        ).build()
    }

    private fun alert(title: String, text: String, offerLower: Boolean, tone: Int) {
        val b = loud(title, text)
        if (offerLower) b.addAction(lowerAction())
        post(ALERT_ID, b.build())
        Live.say(Notice(R.drawable.ic_alert, title, text, tone, lower = offerLower))
    }

    private fun post(id: Int, n: Notification) {
        runCatching { manager().notify(id, n) }
    }

    // ------------------------------------------------------------ volume guard

    private fun watchVolume() {
        if (watching) return
        watching = true
        val filter = IntentFilter(VOLUME_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(volumeReceiver, filter)
            }
        }
        // Some phones skip the broadcast. Volume changes also land in the system settings table.
        runCatching { contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver) }
    }

    /**
     * Called on every media volume change. A rise of a quarter of the range or more that ends
     * at full volume inside three seconds counts as a sudden jump. On earphones Kaan then
     * sets the volume back to 60 percent, once. A second rise soon after is left alone.
     */
    private fun onVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.mode != AudioManager.MODE_NORMAL) return
        val max = runCatching { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1)
        val index = runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val now = SystemClock.elapsedRealtime()
        val before = volIndex
        if (before < 0) {
            volIndex = index
            volTime = now
            return
        }
        if (index == before) return
        if (index > before) {
            // A pause between steps starts a new rise.
            if (riseFrom < 0 || now - volTime > RISE_GAP_MS) {
                riseFrom = before
                riseStart = now
            }
        } else {
            riseFrom = -1
        }
        volIndex = index
        volTime = now
        if (riseFrom < 0 || !store.guard || now < guardHoldUntil) return

        val full = index.toFloat() / max >= 0.95f
        val steep = (index - riseFrom).toFloat() / max >= 0.25f
        if (!full || !steep || now - riseStart > RISE_WINDOW_MS) return

        val sample = AudioProbe.read(this, store)
        // A switch of output moves the volume too. That is not a jump.
        if (sample.deviceId != volDevice) {
            volDevice = sample.deviceId
            riseFrom = -1
            return
        }
        if (sample.deviceId == null || !sample.personal || sample.call) return

        val target = (max * GUARD_LEVEL).roundToInt().coerceIn(1, max)
        if (runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI) }.isFailure) return
        guardFrom = index
        volIndex = target
        riseFrom = -1
        guardHoldUntil = now + GUARD_HOLD_MS
        store.saves = store.saves + 1

        val title = "Volume jumped to full"
        val text = "Kaan set it back to 60 percent to protect your ears. Raise it again if you meant it."
        val undo = PendingIntent.getService(
            this, 7,
            Intent(this, TrackerService::class.java).setAction(ACTION_UNDO_GUARD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = loud(title, text)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_volume), "Go back to full volume", undo
                ).build()
            )
            .build()
        post(GUARD_ID, n)
        Live.say(Notice(R.drawable.ic_shield, title, text, Notice.WARN, undo = true))
        Live.beat++
    }

    /** The user meant it. Restores the volume and leaves it alone for a while. */
    private fun undoGuard() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val back = if (guardFrom > 0) guardFrom.coerceAtMost(max) else max
        guardHoldUntil = SystemClock.elapsedRealtime() + UNDO_HOLD_MS
        riseFrom = -1
        volIndex = back
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, back, AudioManager.FLAG_SHOW_UI) }
        Live.beat++
    }

    // ------------------------------------------------------------ status line

    private fun statusText(sample: Sample?): String {
        val pct = (store.dose() * 100).roundToInt()
        val now = when {
            sample != null && sample.counts && sample.call -> "On a call at about ${sample.db.roundToInt()} dB"
            sample != null && sample.counts -> "Listening at about ${sample.db.roundToInt()} dB"
            else -> "Not listening right now"
        }
        return "$pct percent of today's budget used\n$now"
    }

    private fun statusNotification(text: String): Notification {
        val lines = text.split('\n')
        return Notification.Builder(this, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(getColor(R.color.brand))
            .setContentTitle(lines.first())
            .setContentText(lines.getOrElse(1) { "" })
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 1,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun manager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_LOWER = "com.dronzer.kaan.LOWER"
        const val ACTION_STOP = "com.dronzer.kaan.STOP"
        const val ACTION_EARPHONES = "com.dronzer.kaan.IS_EARPHONES"
        const val ACTION_SPEAKER = "com.dronzer.kaan.IS_SPEAKER"
        const val ACTION_UNDO_GUARD = "com.dronzer.kaan.UNDO_GUARD"
        const val EXTRA_ID = "id"

        /** Sent by Android on every volume change. Not part of the public SDK, but stable for years. */
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

        private const val CH_STATUS = "status"
        const val CH_WARN = "warnings"
        private const val CH_QUIET = "rewards"
        private const val STATUS_ID = 1
        private const val ALERT_ID = 2
        private const val REST_ID = 3
        private const val ASK_ID = 4
        private const val GUARD_ID = 5
        private const val BADGE_ID = 6
        private const val TRIAL_ID = 7
        private const val TEST_ID = 8
        private const val TICK_SECONDS = 5
        private const val BLIND_LIMIT = 6
        private const val RISE_GAP_MS = 1500L
        private const val RISE_WINDOW_MS = 3000L
        private const val GUARD_LEVEL = 0.6f
        private const val GUARD_HOLD_MS = 2L * 60L * 1000L
        private const val UNDO_HOLD_MS = 10L * 60L * 1000L

        fun start(ctx: Context) {
            Store(ctx).tracking = true
            runCatching { ctx.startForegroundService(Intent(ctx, TrackerService::class.java)) }
        }

        fun stop(ctx: Context) {
            Store(ctx).tracking = false
            ctx.stopService(Intent(ctx, TrackerService::class.java))
        }

        /** Sends a command to the running tracker. Does nothing when tracking is off. */
        fun send(ctx: Context, action: String) {
            if (!Store(ctx).tracking) return
            runCatching { ctx.startForegroundService(Intent(ctx, TrackerService::class.java).setAction(action)) }
        }

        /** Shows what a warning looks, sounds and feels like. Started from Settings. */
        fun testAlert(ctx: Context) {
            createChannels(ctx)
            val text = "A warning pops up like this, with a sound and a vibration. " +
                "If it stayed quiet, open notification settings and check the Warnings channel."
            val open = PendingIntent.getActivity(
                ctx, 1,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(ctx, CH_WARN)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(ctx.getColor(R.color.brand))
                .setContentTitle("This is how Kaan warns you")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.notify(TEST_ID, n) }
        }

        /**
         * Warnings pop up on screen with a sound and a vibration. The old "alerts" channel was
         * quiet, and Android does not let an app make an existing channel louder, so it is replaced.
         */
        fun createChannels(ctx: Context) {
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val status = NotificationChannel(CH_STATUS, "Hearing budget", NotificationManager.IMPORTANCE_LOW)
            status.description = "The quiet, always-on line that shows today's budget"
            status.setShowBadge(false)

            val warn = NotificationChannel(CH_WARN, "Warnings", NotificationManager.IMPORTANCE_HIGH)
            warn.description = "Budget warnings, the volume guard, rest reminders and questions about new devices"
            warn.enableVibration(true)
            warn.vibrationPattern = longArrayOf(0, 260, 140, 260, 140, 420)
            warn.enableLights(true)
            warn.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            val quiet = NotificationChannel(CH_QUIET, "Rewards and news", NotificationManager.IMPORTANCE_DEFAULT)
            quiet.description = "Badges you earn and news about your plan"

            manager.createNotificationChannel(status)
            manager.createNotificationChannel(warn)
            manager.createNotificationChannel(quiet)
            runCatching { manager.deleteNotificationChannel("alerts") }
        }
    }
}
