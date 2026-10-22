package net.lopezec.wgtoggler

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var tunnelField: EditText
    private lateinit var ssidField: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        tunnelField = findViewById(R.id.tunnel_name)
        ssidField = findViewById(R.id.home_ssids)
        enabledSwitch = findViewById(R.id.enabled)
        status = findViewById(R.id.status)

        tunnelField.setText(prefs.tunnelName)
        ssidField.setText(prefs.homeSsids.sorted().joinToString("\n"))
        enabledSwitch.isChecked = prefs.enabled

        findViewById<Button>(R.id.grant).setOnClickListener { requestPermissions() }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.open_wireguard).setOnClickListener { openWireGuard() }
        findViewById<Button>(R.id.wg_battery).setOnClickListener { openWireGuardAppInfo() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun save() {
        prefs.tunnelName = tunnelField.text.toString()
        prefs.homeSsids = ssidField.text.toString().lines().toSet()
        val wantEnabled = enabledSwitch.isChecked

        if (wantEnabled && !hasFineLocation()) {
            enabledSwitch.isChecked = false
            prefs.enabled = false
            Toast.makeText(this, R.string.toast_need_location, Toast.LENGTH_LONG).show()
            requestPermissions()
            return
        }
        if (wantEnabled && !WireGuard.hasPermission(this)) {
            enabledSwitch.isChecked = false
            prefs.enabled = false
            Toast.makeText(this, R.string.toast_need_wg_permission, Toast.LENGTH_LONG).show()
            requestPermissions()
            return
        }
        if (wantEnabled && prefs.tunnelName.isEmpty()) {
            enabledSwitch.isChecked = false
            prefs.enabled = false
            Toast.makeText(this, R.string.toast_need_tunnel, Toast.LENGTH_LONG).show()
            return
        }

        prefs.enabled = wantEnabled
        if (wantEnabled) {
            WifiWatcherService.start(this, reevaluate = true)
        } else {
            WifiWatcherService.stop(this)
        }
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun hasFineLocation() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocation() =
        checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasNotifications() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Android requires foreground location to be granted before background location can be
     * requested, so this is done in two steps across repeated taps of the button.
     */
    private fun requestPermissions() {
        val wanted = mutableListOf<String>()
        if (!hasFineLocation()) wanted += Manifest.permission.ACCESS_FINE_LOCATION
        if (!hasNotifications()) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (!WireGuard.hasPermission(this)) wanted += WireGuard.PERMISSION
        when {
            wanted.isNotEmpty() -> requestPermissions(wanted.toTypedArray(), REQ_FOREGROUND)
            !hasBackgroundLocation() -> requestPermissions(
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND
            )
            else -> Toast.makeText(this, R.string.toast_all_granted, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_FOREGROUND && hasFineLocation() && !hasBackgroundLocation()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND)
        }
        refreshStatus()
    }

    private fun openWireGuard() {
        val launch = packageManager.getLaunchIntentForPackage(WireGuard.PACKAGE)
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wireguard.com/install/"))
            )
        }
    }

    /** Opens WireGuard's app-info page, where Battery -> Unrestricted lifts the background limit. */
    private fun openWireGuardAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + WireGuard.PACKAGE))
        )
    }

    private fun refreshStatus() {
        val lines = mutableListOf<String>()
        lines += getString(
            if (WireGuard.isInstalled(this)) R.string.status_wg_found else R.string.status_wg_missing
        )
        if (WireGuard.isInstalled(this)) {
            lines += getString(
                if (WireGuard.isBatteryExempt(this)) R.string.status_battery_ok else R.string.status_battery_missing
            )
        }
        if (WireGuard.isPermissionDefined(this)) {
            lines += getString(
                if (WireGuard.hasPermission(this)) R.string.status_wgperm_ok else R.string.status_wgperm_missing
            )
        } else if (WireGuard.isInstalled(this)) {
            lines += getString(R.string.status_wgperm_legacy)
        }
        lines += getString(
            if (hasFineLocation()) R.string.status_loc_ok else R.string.status_loc_missing
        )
        lines += getString(
            if (hasBackgroundLocation()) R.string.status_bg_ok else R.string.status_bg_missing
        )
        if (!isLocationEnabled()) lines += getString(R.string.status_loc_off)
        lines += getString(if (prefs.enabled) R.string.status_running else R.string.status_stopped)
        if (prefs.lastAction.isNotEmpty()) lines += getString(R.string.status_last, prefs.lastAction)
        status.text = lines.joinToString("\n")
    }

    private fun isLocationEnabled(): Boolean =
        getSystemService(android.location.LocationManager::class.java).isLocationEnabled

    companion object {
        private const val REQ_FOREGROUND = 1
        private const val REQ_BACKGROUND = 2
    }
}
