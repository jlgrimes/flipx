# Anbernic RG Rotate — Hinge Signal Reverse-Engineering

**Device:** Anbernic RG Rotate (Unisoc T618, Android 12, non-rooted, ADB over USB)
**Goal:** Identify the exact signal that fires on hinge open/close so it can be subscribed to from MacroDroid.
**Date:** 2026-05-26

## TL;DR

The hinge is **a Hall-effect switch wired directly into the Linux input subsystem** as a key device — *not* a SensorManager sensor, *not* a broadcast Intent, *not* a sysfs file we need to poll.

It emits `EV_KEY` events on `deviceId=5`:

| Hinge transition          | Raw scancode | Value     | Android KEYCODE       | Notes |
|---------------------------|-------------:|----------:|-----------------------|-------|
| **Close**                 | 88           | 1 (down)  | `KEYCODE_F12`         | Held down while hinge is closed |
| **Open** (device awake)   | 88           | 0 (up)    | `KEYCODE_F12` release | Released when hinge opens |
| **Open** (device asleep)  | 67           | 1 (down)  | `KEYCODE_F8`          | Configured as a wake-key; fires only on wake-from-sleep |

State model: `KEY_F12` is held down for the entire duration the hinge is closed and released when opened. An additional `KEY_F8` press is emitted on wake-from-sleep to bring the device out of suspend.

The `RGSettings` system app (`com.anbanic.rgsettings`) is the OEM consumer of this signal; the `KeyCombinationManager` is configured to *ignore* `KEYCODE_F12` so the OS itself doesn't react.

## Investigation log

### Step 1 — Identify the OEM Settings package

```
adb shell pm list packages -f | grep -iE 'rotate|anbernic|hinge|slide|swivel|setting'
```

→ `package:/system/priv-app/RGSettings/RGSettings.apk=com.anbanic.rgsettings`

Note: vendor namespace is `anbanic` (sic — likely typo of "anbernic"), not `anbernic`.

### Step 2 — Manifest dump

```
adb shell dumpsys package com.anbanic.rgsettings
```

Only statically-declared receivers are AndroidX `ProfileInstaller` boilerplate. No hinge-related actions in the manifest. **Inconclusive on its own**, but two hints:

- App declares `com.anbanic.rgsettings.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — AndroidX auto-generates this only when the app registers receivers at runtime via `Context.registerReceiver(...)`. So *if* there were a broadcast, it'd be runtime-registered and invisible to static analysis.
- Runs as `uid 1000` (system), shared with `com.ylm.launcher`, `com.ylm.game`, `com.ylm.setupwizard`. "ylm" appears to be the ODM behind the device.

### Step 3a — Sensor enumeration

```
adb shell dumpsys sensorservice
```

Standard Spreadtrum/Unisoc sensor catalog only. Vendor-custom sensors are gesture sensors (`com.spreadtrum.shake`, `tap`, `flip`, `face_up_down`, etc.) — **no `TYPE_HINGE_ANGLE` (36), no Anbernic-custom sensor, nothing posture-related.** `RGSettings` is not an active sensor client. → Rules out the SensorManager path entirely.

### Step 3b — Logcat across timed hinge flips

Script:

```
adb logcat -c
adb logcat -v threadtime > /tmp/hinge.log &
LOGPID=$!
sleep 3
echo ">>> CLOSE" && sleep 4
echo ">>> OPEN"  && sleep 4
echo ">>> CLOSE" && sleep 4
echo ">>> OPEN"  && sleep 4
kill $LOGPID
```

Bursts at exactly the expected timestamps (much larger on OPEN since the screen turns on). Filtering for `InputReader: processEventsLocked.*deviceId=5`:

```
16:03:56.603  type=1 Count=2 code=88 value=1 deviceId=5   ← CLOSE #1
16:04:00.143  type=1 Count=5 code=67 value=1 deviceId=5   ← OPEN  #1 (from sleep)
16:04:04.557  type=1 Count=2 code=88 value=1 deviceId=5   ← CLOSE #2
16:04:08.144  type=1 Count=2 code=88 value=0 deviceId=5   ← OPEN  #2 (already awake)
```

Each event is followed by:

```
PowerManagerService: ...reason=WAKE_REASON_WAKE_KEY, details=android.policy:KEY
KeyCombinationManager: interceptKey KEYCODE_F12 ignore
WindowManager: interceptKeyBeforeQueueing KeyEvent.KEYCODE_F8 ...
```

confirming the input subsystem is the source and `KEYCODE_F12` / `KEYCODE_F8` are the Android-level mappings.

Linux input event reference:
- `type=1` = `EV_KEY`
- raw scancode 88 = `KEY_F12`
- raw scancode 67 = `KEY_F9` (remapped by this device's `.kl` file to Android `KEYCODE_F8`)

### Step 4 — Confirm input device identity

```
adb shell getevent -lp
```

```
add device 5: /dev/input/event2
  name:     "gpio-keys"
  events:
    KEY (0001): KEY_F9   KEY_F12   KEY_VOLUMEDOWN   KEY_VOLUMEUP   KEY_POWER
  input props:
    <none>
```

Confirmed: `deviceId=5` = `/dev/input/event2`, kernel driver `gpio-keys`. The same driver multiplexes the **hinge Hall sensor**, the **volume rocker**, and the **power button** — all wired as GPIO-backed key inputs. The hinge contributes two of the five declared keys (`KEY_F9` and `KEY_F12`), so any event on event2 with those scancodes is unambiguously a hinge transition — no other source on this device can produce them.

## MacroDroid integration — candidate paths

### Path A — Direct "Hardware Button" trigger ❌ unlikely to work
MacroDroid (no root) reliably catches **only volume buttons and media/headphone buttons** via its Accessibility service. Non-standard keycodes like `KEYCODE_F8` / `KEYCODE_F12` are generally **not** delivered to user-space accessibility services on Android — the framework filters `onKeyEvent` to a small set. MacroDroid Helper (installed via ADB with `WRITE_SECURE_SETTINGS`) exposes a "Button Press" trigger on some devices but coverage is inconsistent and not guaranteed for arbitrary keycodes. **Path A is worth a 60-second test but should not be relied on.**

### Path B — Shizuku-launched `getevent` watcher → custom broadcast (recommended)
Run a watcher under Shizuku that streams `/dev/input/event2` and rebroadcasts hinge transitions as Intents MacroDroid can subscribe to:

```sh
getevent -l /dev/input/event2 | while read dev type code value; do
  case "$code $value" in
    "KEY_F12 DOWN")  am broadcast -a flipx.HINGE_CLOSE ;;
    "KEY_F12 UP")    am broadcast -a flipx.HINGE_OPEN  ;;
    "KEY_F9  DOWN")  am broadcast -a flipx.HINGE_OPEN  ;;  # wake-from-sleep path
  esac
done
```

MacroDroid trigger: **Intent Received** → action `flipx.HINGE_OPEN` or `flipx.HINGE_CLOSE`. Reliable; requires Shizuku to stay alive (Sui makes this survive reboots).

### Path C — Screen On / Screen Off proxy
MacroDroid's built-in `SCREEN_ON` / `SCREEN_OFF` triggers fire on every hinge flip (since the hinge wakes/sleeps the device). Easiest setup but **will also fire on power-button presses** — only acceptable if power button is never used.

## Constraints to remember

- Non-rooted device.
- Shizuku available as a non-root shell fallback.
- `KEYCODE_F12` is intentionally ignored by `KeyCombinationManager` — meaning the OS won't react to it, but a userspace listener still receives it.
- `deviceId` in `InputReader` logs is not guaranteed to match `/dev/input/eventN` numbering; verify with `getevent -lp`.
