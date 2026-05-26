package com.flipx.hinge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * One-shot service flipx's main process exposes for the UserService (shell uid)
 * to push hinge state changes via `am startservice -n com.flipx.hinge/.StateUpdateService -a <action>`.
 *
 * Runs in flipx's main process, so SharedPreferences writes are direct.
 * Replaces both the earlier broadcast-and-manifest-receiver approach (throttled by Android 8+)
 * and the ContentProvider approach (the `content`/`cmd content` shell tools are stripped
 * on this Unisoc Android 12 build).
 */
class StateUpdateService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            HingeUserService.ACTION_OPEN -> {
                Prefs.setHingeOpen(this, true)
                Log.i(TAG, "state := open via service")
            }
            HingeUserService.ACTION_CLOSE -> {
                Prefs.setHingeOpen(this, false)
                Log.i(TAG, "state := closed via service")
            }
            else -> Log.w(TAG, "unknown action: ${intent?.action}")
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FlipxHinge"
    }
}
