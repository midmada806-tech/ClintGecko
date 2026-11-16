package com.simplefox.browser.browser
import com.simplefox.browser.browser.imagereader.openImageReaderForUrl
import com.simplefox.browser.browser.imagereader.startImageReader
import com.simplefox.browser.settings.datasaver.DataSaverMode
import com.simplefox.browser.browser.home.CLINT_HOME_HOST
import com.simplefox.browser.browser.home.isClintHomeUrl
import com.simplefox.browser.browser.delegates.*
import com.simplefox.browser.browser.sheets.*
import com.simplefox.browser.browser.suggestions.*
import com.simplefox.browser.browser.webview.*

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.abs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.simplefox.browser.base.ClintActivity
import com.simplefox.browser.R
import com.simplefox.browser.BuildConfig
import com.simplefox.browser.crash.CrashHandler
import com.simplefox.browser.downloads.ClintDownloadManager
import com.simplefox.browser.tabs.TabManager
import com.simplefox.browser.ui.ClintSnackbarHost
import com.simplefox.browser.ui.ConfirmDialogHostActivity
import com.simplefox.browser.ui.OverlayHostActivity
import com.simplefox.browser.ui.SnackbarHostActivity
import com.simplefox.browser.ui.theme.ClintComposeTheme
import com.simplefox.browser.ui.theme.ThemeMode

class MainActivity : ClintActivity(), OverlayHostActivity, SnackbarHostActivity, ConfirmDialogHostActivity {

    companion object {
        private const val TRIM_MEMORY_BACKGROUND_LEVEL = 40
        private const val TRIM_MEMORY_MODERATE_LEVEL = 60
        const val EXTRA_REFRESH_LINK_MODE = "extra_refresh_link_mode"
        const val EXTRA_REFRESH_LINK_DOWNLOAD_ID = "extra_refresh_link_download_id"
        const val EXTRA_REFRESH_LINK_FILENAME = "extra_refresh_link_filename"
        const val EXTRA_REFRESH_LINK_ORIGINAL_URL = "extra_refresh_link_original_url"
        const val EXTRA_REFRESH_LINK_ORIGINAL_REFERER = "extra_refresh_link_original_referer"
        const val EXTRA_REFRESH_LINK_PAGE_URL = "extra_refresh_link_page_url"
        const val EXTRA_SHORTCUT_ID = "extra_shortcut_id"
    }

    data class RefreshLinkSession(
        val downloadId: Int,
        val filename: String,
        val originalUrl: String,
        val originalReferer: String,
        val previousTabIndex: Int,
        val pageUrl: String = ""
    )

    internal var refreshLinkSession: RefreshLinkSession? = null

    internal val uiState = MainUiState()

    override var confirmDialogConfig: com.simplefox.browser.ui.listscreen.ConfirmDialogConfig?
        get() = uiState.confirmDialogConfig
        set(value) { uiState.confirmDialogConfig = value }

    override var overlayContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    override val snackbarHostState = SnackbarHostState()

    internal lateinit var webContainer: FrameLayout
    internal lateinit var swipeRefreshView: ClintSwipeRefreshLayout

    internal lateinit var prefs: SharedPreferences
    internal val tabManager = TabManager()
    internal var isDesktopMode: Boolean
        get() = uiState.isDesktopMode
        set(value) { uiState.isDesktopMode = value }
    internal var desktopModeHost: String? = null
    internal var autoDesktopPendingReload: String? = null
    internal val desktopModeOptOutDomains = mutableSetOf<String>()

    internal var bottomBarAnimator2: android.animation.ValueAnimator? = null
    internal var hasWebBottomNav: Boolean = false
    internal var nestedScrollActive = false
    internal var canvasTouchActive = false
    internal var swipeGuardBlocked = false
    internal var pipAspectRatio: android.util.Rational? = null
    internal var contentInsetsUpdatePosted = false
    private var swipeGuardInitX = 0f
    private var swipeGuardInitY = 0f

    internal var fullscreenView: View? = null
    internal var fullscreenCallback: com.simplefox.browser.browser.webview.CustomViewCallback? = null

    internal var suggestionFetcher: SuggestionFetcher? = null
    internal var suggestionsBgThread: android.os.HandlerThread? = null
    internal var suggestionsBgHandler: android.os.Handler? = null
    internal var lastOnlineSuggestions: List<String> = emptyList()

    private var backPressedOnce = false
    private val backPressHandler = Handler(Looper.getMainLooper())
    private val requestConfigProvider: () -> org.json.JSONObject = { buildRequestConfig() }

    internal var pendingFileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var pendingFileChooserParams: com.simplefox.browser.browser.webview.FileChooserParams? = null
    internal var filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    internal var cameraImageUri: android.net.Uri? = null
    internal var cameraVideoUri: android.net.Uri? = null

    internal data class PendingDownload(
        val url: String,
        val filename: String,
        val userAgent: String,
        val referer: String,
        val cookies: String,
        val pageUrl: String = "",
        val startPaused: Boolean = false
    )

    internal var pendingDownload: PendingDownload? = null
    internal var downloadDialogFolderPickerCallback: ((android.net.Uri) -> Unit)? = null
    internal var pendingWebPermissionRequest: com.simplefox.browser.browser.webview.ClintPermissionRequest? = null
    internal var pendingWebMicPermissionRequest: com.simplefox.browser.browser.webview.ClintPermissionRequest? = null
    internal var pendingWebGeoOrigin: String? = null
    internal var pendingWebGeoCallback: com.simplefox.browser.browser.webview.GeolocationCallback? = null
    internal var pendingBridgeNotifCallbackId: String? = null
    internal var pendingBridgeNotifOrigin: String? = null
    internal var pendingBridgeNotifWebView: com.simplefox.browser.browser.webview.ClintWebView? = null

    internal val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            ClintDownloadManager.enqueue(this, pending.url, pending.filename, pending.userAgent, pending.referer, pending.cookies, pageUrl = pending.pageUrl, startPaused = pending.startPaused)
            if (pending.startPaused) showDownloadAddedSnackbar(pending.filename)
            else showDownloadStartedSnackbar(pending.filename, com.simplefox.browser.settings.downloads.DownloadSettingsKeys.MODE_DEFAULT, null)
        }
    }

    internal val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    internal val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val cb = pendingFileChooserCallback
        val params = pendingFileChooserParams
        pendingFileChooserCallback = null
        pendingFileChooserParams = null
        if (cb != null && params != null && !launchFileChooser(cb, params)) cb.onReceiveValue(null)
    }

    internal val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoiceSearch()
    }

    internal val webCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (granted && request != null) {
            showWebCameraDialog(request)
        } else {
            request?.deny()
        }
    }

    internal val webMicrophonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebMicPermissionRequest
        pendingWebMicPermissionRequest = null
        if (granted && request != null) {
            showWebMicDialog(request)
        } else {
            request?.deny()
        }
    }

    internal val webLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val origin = pendingWebGeoOrigin
        val callback = pendingWebGeoCallback
        pendingWebGeoOrigin = null
        pendingWebGeoCallback = null
        val granted = results.values.any { it }
        callback?.invoke(origin ?: "", granted && origin != null, false)
    }

    internal val webNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val callbackId = pendingBridgeNotifCallbackId
        val origin = pendingBridgeNotifOrigin
        val wv = pendingBridgeNotifWebView
        pendingBridgeNotifCallbackId = null
        pendingBridgeNotifOrigin = null
        pendingBridgeNotifWebView = null
        if (wv == null || callbackId == null) return@registerForActivityResult
        val safeId = callbackId.replace("'", "")
        if (granted && origin != null) {
            showWebNotificationPermissionFromBridge(wv, safeId, origin)
        } else {
            wv.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
        }
    }

    internal val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onSearchSubmitted(it) }
        }
    }

    internal val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                cameraImageUri != null -> arrayOf(cameraImageUri!!)
                else -> null
            }
        } else {
            null
        }
        cameraImageUri?.takeIf { uris?.contains(it) != true }?.let { runCatching { contentResolver.delete(it, null, null) } }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
        cameraImageUri = null
        cameraVideoUri = null
    }

    internal val downloadDialogFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            downloadDialogFolderPickerCallback?.invoke(uri)
        }
        downloadDialogFolderPickerCallback = null
    }

    internal var pendingShortcutIconCallback: ((android.graphics.Bitmap?) -> Unit)? = null

    internal val shortcutIconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val callback = pendingShortcutIconCallback
        pendingShortcutIconCallback = null
        val bitmap = uri?.let { picked ->
            runCatching { contentResolver.openInputStream(picked)?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        callback?.invoke(bitmap)
    }

    private val closeIncognitoTabsHandler: () -> Unit = { closeAllIncognitoTabs() }

    private val downloadIncognitoSource: () -> Boolean = {
        lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && tabManager.activeTab?.isIncognito == true
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "javascript_enabled" -> applyJavaScript()
            com.simplefox.browser.privacy.PrivacyPrefs.COOKIE_POLICY -> applyCookiePolicy()
            com.simplefox.browser.privacy.PrivacyPrefs.ETP_LEVEL, com.simplefox.browser.privacy.PrivacyPrefs.FINGERPRINTING_PROTECTION -> applyTrackingProtectionSettings()
            "do_not_track" -> applyDoNotTrackSettings()
            com.simplefox.browser.browser.webview.ClintAutofill.PREF_AUTOFILL_ENABLED -> applyAutofillSettings()
            "custom_user_agent" -> applyUserAgent()
            com.simplefox.browser.mediacapture.MEDIA_CAPTURE_ENABLED_PREF -> updateMediaCaptureEnabledState()
            "show_home_button" -> updateHomeButtonState()
            com.simplefox.browser.tabs.TabGroupPrefs.PREF_SHOW_GROUP_BAR, com.simplefox.browser.tabs.TabStripPrefs.PREF_SHOW_TAB_STRIP -> updateTabCount()
            com.simplefox.browser.tabs.TabGroupPrefs.PREF_TAB_GROUPS_ENABLED -> applyTabGroupsSetting()
            com.simplefox.browser.tabs.TabBarPlacement.PREF_TAB_STRIP_POSITION, com.simplefox.browser.tabs.TabBarPlacement.PREF_TAB_GROUP_BAR_POSITION -> updateTabBarPositions()
            "data_saver_enabled", "data_saver_metered_only", "data_saver_disable_images", "data_saver_disable_autoplay", "data_saver_send_header", "data_saver_block_fonts", "data_saver_block_frames", "data_saver_block_preload", "data_saver_block_video", "data_saver_block_scripts", "data_saver_block_css", "data_saver_cache_first" -> applyDataSaverSettings()
            "data_saver_single_tab" -> syncTabPlayback()
            "hide_bars_on_scroll" -> {
                if (!prefs.getBoolean("hide_bars_on_scroll", true)) {
                    animateBottomBarTo(0f, animated = false)
                }
            }
            "scroll_hide_mode" -> {
                if (prefs.getString("scroll_hide_mode", "off") == "off") {
                    animateBottomBarTo(0f, animated = false)
                }
            }
        }
        onTranslationPrefChanged(key)
        com.simplefox.browser.browser.webview.ClintGecko.pushConfig()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webContainer = FrameLayout(this)
        swipeRefreshView = ClintSwipeRefreshLayout(this).apply {
            addView(webContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("setup_complete", false)) {
            startActivity(android.content.Intent(this, com.simplefox.browser.setup.SetupActivity::class.java))
            finish()
            return
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        com.simplefox.browser.media.MediaPlaybackController.tabIdResolver = { view -> tabManager.tabs.firstOrNull { it.webView === view }?.id }
        com.simplefox.browser.browser.webview.ClintGecko.init(this)
        com.simplefox.browser.browser.webview.ClintGecko.configProvider = requestConfigProvider
        ClintDownloadManager.incognitoSource = downloadIncognitoSource
        setupExtensions()
        com.simplefox.browser.browser.webview.ClintGecko.setCookiePolicy(com.simplefox.browser.privacy.PrivacyPrefs.cookiePolicy(prefs))
        syncBaseScripts()
        syncGlobalScripts()
        DataSaverMode.onActiveChanged = { applyDataSaverSettings() }
        updateMediaCaptureEnabledState()
        updateHomeButtonState()
        updateTabBarPositions()
        setupTranslations()
        tabManager.framelessShortcutIds = com.simplefox.browser.shortcuts.ShortcutStore.framelessIds(this)
        com.simplefox.browser.shortcuts.ShortcutPinner.migrateLaunchIntents(this)
        applySystemUiVisibility()

        val startTheme = prefs.getString("app_theme", "system") ?: "system"
        setContent {
            ClintComposeTheme(theme = startTheme) {
                MainScreen(activity = this, state = uiState)
                overlayContent?.invoke()
                com.simplefox.browser.extensions.ExtensionHost()
                ClintSnackbarHost(hostState = snackbarHostState)
            }
        }

        ClintDownloadManager.createNotificationChannel(this)
        ClintDownloadManager.init(this)
        initializeWebsiteBlockerEngine()
        installServiceWorkerMediaCapture()
        createWebNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        migrateScrollHideMode()
        migrateLegacyTabMenuStyle()
        setupSwipeRefresh()
        setupAddressBar()
        applyAddressBarPosition()
        if (savedInstanceState == null && com.simplefox.browser.privacy.PrivacyPrefs.closesTabsOnExit(prefs)) discardSavedTabs()
        com.simplefox.browser.privacy.IncognitoNotification.closeTabsHandler = closeIncognitoTabsHandler
        val isRefreshLinkMode = intent.getBooleanExtra(EXTRA_REFRESH_LINK_MODE, false)
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        applyShortcutFrameless(shortcutId)
        if (isRefreshLinkMode) {
            val downloadId = intent.getIntExtra(EXTRA_REFRESH_LINK_DOWNLOAD_ID, -1)
            val filename = intent.getStringExtra(EXTRA_REFRESH_LINK_FILENAME) ?: ""
            val originalUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_URL) ?: ""
            val originalReferer = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_REFERER) ?: ""
            val pageUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_PAGE_URL) ?: ""
            setIntent(android.content.Intent())
            if (downloadId != -1) {
                restoreTabs()
                refreshLinkSession = RefreshLinkSession(downloadId, filename, originalUrl, originalReferer, tabManager.activeIndex, pageUrl)
                openRefreshLinkTab(refreshLinkStartUrl(pageUrl, originalReferer, originalUrl))
            } else if (!restoreTabs()) {
                openNewTab(isIncognito = false, url = getHomepageUrl(false))
            }
        } else if (shortcutId != null) {
            val fallbackUrl = getUrlFromIntent(intent)
            setIntent(android.content.Intent())
            restoreTabs()
            openOrResumeShortcutTab(shortcutId, fallbackUrl)
        } else {
            val intentUrl = getUrlFromIntent(intent)
            setIntent(android.content.Intent())
            if (!intentUrl.isNullOrEmpty()) {
                restoreTabs()
                openNewTab(isIncognito = false, url = intentUrl)
            } else {
                val restored = restoreTabs()
                if (shouldReopenInIncognito()) {
                    openNewTab(isIncognito = true, url = getHomepageUrl(true))
                } else if (!restored) {
                    openNewTab(isIncognito = false, url = getHomepageUrl(false))
                }
            }
        }
        setupBackPressedDispatcher()
    }

    private fun refreshLinkStartUrl(pageUrl: String, referer: String, originalUrl: String): String =
        pageUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: referer.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: originalUrl.ifEmpty { getHomepageUrl(false) }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val mediaTabId = intent.getStringExtra(com.simplefox.browser.media.MediaPlaybackService.EXTRA_TAB_ID)
        if (mediaTabId != null) {
            setIntent(android.content.Intent())
            focusTabById(mediaTabId)
            return
        }
        syncFramelessShortcuts()
        val isRefreshLinkMode = intent.getBooleanExtra(EXTRA_REFRESH_LINK_MODE, false)
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        val wasShortcutFrameless = uiState.isShortcutFrameless
        applyShortcutFrameless(shortcutId)
        if (isRefreshLinkMode) {
            val downloadId = intent.getIntExtra(EXTRA_REFRESH_LINK_DOWNLOAD_ID, -1)
            val filename = intent.getStringExtra(EXTRA_REFRESH_LINK_FILENAME) ?: ""
            val originalUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_URL) ?: ""
            val originalReferer = intent.getStringExtra(EXTRA_REFRESH_LINK_ORIGINAL_REFERER) ?: ""
            val pageUrl = intent.getStringExtra(EXTRA_REFRESH_LINK_PAGE_URL) ?: ""
            setIntent(android.content.Intent())
            if (downloadId != -1) {
                refreshLinkSession = RefreshLinkSession(downloadId, filename, originalUrl, originalReferer, tabManager.activeIndex, pageUrl)
                openRefreshLinkTab(refreshLinkStartUrl(pageUrl, originalReferer, originalUrl))
            }
            return
        }
        if (shortcutId != null) {
            val fallbackUrl = getUrlFromIntent(intent)
            setIntent(android.content.Intent())
            openOrResumeShortcutTab(shortcutId, fallbackUrl)
            return
        }
        val url = getUrlFromIntent(intent)
        setIntent(android.content.Intent())
        if (wasShortcutFrameless) {
            exitShortcutFramelessToNormal()
        }
        if (!url.isNullOrEmpty()) {
            openNewTab(isIncognito = false, url = url)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        ClintDownloadManager.resumeTimeoutPaused(this)
        com.simplefox.browser.extensions.ClintExtensions.reassertActiveSession()
        addBackgroundPlaybackScript()
        syncFramelessShortcuts()
        closeInactiveTabs()
        com.simplefox.browser.privacy.BrowsingDataCleaner.consumeCloseTabsRequest()?.let { since ->
            val activeTabId = tabManager.activeTab?.id
            val ids = tabManager.tabs
                .filter {
                    !tabManager.isGhostTab(it) &&
                        com.simplefox.browser.privacy.BrowsingDataCleaner.isTabInRange(it.lastActiveAt, it.id == activeTabId, since)
                }
                .map { it.id }
            if (ids.isNotEmpty()) closeTabsWithUndo(ids, resources.getQuantityString(R.plurals.tab_menu_closed_tabs, ids.size, ids.size))
        }
        updateTabCount()
        if (::swipeRefreshView.isInitialized) updateSwipeRefreshColors(uiState.isIncognito)
        bottomBarAnimator2?.cancel()
        uiState.topBarFraction = 0f
        uiState.bottomBarFraction = 0f
        nestedScrollActive = false
        canvasTouchActive = false
        hasWebBottomNav = false
        swipeRefreshView.isEnabled = true
        updateMainContentInsets()
        uiState.homepageDesign = com.simplefox.browser.browser.home.readHomepageDesign(this, prefs)
        uiState.homepageImageVersion = prefs.getLong(com.simplefox.browser.browser.home.PREF_HOMEPAGE_IMAGE_VERSION, 0L)
        uiState.homepageShowFavorites = prefs.getBoolean(com.simplefox.browser.browser.home.PREF_HOMEPAGE_SHOW_FAVORITES, true)
        uiState.homepageShowQuickActions = prefs.getBoolean(com.simplefox.browser.browser.home.PREF_HOMEPAGE_SHOW_QUICK_ACTIONS, true)
        uiState.homepageShowRecent = prefs.getBoolean(com.simplefox.browser.browser.home.PREF_HOMEPAGE_SHOW_RECENT, true)
        uiState.homepageCenterContent = prefs.getBoolean(com.simplefox.browser.browser.home.PREF_HOMEPAGE_CENTER_CONTENT, true)
        uiState.homepageImageFullBrightness = prefs.getBoolean(com.simplefox.browser.browser.home.PREF_HOMEPAGE_IMAGE_FULL_BRIGHTNESS, false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUiVisibility()
    }

    override fun onStart() {
        super.onStart()
        tabManager.activeTab?.webView?.onResume()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterVideoPictureInPicture()
    }

    override fun onStop() {
        super.onStop()
        recordBrowsingMode()
        if (isFinishing && com.simplefox.browser.privacy.PrivacyPrefs.closesTabsOnExit(prefs)) {
            discardSavedTabsAsync()
        } else {
            saveTabs()
        }
        tabManager.activeTab?.webView?.onPause()
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= TRIM_MEMORY_MODERATE_LEVEL -> {
                trimBackgroundTabs(0)
                hibernateActiveTabIfHidden()
            }
            level >= TRIM_MEMORY_BACKGROUND_LEVEL -> trimBackgroundTabs(BACKGROUND_TRIM_KEEP_LIVE)
            level == 10 || level == 15 -> trimBackgroundTabs(0)
        }
    }

    override fun onSystemThemeChanged() {
        applyWebDarkModeToAllTabs()
        if (::swipeRefreshView.isInitialized) updateSwipeRefreshColors(uiState.isIncognito)
    }

    override fun onDestroy() {
        teardownExtensions()
        teardownTranslations()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        DataSaverMode.onActiveChanged = null
        if (com.simplefox.browser.browser.webview.ClintGecko.configProvider === requestConfigProvider) {
            com.simplefox.browser.browser.webview.ClintGecko.configProvider = null
        }
        com.simplefox.browser.media.MediaPlaybackController.tabIdResolver = null
        if (refreshLinkSession != null) {
            cleanupRefreshLinkTabs()
            refreshLinkSession = null
        }
        if (ClintDownloadManager.incognitoSource === downloadIncognitoSource) {
            ClintDownloadManager.incognitoSource = null
        }
        if (com.simplefox.browser.privacy.IncognitoNotification.closeTabsHandler === closeIncognitoTabsHandler) {
            com.simplefox.browser.privacy.IncognitoNotification.closeTabsHandler = null
        }
        tabManager.destroyAll()
        com.simplefox.browser.privacy.IncognitoNotification.sync(this, false)
        suggestionFetcher?.cancel()
        suggestionsBgThread?.quitSafely()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeGuardInitX = ev.x
                swipeGuardInitY = ev.y
                swipeGuardBlocked = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!swipeGuardBlocked) {
                    swipeGuardBlocked = true
                    swipeRefreshView.isEnabled = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeGuardBlocked) {
                    val dx = abs(ev.x - swipeGuardInitX)
                    val dy = abs(ev.y - swipeGuardInitY)
                    if (dx > slop && dx >= dy) {
                        swipeGuardBlocked = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeGuardBlocked) {
                    swipeGuardBlocked = false
                    updateMainContentInsets()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupBackPressedDispatcher() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState.websiteBlockedRequest != null) {
                    dismissWebsiteBlockedOverlay()
                    return
                }
                if (fullscreenView != null) {
                    exitFullscreen()
                    return
                }
                if (uiState.searchOverlayOpen) {
                    closeSearchOverlay()
                    return
                }
                val activeTab = tabManager.activeTab
                val wv = activeTab?.webView
                if (wv?.canGoBack() == true) {
                    wv.goBack()
                    return
                }
                if (activeTab != null && closePopupTabToOpener(activeTab)) {
                    return
                }
                handleExitConfirmation()
            }
        })
    }

    private fun handleExitConfirmation() {
        val mode = prefs.getString("exit_confirmation", "toast") ?: "toast"
        when (mode) {
            "off" -> finish()
            "dialog" -> showExitDialog()
            else -> handleToastExit()
        }
    }

    private fun handleToastExit() {
        if (backPressedOnce) {
            backPressHandler.removeCallbacksAndMessages(null)
            finish()
            return
        }
        backPressedOnce = true
        Toast.makeText(this, getString(R.string.exit_tap_again), Toast.LENGTH_SHORT).show()
        backPressHandler.postDelayed({ backPressedOnce = false }, 2000L)
    }

    private fun showExitDialog() {
        uiState.confirmDialogConfig = com.simplefox.browser.ui.listscreen.ConfirmDialogConfig(
            title = getString(R.string.exit_dialog_title),
            message = getString(R.string.exit_dialog_message),
            cancelable = false,
            positiveLabel = getString(R.string.exit_dialog_confirm),
            onPositive = { finish() },
            negativeLabel = getString(R.string.action_cancel)
        )
    }

    fun onTabSelected(index: Int) { captureActiveTabThumbnail(); tabManager.switchTo(index); attachActiveWebView() }
    fun onSwipeTabChange(direction: Int): Boolean {
        // direction: 1 = swipe right (previous tab), -1 = swipe left (next tab).
        // Only moves through tabs of the current mode (normal / incognito); no wrap-around at the ends.
        val activeIncognito = tabManager.activeTab?.isIncognito == true
        val visibleIndices = tabManager.tabs.indices.filter {
            val tab = tabManager.tabs[it]
            !tabManager.isGhostTab(tab) && tab.isIncognito == activeIncognito
        }
        if (visibleIndices.size < 2) return false
        val currentPos = visibleIndices.indexOf(tabManager.activeIndex)
        if (currentPos == -1) return false
        val newPos = currentPos - direction
        if (newPos !in visibleIndices.indices) return false
        onTabSelected(visibleIndices[newPos])
        return true
    }
    fun releaseShortcutGroupTabs(ids: List<String>) {
        ids.forEach { id ->
            val index = tabManager.tabs.indexOfFirst { it.id == id }
            val tab = tabManager.tabs.getOrNull(index) ?: return@forEach
            removeDesktopScript(tab)
            com.simplefox.browser.mediacapture.MediaCaptureStore.removeTab(tab.id)
            com.simplefox.browser.ui.FaviconCache.evict(this, tab.url)
            com.simplefox.browser.tabs.TabThumbnailCache.evict(this, tab.id)
            tabManager.closeTab(index)
        }
    }

    fun onTabClosed(index: Int) {
        val tab = tabManager.tabs.getOrNull(index)
        tab?.let {
            removeDesktopScript(it)
            com.simplefox.browser.mediacapture.MediaCaptureStore.removeTab(it.id)
            if (!it.isIncognito) com.simplefox.browser.ui.FaviconCache.evict(this, it.url)
            com.simplefox.browser.tabs.TabThumbnailCache.evict(this, it.id)
        }
        val wasActive = index == tabManager.activeIndex
        tabManager.closeTab(index)
        resetProgressBar()
        if (tabManager.previews().isEmpty()) {
            openNewTab(false)
            return
        }
        val activeTab = tabManager.activeTab
        if (activeTab != null && tabManager.isGhostTab(activeTab)) {
            val idx = tabManager.tabs.indexOfFirst { !tabManager.isGhostTab(it) }
            if (idx != -1) { tabManager.switchTo(idx); attachActiveWebView() } else openNewTab(false)
        } else if (wasActive) attachActiveWebView()
        else updateTabCount()
    }
    fun onNewTab() { openNewTab(false) }
    fun onNewTabInProfile(profileId: String) { openNewTab(isIncognito = false, profileId = profileId) }
    fun onNewIncognitoTab() { openNewTab(true) }

    fun onMenuGoBack() { navGoBack() }
    fun onMenuGoForward() { navGoForward() }
    fun onMenuHome() { navGoHome() }
    fun onMenuRefreshOrStop() { navRefreshOrStop() }
    fun onMenuToggleBookmark() { navToggleBookmark() }
    fun onMenuNewTab() { openNewTab(false) }
    fun onMenuIncognito() { openNewTab(true) }
    fun onMenuOpenDesktopModeSettings() {
        startActivity(android.content.Intent(this, com.simplefox.browser.settings.desktopmode.DesktopModeActivity::class.java))
    }
    fun onMenuOpenDataSaverSiteSettings() {
        startActivity(android.content.Intent(this, com.simplefox.browser.settings.datasaver.DataSaverExceptionActivity::class.java))
    }
    fun onMenuOpenShortcutManager() {
        startActivity(android.content.Intent(this, com.simplefox.browser.settings.shortcutmanager.ShortcutManagerActivity::class.java))
    }
    fun onMenuOpenProfiles() {
        startActivity(android.content.Intent(this, com.simplefox.browser.profiles.ProfilesActivity::class.java))
    }
    fun onMenuNewProfileTab() {
        if (com.simplefox.browser.profiles.ProfileRepository.all(this).isEmpty()) {
            startActivity(android.content.Intent(this, com.simplefox.browser.profiles.ProfilesActivity::class.java))
        } else {
            uiState.profilePickerOpen = true
        }
    }
    fun onMenuShare() {
        val shareUrl = tabManager.activeTab?.webView?.url
        if (isClintHomeUrl(shareUrl)) return
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
        }
        startActivity(android.content.Intent.createChooser(i, getString(R.string.share_url)))
    }
    fun onMenuOpenInApp() {
        val currentUrl = tabManager.activeTab?.webView?.url?.takeUnless { isClintHomeUrl(it) } ?: return
        val currentUri = runCatching { android.net.Uri.parse(currentUrl) }.getOrNull() ?: return
        val webClient = tabManager.activeTab?.webView?.webViewClient ?: return
        val appMatches = webClient.resolveAppMatches(currentUri, this)
        if (appMatches.size == 1) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, currentUri)
                .setPackage(appMatches[0].activityInfo.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        } else {
            webClient.tryOpenInApp(tabManager.activeTab?.webView ?: return, currentUri, force = true)
        }
    }
    fun onMenuCreateShortcut() {
        val tab = tabManager.activeTab ?: return
        val url = tab.webView.url
        if (url.isNullOrEmpty() || isClintHomeUrl(url)) return
        uiState.createShortcutRequest = com.simplefox.browser.browser.dialogs.CreateShortcutRequest(
            pageUrl = url,
            initialName = tab.title.ifBlank { url }
        )
    }
    fun onMenuDownloads() { startActivity(android.content.Intent(this, com.simplefox.browser.downloads.DownloadsActivity::class.java)) }
    fun onMenuBookmarks() { startActivity(android.content.Intent(this, com.simplefox.browser.bookmarks.BookmarksActivity::class.java)) }
    fun onMenuHistory() { startActivity(android.content.Intent(this, com.simplefox.browser.history.HistoryActivity::class.java)) }
    fun onMenuDesktopMode() {
        isDesktopMode = !isDesktopMode

        val activeTab = tabManager.activeTab
        val wv = activeTab?.webView
        val currentWebUrl = wv?.url
        val onHome = isClintHomeUrl(currentWebUrl)
        val host = if (onHome) null else currentWebUrl?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }?.takeIf { it.isNotEmpty() }

        desktopModeHost = if (isDesktopMode) (if (onHome) CLINT_HOME_HOST else host) else null
        autoDesktopPendingReload = null

        if (host != null && isDesktopModeSaveStateEnabled()) {
            val domain = com.simplefox.browser.util.registeredDomain(host)
            if (activeTab?.isIncognito == true) {
                if (isDesktopMode) desktopModeOptOutDomains.remove(domain) else desktopModeOptOutDomains.add(domain)
            } else if (isDesktopMode) {
                desktopModeOptOutDomains.remove(domain)
                com.simplefox.browser.settings.sitepermissions.SitePermissionManager.setState(
                    this, host,
                    com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE,
                    com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW
                )
            } else {
                com.simplefox.browser.settings.sitepermissions.SitePermissionManager.deleteEntry(
                    this, host,
                    com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.TYPE_DESKTOP_MODE
                )
            }
        }

        applyDesktopModeToTabs()

        if (wv != null && !onHome) {
            com.simplefox.browser.browser.webview.ClintGecko.syncThen {
                if (tabManager.activeTab?.webView === wv) wv.reload()
            }
        }
    }
    fun onMenuSettings() { startActivity(android.content.Intent(this, com.simplefox.browser.settings.SettingsActivity::class.java)) }
    fun onMenuDataSaver() {
        val enabled = !prefs.getBoolean("data_saver_enabled", false)
        prefs.edit().putBoolean("data_saver_enabled", enabled).apply()
    }
    fun onMenuOpenDataSaverSettings() {
        startActivity(android.content.Intent(this, com.simplefox.browser.settings.SettingsActivity::class.java)
            .putExtra(com.simplefox.browser.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "data_saver"))
    }
    fun onMenuOpenDownloadSettings() {
        startActivity(android.content.Intent(this, com.simplefox.browser.settings.SettingsActivity::class.java)
            .putExtra(com.simplefox.browser.settings.SettingsActivity.EXTRA_OPEN_FRAGMENT, "download_settings"))
    }
    fun onMenuExtensionClick(id: String, actionable: Boolean) {
        if (actionable) com.simplefox.browser.extensions.ClintExtensions.clickAction(id)
        else com.simplefox.browser.extensions.ExtensionsActivity.launch(this, id)
    }
    fun onMenuOpenExtensionSettings(id: String) {
        com.simplefox.browser.extensions.ExtensionsActivity.launch(this, id)
    }
    fun onMenuOpenExtensionsSettings() {
        com.simplefox.browser.extensions.ExtensionsActivity.launch(this)
    }
    fun onMenuMediaCapture() {
        val enabled = !prefs.getBoolean(com.simplefox.browser.mediacapture.MEDIA_CAPTURE_ENABLED_PREF, true)
        prefs.edit().putBoolean(com.simplefox.browser.mediacapture.MEDIA_CAPTURE_ENABLED_PREF, enabled).apply()
    }
    fun onMenuDisableDataSaverForSite() {
        val tab = tabManager.activeTab ?: return
        val currentUrl = tab.webView.url ?: return
        if (!currentUrl.startsWith("http://") && !currentUrl.startsWith("https://")) return
        val host = runCatching { android.net.Uri.parse(currentUrl).host }.getOrNull() ?: return
        if (tab.isIncognito) return
        if (com.simplefox.browser.settings.datasaver.DataSaverSiteException.isExcepted(this, host)) {
            com.simplefox.browser.settings.datasaver.DataSaverSiteException.remove(this, host)
        } else {
            com.simplefox.browser.settings.datasaver.DataSaverSiteException.add(this, host)
        }
        reloadActiveTabWithHeaders()
    }
    fun onMenuToggleTabMute() {
        val tab = tabManager.activeTab ?: return
        tab.webView.setAudioMuted(!tab.webView.audioState.muted)
    }
    fun onMenuCookiesControl() {
        openCookiesControl()
    }
    fun onMenuTranslate() {
        openTranslatePage()
    }
    fun onMenuOpenTranslationSettings() {
        openTranslationSettings()
    }
    fun onMenuWebsiteBlocker() {
        val enabled = !prefs.getBoolean("website_blocker_enabled", false)
        prefs.edit().putBoolean("website_blocker_enabled", enabled).apply()
    }
    fun onMenuOpenWebsiteBlockerSettings() {
        startActivity(android.content.Intent(this, com.simplefox.browser.blocker.WebsiteBlockerActivity::class.java))
    }
    fun onMenuReaderMode() {
        val wv = tabManager.activeTab?.webView ?: return
        val pageUrl = wv.url ?: return
        wv.extractReaderContent { raw ->
            if (isFinishing) return@extractReaderContent
            val json = runCatching { org.json.JSONObject(raw ?: "") }.getOrNull()
            val content = json?.optString("content", "").orEmpty()
            if (json == null || content.isBlank()) {
                runOnUiThread { Toast.makeText(this, R.string.reader_mode_unavailable, Toast.LENGTH_SHORT).show() }
                return@extractReaderContent
            }
            val title = json.optString("title", "")
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val theme = prefs.getString("app_theme", "system") ?: "system"
            val isDark = !ThemeMode.isLight(theme)
            val bgColor = if (isDark) "#121212" else "#ffffff"
            val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
            val secondaryColor = if (isDark) "#aaaaaa" else "#555555"
            val linkColor = if (isDark) "#90caf9" else "#1a73e8"
            val html = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:Georgia,'Times New Roman',serif;font-size:18px;line-height:1.75;max-width:700px;margin:0 auto;padding:16px 20px 32px;background-color:$bgColor;color:$textColor;}
h1,h2,h3,h4,h5,h6{font-family:-apple-system,sans-serif;line-height:1.3;color:$textColor;}
a{color:$linkColor;}
img{max-width:100%;height:auto;display:block;margin:12px auto;}
pre,code{overflow-x:auto;font-size:14px;}
blockquote{border-left:3px solid $secondaryColor;margin:0;padding:4px 16px;color:$secondaryColor;}
figure{margin:12px 0;}
figcaption{font-size:13px;color:$secondaryColor;text-align:center;margin-top:4px;}
table{border-collapse:collapse;width:100%;}
td,th{border:1px solid $secondaryColor;padding:6px 8px;}
</style>
</head>
<body>$content</body>
</html>"""
            runOnUiThread {
                uiState.contentPreviewRequest = com.simplefox.browser.browser.sheets.ContentPreviewRequest.forReaderMode(pageUrl, title, html)
            }
        }
    }

    fun onMenuImageReader() = startImageReader()

    fun openImageReaderFor(url: String) = openImageReaderForUrl(url)

    inner class NestedScrollBridge {
        @android.webkit.JavascriptInterface
        fun onNestedScroll(active: Boolean) {
            runOnUiThread { nestedScrollActive = active }
        }
    }

    inner class CanvasTouchBridge {
        @android.webkit.JavascriptInterface
        fun onCanvasTouch(active: Boolean) {
            runOnUiThread { canvasTouchActive = active }
        }
    }

    inner class BottomNavBridge {
        @android.webkit.JavascriptInterface
        fun onBottomNavDetected(detected: Boolean) {
            runOnUiThread {
                if (detected && !hasWebBottomNav) {
                    hasWebBottomNav = true
                }
            }
        }
    }

    inner class NotificationBridge(private val webView: com.simplefox.browser.browser.webview.ClintWebView) {
        @android.webkit.JavascriptInterface
        fun getPermissionState(origin: String): String {
            val tab = tabManager.tabs.find { it.webView == webView }
            if (tab?.isIncognito == true) return "denied"
            val rawOrigin = origin.trim()
            return when (com.simplefox.browser.settings.sitepermissions.SitePermissionManager.getState(
                this@MainActivity, rawOrigin,
                com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
            )) {
                com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW -> "granted"
                com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.STATE_DENY -> "denied"
                else -> "default"
            }
        }

        @android.webkit.JavascriptInterface
        fun requestPermission(callbackId: String, origin: String) {
            runOnUiThread {
                val tab = tabManager.tabs.find { it.webView == webView }
                val safeId = callbackId.replace("'", "")
                if (tab?.isIncognito == true) {
                    webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                    return@runOnUiThread
                }
                val rawOrigin = origin.trim()
                val stored = com.simplefox.browser.settings.sitepermissions.SitePermissionManager.getState(
                    this@MainActivity, rawOrigin,
                    com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
                )
                when (stored) {
                    com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            !isSystemPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            pendingBridgeNotifCallbackId = safeId
                            pendingBridgeNotifOrigin = rawOrigin
                            pendingBridgeNotifWebView = webView
                            webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            webView.evaluateJavascript("window._ClintResolvePermission('$safeId','granted')", null)
                        }
                    }
                    com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.STATE_DENY -> {
                        webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                    }
                    else -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            !isSystemPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            pendingBridgeNotifCallbackId = safeId
                            pendingBridgeNotifOrigin = rawOrigin
                            pendingBridgeNotifWebView = webView
                            val needsRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
                            if (needsRationale) {
                                uiState.confirmDialogConfig = com.simplefox.browser.ui.listscreen.ConfirmDialogConfig(
                                    title = getString(R.string.notification_permission_title),
                                    message = getString(R.string.notification_permission_message),
                                    positiveLabel = getString(R.string.action_allow),
                                    onPositive = { webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                                    negativeLabel = getString(R.string.action_deny),
                                    onNegative = {
                                        pendingBridgeNotifCallbackId = null
                                        pendingBridgeNotifOrigin = null
                                        pendingBridgeNotifWebView = null
                                        webView.evaluateJavascript("window._ClintResolvePermission('$safeId','denied')", null)
                                    }
                                )
                            } else {
                                webNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            showWebNotificationPermissionFromBridge(webView, safeId, rawOrigin)
                        }
                    }
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun postNotification(title: String, body: String, tag: String, origin: String) {
            val rawOrigin = origin.trim()
            val tab = tabManager.tabs.find { it.webView == webView }
            if (tab?.isIncognito == true) return
            val stored = com.simplefox.browser.settings.sitepermissions.SitePermissionManager.getState(
                this@MainActivity, rawOrigin,
                com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.TYPE_NOTIFICATION
            )
            if (stored != com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase.STATE_ALLOW) return
            runOnUiThread { postWebNotification(title, body, tag, rawOrigin) }
        }
    }

    inner class BlobDownloadBridge {
        @android.webkit.JavascriptInterface
        fun receiveBlob(base64: String, filename: String, mimeType: String) {
            com.simplefox.browser.browser.webview.ClintGecko.io.execute {
                val blob = com.simplefox.browser.downloads.DownloadBlobStore.fromBase64(applicationContext, base64)
                runOnUiThread {
                    if (blob == null) {
                        Toast.makeText(this@MainActivity, R.string.download_response_failed, Toast.LENGTH_SHORT).show()
                    } else if (isFinishing || isDestroyed) {
                        ClintDownloadManager.enqueueBlob(applicationContext, blob, filename, mimeType)
                    } else {
                        showDownloadDialogForBlob(blob, filename, mimeType)
                    }
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onError(error: String) {
        }
    }

    internal fun isNetworkMetered(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    private fun migrateScrollHideMode() {
        when (prefs.getString("scroll_hide_mode", "off")) {
            "top_only" -> prefs.edit().putString("scroll_hide_mode", "search_bar").apply()
            "bottom_only" -> {
                val position = prefs.getString("address_bar_position", "bottom") ?: "bottom"
                val newValue = if (position == "bottom") "search_bar" else "navigation_bar"
                prefs.edit().putString("scroll_hide_mode", newValue).apply()
            }
        }
    }

    private fun migrateLegacyTabMenuStyle() {
        if (prefs.contains("tab_menu_style")) {
            prefs.edit().remove("tab_menu_style").apply()
        }
    }

    fun onImageOpenInNewTab(imageUrl: String) { handleImageOpenInNewTab(imageUrl) }
    fun onImageOpenIncognito(imageUrl: String) { openNewTab(isIncognito = true, url = imageUrl) }
    fun onImageOpenInCurrentTab(imageUrl: String) { dismissContentPreview(); loadUrl(imageUrl) }
    fun onImagePreview(imageUrl: String) { handleImagePreview(imageUrl) }
    fun onImageCopy(imageUrl: String) { handleImageCopy(imageUrl) }
    fun onImageDownload(imageUrl: String, altText: String) { handleImageDownload(imageUrl, altText) }
    fun onImageShare(imageUrl: String) { handleImageShare(imageUrl) }

    fun onLinkOpenInNewTab(url: String) { handleLinkOpenInNewTab(url) }
    fun onLinkOpenIncognito(url: String) { handleLinkOpenIncognito(url) }
    fun onLinkOpenNewTabBackground(url: String) { handleLinkOpenNewTabBackground(url) }
    fun onLinkPreviewPage(url: String) { handleLinkPreviewPage(url) }
    fun onLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    fun onLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    fun onLinkShare(url: String) { handleLinkShare(url) }

    fun onPreviewOpenInNewTab(url: String) { openNewTab(isIncognito = false, url = url, profileId = activeProfileId()) }

    fun onPreviewLinkOpenInNewTab(url: String) { dismissContentPreview(); handleLinkOpenInNewTab(url) }
    fun onPreviewLinkOpenIncognito(url: String) { dismissContentPreview(); handleLinkOpenIncognito(url) }
    fun onPreviewLinkOpenInCurrentTab(url: String) { dismissContentPreview(); loadUrl(url) }
    fun onPreviewLinkCopyAddress(url: String) { handleLinkCopyAddress(url) }
    fun onPreviewLinkCopyText(url: String, text: String) { handleLinkCopyText(text) }
    fun onPreviewLinkShare(url: String) { handleLinkShare(url) }

    private fun dismissContentPreview() {
        uiState.contentPreviewRequest = null
    }

    private fun getUrlFromIntent(intent: android.content.Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            else -> intent.data?.toString()
        }
    }
}
