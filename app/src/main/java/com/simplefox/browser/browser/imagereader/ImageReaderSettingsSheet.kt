package com.simplefox.browser.browser.imagereader

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplefox.browser.R
import com.simplefox.browser.browser.MainActivity
import com.simplefox.browser.browser.sheets.ContentPreviewRequest
import com.simplefox.browser.ui.theme.LocalClintColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageReaderSettingsSheet(
    activity: MainActivity,
    request: ContentPreviewRequest,
    prefs: ImageReaderPrefs,
    onPrefsChange: (ImageReaderPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalClintColors.current
    val context = LocalContext.current
    val host = remember(request.url) { ImageReaderStore.hostOf(request.url) }
    val saved = remember(host) { ImageReaderStore.rules(context, host) }
    var prevRule by remember { mutableStateOf(saved.prev) }
    var nextRule by remember { mutableStateOf(saved.next) }
    var includeRules by remember { mutableStateOf(saved.include) }
    var blockRules by remember { mutableStateOf(saved.block) }
    val groups = remember(request.allImages) {
        request.allImages.groupingBy { patternFor(it) }.eachCount().entries
            .sortedByDescending { it.value }.take(8)
    }
    var detectedPrev by remember { mutableStateOf(request.prevUrl) }
    var detectedNext by remember { mutableStateOf(request.nextUrl) }
    var mapping by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.popupBackground
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(stringResource(R.string.image_reader_settings), color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            SectionLabel(stringResource(R.string.image_reader_mode_label))
            ChipRow {
                Choice(!prefs.paged, stringResource(R.string.image_reader_webtoon)) { onPrefsChange(prefs.copy(paged = false)) }
                Choice(prefs.paged, stringResource(R.string.image_reader_paged)) { onPrefsChange(prefs.copy(paged = true)) }
            }

            if (prefs.paged) {
                SectionLabel(stringResource(R.string.image_reader_direction))
                ChipRow {
                    Choice(!prefs.rtl, stringResource(R.string.image_reader_ltr)) { onPrefsChange(prefs.copy(rtl = false)) }
                    Choice(prefs.rtl, stringResource(R.string.image_reader_rtl)) { onPrefsChange(prefs.copy(rtl = true)) }
                }
                SectionLabel(stringResource(R.string.image_reader_fit))
                ChipRow {
                    Choice(!prefs.fitWidth, stringResource(R.string.image_reader_fit_screen)) { onPrefsChange(prefs.copy(fitWidth = false)) }
                    Choice(prefs.fitWidth, stringResource(R.string.image_reader_fit_width)) { onPrefsChange(prefs.copy(fitWidth = true)) }
                }
                SwitchRow(stringResource(R.string.image_reader_tap_zones), prefs.tapZones) { onPrefsChange(prefs.copy(tapZones = it)) }
                SwitchRow(stringResource(R.string.image_reader_page_number), prefs.showPageNumber) { onPrefsChange(prefs.copy(showPageNumber = it)) }
            } else {
                SectionLabel(stringResource(R.string.image_reader_gap))
                ChipRow {
                    listOf(0, 4, 8, 16).forEach { g ->
                        Choice(prefs.gapPx == g, "$g px") { onPrefsChange(prefs.copy(gapPx = g)) }
                    }
                }
            }

            SectionLabel(stringResource(R.string.image_reader_background))
            ChipRow {
                Choice(prefs.background == ImageReaderPrefs.BG_BLACK, stringResource(R.string.image_reader_bg_black)) { onPrefsChange(prefs.copy(background = ImageReaderPrefs.BG_BLACK)) }
                Choice(prefs.background == ImageReaderPrefs.BG_DARK, stringResource(R.string.image_reader_bg_dark)) { onPrefsChange(prefs.copy(background = ImageReaderPrefs.BG_DARK)) }
                Choice(prefs.background == ImageReaderPrefs.BG_WHITE, stringResource(R.string.image_reader_bg_white)) { onPrefsChange(prefs.copy(background = ImageReaderPrefs.BG_WHITE)) }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.image_reader_links_title, host.ifEmpty { "—" }),
                color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.image_reader_links_help),
                color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = prevRule,
                onValueChange = { prevRule = it },
                label = { Text(stringResource(R.string.image_reader_prev_rule)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nextRule,
                onValueChange = { nextRule = it },
                label = { Text(stringResource(R.string.image_reader_next_rule)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val none = stringResource(R.string.image_reader_none)
            Text(stringResource(R.string.image_reader_detected_prev, detectedPrev.ifEmpty { none }), color = colors.secondaryText, fontSize = 11.sp, maxLines = 2)
            Text(stringResource(R.string.image_reader_detected_next, detectedNext.ifEmpty { none }), color = colors.secondaryText, fontSize = 11.sp, maxLines = 2)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (mapping) return@FilledTonalButton
                        mapping = true
                        activity.runImageReaderAutoMap { r ->
                            mapping = false
                            if (r == null || (r.prevUrl.isEmpty() && r.nextUrl.isEmpty())) {
                                Toast.makeText(context, R.string.image_reader_auto_map_failed, Toast.LENGTH_SHORT).show()
                            } else {
                                // Prefer a selector (follows the site's own links); fall back to a URL pattern.
                                prevRule = r.prevSelector.ifEmpty { r.prevTemplate }
                                nextRule = r.nextSelector.ifEmpty { r.nextTemplate }
                                // Image map: if several image sources exist, keep only the dominant one.
                                if (includeRules.isBlank() && groups.size >= 2) includeRules = groups[0].key
                                detectedPrev = r.prevUrl
                                detectedNext = r.nextUrl
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.image_reader_auto_map)) }
                OutlinedButton(
                    onClick = {
                        prevRule = ""; nextRule = ""
                        ImageReaderStore.saveRules(context, host, saved.copy(prev = "", next = "", include = includeRules, block = blockRules))
                        activity.refreshImageReaderNav("", "")
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.image_reader_clear_rules)) }
                Button(
                    onClick = {
                        ImageReaderStore.saveRules(context, host, SiteRules(prevRule, nextRule, includeRules, blockRules))
                        activity.refreshImageReaderNav(prevRule.trim(), nextRule.trim())
                        Toast.makeText(context, R.string.image_reader_rules_saved, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.image_reader_save_rules)) }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.image_reader_images_title), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.image_reader_images_help),
                color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            groups.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.key, color = colors.onSurface, fontSize = 12.sp, maxLines = 1)
                        Text(stringResource(R.string.image_reader_group_count, g.value), color = colors.secondaryText, fontSize = 11.sp)
                    }
                    TextButton(onClick = { includeRules = addLine(includeRules, g.key) }) { Text(stringResource(R.string.image_reader_only)) }
                    TextButton(onClick = { blockRules = addLine(blockRules, g.key) }) { Text(stringResource(R.string.image_reader_block)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = includeRules,
                onValueChange = { includeRules = it },
                label = { Text(stringResource(R.string.image_reader_include_rules)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = blockRules,
                onValueChange = { blockRules = it },
                label = { Text(stringResource(R.string.image_reader_block_rules)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    ImageReaderStore.saveRules(context, host, SiteRules(prevRule, nextRule, includeRules, blockRules))
                    // Close the reader and scan the page again with the new image rules.
                    activity.uiState.contentPreviewRequest = null
                    activity.startImageReader()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.image_reader_apply_images)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalClintColors.current
    Text(text, color = colors.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalClintColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Reusable pattern for an image URL: host + leading path segments that contain no digits, then '*'. */
private fun patternFor(url: String): String {
    val u = runCatching { java.net.URI(url) }.getOrNull() ?: return url
    val host = u.host ?: return url
    val segs = (u.path ?: "").split('/').filter { it.isNotEmpty() }
    val dirs = if (segs.isEmpty()) segs else segs.dropLast(1)
    val keep = dirs.takeWhile { seg -> seg.none { it.isDigit() } }
    return u.scheme + "://" + host + "/" + keep.joinToString("/") + (if (keep.isEmpty()) "" else "/") + "*"
}

private fun addLine(existing: String, line: String): String {
    val lines = existing.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return if (line in lines) existing else (lines + line).joinToString("\n")
}
