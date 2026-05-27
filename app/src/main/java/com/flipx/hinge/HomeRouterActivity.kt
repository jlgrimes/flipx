package com.flipx.hinge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

class HomeRouterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        route()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        route()
    }

    private fun route() {
        val open = Prefs.isHingeOpen(this)
        val target = if (open) Prefs.openLauncher(this) else Prefs.closeLauncher(this)
        Log.i(TAG, "route: hingeOpen=$open target='$target'")

        val launched = if (target.isNotEmpty() && target != packageName) {
            launchTarget(target)
        } else false

        if (!launched) {
            // No valid target — drop the user into our settings UI so they can pick one.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        overridePendingTransition(0, 0)
        finishAndRemoveTask()
    }

    private fun launchTarget(pkg: String): Boolean {
        // Prefer the LAUNCHER intent — getLaunchIntentForPackage returns the activity's
        // standard launcher entry (e.g. ES-DE's MainActivity, not MainActivityHomeApp).
        // The HOME variant gives proper home-stack placement BUT reloads on every
        // re-launch due to its launchMode, which makes flipping unbearable. The LAUNCHER
        // entry stays warm in its task across flips.
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        val component = launchIntent?.component
        if (component != null) {
            val svc = ShizukuBridge.service
            if (svc != null) {
                val componentStr = component.flattenToString()
                val launched = runCatching { svc.launchComponent(componentStr) }
                    .getOrDefault(false)
                if (launched) {
                    Log.i(TAG, "launched via Shizuku: $componentStr")
                    return true
                }
                Log.w(TAG, "Shizuku launch failed for $componentStr; falling back to startActivity")
            }
            return runCatching {
                startActivity(launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                ))
            }.isSuccess
        }

        // Fallback: HOME-category intent for launchers without a LAUNCHER entry.
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (homeIntent.resolveActivity(packageManager) != null) {
            return runCatching { startActivity(homeIntent) }.isSuccess
        }
        return false
    }

    companion object {
        private const val TAG = "FlipxHinge"
    }
}
