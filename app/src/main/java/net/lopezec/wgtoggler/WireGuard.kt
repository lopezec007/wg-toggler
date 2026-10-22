package net.lopezec.wgtoggler

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log

/**
 * Drives the official WireGuard Android app (com.wireguard.android) through its
 * remote-control broadcast intents. The user must enable
 * "Allow remote control apps" in WireGuard's settings for these to be honoured.
 */
object WireGuard {
    const val PACKAGE = "com.wireguard.android"
    /** Runtime permission guarding the control receiver in recent WireGuard builds. */
    const val PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS"
    private const val ACTION_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
    private const val ACTION_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
    private const val TAG = "WgToggler"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** False on older WireGuard builds that use the "Allow remote control apps" setting instead. */
    fun isPermissionDefined(context: Context): Boolean = try {
        context.packageManager.getPermissionInfo(PERMISSION, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasPermission(context: Context): Boolean =
        !isPermissionDefined(context) ||
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Android refuses to let a background app start a service ("Background start not allowed")
     * unless it is exempt from battery optimization. WireGuard starts its VpnService that way
     * when it receives our UP intent, so without the exemption the tunnel silently stays down
     * (unless always-on VPN keeps the service alive).
     */
    fun isBatteryExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(PACKAGE)

    fun setTunnel(context: Context, tunnel: String, up: Boolean) {
        if (tunnel.isBlank()) return
        val intent = Intent(if (up) ACTION_UP else ACTION_DOWN)
            .setPackage(PACKAGE)
            .putExtra("tunnel", tunnel)
        Log.i(TAG, "Sending ${if (up) "UP" else "DOWN"} for tunnel '$tunnel'")
        context.sendBroadcast(intent)
    }
}
