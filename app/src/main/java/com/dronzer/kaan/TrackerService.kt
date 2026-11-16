package com.dronzer.kaan

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Samples the media volume every few seconds while audio plays through earphones
 * and adds the estimated dose to today's total. It never touches the microphone.
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
    private val poller = object : Runnable {
        override fun run() {
            meter.poll()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        createChannels()
        store.prune()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_LOWER -> {
                AudioProbe.lowerToSafe(this, store)
                manager().cancel(ALERT_ID)
            }
            ACTION_EARPHONES, ACTION_SPEAKER -> {
                intent?.getStringExtra(EXTRA_ID)?.let { store.classify(it, action == ACTION_SPEAKER) }
                manager().cancel(ASK_ID)
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

        checkAlerts(sample)
        checkRest()
        askAbout(sample)
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

    private fun checkAlerts(sample: Sample) {
        val dose = store.dose()
        if (dose >= 1.0 && !store.flag("full")) {
            store.setFlag("full")
            val lowered = store.isPro && store.autoLower && AudioProbe.lowerToSafe(this, store)
            if (store.alertFull) {
                alert(
                    "Today's hearing budget is used up",
                    if (lowered) "Kaan lowered the volume to a level that is safe for longer."
                    else "Lower the volume or give your ears a rest.",
                    offerLower = !lowered && sample.counts && !sample.call
                )
            }
        } else if (dose >= 0.8 && dose < 1.0 && !store.flag("near") && store.alertNear) {
            store.setFlag("near")
            val left = if (sample.counts) Dose.remainingSeconds(dose, sample.db, store.sensitive) else -1L
            alert(
                "80 percent of today's hearing budget used",
                if (left >= 0) "About ${duration(left)} left at this volume." else "Keep the volume low for the rest of the day.",
                offerLower = sample.counts && !sample.call && sample.db > Dose.refDb(store.sensitive)
            )
        }
    }

    private fun checkRest() {
        val every = store.breakMinutes
        if (!store.isPro || every <= 0 || continuous < every * 60) return
        continuous = 0
        val n = Notification.Builder(this, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Time for an ear rest")
            .setContentText("You have listened for $every minutes without a break. Five quiet minutes help.")
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager().notify(REST_ID, n)
    }

    /** Asks once, the first time an unconfirmed Bluetooth device plays audio. */
    private fun askAbout(sample: Sample) {
        val id = sample.deviceId ?: return
        if (!sample.ask || !sample.playing || store.asked(id)) return
        store.setAsked(id)
        val guessedSpeaker = store.isSpeaker(id)
        val name = sample.deviceName
        val n = Notification.Builder(this, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (guessedSpeaker) "Is $name a speaker?" else "Is $name earphones or a speaker?")
            .setContentText(
                if (guessedSpeaker) "Kaan is not counting it. Tell Kaan if it sits in or on your ears."
                else "Kaan counts earphones toward your hearing budget. Speakers do not count."
            )
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .addAction(choice(ACTION_EARPHONES, 4, id, R.drawable.ic_stat, "Earphones"))
            .addAction(choice(ACTION_SPEAKER, 5, id, R.drawable.ic_stat, "Speaker"))
            .build()
        manager().notify(ASK_ID, n)
    }

    private fun choice(action: String, code: Int, id: String, icon: Int, label: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, code,
            Intent(this, TrackerService::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun alert(title: String, text: String, offerLower: Boolean) {
        val b = Notification.Builder(this, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
        if (offerLower) {
            val lower = PendingIntent.getService(
                this, 2,
                Intent(this, TrackerService::class.java).setAction(ACTION_LOWER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_down),
                    "Lower to a safe volume",
                    lower
                ).build()
            )
        }
        manager().notify(ALERT_ID, b.build())
    }

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

    private fun createChannels() {
        val status = NotificationChannel(CH_STATUS, "Hearing budget", NotificationManager.IMPORTANCE_LOW)
        status.description = "The quiet, always-on line that shows today's budget"
        status.setShowBadge(false)
        val alerts = NotificationChannel(CH_ALERTS, "Alerts", NotificationManager.IMPORTANCE_DEFAULT)
        alerts.description = "Warnings near the daily limit and rest reminders"
        manager().createNotificationChannel(status)
        manager().createNotificationChannel(alerts)
    }

    companion object {
        const val ACTION_LOWER = "com.dronzer.kaan.LOWER"
        const val ACTION_STOP = "com.dronzer.kaan.STOP"
        const val ACTION_EARPHONES = "com.dronzer.kaan.IS_EARPHONES"
        const val ACTION_SPEAKER = "com.dronzer.kaan.IS_SPEAKER"
        const val EXTRA_ID = "id"
        private const val CH_STATUS = "status"
        private const val CH_ALERTS = "alerts"
        private const val STATUS_ID = 1
        private const val ALERT_ID = 2
        private const val REST_ID = 3
        private const val ASK_ID = 4
        private const val TICK_SECONDS = 5
        private const val BLIND_LIMIT = 6

        fun start(ctx: Context) {
            Store(ctx).tracking = true
            runCatching { ctx.startForegroundService(Intent(ctx, TrackerService::class.java)) }
        }

        fun stop(ctx: Context) {
            Store(ctx).tracking = false
            ctx.stopService(Intent(ctx, TrackerService::class.java))
        }
    }
}
