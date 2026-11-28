package com.v2ray.ang.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.HotaruSmartEngine
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.SoftReference

class QSTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectJob: Job? = null

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    private fun isPersian(): Boolean {
        val lang = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE)
        if (lang == "fa") return true
        if (lang == "en") return false
        return resources.configuration.locales.get(0)?.language == "fa"
    }

    fun setState(state: Int, connecting: Boolean = false) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_stat_hotaru)
        tile.label = getString(R.string.app_name)
        val fa = isPersian()
        if (connecting) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (fa) "در حال اتصال..." else "Optimizing..."
            }
        } else if (state == Tile.STATE_INACTIVE) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (fa) "آماده اتصال" else "Tap to connect"
            }
        } else if (state == Tile.STATE_ACTIVE) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (fa) "متصل" else "Connected"
            }
        }
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (CoreServiceManager.isRunning()) {
            setState(Tile.STATE_ACTIVE)
        } else if (connectJob?.isActive == true) {
            setState(Tile.STATE_ACTIVE, connecting = true)
        } else {
            setState(Tile.STATE_INACTIVE)
        }
        mMsgReceive = ReceiveMessageHandler(this)
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(applicationContext, mMsgReceive, mFilter, Utils.receiverFlags())
        MessageHelper.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            applicationContext.unregisterReceiver(mMsgReceive)
            mMsgReceive = null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unregister receiver", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun openMainForPermission() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    override fun onClick() {
        super.onClick()
        if (CoreServiceManager.isRunning() || connectJob?.isActive == true) {
            connectJob?.cancel()
            connectJob = null
            LauncherManager.stopService(this)
            setState(Tile.STATE_INACTIVE)
            return
        }

        if (SettingsManager.isVpnMode() && VpnService.prepare(this) != null) {
            openMainForPermission()
            return
        }

        setState(Tile.STATE_ACTIVE, connecting = true)
        val appCtx = applicationContext
        connectJob = tileScope.launch {
            try {
                SettingsManager.initApp(appCtx)
                val picked = HotaruSmartEngine.selectBestServer(appCtx, forceRescan = false)
                if (picked != null && picked.delayMs > 0L) {
                    LauncherManager.startService(appCtx, picked.guid)
                } else {
                    setState(Tile.STATE_INACTIVE)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "QSTile smart connect failed", e)
                setState(Tile.STATE_INACTIVE)
            }
        }
    }

    private var mMsgReceive: BroadcastReceiver? = null

    private class ReceiveMessageHandler(context: QSTileService) : BroadcastReceiver() {
        var mReference: SoftReference<QSTileService> = SoftReference(context)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val context = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    context?.connectJob = null
                    context?.setState(Tile.STATE_ACTIVE)
                }
                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_START_FAILURE,
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    context?.connectJob = null
                    context?.setState(Tile.STATE_INACTIVE)
                }
            }
        }
    }
}
