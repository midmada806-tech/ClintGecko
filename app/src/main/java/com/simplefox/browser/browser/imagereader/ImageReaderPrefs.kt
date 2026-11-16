package com.simplefox.browser.browser.imagereader

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject

/** User-facing reader settings. Stored in default shared preferences. */
data class ImageReaderPrefs(
    val paged: Boolean = false,        // false = webtoon (vertical scroll), true = one page at a time
    val rtl: Boolean = false,          // paged only: right-to-left (manga)
    val fitWidth: Boolean = false,     // paged only: false = fit whole page on screen, true = fit width
    val background: String = BG_BLACK, // black | dark | white
    val gapPx: Int = 0,                // webtoon only: space between images
    val tapZones: Boolean = true,      // paged only: tap left/right edge to turn page
    val showPageNumber: Boolean = true // paged only
) {
    companion object {
        const val BG_BLACK = "black"
        const val BG_DARK = "dark"
        const val BG_WHITE = "white"
    }
}

data class SiteRules(
    val prev: String = "",
    val next: String = "",
    val include: String = "",
    val block: String = ""
) {
    fun isEmpty() = prev.isBlank() && next.isBlank() && include.isBlank() && block.isBlank()
}

object ImageReaderStore {
    private const val K_PAGED = "image_reader_paged"
    private const val K_RTL = "image_reader_rtl"
    private const val K_FIT_WIDTH = "image_reader_fit_width"
    private const val K_BG = "image_reader_bg"
    private const val K_GAP = "image_reader_gap"
    private const val K_TAP = "image_reader_tap_zones"
    private const val K_PAGE_NUM = "image_reader_page_number"
    private const val K_RULES = "image_reader_rules"

    fun load(context: Context): ImageReaderPrefs {
        val p = PreferenceManager.getDefaultSharedPreferences(context)
        return ImageReaderPrefs(
            paged = p.getBoolean(K_PAGED, false),
            rtl = p.getBoolean(K_RTL, false),
            fitWidth = p.getBoolean(K_FIT_WIDTH, false),
            background = p.getString(K_BG, ImageReaderPrefs.BG_BLACK) ?: ImageReaderPrefs.BG_BLACK,
            gapPx = p.getInt(K_GAP, 0),
            tapZones = p.getBoolean(K_TAP, true),
            showPageNumber = p.getBoolean(K_PAGE_NUM, true)
        )
    }

    fun save(context: Context, prefs: ImageReaderPrefs) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(K_PAGED, prefs.paged)
            .putBoolean(K_RTL, prefs.rtl)
            .putBoolean(K_FIT_WIDTH, prefs.fitWidth)
            .putString(K_BG, prefs.background)
            .putInt(K_GAP, prefs.gapPx)
            .putBoolean(K_TAP, prefs.tapZones)
            .putBoolean(K_PAGE_NUM, prefs.showPageNumber)
            .apply()
    }

    /** Per-site rules. Empty string = automatic. include/block hold one pattern per line. */
    fun rules(context: Context, host: String): SiteRules {
        if (host.isEmpty()) return SiteRules()
        val raw = PreferenceManager.getDefaultSharedPreferences(context).getString(K_RULES, null) ?: return SiteRules()
        val o = runCatching { JSONObject(raw).optJSONObject(host) }.getOrNull() ?: return SiteRules()
        return SiteRules(
            prev = o.optString("prev", ""),
            next = o.optString("next", ""),
            include = o.optString("inc", ""),
            block = o.optString("blk", "")
        )
    }

    fun saveRules(context: Context, host: String, rules: SiteRules) {
        if (host.isEmpty()) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val all = runCatching { JSONObject(sp.getString(K_RULES, null) ?: "{}") }.getOrDefault(JSONObject())
        if (rules.isEmpty()) {
            all.remove(host)
        } else {
            all.put(
                host,
                JSONObject()
                    .put("prev", rules.prev.trim()).put("next", rules.next.trim())
                    .put("inc", rules.include.trim()).put("blk", rules.block.trim())
            )
        }
        sp.edit().putString(K_RULES, all.toString()).apply()
    }

    fun hostOf(url: String): String = runCatching { java.net.URL(url).host }.getOrDefault("")
}
