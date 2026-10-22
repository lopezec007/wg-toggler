# WG Toggler

A deliberately tiny Android app that switches a WireGuard tunnel **off** when the phone is
on a home Wi‑Fi network and **on** everywhere else (other Wi‑Fi, mobile data).

It does not implement WireGuard itself. It drives the official
[WireGuard Android app](https://www.wireguard.com/install/) (`com.wireguard.android`, from
F‑Droid, Play, or GitHub) through that app's remote-control broadcast intents.

Targets: GrapheneOS (Android 17 / API 37), stock Android 17 and 16. Minimum Android 10.
Developed and tested against WireGuard 1.0.20260315 on a Pixel 10 Pro XL running GrapheneOS
(Android 17), plus an API 36 emulator.

## How it works

* A foreground service registers a network callback for Wi‑Fi networks. VPN networks are
  excluded from the request, so the tunnel itself never hides the SSID.
* Whenever Wi‑Fi connects, changes or drops, the service reads the SSID
  (`WifiInfo` from the network's transport info, which needs location permission).
* Home SSID → send `SET_TUNNEL_DOWN`; any other Wi‑Fi, or no Wi‑Fi at all (mobile data,
  offline) → `SET_TUNNEL_UP`. Intents are only sent when the desired state changes.
* A boot receiver restarts the service after a reboot or app update.

No third‑party or AndroidX dependencies; the APK is just the platform SDK plus Kotlin.

## Setup on the phone

1. Install the WireGuard app and import your tunnel.
2. Install WG Toggler with `make android-install` (phone connected over adb).
3. Open WG Toggler:
   * enter the tunnel name exactly as WireGuard shows it;
   * list your home SSIDs, one per line;
   * tap **Grant permissions** and accept each prompt (see below);
   * tap **WireGuard battery settings** and set **Battery → Unrestricted** (see below);
   * turn on **Automatic toggling enabled** and tap **Save & apply**.
4. Keep the system **Location** toggle on. Android 10+ hides SSIDs from apps while it is off.
   On GrapheneOS the app's per‑app Location toggle must be on as well (it is, by default,
   once you grant the permission).

The status block at the bottom of the screen shows each of these requirements with a
✔ / ✘ mark, so if toggling stops working, open the app and look there first.

### The permissions, and why each one is needed

* **Control WireGuard tunnels** (`com.wireguard.android.permission.CONTROL_TUNNELS`).
  WireGuard 1.0.2026xxxx and later protect their control receiver with this runtime
  permission. Without it the system silently drops every broadcast this app sends.
  Older WireGuard builds do not define the permission; for those, enable
  **Settings → Allow remote control apps** inside WireGuard instead. The app detects
  which case applies and shows the matching status line.
* **Location** (`ACCESS_FINE_LOCATION`, then `ACCESS_BACKGROUND_LOCATION`). Since Android 10
  the SSID counts as location data. Choose *While using the app* on the first prompt and
  *Allow all the time* on the second. "All the time" is only needed so the service can read
  the SSID when it starts in the background after a reboot; without it the first evaluation
  after a reboot may see "SSID unavailable" and bring the tunnel UP until the next change.
* **Notifications**. The watcher is a foreground service and shows a low-priority
  persistent notification with the current network and tunnel state.

### WireGuard battery optimization (the non-obvious one)

When WireGuard receives the UP intent it starts its own `VpnService`. Android refuses to let
a background app start a service unless that app is exempt from battery optimization, and
WireGuard's own UI never asks for the exemption. The symptom is that DOWN works, UP does
nothing, and logcat shows

```
ActivityManager: Background start not allowed: service Intent { cmp=com.wireguard.android/.backend.GoBackend$VpnService } ...
```

Fix: Settings → Apps → WireGuard → Battery → **Unrestricted** (the in-app button opens that
page), or over adb:

```sh
adb shell dumpsys deviceidle whitelist +com.wireguard.android
```

### Always-on VPN settings

* **Always-on VPN** is not required once the battery exemption is in place. It only hides
  the problem above, because the system then keeps WireGuard's VPN service alive itself.
* **Block connections without VPN** must stay **off**. It would cut off all traffic on the
  home network whenever this app brings the tunnel down, which is the whole point.

## Building

Requirements: a JDK (17 or newer) and an Android SDK with platform `android-37.0`.

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
make android           # debug APK -> app/build/outputs/apk/debug/app-debug.apk
make android-install   # build and install on the phone connected over adb
make android-release   # release APK
make android-uninstall
make clean
```

`assembleRelease` is configured to sign with the debug key so it produces an installable
APK; replace the `signingConfig` in `app/build.gradle.kts` before distributing.

## Debugging

```sh
# what this app decided and sent
adb logcat -s WgToggler

# whether the system delivered each control broadcast to WireGuard, or skipped it
adb shell dumpsys activity broadcasts history | grep -A12 com.wireguard.android.action

# what WireGuard did with it
adb logcat -d | grep -E "WireGuard/GoBackend|Background start not allowed"
```

What to look for:

| Log line | Meaning |
| --- | --- |
| `Permission Denial ... requires com.wireguard.android.permission.CONTROL_TUNNELS` | Control permission not granted; tap **Grant permissions**. |
| `Background start not allowed ... GoBackend$VpnService` | WireGuard is battery-optimized; set Battery → Unrestricted. |
| `Bringing tunnel X UP` followed by `Device started` | Working. |
| Toggler logs `Wi‑Fi (SSID unavailable)` | Location permission or system Location is off. |

The persistent notification also shows the current network and the state the tunnel was set to.
