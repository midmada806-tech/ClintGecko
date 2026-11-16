package com.simplefox.browser.browser.delegates
import com.simplefox.browser.browser.webview.*
import com.simplefox.browser.browser.MainActivity

import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal fun MainActivity.onShowCustomView(view: View, callback: CustomViewCallback) {
    val current = fullscreenView
    if (current != null && current !== view) { callback.onCustomViewHidden(); return }
    fullscreenCallback = callback
    if (current === view) return
    fullscreenView = view
    uiState.isFullscreen = true
    updateMainContentInsets()
    val ctrl = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    applyFullscreenOrientation(view, retry = true)
}

private fun MainActivity.applyFullscreenOrientation(view: View, retry: Boolean) {
    val webView = tabManager.activeTab?.webView ?: return
    webView.queryVideoSize { result ->
        if (fullscreenView !== view) return@queryVideoSize
        val parts = result?.trim('"')?.split(",")
        val vw = parts?.getOrNull(0)?.toIntOrNull() ?: 0
        val vh = parts?.getOrNull(1)?.toIntOrNull() ?: 0
        if ((vw <= 0 || vh <= 0) && retry) {
            view.postDelayed({ if (fullscreenView === view) applyFullscreenOrientation(view, retry = false) }, 400)
            return@queryVideoSize
        }
        if (vw > 0 && vh > 0) updatePictureInPictureParams(vw, vh)
        requestedOrientation = when {
            vw > 0 && vh > 0 && vh > vw -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

internal fun MainActivity.exitFullscreen() {
    fullscreenCallback?.onCustomViewHidden()
    fullscreenCallback = null
    fullscreenView = null
    pipAspectRatio = null
    clearPictureInPictureParams()
    uiState.isFullscreen = false
    bottomBarAnimator2?.cancel()
    uiState.topBarFraction = 0f
    uiState.bottomBarFraction = 0f
    nestedScrollActive = false
    canvasTouchActive = false
    hasWebBottomNav = false
    updateMainContentInsets()
    swipeRefreshView.isEnabled = true
    val ctrl = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    ctrl.show(WindowInsetsCompat.Type.navigationBars())
    applySystemUiVisibility()
    window.decorView.post {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        uiState.topBarFullHeightPx = 0
        uiState.bottomBarFullHeightPx = 0
    }
}

internal fun MainActivity.applySystemUiVisibility() {
    val hideStatusBar = prefs.getBoolean("hide_status_bar", false)
    val hideSystemNavigation = prefs.getBoolean("hide_system_navigation", false)
    uiState.hideStatusBar = hideStatusBar
    uiState.hideSystemNavigation = hideSystemNavigation
    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    if (hideStatusBar) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
    if (hideSystemNavigation) {
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    } else {
        controller.show(WindowInsetsCompat.Type.navigationBars())
    }
}

private fun pipRatio(width: Int, height: Int): android.util.Rational {
    // Android only accepts aspect ratios between roughly 1:2.39 and 2.39:1.
    val ratio = width.toFloat() / height.toFloat()
    return when {
        ratio > 2.39f -> android.util.Rational(239, 100)
        ratio < 0.42f -> android.util.Rational(42, 100)
        else -> android.util.Rational(width, height)
    }
}

internal fun MainActivity.updatePictureInPictureParams(videoWidth: Int, videoHeight: Int) {
    if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    val ratio = pipRatio(videoWidth, videoHeight)
    pipAspectRatio = ratio
    val builder = android.app.PictureInPictureParams.Builder().setAspectRatio(ratio)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) builder.setAutoEnterEnabled(true)
    runCatching { setPictureInPictureParams(builder.build()) }
}

internal fun MainActivity.clearPictureInPictureParams() {
    if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    val builder = android.app.PictureInPictureParams.Builder()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) builder.setAutoEnterEnabled(false)
    runCatching { setPictureInPictureParams(builder.build()) }
}

internal fun MainActivity.enterVideoPictureInPicture() {
    if (fullscreenView == null) return
    if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    val params = android.app.PictureInPictureParams.Builder()
        .setAspectRatio(pipAspectRatio ?: android.util.Rational(16, 9))
        .build()
    runCatching { enterPictureInPictureMode(params) }
}
