package com.dronzer.kaan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Debug builds only. Looks for a newer build among this repository's GitHub Releases,
 * downloads the APK to the cache and hands it to the system installer.
 * The release source set holds a stand-in with the same shape that does nothing.
 */
object Updater {
    const val ENABLED = true

    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    private const val ASSET = "kaan-debug.apk"
    private const val PREFS = "kaan_update"
    private const val CHANNEL = "updates"
    private const val NOTE_ID = 20
    private const val AUTO_GAP_MS = 3L * 60L * 60L * 1000L

    private val main = Handler(Looper.getMainLooper())
    private var apkUrl: String? = null
    private var working = false

    private fun set(next: UpdateState) {
        main.post { state = next }
    }

    private fun apk(ctx: Context) = File(File(ctx.cacheDir, "updates"), ASSET)

    /** Looks for a newer build without being asked, at most once every few hours. */
    fun autoCheck(ctx: Context, notify: Boolean = false) {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checked", 0L) < AUTO_GAP_MS) return
        if (working || state is UpdateState.Downloading) return
        prefs.edit().putLong("checked", now).apply()
        look(ctx.applicationContext, quiet = true, notify = notify)
    }

    fun check(ctx: Context) = look(ctx.applicationContext, quiet = false, notify = false)

    /** What a tap on the update row does. It depends on how far the update has come. */
    fun act(ctx: Context) {
        val app = ctx.applicationContext
        when (val s = state) {
            is UpdateState.Checking, is UpdateState.Downloading -> Unit
            is UpdateState.Available -> download(app, s.build)
            is UpdateState.NeedsPermission -> install(app, s.build)
            is UpdateState.Ready -> install(app, s.build)
            else -> check(app)
        }
    }

    private fun look(app: Context, quiet: Boolean, notify: Boolean) {
        if (working) return
        working = true
        val before = state
        if (!quiet) set(UpdateState.Checking)
        Thread {
            try {
                val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "kaan-debug-updater")
                val code = conn.responseCode
                if (code != 200) throw IllegalStateException("GitHub answered with code $code.")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()

                val remote = remoteBuild(json)
                    ?: throw IllegalStateException("The newest release carries no build number.")
                val assets = json.optJSONArray("assets")
                var url: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name") == ASSET) url = a.optString("browser_download_url")
                    }
                }
                if (remote > BuildConfig.VERSION_CODE) {
                    if (url.isNullOrBlank()) throw IllegalStateException("Build $remote has no APK attached yet.")
                    apkUrl = url
                    set(UpdateState.Available(remote))
                    if (notify) announce(app, remote)
                } else if (!quiet || before is UpdateState.Idle) {
                    set(UpdateState.UpToDate)
                }
            } catch (e: Exception) {
                // A check nobody asked for fails silently and tries again later.
                if (!quiet) set(UpdateState.Failed(reason(e)))
            } finally {
                working = false
            }
        }.start()
    }

    /** The tag is build-N. The release body repeats it as versionCode=N on a line of its own. */
    private fun remoteBuild(json: JSONObject): Int? {
        val tag = json.optString("tag_name")
        Regex("^build-(\\d+)$").find(tag)?.let { return it.groupValues[1].toIntOrNull() }
        val body = json.optString("body")
        return Regex("(?m)^versionCode=(\\d+)\\s*$").find(body)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun download(app: Context, build: Int) {
        val url = apkUrl
        if (url == null) {
            check(app)
            return
        }
        if (working) return
        working = true
        set(UpdateState.Downloading(build, 0f))
        Thread {
            try {
                val target = apk(app)
                target.parentFile?.mkdirs()
                val part = File(target.parentFile, "$ASSET.part")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "kaan-debug-updater")
                val code = conn.responseCode
                if (code != 200) throw IllegalStateException("The download answered with code $code.")
                val total = conn.contentLengthLong
                var done = 0L
                var shown = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (total > 0 && now - shown > 120) {
                                shown = now
                                set(UpdateState.Downloading(build, done.toFloat() / total))
                            }
                        }
                    }
                }
                conn.disconnect()
                if (total > 0 && done != total) throw IllegalStateException("The download stopped early.")
                target.delete()
                if (!part.renameTo(target)) throw IllegalStateException("Kaan could not save the download.")
                working = false
                main.post { install(app, build) }
            } catch (e: Exception) {
                set(UpdateState.Failed(reason(e)))
            } finally {
                working = false
            }
        }.start()
    }

    private fun install(app: Context, build: Int) {
        val file = apk(app)
        if (!file.exists()) {
            download(app, build)
            return
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedsPermission(build)
            val ask = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(ask) }
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        state = if (runCatching { app.startActivity(view) }.isSuccess) {
            UpdateState.Ready(build)
        } else {
            UpdateState.Failed("Android could not open the installer.")
        }
    }

    /** One notification when a check from the background finds a newer build. */
    private fun announce(app: Context, build: Int) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("announced", 0) >= build) return
        prefs.edit().putInt("announced", build).apply()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Debug updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            app, 6,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle("A new version of Kaan is available")
            .setContentText("Build $build is ready. Open Kaan to update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTE_ID, n) }
    }

    private fun reason(e: Exception): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "Kaan could not reach GitHub. Check the connection."
        is IllegalStateException -> e.message ?: "The update failed."
        else -> "The update failed."
    }
}
