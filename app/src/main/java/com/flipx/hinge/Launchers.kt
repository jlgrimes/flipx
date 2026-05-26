package com.flipx.hinge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class LauncherInfo(val pkg: String, val label: String)

object Launchers {
    /** All installed apps that declare an activity with category.HOME. */
    fun listHomeApps(ctx: Context): List<LauncherInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = ctx.packageManager
        val resolves = pm.queryIntentActivities(intent, 0)
        return resolves
            .asSequence()
            .map { LauncherInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .filter { it.pkg != "android" } // hide the system resolver shim
            .filter { it.pkg != ctx.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun labelFor(ctx: Context, pkg: String): String {
        if (pkg.isEmpty()) return "(not set)"
        return try {
            ctx.packageManager.getApplicationLabel(
                ctx.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            "$pkg (not installed)"
        }
    }
}
