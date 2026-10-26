# TV Companion

Phone remote plus debloat tool for a TCL Google TV and a Google TV Streamer, built as three pieces:

- `agent/` - Android TV app installed on the TV **and** the Streamer. Runs a small LAN server and does the actual key presses.
- `phone/` - Android phone app. Discovers agents, shows a remote per device, switches HDMI inputs, launches apps, types text, and toggles debloat packages.
- `scripts/debloat.ps1` + `debloat/packages.json` - PC-side bulk debloat for the Streamer over `adb`.

Everything stays on your LAN. Each agent generates a random token; the phone must present it on every request.

## How control works

A normal Android TV app cannot press keys in other apps, so the agent has two tiers:

| Tier | Needs | Gives |
|------|-------|-------|
| Accessibility | Enable "TV Companion remote control" under Settings > System > Accessibility | D-pad, OK, Back, Home, Recents, power menu, typing, app launch, HDMI input switch, media keys, volume |
| ADB (optional) | Wireless debugging paired once from the agent's setup screen | Debloat (disable/restore packages), raw keycodes, fallback for everything above |

Media keys and volume work with neither tier enabled.

## Build

Requires JDK 17+ (Android Studio's bundled JDK works) and the Android SDK with platform 37.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :agent:assembleDebug :phone:assembleDebug
```

APKs land in `agent/build/outputs/apk/debug/agent-debug.apk` and `phone/build/outputs/apk/debug/phone-debug.apk`.

## Install

### Agent on the TV and on the Streamer

Install over ADB rather than through a downloader app: Android 13+ blocks the accessibility toggle for apps installed by a "restricted" source, and ADB installs are exempt.

1. On the device: Settings > System > About > tap "Android TV OS build" 7 times. Then Settings > System > Developer options > Wireless debugging > on.
2. On the PC, with the device's IP and the pairing port/code from "Pair device with pairing code":
   ```powershell
   adb pair <ip>:<pairing-port>
   adb connect <ip>:<connect-port>
   adb install -r agent\build\outputs\apk\debug\agent-debug.apk
   ```
3. Open "TV Companion Agent" from the apps row. It shows the server status, the token, and a QR code.
4. Press "Accessibility settings" and enable "TV Companion remote control". The status line on the agent screen flips to "enabled".
5. Optional ADB tier (needed for debloat): on the agent screen press "Developer options", open Wireless debugging > "Pair device with pairing code", type the port and the 6-digit code into the agent's fields, press Pair, then "Connect (auto)". If auto-connect cannot find the port, type the port shown under Wireless debugging into "Connect port" and press Connect. The agent reconnects on boot as long as Wireless debugging stays on.

If you sideloaded through a browser or file manager instead of ADB and the accessibility switch is greyed out: Settings > Apps > TV Companion Agent > three-dot menu > "Allow restricted settings".

### Phone app

Install `phone-debug.apk`, open it, tap the gear icon. Agents on the same Wi-Fi appear under "Found on this network"; tap Add and type the token from the TV screen. You can also paste the full `tvc://...` link encoded in the QR into the address field.

## Using it

- Tabs along the top switch between the TV and the Streamer.
- On the TV tab, the "Inputs" row lists HDMI ports. Long-press the one the Streamer is plugged into to mark it. A "Switch TV to Streamer and control it" button then switches input and jumps to the Streamer tab.
- "Debloat" appears on a device once its ADB tier is connected. Safe-tier packages toggle immediately; risky ones ask first. "Restore all" re-enables everything from the catalog.

## Debloat from the PC

```powershell
# dry-run table of what the catalog would touch
.\scripts\debloat.ps1 -Target 192.168.1.50:<connect-port> -Action list -Tier all

# disable the safe tier
.\scripts\debloat.ps1 -Target 192.168.1.50:<connect-port> -Action disable -Tier safe -Confirm

# put everything back
.\scripts\debloat.ps1 -Target 192.168.1.50:<connect-port> -Action restore -Tier all -Confirm

# full package dump, useful for extending debloat/packages.json
.\scripts\debloat.ps1 -Target 192.168.1.50:<connect-port> -Action dump
```

`packages.json` has three tiers. Edit it to taste; the phone app bundles the same file at build time. Package names that do not exist on the device fail harmlessly.

## Verifying on real hardware

1. `curl -H "Authorization: Bearer <token>" http://<tv-ip>:7887/info` returns JSON with `accessibilityEnabled` and `adbConnected`.
2. `/inputs` on the TCL lists the HDMI ports. `/inputs` on the Streamer is empty.
3. D-pad on the phone moves focus on the Streamer home screen.
4. Switching input from the phone changes the TCL's source.
5. After a Streamer reboot the agent's ADB status returns to "connected" within about 15 seconds.

## Known limits

- Power on/off: the "Power" button opens the device's power menu through accessibility; with the ADB tier it sends KEYCODE_POWER. Waking a sleeping TV from the phone is not possible without HDMI-CEC control, which apps cannot drive.
- If the TCL runs an Android version below 13 the D-pad uses a focus-search fallback that can miss custom views. Enabling the ADB tier on the TV fixes that.
- Some firmwares turn Wireless debugging off after a reboot; the ADB tier then needs re-enabling on the device. Navigation does not depend on it.
