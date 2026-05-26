# flipx

Hinge → broadcast bridge for the Anbernic RG Rotate. Listens to the device's `gpio-keys` Hall-sensor scancodes on `/dev/input/event2` via Shizuku and fires `flipx.HINGE_OPEN` / `flipx.HINGE_CLOSE` broadcasts that MacroDroid can subscribe to.

See [`hinge-findings.md`](./hinge-findings.md) for the reverse-engineering trail.

## Architecture

```
[hinge Hall sensor]
        │
        ▼  (gpio-keys → KEY_F12 press/release, KEY_F9 wake-press)
/dev/input/event2
        │
        ▼  (getevent, running as shell uid via Shizuku UserService)
HingeUserService
        │
        ▼  (Runtime.exec "am broadcast -a flipx.HINGE_{OPEN,CLOSE}")
[Android broadcast]
        │
        ▼  (Intent Received trigger)
MacroDroid
```

## Build

Open the repo root in Android Studio and let it sync. On first sync Studio will fetch the Gradle 8.7 wrapper jar.

Alternatively from the CLI (after first `gradle wrapper` to generate the jar):

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Runtime setup on the device

1. **Install Shizuku.** Play Store or [shizuku.rikka.app](https://shizuku.rikka.app).
2. **Start Shizuku.** Non-rooted: pair via Wireless Debugging (Shizuku's own UI walks you through it). After first start, grant Shizuku `WRITE_SECURE_SETTINGS` via ADB so it autostarts at boot:
   ```
   adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS
   ```
3. **Install flipx**, open it, tap **Bind / Request**. Grant the Shizuku permission prompt.
4. **Flip the hinge.** "Last event" in the UI should update; `adb logcat -s FlipxHinge` should show `fired flipx.HINGE_OPEN` / `_CLOSE`.

## MacroDroid integration

Create two macros:

| Trigger (Intent Received) | Action |
|---------------------------|--------|
| Action: `flipx.HINGE_OPEN`  | Launch app: ES-DE |
| Action: `flipx.HINGE_CLOSE` | Launch app: (default Android launcher) |

## Notes / gotchas

- The hinge holds `KEY_F12` *down* the entire time it's closed. We map `down → CLOSE`, `up → OPEN`. The extra `KEY_F9` press on wake-from-sleep also fires `OPEN`.
- `/dev/input/event2` is on a `gpio-keys` driver shared with volume rocker + power button. The watcher filters to F12/F9 scancodes only.
- Shizuku must be running for the watcher to be active. The BootReceiver polls for Shizuku for ~30s after boot.
- `am broadcast` from the shell uid reaches MacroDroid's dynamically-registered intent receiver — no special manifest declaration needed in MacroDroid.
