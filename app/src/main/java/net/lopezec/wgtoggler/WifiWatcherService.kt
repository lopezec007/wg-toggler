package net.lopezec.wgtoggler

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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Foreground service that watches Wi-Fi networks (VPN networks are excluded by the request,
 * so the WireGuard tunnel itself never hides the SSID). Rule:
 *   - connected to a home SSID       -> tunnel DOWN
 *   - any other Wi-Fi, or no Wi-Fi   -> tunnel UP  (covers mobile data)
 * The WireGuard intent is only sent when the desired state actually changes.
 */
class WifiWatcherService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var cm: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** null = nothing sent yet. */
    private var lastSentUp: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        cm = getSystemService(ConnectivityManager::class.java)
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.notif_waiting)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            // Typically: location permission was revoked, or the system refused a
            // background start. Nothing useful can be done without the SSID.
            Log.e(TAG, "Cannot start foreground service: $e")
            stopSelf()
            return
        }
        registerCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REEVALUATE && callback != null) {
            lastSentUp = null // force a resend with the current settings
            unregisterCallback()
            registerCallback()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterCallback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerCallback() {
        val cb = object : ConnectivityManager.NetworkCallback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) FLAG_INCLUDE_LOCATION_INFO else 0
        ) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                onWifi(currentSsid(caps))
            }

            override fun onLost(network: Network) {
                onNoWifi()
            }
        }
        callback = cb
        // NetworkRequest excludes VPN networks by default (NET_CAPABILITY_NOT_VPN).
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        // Registering fires onCapabilitiesChanged immediately for an already-connected Wi-Fi.
        cm.registerNetworkCallback(request, cb)

        // If no Wi-Fi is connected right now the callback stays silent, so check once.
        val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps == null || !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            onNoWifi()
        }
    }

    private fun unregisterCallback() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }

    private fun onWifi(ssid: String?) {
        val where = if (ssid == null) getString(R.string.notif_wifi_unknown)
        else getString(R.string.notif_wifi, ssid)
        apply(desiredUp = !prefs.isHome(ssid), where = where)
    }

    private fun onNoWifi() {
        apply(desiredUp = true, where = getString(R.string.notif_no_wifi))
    }

    private fun apply(desiredUp: Boolean, where: String) {
        val tunnel = prefs.tunnelName
        if (desiredUp != lastSentUp) {
            lastSentUp = desiredUp
            WireGuard.setTunnel(this, tunnel, desiredUp)
            prefs.lastAction = getString(
                if (desiredUp) R.string.action_up else R.string.action_down, tunnel, where
            )
            Log.i(TAG, prefs.lastAction)
        }
        updateNotification(
            getString(if (desiredUp) R.string.notif_state_up else R.string.notif_state_down, where)
        )
    }

    /** Returns the unquoted SSID, or null if not available (no permission, location off, ...). */
    @Suppress("DEPRECATION")
    private fun currentSsid(caps: NetworkCapabilities): String? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps.transportInfo as? WifiInfo
        } else {
            getSystemService(WifiManager::class.java).connectionInfo
        }
        val raw = info?.ssid ?: return null
        if (raw == WifiManager.UNKNOWN_SSID || raw.isEmpty()) return null
        return if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
            raw.substring(1, raw.length - 1)
        } else {
            raw // hex-encoded, non-UTF-8 SSID
        }
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "WgToggler"
        private const val CHANNEL_ID = "watcher"
        private const val NOTIFICATION_ID = 1
        const val ACTION_REEVALUATE = "net.lopezec.wgtoggler.REEVALUATE"

        fun start(context: Context, reevaluate: Boolean = false) {
            val i = Intent(context, WifiWatcherService::class.java)
            if (reevaluate) i.action = ACTION_REEVALUATE
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WifiWatcherService::class.java))
        }
    }
}
