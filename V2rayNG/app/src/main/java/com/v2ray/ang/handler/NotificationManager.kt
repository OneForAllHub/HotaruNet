package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.delay
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val QUERY_INTERVAL_MS = 2000L

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    // Session traffic and speed tracking
    private var sessionStartTimeMs = 0L
    private var sessionTotalDownBytes = 0L
    private var sessionTotalUpBytes = 0L
    private var initialRxBytes = 0L
    private var initialTxBytes = 0L
    private var lastDisplayDown = 0L
    private var lastDisplayUp = 0L
    private var cachedLargeIcon: Bitmap? = null

    /**
     * Starts the live speed and session traffic notification loop.
     */
    fun startSpeedNotification() {
        if (speedNotificationJob != null || CoreServiceManager.isRunning() == false) return

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                updateSpeedNotificationOnce()
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    /**
     * Shows or updates the foreground notification.
     * @param currentConfig The current profile configuration.
     * @param preserveSession Keep cumulative traffic & uptime counters intact across in-place failover reloads.
     */
    fun showNotification(currentConfig: ProfileItem?, preserveSession: Boolean = false) {
        val service = getService() ?: return

        if (!preserveSession || sessionStartTimeMs <= 0L) {
            // Initialize session traffic baseline
            sessionStartTimeMs = SystemClock.elapsedRealtime()
            sessionTotalDownBytes = 0L
            sessionTotalUpBytes = 0L
            lastDisplayDown = 0L
            lastDisplayUp = 0L
            lastQueryTime = System.currentTimeMillis()

            val uid = service.applicationInfo.uid
            initialRxBytes = TrafficStats.getUidRxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
            initialTxBytes = TrafficStats.getUidTxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        }

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            ""
        }

        val isPersian = isPersianLocale(service)
        val initialTitle = if (isPersian) "⚡ HotaruNet | متصل" else "⚡ HotaruNet | Connected"
        val initialShort = if (isPersian) "🚀 سرعت: ↓ 0 B/s  ↑ 0 B/s  |  📊 حجم: 0 B" else "🚀 Speed: ↓ 0 B/s  ↑ 0 B/s  |  📊 Data: 0 B"

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_hotaru)
            .setColor(0xFF00E5FF.toInt())
            .setContentTitle(initialTitle)
            .setContentText(initialShort)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_restore_24dp,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        val largeIcon = getHotaruNotificationLargeIcon(service)
        if (largeIcon != null) {
            mBuilder?.setLargeIcon(largeIcon)
        }

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
        startSpeedNotification()
    }

    /**
     * Fulfills or refreshes the foreground-service contract.
     */
    fun ensureForeground() {
        val service = getService() ?: return
        val notification = mBuilder?.build()
        if (notification == null) showNotification(null) else service.startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Cancels the notification and resets session trackers.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
        sessionStartTimeMs = 0L
        sessionTotalDownBytes = 0L
        sessionTotalUpBytes = 0L
    }

    /**
     * Stops the speed notification loop.
     */
    fun stopSpeedNotification() {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val service = getService() ?: return channelId
        NotificationHelper.ensureNotificationChannel(
            context = service,
            channelId = channelId,
            channelNameRes = R.string.notification_channel_service,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            lightColor = Color.DKGRAY
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        return channelId
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    @Synchronized
    fun sampleSessionTrafficSnapshot(): TrafficSnapshot {
        val service = getService() ?: return TrafficSnapshot(lastDisplayDown, lastDisplayUp, 0L, 0L, System.currentTimeMillis())
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime).coerceAtLeast(0L)
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        var proxyUplink = 0L
        var proxyDownlink = 0L

        CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
            if (stat.tag != AppConfig.TAG_DIRECT && stat.tag != AppConfig.TAG_BLOCKED) {
                when (stat.direction) {
                    AppConfig.UPLINK -> proxyUplink += stat.value
                    AppConfig.DOWNLINK -> proxyDownlink += stat.value
                }
            }
        }

        val uid = service.applicationInfo.uid
        val rxNow = TrafficStats.getUidRxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        val txNow = TrafficStats.getUidTxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        val deltaUidRx = (rxNow - initialRxBytes).coerceAtLeast(0L)
        val deltaUidTx = (txNow - initialTxBytes).coerceAtLeast(0L)

        sessionTotalDownBytes += proxyDownlink
        sessionTotalUpBytes += proxyUplink

        val displayDown = maxOf(sessionTotalDownBytes, deltaUidRx)
        val displayUp = maxOf(sessionTotalUpBytes, deltaUidTx)

        val speedDown = if (sinceLastQueryInSeconds >= 0.5) {
            val coreSpeed = (proxyDownlink / sinceLastQueryInSeconds).toLong()
            val uidSpeed = ((displayDown - lastDisplayDown) / sinceLastQueryInSeconds).toLong()
            maxOf(coreSpeed, uidSpeed).coerceAtLeast(0L)
        } else 0L

        val speedUp = if (sinceLastQueryInSeconds >= 0.5) {
            val coreSpeed = (proxyUplink / sinceLastQueryInSeconds).toLong()
            val uidSpeed = ((displayUp - lastDisplayUp) / sinceLastQueryInSeconds).toLong()
            maxOf(coreSpeed, uidSpeed).coerceAtLeast(0L)
        } else 0L

        lastDisplayDown = displayDown
        lastDisplayUp = displayUp
        lastQueryTime = queryTime
        return TrafficSnapshot(displayDown, displayUp, speedDown, speedUp, queryTime)
    }

    fun currentSessionTrafficBytes(): Long {
        val snap = sampleSessionTrafficSnapshot()
        return (snap.displayDown + snap.displayUp).coerceAtLeast(0L)
    }

    data class TrafficSnapshot(
        val displayDown: Long,
        val displayUp: Long,
        val speedDown: Long,
        val speedUp: Long,
        val queryTime: Long,
    )

    /**
     * Updates the speed and session volume once in the notification.
     */
    private fun updateSpeedNotificationOnce() {
        val service = getService() ?: return
        if (System.currentTimeMillis() - lastQueryTime < 1000L) {
            return
        }
        val snap = sampleSessionTrafficSnapshot()
        val displayDown = snap.displayDown
        val displayUp = snap.displayUp
        val speedDown = snap.speedDown
        val speedUp = snap.speedUp

        val isPersian = isPersianLocale(service)
        val totalSessionBytes = displayDown + displayUp
        val totalTrafficStr = formatBytes(totalSessionBytes)
        val downSpeedStr = formatSpeed(speedDown)
        val upSpeedStr = formatSpeed(speedUp)

        val title = if (isPersian) "⚡ HotaruNet | متصل" else "⚡ HotaruNet | Connected"
        val collapsedText = if (isPersian) {
            "🚀 سرعت: ↓ $downSpeedStr  ↑ $upSpeedStr  |  📊 حجم: $totalTrafficStr"
        } else {
            "🚀 Speed: ↓ $downSpeedStr  ↑ $upSpeedStr  |  📊 Data: $totalTrafficStr"
        }

        val durationSec = if (sessionStartTimeMs > 0) (SystemClock.elapsedRealtime() - sessionStartTimeMs) / 1000 else 0L
        val durationStr = String.format(Locale.US, "%02d:%02d:%02d", durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60)
        val ping = CoreServiceManager.currentLivePingMs

        val expandedText = if (isPersian) {
            val serverLine = if (ping > 0) "⚡ مسیر خودکار هوشمند فعال است (پینگ: ${ping}ms)" else "⚡ مسیر خودکار هوشمند فعال است"
            "$serverLine\n🚀 سرعت مصرفی لحظه‌ای:  ↓ $downSpeedStr  •  ↑ $upSpeedStr\n📊 حجم کل مصرف شده: $totalTrafficStr  •  ⏱ مدت زمان: $durationStr\n🛡 اتصال پایدار و محافظت‌شده • بدون قطعی"
        } else {
            val serverLine = if (ping > 0) "⚡ Smart Auto-Pilot Route Active (Ping: ${ping}ms)" else "⚡ Smart Auto-Pilot Route Active"
            "$serverLine\n🚀 Live Speed:  ↓ $downSpeedStr  •  ↑ $upSpeedStr\n📊 Session Data Consumed: $totalTrafficStr  •  ⏱ Duration: $durationStr\n🛡 Stable Connection • 5-Rule Shield Active"
        }

        mBuilder?.let { builder ->
            builder.setContentTitle(title)
            builder.setContentText(collapsedText)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            builder.setSmallIcon(R.drawable.ic_stat_hotaru)
            val largeIcon = getHotaruNotificationLargeIcon(service)
            if (largeIcon != null) {
                builder.setLargeIcon(largeIcon)
            }
            builder.setColor(0xFF00E5FF.toInt())
            getNotificationManager()?.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return "${formatBytes(bytesPerSec)}/s"
    }

    private fun isPersianLocale(context: Context): Boolean {
        val lang = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE)
        if (lang == "fa") return true
        if (lang == "en") return false
        return context.resources.configuration.locales.get(0)?.language == "fa"
    }

    /**
     * Generates a circular Cyber-Anime Hotaru icon bitmap for the notification header.
     */
    private fun getHotaruNotificationLargeIcon(context: Context): Bitmap? {
        if (cachedLargeIcon != null && !cachedLargeIcon!!.isRecycled) {
            return cachedLargeIcon
        }
        return try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
                ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                val density = context.resources.displayMetrics.density
                val size = (64 * density).toInt().coerceAtLeast(128)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                cachedLargeIcon = bitmap
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to create Hotaru notification large icon", e)
            null
        }
    }

    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
