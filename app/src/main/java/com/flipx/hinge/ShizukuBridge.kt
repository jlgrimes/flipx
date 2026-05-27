package com.flipx.hinge

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuBridge {

    private const val TAG = "FlipxHinge"
    private const val PERM_REQ_CODE = 1001

    // Bump on every UserService code change — Shizuku restarts the daemon when the
    // version increments (otherwise it keeps the old loaded class running).
    private const val SERVICE_VERSION = 12

    @Volatile var service: IUserService? = null
        private set

    @Volatile private var appCtx: Context? = null

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                "com.flipx.hinge",
                HingeUserService::class.java.name
            )
        )
            .daemon(true)
            .processNameSuffix("hinge_watcher")
            .debuggable(false)
            .version(SERVICE_VERSION)
    }

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                val svc = IUserService.Stub.asInterface(binder)
                service = svc
                Log.i(TAG, "UserService connected")
                runCatching { svc.startWatch() }
                    .onFailure { Log.w(TAG, "startWatch err: ${it.message}") }
                // Sync the user's launcher picks into the daemon's in-memory fields
                // so it can detect "are we on a configured launcher right now."
                appCtx?.let { ctx ->
                    runCatching {
                        svc.setLaunchers(Prefs.openLauncher(ctx), Prefs.closeLauncher(ctx))
                    }.onFailure { Log.w(TAG, "setLaunchers err: ${it.message}") }
                    // Reapply the orientation-lock preference — wm flag resets across reboots
                    runCatching {
                        svc.setOrientationLock(Prefs.orientationLock(ctx))
                    }.onFailure { Log.w(TAG, "setOrientationLock err: ${it.message}") }
                    runCatching {
                        svc.setForceFullscreen(Prefs.forceFullscreen(ctx))
                    }.onFailure { Log.w(TAG, "setForceFullscreen err: ${it.message}") }
                }
                listener?.onConnected()
            } else {
                Log.w(TAG, "UserService binder invalid")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            Log.i(TAG, "UserService disconnected")
            listener?.onDisconnected()
        }
    }

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onPermissionResult(granted: Boolean)
    }

    @Volatile var listener: Listener? = null

    private val permListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == PERM_REQ_CODE) {
            val granted = result == PackageManager.PERMISSION_GRANTED
            listener?.onPermissionResult(granted)
            if (granted) bind()
        }
    }

    fun init(context: Context) {
        appCtx = context.applicationContext
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky { tryBindOrRequest() }
        Shizuku.addBinderDeadListener {
            service = null
            listener?.onDisconnected()
        }
    }

    fun tryBindOrRequest() {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku not running")
            return
        }
        if (hasPermission()) bind() else Shizuku.requestPermission(PERM_REQ_CODE)
    }

    fun hasPermission(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun isShizukuAlive(): Boolean = Shizuku.pingBinder()

    /** Push launcher picks to the running daemon, e.g. after the user changes them. */
    fun pushLaunchers(ctx: Context) {
        val svc = service ?: return
        runCatching {
            svc.setLaunchers(Prefs.openLauncher(ctx), Prefs.closeLauncher(ctx))
        }.onFailure { Log.w(TAG, "pushLaunchers err: ${it.message}") }
    }

    private fun bind() {
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Throwable) {
            Log.e(TAG, "bindUserService failed", e)
        }
    }

    fun unbind() {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
    }
}
