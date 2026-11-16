package com.simplefox.browser.browser.menu
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowForward

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.preference.PreferenceManager
import com.simplefox.browser.R
import com.simplefox.browser.bookmarks.BookmarkManager
import com.simplefox.browser.browser.MainActivity
import com.simplefox.browser.browser.sheets.LongPressContentMaxWidth
import com.simplefox.browser.downloads.ClintDownloadManager
import com.simplefox.browser.downloads.DownloadStatus
import com.simplefox.browser.settings.sitepermissions.SitePermissionDatabase
import com.simplefox.browser.settings.sitepermissions.SitePermissionManager
import com.simplefox.browser.settings.datasaver.DataSaverSiteException
import com.simplefox.browser.ui.ClintDialogStatusBarEffect
import com.simplefox.browser.ui.listscreen.PopupShape
import com.simplefox.browser.ui.theme.LocalClintColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MenuPopupWidth = 260.dp
private const val MenuPopupColumns = 3
// Space kept between the menu and the address bar so the bar is never covered.
private val MenuBarClearance = 20.dp
private const val MenuGridMinCellWidthDp = 72

internal data class BrowserMenuPageState(
    val isBookmarked: Boolean,
    val isDataSaverExceptionForSite: Boolean,
    val openInAppEnabled: Boolean,
    val openInAppLabel: String?
)

internal data class BrowserMenuSnapshot(
    val url: String,
    val showNavRow: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val page: BrowserMenuPageState,
    val isLoading: Boolean,
    val isDesktopMode: Boolean,
    val isDataSaverEnabled: Boolean,
    val pendingDownloadCount: Long,
    val mediaCaptureCount: Long,
    val isMediaCaptureEnabled: Boolean,
    val isWebsiteBlockerEnabled: Boolean,
    val canCreateShortcut: Boolean,
    val canToggleTabMute: Boolean,
    val isTabMuted: Boolean,
    val canUseCookiesControl: Boolean,
    val canTranslatePage: Boolean,
    val isPageTranslated: Boolean,
    val visibleMenuItems: List<CustomizableMenuItem>
)

internal class BrowserMenuActions(
    val onGoBack: () -> Unit,
    val onGoForward: () -> Unit,
    val onHome: () -> Unit,
    val onRefreshOrStop: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onNewTab: () -> Unit,
    val onIncognito: () -> Unit,
    val onNewProfileTab: () -> Unit,
    val onOpenProfiles: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInApp: () -> Unit,
    val onCreateShortcut: () -> Unit,
    val onOpenShortcutManager: () -> Unit,
    val onDownloads: () -> Unit,
    val onOpenDownloadSettings: () -> Unit,
    val onMediaCapture: () -> Unit,
    val onExtensionClick: (String, Boolean) -> Unit,
    val onOpenExtensionSettings: (String) -> Unit,
    val onOpenExtensionsSettings: () -> Unit,
    val onBookmarks: () -> Unit,
    val onHistory: () -> Unit,
    val onDesktopMode: () -> Unit,
    val onSettings: () -> Unit,
    val onReaderMode: () -> Unit,
    val onImageReader: () -> Unit,
    val onDataSaver: () -> Unit,
    val onOpenDataSaverSettings: () -> Unit,
    val onOpenDesktopModeSettings: () -> Unit,
    val onOpenDataSaverSiteSettings: () -> Unit,
    val onDisableDataSaverForSite: () -> Unit,
    val onToggleTabMute: () -> Unit,
    val onWebsiteBlocker: () -> Unit,
    val onOpenWebsiteBlockerSettings: () -> Unit,
    val onCookiesControl: () -> Unit,
    val onTranslate: () -> Unit,
    val onOpenTranslationSettings: () -> Unit
)

internal fun MainActivity.buildMenuSnapshot(previous: BrowserMenuSnapshot?): BrowserMenuSnapshot {
    val position = prefs.getString("address_bar_position", "bottom") ?: "bottom"
    val wv = tabManager.activeTab?.webView
    val currentUrl = wv?.url ?: ""
    val cachedPage = previous?.takeIf { it.url == currentUrl }?.page
    val page = BrowserMenuPageState(
        isBookmarked = currentUrl.isNotEmpty() && uiState.hasActiveUrl && uiState.isBookmarked,
        isDataSaverExceptionForSite = cachedPage?.isDataSaverExceptionForSite ?: false,
        openInAppEnabled = cachedPage?.openInAppEnabled ?: false,
        openInAppLabel = cachedPage?.openInAppLabel
    )

    return BrowserMenuSnapshot(
        url = currentUrl,
        showNavRow = position != "split",
        canGoBack = wv?.canGoBack() == true,
        canGoForward = wv?.canGoForward() == true,
        page = page,
        isLoading = uiState.isPageLoading,
        isDesktopMode = isDesktopMode,
        isDataSaverEnabled = prefs.getBoolean("data_saver_enabled", false),
        pendingDownloadCount = ClintDownloadManager.downloadsFlow.value
            .count { it.status in DownloadStatus.RUNNING_OR_QUEUED }.toLong(),
        mediaCaptureCount = com.simplefox.browser.mediacapture.MediaCaptureStore.countForTab(tabManager.activeTab?.id).toLong(),
        isMediaCaptureEnabled = prefs.getBoolean(com.simplefox.browser.mediacapture.MEDIA_CAPTURE_ENABLED_PREF, true),
        isWebsiteBlockerEnabled = prefs.getBoolean("website_blocker_enabled", false),
        canCreateShortcut = currentUrl.isNotEmpty(),
        canToggleTabMute = wv != null && (wv.audioState.audible || wv.audioState.muted),
        isTabMuted = wv?.audioState?.muted == true,
        canUseCookiesControl = tabManager.activeTab?.isIncognito == false &&
            (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")),
        canTranslatePage = currentUrl.startsWith("http://") || currentUrl.startsWith("https://"),
        isPageTranslated = wv != null && wv.translator.state.value.isRequested,
        visibleMenuItems = BrowserMenuCustomizationStore.visibleOrderedItems(prefs)
    )
}

internal suspend fun MainActivity.loadMenuPageState(url: String): BrowserMenuPageState {
    val webClient = tabManager.activeTab?.webView?.webViewClient
    val appContext = applicationContext
    return withContext(Dispatchers.IO) {
        val uri = url.takeIf { it.isNotEmpty() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val appMatches = if (uri != null && webClient != null && (uri.scheme == "http" || uri.scheme == "https")) {
            runCatching { webClient.resolveAppMatches(uri, appContext) }.getOrDefault(emptyList())
        } else emptyList()
        val openInAppLabel = if (appMatches.size == 1) {
            val appLabel = runCatching { appMatches[0].loadLabel(appContext.packageManager).toString() }.getOrNull()
            appLabel?.let { getString(R.string.menu_open_in_named_app, it) }
        } else null
        BrowserMenuPageState(
            isBookmarked = url.isNotEmpty() && runCatching { BookmarkManager.isBookmarked(appContext, url) }.getOrDefault(false),
            isDataSaverExceptionForSite = runCatching { DataSaverSiteException.isExceptedUrl(appContext, url) }.getOrDefault(false),
            openInAppEnabled = appMatches.isNotEmpty(),
            openInAppLabel = openInAppLabel
        )
    }
}

internal fun MainActivity.buildMenuActions(dismiss: () -> Unit): BrowserMenuActions = BrowserMenuActions(
    onGoBack = { dismiss(); onMenuGoBack() },
    onGoForward = { dismiss(); onMenuGoForward() },
    onHome = { dismiss(); onMenuHome() },
    onRefreshOrStop = { dismiss(); onMenuRefreshOrStop() },
    onToggleBookmark = { dismiss(); onMenuToggleBookmark() },
    onNewTab = { dismiss(); onMenuNewTab() },
    onIncognito = { dismiss(); onMenuIncognito() },
    onNewProfileTab = { dismiss(); onMenuNewProfileTab() },
    onOpenProfiles = { dismiss(); onMenuOpenProfiles() },
    onShare = { dismiss(); onMenuShare() },
    onOpenInApp = { dismiss(); onMenuOpenInApp() },
    onCreateShortcut = { dismiss(); onMenuCreateShortcut() },
    onOpenShortcutManager = { dismiss(); onMenuOpenShortcutManager() },
    onDownloads = { dismiss(); onMenuDownloads() },
    onOpenDownloadSettings = { dismiss(); onMenuOpenDownloadSettings() },
    onMediaCapture = { dismiss(); onMenuMediaCapture() },
    onExtensionClick = { id, actionable -> dismiss(); onMenuExtensionClick(id, actionable) },
    onOpenExtensionSettings = { id -> dismiss(); onMenuOpenExtensionSettings(id) },
    onOpenExtensionsSettings = { dismiss(); onMenuOpenExtensionsSettings() },
    onBookmarks = { dismiss(); onMenuBookmarks() },
    onHistory = { dismiss(); onMenuHistory() },
    onDesktopMode = { dismiss(); onMenuDesktopMode() },
    onSettings = { dismiss(); onMenuSettings() },
    onReaderMode = { dismiss(); onMenuReaderMode() },
    onImageReader = { dismiss(); onMenuImageReader() },
    onDataSaver = { dismiss(); onMenuDataSaver() },
    onOpenDataSaverSettings = { dismiss(); onMenuOpenDataSaverSettings() },
    onOpenDesktopModeSettings = { dismiss(); onMenuOpenDesktopModeSettings() },
    onOpenDataSaverSiteSettings = { dismiss(); onMenuOpenDataSaverSiteSettings() },
    onDisableDataSaverForSite = { dismiss(); onMenuDisableDataSaverForSite() },
    onToggleTabMute = { dismiss(); onMenuToggleTabMute() },
    onWebsiteBlocker = { dismiss(); onMenuWebsiteBlocker() },
    onOpenWebsiteBlockerSettings = { dismiss(); onMenuOpenWebsiteBlockerSettings() },
    onCookiesControl = { dismiss(); onMenuCookiesControl() },
    onTranslate = { dismiss(); onMenuTranslate() },
    onOpenTranslationSettings = { dismiss(); onMenuOpenTranslationSettings() }
)

@Composable
internal fun MenuTriggerButton(activity: MainActivity, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<BrowserMenuSnapshot?>(null) }
    val colors = LocalClintColors.current
    val configuration = LocalConfiguration.current
    val maxMenuHeight = configuration.screenHeightDp.dp * 0.7f
    val menuWidth = MenuPopupWidth.coerceAtMost((configuration.screenWidthDp - 24).dp)
    val actions = remember(activity) { activity.buildMenuActions(dismiss = { expanded = false }) }

    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        val url = snapshot?.url ?: return@LaunchedEffect
        val page = activity.loadMenuPageState(url)
        snapshot = snapshot?.let { current -> if (current.url == url) current.copy(page = page) else current }
    }

    Box(modifier = modifier) {
        IconButton(onClick = {
            snapshot = activity.buildMenuSnapshot(snapshot)
            expanded = true
        }) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.menu),
                tint = colors.iconTint
            )
        }

        val currentSnapshot = snapshot
        if (expanded && currentSnapshot != null) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val gapPx = with(density) { MenuBarClearance.roundToPx() }
            val edgePx = with(density) { 8.dp.roundToPx() }
            val positionProvider = remember(gapPx, edgePx) {
                object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize
                    ): androidx.compose.ui.unit.IntOffset {
                        val x = (anchorBounds.right - popupContentSize.width)
                            .coerceIn(edgePx, (windowSize.width - popupContentSize.width - edgePx).coerceAtLeast(edgePx))
                        val anchorInBottomHalf = anchorBounds.center.y > windowSize.height / 2
                        // Bar at bottom: open above the whole bar. Bar at top: open below it.
                        val y = if (anchorInBottomHalf) {
                            (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(edgePx)
                        } else {
                            anchorBounds.bottom + gapPx
                        }
                        return androidx.compose.ui.unit.IntOffset(x, y)
                    }
                }
            }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                androidx.compose.material3.Surface(
                    color = colors.popupBackground,
                    shape = PopupShape,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(menuWidth).heightIn(max = maxMenuHeight)
                ) {
                    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        BrowserMenuContent(currentSnapshot, actions, columns = MenuPopupColumns)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserMenuBottomSheet(
    snapshot: BrowserMenuSnapshot,
    actions: BrowserMenuActions,
    onDismissRequest: () -> Unit
) {
    val colors = LocalClintColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val hideStatusBar = remember {
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hide_status_bar", false)
    }
    val hideSystemNavigation = remember {
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("hide_system_navigation", false)
    }
    val listState = rememberLazyListState()

    val flingBoundaryConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val maxSheetHeight = if (isPortrait) {
        (configuration.screenHeightDp.dp * 0.8f).coerceAtLeast(320.dp)
    } else {
        (configuration.screenHeightDp.dp - 96.dp).coerceAtLeast(320.dp)
    }
    val sheetWidthDp = configuration.screenWidthDp.coerceAtMost(LongPressContentMaxWidth.value.toInt())
    val columns = ((sheetWidthDp - 16) / MenuGridMinCellWidthDp).coerceIn(MenuPopupColumns, 5)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.popupBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) }
    ) {
        ClintDialogStatusBarEffect(hideStatusBar, hideSystemNavigation)
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).nestedScroll(flingBoundaryConnection),
            state = listState
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    BrowserMenuContent(snapshot, actions, columns = columns, modifier = Modifier.fillMaxWidth().widthIn(max = LongPressContentMaxWidth))
                }
            }
        }
    }
}

private sealed interface MenuGridEntry {
    data class Item(val item: CustomizableMenuItem) : MenuGridEntry
    object Settings : MenuGridEntry
}

@Composable
private fun BrowserMenuContent(
    snapshot: BrowserMenuSnapshot,
    actions: BrowserMenuActions,
    columns: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalClintColors.current
    val context = LocalContext.current
    val menuPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var extensionsExpanded by remember { mutableStateOf(menuPrefs.getBoolean("extensions_menu_expanded", false)) }
    val onToggleExtensions = {
        extensionsExpanded = !extensionsExpanded
        menuPrefs.edit().putBoolean("extensions_menu_expanded", extensionsExpanded).apply()
    }
    val profilesSupported = com.simplefox.browser.profiles.WebProfiles.isSupported()
    val entries = buildList<MenuGridEntry> {
        snapshot.visibleMenuItems.forEach { item ->
            val shown = when (item) {
                CustomizableMenuItem.NEW_PROFILE_TAB -> profilesSupported
                CustomizableMenuItem.MUTE_TAB -> snapshot.canToggleTabMute
                else -> true
            }
            if (shown) add(MenuGridEntry.Item(item))
        }
        add(MenuGridEntry.Settings)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.popupBackground)
            .padding(vertical = 4.dp)
    ) {
        entries.chunked(columns).forEach { rowEntries ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                rowEntries.forEach { entry ->
                    when (entry) {
                        MenuGridEntry.Settings -> MenuGridCell(
                            androidx.compose.material.icons.Icons.Filled.Settings,
                            stringResource(R.string.settings),
                            onClick = actions.onSettings
                        )
                        is MenuGridEntry.Item -> MenuGridCellFor(entry.item, snapshot, actions, extensionsExpanded, onToggleExtensions)
                    }
                }
                repeat(columns - rowEntries.size) { Spacer(Modifier.weight(1f)) }
            }
            val hasExtensions = rowEntries.any { it is MenuGridEntry.Item && it.item == CustomizableMenuItem.EXTENSIONS }
            if (hasExtensions) {
                ExtensionsMenuPanel(
                    expanded = extensionsExpanded,
                    onExtensionClick = actions.onExtensionClick,
                    onExtensionSettings = actions.onOpenExtensionSettings,
                    onManage = actions.onOpenExtensionsSettings
                )
            }
        }
        if (snapshot.showNavRow) {
            MenuDivider()
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuNavIcon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), snapshot.canGoBack, actions.onGoBack)
                MenuNavIcon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward), snapshot.canGoForward, actions.onGoForward)
                MenuNavIcon(androidx.compose.material.icons.Icons.Filled.Home, stringResource(R.string.home), true, actions.onHome)
                MenuNavIcon(
                    if (snapshot.isLoading) androidx.compose.material.icons.Icons.Filled.Close else androidx.compose.material.icons.Icons.Filled.Refresh,
                    stringResource(R.string.refresh), true, actions.onRefreshOrStop
                )
                MenuNavIcon(
                    if (snapshot.page.isBookmarked) androidx.compose.material.icons.Icons.Filled.Bookmark else androidx.compose.material.icons.Icons.Filled.BookmarkBorder,
                    stringResource(R.string.content_desc_bookmark), true, actions.onToggleBookmark
                )
            }
        }
    }
}

@Composable
private fun RowScope.MenuGridCellFor(
    item: CustomizableMenuItem,
    snapshot: BrowserMenuSnapshot,
    actions: BrowserMenuActions,
    extensionsExpanded: Boolean,
    onToggleExtensions: () -> Unit
) {
    when (item) {
        CustomizableMenuItem.NEW_TAB -> MenuGridCell(item.icon(), stringResource(item.titleRes()), onClick = actions.onNewTab)
        CustomizableMenuItem.NEW_INCOGNITO_TAB -> MenuGridCell(item.icon(), stringResource(item.titleRes()), onClick = actions.onIncognito)
        CustomizableMenuItem.NEW_PROFILE_TAB -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            onClick = actions.onNewProfileTab,
            onLongClick = actions.onOpenProfiles
        )
        CustomizableMenuItem.SHARE -> MenuGridCell(item.icon(), stringResource(item.titleRes()), onClick = actions.onShare)
        CustomizableMenuItem.OPEN_IN_APP -> MenuGridCell(
            item.icon(),
            snapshot.page.openInAppLabel ?: stringResource(item.titleRes()),
            enabled = snapshot.page.openInAppEnabled,
            onClick = actions.onOpenInApp
        )
        CustomizableMenuItem.CREATE_SHORTCUT -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            enabled = snapshot.canCreateShortcut,
            onClick = actions.onCreateShortcut,
            onLongClick = actions.onOpenShortcutManager
        )
        CustomizableMenuItem.DOWNLOADS -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            badge = if (snapshot.pendingDownloadCount > 0L) com.simplefox.browser.ui.status.formatCompactCount(snapshot.pendingDownloadCount) else null,
            onClick = actions.onDownloads,
            onLongClick = actions.onOpenDownloadSettings
        )
        CustomizableMenuItem.MEDIA_CAPTURE -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            checked = snapshot.isMediaCaptureEnabled,
            onClick = actions.onMediaCapture
        )
        CustomizableMenuItem.EXTENSIONS -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            onClick = onToggleExtensions,
            onLongClick = actions.onOpenExtensionsSettings,
            trailing = { ExtensionsChevron(extensionsExpanded) }
        )
        CustomizableMenuItem.WEBSITE_BLOCKER -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            checked = snapshot.isWebsiteBlockerEnabled,
            onClick = actions.onWebsiteBlocker,
            onLongClick = actions.onOpenWebsiteBlockerSettings
        )
        CustomizableMenuItem.BOOKMARKS -> MenuGridCell(item.icon(), stringResource(item.titleRes()), onClick = actions.onBookmarks)
        CustomizableMenuItem.HISTORY -> MenuGridCell(item.icon(), stringResource(item.titleRes()), onClick = actions.onHistory)
        CustomizableMenuItem.READER_MODE -> MenuGridCell(item.icon(), stringResource(item.titleRes()), onClick = actions.onReaderMode)
        CustomizableMenuItem.IMAGE_READER -> MenuGridCell(item.icon(), stringResource(item.titleRes()), onClick = actions.onImageReader)
        CustomizableMenuItem.TRANSLATE -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            enabled = snapshot.canTranslatePage,
            checked = snapshot.isPageTranslated,
            onClick = actions.onTranslate,
            onLongClick = actions.onOpenTranslationSettings
        )
        CustomizableMenuItem.DESKTOP_MODE -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            checked = snapshot.isDesktopMode,
            onClick = actions.onDesktopMode,
            onLongClick = actions.onOpenDesktopModeSettings
        )
        CustomizableMenuItem.DATA_SAVER -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            checked = snapshot.isDataSaverEnabled,
            onClick = actions.onDataSaver,
            onLongClick = actions.onOpenDataSaverSettings
        )
        CustomizableMenuItem.MUTE_TAB -> MenuGridCell(
            if (snapshot.isTabMuted) androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp else item.icon(),
            stringResource(if (snapshot.isTabMuted) R.string.menu_unmute_tab else item.titleRes()),
            onClick = actions.onToggleTabMute
        )
        CustomizableMenuItem.COOKIES_CONTROL -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            enabled = snapshot.canUseCookiesControl,
            onClick = actions.onCookiesControl
        )
        CustomizableMenuItem.DISABLE_DATA_SAVER_FOR_SITE -> MenuGridCell(
            item.icon(),
            stringResource(item.titleRes()),
            checked = snapshot.page.isDataSaverExceptionForSite,
            onClick = actions.onDisableDataSaverForSite,
            onLongClick = actions.onOpenDataSaverSiteSettings
        )
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        color = LocalClintColors.current.popupStroke,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

@Composable
private fun RowScope.MenuNavIcon(iconRes: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalClintColors.current
    IconButton(onClick = onClick, modifier = Modifier.weight(1f).fillMaxHeight()) {
        Icon(
            imageVector = iconRes,
            contentDescription = description,
            tint = colors.iconTint,
            modifier = Modifier.alpha(if (enabled) 1.0f else 0.38f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.MenuGridCell(
    iconRes: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    checked: Boolean? = null,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colors = LocalClintColors.current
    val isOn = checked == true
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(12.dp))
            .let { if (enabled) it.combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick) else it }
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 2.dp, vertical = 5.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.size(36.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isOn) colors.primary else colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconRes,
                        contentDescription = null,
                        tint = if (isOn) colors.onPrimary else colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isOn) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(colors.popupBackground)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.popupCheck,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                if (badge != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .defaultMinSize(minWidth = 13.dp)
                            .height(13.dp)
                            .clip(RoundedCornerShape(6.5.dp))
                            .background(colors.primary)
                            .padding(horizontal = 3.dp)
                    ) {
                        Text(
                            text = badge,
                            color = colors.onPrimary,
                            fontSize = 7.sp,
                            lineHeight = 7.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }
            Text(
                text = label,
                color = colors.popupText,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (trailing != null) {
            Box(Modifier.align(Alignment.TopEnd)) { trailing() }
        }
    }
}
