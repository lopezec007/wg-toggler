package net.lopezec.wgtoggler

import android.content.Context
import android.content.SharedPreferences

/** Tiny wrapper around SharedPreferences holding the three things the app needs to know. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("wg_toggler", Context.MODE_PRIVATE)

    /** Tunnel name exactly as shown in the WireGuard app. */
    var tunnelName: String
        get() = sp.getString(KEY_TUNNEL, "") ?: ""
        set(v) = sp.edit().putString(KEY_TUNNEL, v.trim()).apply()

    /** SSIDs (one per line in the UI) on which the tunnel should be DOWN. */
    var homeSsids: Set<String>
        get() = sp.getStringSet(KEY_SSIDS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_SSIDS, v.map { it.trim() }.filter { it.isNotEmpty() }.toSet()).apply()

    /** Whether the background watcher should run. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Last state we told WireGuard to switch to, for the status line. */
    var lastAction: String
        get() = sp.getString(KEY_LAST, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST, v).apply()

    fun isHome(ssid: String?): Boolean = ssid != null && ssid in homeSsids

    companion object {
        private const val KEY_TUNNEL = "tunnel"
        private const val KEY_SSIDS = "home_ssids"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST = "last_action"
    }
}
