package com.moagauge.pleos

import android.os.Bundle
import android.os.Build
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.moagauge.pleos.ui.MoaLaunchHost
import com.moagauge.pleos.ui.MoaTheme
import com.moagauge.pleos.startup.StartupLaunch
import com.moagauge.pleos.settings.AppearanceMode

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Resolve the saved app choice before inflating a window. Do not change
        // UiModeManager: SYSTEM must keep following the vehicle's night mode.
        val appearance = runCatching { getSharedPreferences("app_settings", 0).getString("appearance", null) }.getOrNull()
        val dark = when (AppearanceMode.entries.find { it.name == appearance } ?: AppearanceMode.LIGHT) {
            AppearanceMode.DARK -> true
            AppearanceMode.LIGHT -> false
            AppearanceMode.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (dark) R.style.Theme_MoaDrive_Dark else R.style.Theme_MoaDrive_Light)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(getColor(if (dark) R.color.moa_launch_dark else R.color.moa_launch_light)))
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { it.remove() }
        setContent { MoaTheme { MoaLaunchHost(showSplash = savedInstanceState == null, hostWindow = window) } }
        if (BuildConfig.DEBUG) Log.d("PleosWindow", "created instance=${System.identityHashCode(this)}")
    }

    // Pleos changes the app window between one and two areas. Compose consumes
    // the new constraints; keep the SDK's live navigation view and focus owner.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (BuildConfig.DEBUG) Log.d("PleosWindow", "resized instance=${System.identityHashCode(this)} size=${newConfig.screenWidthDp}x${newConfig.screenHeightDp}dp")
    }

    override fun onResume() {
        super.onResume()
        // The startup notification only opens/resumes this task. Compose restores its page.
        StartupLaunch.onAppVisible(this)
    }
}
