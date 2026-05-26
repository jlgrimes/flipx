package com.flipx.hinge

import android.content.Context

object Prefs {
    private const val NAME = "flipx_prefs"
    private const val KEY_OPEN = "open_launcher_pkg"
    private const val KEY_CLOSE = "close_launcher_pkg"
    private const val KEY_HINGE_OPEN = "hinge_is_open"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun openLauncher(ctx: Context): String =
        prefs(ctx).getString(KEY_OPEN, "") ?: ""

    fun closeLauncher(ctx: Context): String =
        prefs(ctx).getString(KEY_CLOSE, "") ?: ""

    fun setOpenLauncher(ctx: Context, pkg: String) {
        prefs(ctx).edit().putString(KEY_OPEN, pkg).apply()
    }

    fun setCloseLauncher(ctx: Context, pkg: String) {
        prefs(ctx).edit().putString(KEY_CLOSE, pkg).apply()
    }

    /** Hinge state. Default true (open) until the UserService writes a transition. */
    fun isHingeOpen(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_HINGE_OPEN, true)

    fun setHingeOpen(ctx: Context, open: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_HINGE_OPEN, open).apply()
    }
}
