package com.flipx.hinge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ShizukuBridge.init(context.applicationContext)
        val h = Handler(Looper.getMainLooper())
        var attempts = 0
        val poll = object : Runnable {
            override fun run() {
                if (ShizukuBridge.isShizukuAlive()) {
                    ShizukuBridge.tryBindOrRequest()
                } else if (attempts++ < 15) {
                    h.postDelayed(this, 2000)
                }
            }
        }
        h.postDelayed(poll, 3000)
    }
}
