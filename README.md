# flipx

> Two home screens for the Anbernic RG Rotate. Flip the hinge open, you land on your gaming launcher. Flip it closed, you land on your regular launcher. Automatic.

The RG Rotate has a swivel hinge with a built-in Hall sensor. flipx listens for that sensor's state changes and routes the system home button to a different launcher depending on whether the hinge is open or closed — turning the physical flip into a one-handed gaming-vs-everyday-use toggle.

## Requirements

- **Anbernic RG Rotate** (Android 12, Unisoc T618). Other devices won't work — flipx is hard-coded to this device's specific hinge input event.
- **No root needed.** flipx runs as the `shell` user via Shizuku.
- **[Shizuku](https://shizuku.rikka.app)** installed and started. flipx will not function without it.
- Two launchers you actually want to switch between. flipx works with any app that declares `category.HOME` in its manifest (Pixel/AOSP launcher, Niagara, Lawnchair, hype, ES-DE in launcher mode, etc.). It won't list non-launcher apps.

## Install — from scratch

### 1. Install Shizuku

Get it from the [Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or from [shizuku.rikka.app](https://shizuku.rikka.app).

### 2. Start Shizuku

Shizuku gives non-root apps shell-level access using ADB. There are two ways to start it; pick one.

**Via Wireless Debugging (no PC needed after setup):**
1. On the device: Settings → System → Developer options → enable **Wireless debugging**.
2. Open Shizuku → **Start via Wireless debugging** → follow the on-screen pairing flow (Shizuku walks you through it).
3. After it starts the first time, you can persist it across reboots — grant it WRITE_SECURE_SETTINGS via ADB from a PC once:
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

Grab the latest APK from the [Releases](https://github.com/jlgrimes/flipx/releases) page on this repo and sideload it (`adb install flipx-vX.Y.apk` or open the APK file on the device).

### 4. Configure flipx

Open flipx. You'll see a status panel with several sequential gates — work through them top to bottom.

1. **Shizuku permission** — flipx will prompt; tap Grant.
2. **Bind the watcher** — tap *Start watcher*. "Watcher active: yes" appears in the status block when it's running.
3. **Pick your launchers:**
   - **On hinge OPEN, route home to:** your gaming launcher (e.g. ES-DE, or whatever you use when playing).
   - **On hinge CLOSE, route home to:** your everyday launcher (e.g. the stock Anbernic launcher, Pixel Launcher, Niagara).
   - The picker shows every installed app that declares `category.HOME`. If an app you want isn't there, it doesn't qualify as a launcher and can't be routed to.
4. **Make flipx the default home.** Tap **"Make flipx the home"**. This silently sets flipx as the system HOME role holder via Shizuku — no system chooser dialog.

That's it. Press home or flip the hinge — flipx routes you to the right launcher based on hinge state.

### 5. (Important) Turn off other "launcher mode" toggles

If ES-DE has its **launcher mode** Quick Settings tile enabled, **turn it off**. flipx needs to be the *only* app holding the HOME role; otherwise ES-DE will re-grab it and you'll lose the routing. Same for any other app that tries to be the home.

## How it works

```
[hinge Hall sensor]
         │  (KEY_F12 press = closed, release = open;
         │   KEY_F9 press = wake-from-sleep open)
         ▼
/dev/input/event2  (gpio-keys, kernel input device)
         │
         ▼  (getevent, running as shell uid via Shizuku UserService)
HingeUserService  ┐
                  │  Explicit broadcast: am broadcast -n com.flipx.hinge/.HingeReceiver
                  ▼
       HingeReceiver  →  SharedPreferences  (hinge_is_open := true/false)

When you press Home:
         ▼
HomeRouterActivity (flipx is the HOME role holder, so this is what runs)
         │  reads hinge state from SharedPreferences
         ▼  fires HOME intent at the configured target launcher
[your gaming launcher OR your everyday launcher]
```

The auto-switch on flip is the same path — when the UserService catches a transition, it also checks the current foreground app (via `dumpsys window`) and if you're sitting on either of the configured launchers, it fires a HOME intent so the new launcher takes over without you needing to press home manually.

The detailed reverse-engineering trail (how we figured out the hinge is wired to `KEY_F12` / `KEY_F9` on `/dev/input/event2`) is in [`hinge-findings.md`](./hinge-findings.md).

## Troubleshooting

**flipx says "Shizuku not running" but I just started it.**
Open Shizuku, confirm it shows "Shizuku is running". If it's stopped, restart it via Wireless Debugging or ADB.

**The flip works but the launcher doesn't auto-switch when I'm on a home screen.**
Check `adb logcat -s FlipxHinge`. You should see, on every flip:
```
fired flipx.HINGE_OPEN; state := open
state := open via receiver
foreground=<pkg> onLauncher=true ...
```
If `state := ... via receiver` is missing, the receiver isn't being invoked — make sure you installed the latest APK (we hit several flavors of Android 12 IPC restrictions and the current version uses explicit broadcasts to a manifest receiver).

**I want to use a launcher that doesn't show up in the picker.**
flipx's picker only lists apps that declare `category.HOME`. Apps like ES-DE only declare it when their internal "launcher mode" is enabled — turn that on in ES-DE settings first, then reopen the picker.

**flipx grabbed my home role and now I can't get back to my old launcher.**
Open flipx, change the close-launcher pick to whatever you want, then close the hinge — flipx will route you there. Or uninstall flipx (Android will fall back to the next HOME-declaring app automatically).

**Logcat for diagnostics:**
```
adb logcat -s FlipxHinge
```
Every important transition logs from a single tag.

## Build from source

Requires Android Studio (or just the Android SDK + a JDK 17 + Gradle 8.7).

```
git clone https://github.com/jlgrimes/flipx
cd flipx
# Open in Android Studio → Run, OR from CLI:
gradle assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug keystore for in-place upgrades from dev builds. For distribution to others, generate a dedicated keystore and replace `signingConfig = signingConfigs.getByName("debug")` in `app/build.gradle.kts`.

## Credits

Reverse engineering by [Jared Grimes](https://github.com/jlgrimes) with Claude. The trail is documented in [`hinge-findings.md`](./hinge-findings.md) — useful reading if you want to do something similar on a different OEM handheld.
