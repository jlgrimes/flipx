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

    private fun runWatcher() {
        while (watcherThread != null) {
            try {
                val proc = ProcessBuilder("/system/bin/getevent", EVENT_DEVICE)
                    .redirectErrorStream(true)
                    .start()
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
        // getevent raw output: "0001 0058 00000001" (type code value, hex)
        val parts = rawLine.trim().split(Regex("\\s+"))
        if (parts.size < 3) return
        val type = parts[0].toIntOrNull(16) ?: return
        val code = parts[1].toIntOrNull(16) ?: return
        val value = parts[2].toLongOrNull(16)?.toInt() ?: return
        if (type != EV_KEY) return

        val action: String? = when (code) {
            KEY_F12 -> when (value) {
                1 -> ACTION_CLOSE   // hinge closed (press)
                0 -> ACTION_OPEN    // hinge opened (release)
                else -> null
            }
            KEY_F9 -> if (value == 1) ACTION_OPEN else null  // wake-from-sleep open
            else -> null
        }

        if (action != null) {
            lastEvent = "${System.currentTimeMillis()} $action (code=0x${code.toString(16)} v=$value)"
            broadcast(action)
            Log.i(TAG, "fired $action")
        }
    }

    private fun broadcast(action: String) {
        try {
            ProcessBuilder("/system/bin/am", "broadcast", "-a", action)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            Log.w(TAG, "broadcast failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FlipxHinge"

        private const val EVENT_DEVICE = "/dev/input/event2"
        private const val EV_KEY = 0x01
        private const val KEY_F12 = 0x58   // Linux scancode 88
        private const val KEY_F9  = 0x43   // Linux scancode 67

        const val ACTION_OPEN = "flipx.HINGE_OPEN"
        const val ACTION_CLOSE = "flipx.HINGE_CLOSE"
    }
}
