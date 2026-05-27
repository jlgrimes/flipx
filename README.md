# flipx

> Two home screens for the Anbernic RG Rotate. Flip the hinge open, you land on your gaming launcher. Flip it closed, you land on your everyday launcher. Automatic.

The RG Rotate has a swivel hinge with a built-in Hall sensor. flipx listens for that sensor's state changes and routes the system home button to a different launcher depending on whether the hinge is open or closed — turning the physical flip into a one-handed gaming-vs-everyday-use toggle. Optionally also locks the screen rotation to match each hinge state.

## Requirements

- **Anbernic RG Rotate** (Android 12, Unisoc T618). Other devices won't work — flipx is hard-coded to this device's specific hinge input event.
- **No root needed.** flipx runs as the `shell` user via Shizuku.
- **[Shizuku](https://shizuku.rikka.app)** installed and started. flipx will not function without it.
- Two launchers you actually want to switch between. flipx works with any app that declares `category.HOME` in its manifest (the stock Anbernic launcher, Pixel/AOSP launcher, Niagara, Lawnchair, hype, ES-DE in launcher mode, etc.). It won't list non-launcher apps.

## Install — from scratch

### 1. Install Shizuku

Get it from the [Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or from [shizuku.rikka.app](https://shizuku.rikka.app).

### 2. Start Shizuku

Shizuku gives non-root apps shell-level access using ADB. There are two ways to start it; pick one.

**Via Wireless Debugging (no PC needed after setup):**
1. On the device: Settings → System → Developer options → enable **Wireless debugging**.
2. Open Shizuku → **Start via Wireless debugging** → follow the on-screen pairing flow.
3. To persist across reboots, grant Shizuku `WRITE_SECURE_SETTINGS` via ADB from a PC once:
   ```
   adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS
   ```
   After that, Shizuku autostarts on every boot.

**Via ADB (if you have a PC handy):**
1. Connect the device by USB, enable USB debugging.
2. Open Shizuku → **Start via ADB**, then on your PC:
   ```
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```

### 3. Install flipx

Grab the latest APK from the [Releases](https://github.com/jlgrimes/flipx/releases) page and sideload it (`adb install flipx-vX.Y.apk` or open the APK file on the device).

### 4. Configure flipx

Open flipx. The main screen is a status panel with sequential gates — work through them top to bottom.

1. **Grant Shizuku permission** — flipx will prompt; tap Grant.
2. **Bind the watcher** — tap *Start watcher*. "Watcher active: yes" appears in the status block when it's running.
3. **Pick your launchers:**
   - **On hinge OPEN, route home to:** your gaming launcher (e.g. ES-DE).
   - **On hinge CLOSE, route home to:** your everyday launcher (e.g. the stock Anbernic launcher).
   - The picker shows every installed app that declares `category.HOME`. If an app you want isn't there, it doesn't qualify as a launcher and can't be routed to.
4. **Make flipx the default home** — tap **"Make flipx the home"**. Silent (no system chooser dialog) because it goes through Shizuku.

That's it. Press home or flip the hinge — flipx routes you to the right launcher based on hinge state.

### 5. (Optional) Lock orientation to hinge state

flipx has a toggle in the **Rotation** section that hard-locks the display rotation based on hinge state:

| Toggle | Hinge close | Hinge open |
|--------|-------------|------------|
| ON  | locked landscape (rotation 1) | locked 90° CCW of close (rotation 0) |
| OFF | flipx doesn't touch rotation | flipx doesn't touch rotation |

The open rotation matches the device's physical pivoted orientation when the hinge is open. Default off — turn on if you want flipx to control rotation; leave off if you want apps + sensor to handle it.

### 6. (Important) Turn off other "launcher mode" toggles

If ES-DE has its **launcher mode** Quick Settings tile enabled, **turn it off**. flipx needs to be the only app holding the HOME role; otherwise ES-DE will re-grab it and you'll lose the routing.

## How it works

```
[hinge Hall sensor]
         │  (KEY_F12 press = closed, release = open;
         │   KEY_F9 press = wake-from-sleep open)
         ▼
/dev/input/event2  (gpio-keys, kernel input device)
         │
         ▼  (getevent, running as shell uid via Shizuku UserService)
HingeUserService
         │  Explicit broadcast: am broadcast -n com.flipx.hinge/.HingeReceiver
         ▼
HingeReceiver  →  SharedPreferences  (hinge_is_open := true/false)

When you press home (or the auto-switch fires after a flip):
         ▼
HomeRouterActivity (flipx is the HOME role holder)
         │  reads hinge state from SharedPreferences
         ▼  am start -n <target>/.MainActivity via Shizuku (shell uid)
[gaming launcher OR everyday launcher]
```

The auto-switch on flip is the same path — when the UserService catches a transition, it also checks the current foreground app (via `dumpsys window`) and, if you're already sitting on either configured launcher, fires a HOME intent so the new launcher takes over without you needing to press home manually.

Both launchers stay warm in their task stacks across flips — the second time you flip back to one, it resumes instantly instead of cold-starting.

The full reverse-engineering trail (how we figured out the hinge is wired to `KEY_F12` / `KEY_F9` on `/dev/input/event2`) is in [`hinge-findings.md`](./hinge-findings.md).

## Known limitations

**ES-DE renders at 648×720 instead of the full 720×720 panel.** The RG Rotate's Unisoc Android 12 build applies a framework-level letterbox to apps that declare `screenOrientation="userLandscape"` in their manifest (which ES-DE does), reserving 36 pixels of black on each side. This is enforced by the OEM framework overlay; no userspace lever exposed to shell uid overrides it. We tried every documented mechanism (`wm set-ignore-orientation-request`, `policy_control immersive.full=*`, per-app `am compat` overrides, alternative intent routing) — none take effect on this build. Fixing requires root + a Magisk module that patches the framework resources.

## Troubleshooting

**flipx says "Shizuku not running" but I just started it.**
Open Shizuku, confirm it shows "Shizuku is running". If it's stopped, restart it via Wireless Debugging or ADB.

**The flip works but the launcher doesn't auto-switch when I'm on a home screen.**
Check `adb logcat -s FlipxHinge`. You should see, on every flip:
```
fired flipx.HINGE_OPEN; state := open
state := open via receiver
foreground=<pkg> onLauncher=true ...
launched via Shizuku: <component>
```
If `state := ... via receiver` is missing, make sure you installed the latest APK and that the Shizuku daemon was restarted to pick up the new code (toggle Start/Stop watcher in flipx).

**I want to use a launcher that doesn't show up in the picker.**
flipx's picker only lists apps that declare `category.HOME`. Apps like ES-DE only declare it when their internal "launcher mode" is enabled — turn that on in ES-DE settings first, then reopen the picker.

**flipx grabbed my home role and now I can't get back to my old launcher.**
Open flipx, change the close-launcher pick to whatever you want, then close the hinge. Or uninstall flipx — Android falls back to the next HOME-declaring app automatically.

**ES-DE reloads every time I flip back to it.**
That shouldn't happen in v0.7+ — both launchers stay warm and resume instantly. If you're seeing reloads, you might be on an older release; pull the latest from the [Releases](https://github.com/jlgrimes/flipx/releases) page.

**Logcat for general diagnostics:**
```
adb logcat -s FlipxHinge
```
Every important transition logs from a single tag.

## Build from source

Requires Android Studio (or just the Android SDK + JDK 17 + Gradle 8.7).

```
git clone https://github.com/jlgrimes/flipx
cd flipx
# Open in Android Studio → Run, OR from CLI:
gradle assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug keystore for in-place upgrades from dev builds. For distribution beyond personal sideload, generate a dedicated release keystore and replace `signingConfig = signingConfigs.getByName("debug")` in `app/build.gradle.kts`.

## Credits

Reverse engineering by [Jared Grimes](https://github.com/jlgrimes) with Claude. The trail is in [`hinge-findings.md`](./hinge-findings.md) — useful reading if you want to do something similar on a different OEM handheld.
