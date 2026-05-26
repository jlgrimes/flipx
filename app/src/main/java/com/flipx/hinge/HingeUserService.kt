package com.flipx.hinge

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class HingeUserService : IUserService.Stub {

    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context) : this()

    @Volatile private var watcherThread: Thread? = null
    @Volatile private var watcherProcess: Process? = null
    @Volatile private var lastEvent: String = "none"
    @Volatile private var openLauncherPkg: String = ""
    @Volatile private var closeLauncherPkg: String = ""

    override fun destroy() {
        stopWatch()
    }

    override fun startWatch() {
        if (watcherThread != null) return
        val t = Thread({ runWatcher() }, "flipx-hinge-watcher")
        t.isDaemon = true
        watcherThread = t
        t.start()
        Log.i(TAG, "watcher started")
    }

    override fun stopWatch() {
        watcherProcess?.destroy()
        watcherProcess = null
        watcherThread = null
        Log.i(TAG, "watcher stopped")
    }

    override fun isRunning(): Boolean = watcherThread != null

    override fun lastEvent(): String = lastEvent

    override fun setHomeHolder(pkg: String?): Boolean {
        val target = pkg.orEmpty()
        if (target.isEmpty()) return false
        return try {
            val proc = ProcessBuilder(
                "/system/bin/cmd", "role", "add-role-holder",
                "android.app.role.HOME", target
            ).redirectErrorStream(true).start()
            proc.waitFor()
            proc.exitValue() == 0
        } catch (e: Exception) {
            Log.w(TAG, "setHomeHolder failed: ${e.message}")
            false
        }
    }

    override fun currentHomeHolder(): String {
        return try {
            val proc = ProcessBuilder(
                "/system/bin/cmd", "role", "get-role-holders",
                "android.app.role.HOME"
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out.substringAfterLast(":").trim()
        } catch (e: Exception) {
            Log.w(TAG, "currentHomeHolder failed: ${e.message}")
            ""
        }
    }

    override fun setLaunchers(openPkg: String?, closePkg: String?) {
        openLauncherPkg = openPkg.orEmpty()
        closeLauncherPkg = closePkg.orEmpty()
        Log.i(TAG, "launchers configured: open='$openLauncherPkg' close='$closeLauncherPkg'")
    }

    override fun setIgnoreOrientationRequest(ignore: Boolean): Boolean {
        return try {
            val proc = ProcessBuilder(
                "/system/bin/wm", "set-ignore-orientation-request", ignore.toString()
            ).redirectErrorStream(true).start()
            proc.waitFor()
            val ok = proc.exitValue() == 0
            if (!ok) {
                val out = proc.inputStream.bufferedReader().readText().trim()
                Log.w(TAG, "wm set-ignore-orientation-request exit=${proc.exitValue()} out=$out")
            } else {
                Log.i(TAG, "ignore-orientation-request := $ignore")
            }
            ok
        } catch (e: Exception) {
            Log.w(TAG, "setIgnoreOrientationRequest failed: ${e.message}")
            false
        }
    }

    override fun isIgnoringOrientationRequest(): Boolean {
        return try {
            val proc = ProcessBuilder(
                "/system/bin/wm", "get-ignore-orientation-request"
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            // Output looks like: "ignoreOrientationRequest true for displayId=0"
            out.contains("true", ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "isIgnoringOrientationRequest failed: ${e.message}")
            false
        }
    }

    private fun runWatcher() {
        while (watcherThread != null) {
            try {
                val proc = ProcessBuilder("/system/bin/getevent", EVENT_DEVICE)
                    .redirectErrorStream(true).start()
                watcherProcess = proc
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                reader.useLines { lines ->
                    for (line in lines) {
                        handle(line)
                        if (watcherThread == null) break
                    }
                }
                proc.waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "watcher iter failed: ${e.message}")
                Thread.sleep(2000)
            }
        }
    }

    private fun handle(rawLine: String) {
        val parts = rawLine.trim().split(Regex("\\s+"))
        if (parts.size < 3) return
        val type = parts[0].toIntOrNull(16) ?: return
        val code = parts[1].toIntOrNull(16) ?: return
        val value = parts[2].toLongOrNull(16)?.toInt() ?: return
        if (type != EV_KEY) return

        val action: String? = when (code) {
            KEY_F12 -> when (value) {
                1 -> ACTION_CLOSE
                0 -> ACTION_OPEN
                else -> null
            }
            KEY_F9 -> if (value == 1) ACTION_OPEN else null
            else -> null
        }
        if (action == null) return

        val isOpen = action == ACTION_OPEN
        lastEvent = "${System.currentTimeMillis()} $action (code=0x${code.toString(16)} v=$value)"

        writeState(isOpen)
        broadcast(action)
        maybeAutoSwitch()
        Log.i(TAG, "fired $action; state := ${if (isOpen) "open" else "closed"}")
    }

    /** If the user is currently looking at one of the configured launchers, fire a HOME
     *  intent so flipx's HomeRouterActivity re-routes to the launcher for the new state. */
    private fun maybeAutoSwitch() {
        val openPkg = openLauncherPkg
        val closePkg = closeLauncherPkg
        if (openPkg.isEmpty() && closePkg.isEmpty()) {
            Log.i(TAG, "autoSwitch skipped: no launchers configured")
            return
        }
        val foreground = currentForegroundPackage()
        if (foreground == null) {
            Log.w(TAG, "autoSwitch skipped: couldn't read foreground")
            return
        }
        val onLauncher = foreground == openPkg || foreground == closePkg || foreground == FLIPX_PKG
        Log.i(TAG, "foreground=$foreground onLauncher=$onLauncher (open=$openPkg close=$closePkg)")
        if (onLauncher) {
            goHome()
        }
    }

    private fun currentForegroundPackage(): String? = try {
        // Run dumpsys directly (no shell pipe — the shell context inside the Shizuku
        // UserService doesn't always honor pipes reliably). Grep with Kotlin regex instead.
        val proc = ProcessBuilder("/system/bin/dumpsys", "window")
            .redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        // Sample: mCurrentFocus=Window{e9c40e4 u0 com.launcher.hype/com.launcher.hype.MainActivity}
        val match = Regex("""(?:mCurrentFocus|mFocusedApp)=\S+\{[^}]*\bu\d+\s+(\S+?)/""")
            .find(out)
        if (match == null) Log.w(TAG, "foreground regex didn't match; dumpsys output begins: ${out.take(200)}")
        match?.groupValues?.get(1)
    } catch (e: Exception) {
        Log.w(TAG, "currentForegroundPackage failed: ${e.message}")
        null
    }

    private fun goHome() {
        try {
            ProcessBuilder(
                "/system/bin/am", "start",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.HOME"
            ).redirectErrorStream(true).start()
        } catch (e: Exception) {
            Log.w(TAG, "goHome failed: ${e.message}")
        }
    }

    private fun writeState(open: Boolean) {
        try {
            val action = if (open) ACTION_OPEN else ACTION_CLOSE
            val proc = ProcessBuilder(
                "/system/bin/am", "broadcast",
                "--include-stopped-packages",
                "-n", "com.flipx.hinge/.HingeReceiver",
                "-a", action
            ).redirectErrorStream(true).start()
            proc.waitFor()
            if (proc.exitValue() != 0) {
                val out = proc.inputStream.bufferedReader().readText().trim()
                Log.w(TAG, "am broadcast (state) exit=${proc.exitValue()} out=$out")
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeState failed: ${e.message}")
        }
    }

    private fun broadcast(action: String) {
        // Implicit broadcast for external listeners (MacroDroid, etc.)
        try {
            ProcessBuilder("/system/bin/am", "broadcast", "-a", action)
                .redirectErrorStream(true).start()
        } catch (e: Exception) {
            Log.w(TAG, "external broadcast failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FlipxHinge"
        private const val FLIPX_PKG = "com.flipx.hinge"

        private const val EVENT_DEVICE = "/dev/input/event2"
        private const val EV_KEY = 0x01
        private const val KEY_F12 = 0x58
        private const val KEY_F9  = 0x43

        const val ACTION_OPEN = "flipx.HINGE_OPEN"
        const val ACTION_CLOSE = "flipx.HINGE_CLOSE"
    }
}
