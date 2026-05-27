package com.flipx.hinge

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.flipx.hinge.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ShizukuBridge.Listener {

    private lateinit var binding: ActivityMainBinding
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ShizukuBridge.listener = this
        ShizukuBridge.init(applicationContext)

        binding.btnStop.setOnClickListener { ShizukuBridge.unbind() }
        binding.txtLauncherOpen.setOnClickListener { pickLauncher(true) }
        binding.txtLauncherClose.setOnClickListener { pickLauncher(false) }
        binding.btnMakeHome.setOnClickListener { makeFlipxHome() }

        binding.swForceRotation.isChecked = Prefs.orientationLock(this)
        binding.swForceRotation.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setOrientationLock(this, isChecked)
            applyOrientationLock(isChecked)
        }

        binding.swForceFullscreen.isChecked = Prefs.forceFullscreen(this)
        binding.swForceFullscreen.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setForceFullscreen(this, isChecked)
            applyForceFullscreen(isChecked)
        }
    }

    private fun applyForceFullscreen(enabled: Boolean) {
        val svc = ShizukuBridge.service ?: return
        Thread {
            runCatching { svc.setForceFullscreen(enabled) }
                .onFailure { e ->
                    runOnUiThread {
                        Toast.makeText(this, "fullscreen toggle failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }.start()
    }

    private fun applyOrientationLock(enabled: Boolean) {
        val svc = ShizukuBridge.service ?: return
        Thread {
            runCatching { svc.setOrientationLock(enabled) }
                .onFailure { e ->
                    runOnUiThread {
                        Toast.makeText(this, "rotation toggle failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refresh)
    }

    override fun onConnected() = runOnUiThread { render() }
    override fun onDisconnected() = runOnUiThread { render() }
    override fun onPermissionResult(granted: Boolean) = runOnUiThread { render() }

    private enum class Stage { NotInstalled, NotRunning, NotGranted, NotBound, Ready }

    private fun stage(): Stage {
        if (!isShizukuInstalled()) return Stage.NotInstalled
        if (!ShizukuBridge.isShizukuAlive()) return Stage.NotRunning
        if (!ShizukuBridge.hasPermission()) return Stage.NotGranted
        val svc = ShizukuBridge.service ?: return Stage.NotBound
        val running = runCatching { svc.isRunning }.getOrDefault(false)
        return if (running) Stage.Ready else Stage.NotBound
    }

    private fun isShizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun render() {
        val s = stage()
        val svc = ShizukuBridge.service
        val last = runCatching { svc?.lastEvent() ?: "—" }.getOrDefault("—")
        val homeHolder = runCatching { svc?.currentHomeHolder() ?: "" }.getOrDefault("")
        val flipxIsHome = homeHolder == packageName
        val hingeOpen = Prefs.isHingeOpen(this)

        binding.txtShizukuInstalled.text = "Shizuku installed: ${yesNo(isShizukuInstalled())}"
        binding.txtShizuku.text          = "Shizuku running:   ${yesNo(ShizukuBridge.isShizukuAlive())}"
        binding.txtPerm.text             = "Permission:        ${yesNo(ShizukuBridge.hasPermission())}"
        binding.txtBound.text            = "UserService bound: ${yesNo(svc != null)}"
        binding.txtWatch.text            = "Watcher active:    ${yesNo(s == Stage.Ready)}"
        binding.txtHingeState.text       = "Hinge state:       ${if (hingeOpen) "open" else "closed"}"
        binding.txtLast.text             = "Last event: $last"

        binding.txtLauncherOpen.text = Launchers.labelFor(this, Prefs.openLauncher(this))
        binding.txtLauncherClose.text = Launchers.labelFor(this, Prefs.closeLauncher(this))

        binding.txtHomeHolder.text = when {
            homeHolder.isEmpty() -> "Current home: (unknown — bind Shizuku to check)"
            flipxIsHome -> "Current home: flipx ✓"
            else -> "Current home: $homeHolder"
        }
        binding.btnMakeHome.isEnabled = svc != null && !flipxIsHome

        when (s) {
            Stage.NotInstalled -> {
                binding.txtPrereq.setText(R.string.prereq_body)
                binding.btnPrimary.setText(R.string.btn_install_shizuku)
                binding.btnPrimary.setOnClickListener { openPlayStore(SHIZUKU_PKG) }
                binding.btnSecondary.visibility = View.GONE
                binding.btnStop.visibility = View.GONE
            }
            Stage.NotRunning -> {
                binding.txtPrereq.setText(R.string.prereq_body_start)
                binding.btnPrimary.setText(R.string.btn_open_shizuku)
                binding.btnPrimary.setOnClickListener { openShizuku() }
                binding.btnSecondary.visibility = View.GONE
                binding.btnStop.visibility = View.GONE
            }
            Stage.NotGranted -> {
                binding.txtPrereq.setText(R.string.prereq_body_grant)
                binding.btnPrimary.setText(R.string.btn_grant)
                binding.btnPrimary.setOnClickListener { ShizukuBridge.tryBindOrRequest() }
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.setText(R.string.btn_open_shizuku)
                binding.btnSecondary.setOnClickListener { openShizuku() }
                binding.btnStop.visibility = View.GONE
            }
            Stage.NotBound -> {
                binding.txtPrereq.setText(R.string.prereq_body_bind)
                binding.btnPrimary.setText(R.string.btn_bind)
                binding.btnPrimary.setOnClickListener { ShizukuBridge.tryBindOrRequest() }
                binding.btnSecondary.visibility = View.GONE
                binding.btnStop.visibility = View.GONE
            }
            Stage.Ready -> {
                binding.txtPrereq.setText(R.string.prereq_body_ready)
                binding.btnPrimary.visibility = View.GONE
                binding.btnSecondary.visibility = View.GONE
                binding.btnStop.visibility = View.VISIBLE
            }
        }
        if (s != Stage.Ready) binding.btnPrimary.visibility = View.VISIBLE
    }

    private fun pickLauncher(forOpen: Boolean) {
        val installed = Launchers.listHomeApps(this).filter { it.pkg != packageName }
        val labels = mutableListOf(getString(R.string.launcher_none))
        labels.addAll(installed.map { it.label })
        AlertDialog.Builder(this)
            .setTitle(R.string.launcher_picker_title)
            .setItems(labels.toTypedArray()) { _, which ->
                val pkg = if (which == 0) "" else installed[which - 1].pkg
                if (forOpen) Prefs.setOpenLauncher(this, pkg)
                else Prefs.setCloseLauncher(this, pkg)
                ShizukuBridge.pushLaunchers(this)
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun makeFlipxHome() {
        val svc = ShizukuBridge.service ?: run {
            Toast.makeText(this, "Bind Shizuku first", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val ok = runCatching { svc.setHomeHolder(packageName) }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "flipx is now the default home" else "Failed to set home — check logcat",
                    Toast.LENGTH_SHORT
                ).show()
                render()
            }
        }.start()
    }

    private fun yesNo(v: Boolean) = if (v) "yes" else "no"

    private fun openPlayStore(pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        try {
            startActivity(market)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app")))
        }
    }

    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Shizuku not installed", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    }
}
