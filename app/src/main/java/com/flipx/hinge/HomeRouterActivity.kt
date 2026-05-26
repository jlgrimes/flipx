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
        // Prefer HOME-category intent (engages the launcher's home behavior, not just app launch)
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (homeIntent.resolveActivity(packageManager) != null) {
            return runCatching { startActivity(homeIntent) }.isSuccess
        }
        // Fallback: regular launcher intent (covers apps like ES-DE if their HOME activity is
        // currently disabled, or apps that aren't HOME-declared at all)
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (launchIntent != null) {
            return runCatching { startActivity(launchIntent) }.isSuccess
        }
        return false
    }

    companion object {
        private const val TAG = "FlipxHinge"
    }
}
