package com.flipx.hinge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Catches explicit broadcasts from the Shizuku UserService and persists hinge state.
 *
 * Why a BroadcastReceiver instead of a Service: Android 12+ blocks shell uid from
 * starting services in apps that have been in the background more than a minute or so
 * ("Error: app is in background uid ..."). Broadcasts to manifest receivers do NOT
 * trigger that restriction — they're delivered to the receiver, which runs briefly
 * even in background apps. That's enough to flip a SharedPreferences bit.
 */
class HingeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HingeUserService.ACTION_OPEN -> {
                Prefs.setHingeOpen(context, true)
                Log.i(TAG, "state := open via receiver")
            }
            HingeUserService.ACTION_CLOSE -> {
                Prefs.setHingeOpen(context, false)
                Log.i(TAG, "state := closed via receiver")
            }
            else -> Log.w(TAG, "unknown action: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "FlipxHinge"
    }
}
