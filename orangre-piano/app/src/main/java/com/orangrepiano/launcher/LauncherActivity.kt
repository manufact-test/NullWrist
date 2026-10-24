package com.orangrepiano.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class LauncherActivity : ComponentActivity() {
    private val homeRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        applyStatusBarSetting()
        setContent {
            PianoApp(
                onRequestHome = ::requestHome,
                onStatusBarChanged = { applyStatusBarSetting() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarSetting()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarSetting()
    }

    private fun applyStatusBarSetting() {
        val hide = LauncherPrefs(this).hideStatusBar
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun requestHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                homeRequest.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
