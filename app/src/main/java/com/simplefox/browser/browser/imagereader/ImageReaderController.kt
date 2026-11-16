package com.simplefox.browser.browser.imagereader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.simplefox.browser.R
import com.simplefox.browser.browser.MainActivity
import com.simplefox.browser.browser.sheets.ContentPreviewRequest
import org.json.JSONObject
import org.json.JSONTokener

internal object ImageReaderState {
    @Volatile var busy = false
    @Volatile var autoOpenAfterLoad = false
}

data class AutoMapResult(
    val prevUrl: String, val prevSelector: String, val prevTemplate: String,
    val nextUrl: String, val nextSelector: String, val nextTemplate: String
)

private fun parseJson(raw: String?): JSONObject? = runCatching {
    val v = JSONTokener(raw ?: "").nextValue()
    JSONObject(if (v is String) v else v?.toString().orEmpty())
}.getOrNull()

/** Collects the page's images (after auto-scrolling for lazy loading) and opens the reader. */
internal fun MainActivity.startImageReader() {
    if (ImageReaderState.busy) return
    val wv = tabManager.activeTab?.webView ?: return
    val pageUrl = wv.url ?: return
    val collectJs = ImageReaderJs.collect(ImageReaderStore.rules(this, ImageReaderStore.hostOf(pageUrl)))

    ImageReaderState.busy = true
    Toast.makeText(this, R.string.image_reader_loading, Toast.LENGTH_SHORT).show()
    wv.evaluateJavascript(ImageReaderJs.SCROLL, null)

    val handler = Handler(Looper.getMainLooper())
    val start = SystemClock.uptimeMillis()
    var lastCount = -1
    var stable = 0
    lateinit var poll: Runnable
    poll = Runnable {
        if (isFinishing) { ImageReaderState.busy = false; return@Runnable }
        wv.evaluateJavascript(collectJs) { raw ->
            val json = parseJson(raw)
            val count = json?.optJSONArray("images")?.length() ?: 0
            val done = json?.optBoolean("done", false) == true
            if (count == lastCount) stable++ else { stable = 0; lastCount = count }
            val timedOut = SystemClock.uptimeMillis() - start > 25000
            if ((done && stable >= 2) || timedOut) {
                handler.post {
                    ImageReaderState.busy = false
                    if (!isFinishing) openImageReader(pageUrl, json)
                }
            } else {
                handler.postDelayed(poll, 700)
            }
        }
    }
    handler.postDelayed(poll, 800)
}

private fun MainActivity.openImageReader(pageUrl: String, json: JSONObject?) {
    val arr = json?.optJSONArray("images")
    if (json == null || arr == null || arr.length() == 0) {
        Toast.makeText(this, R.string.image_reader_unavailable, Toast.LENGTH_SHORT).show()
        // Keep the reader reachable so the user can fix include/block rules.
        val allArr0 = json?.optJSONArray("all")
        if (json != null && allArr0 != null && allArr0.length() > 0) {
            val all0 = List(allArr0.length()) { allArr0.getString(it) }
            uiState.contentPreviewRequest = ContentPreviewRequest.forImageReader(
                pageUrl, json.optString("title", ""), ImageReaderHtml.build(emptyList(), ImageReaderStore.load(this)),
                json.optString("prev", ""), json.optString("next", ""), emptyList(), all0
            )
        }
        return
    }
    val images = List(arr.length()) { arr.getString(it) }
    val allArr = json.optJSONArray("all")
    val allImages = if (allArr != null) List(allArr.length()) { allArr.getString(it) } else images
    val html = ImageReaderHtml.build(images, ImageReaderStore.load(this))
    uiState.contentPreviewRequest = ContentPreviewRequest.forImageReader(
        pageUrl = pageUrl,
        pageTitle = json.optString("title", ""),
        html = html,
        prevUrl = json.optString("prev", ""),
        nextUrl = json.optString("next", ""),
        images = images,
        allImages = allImages
    )
}

/** Loads [url] in the current tab and reopens the reader when the page finishes loading. */
internal fun MainActivity.openImageReaderForUrl(url: String) {
    val wv = tabManager.activeTab?.webView ?: return
    ImageReaderState.autoOpenAfterLoad = true
    wv.loadUrl(url)
    Handler(Looper.getMainLooper()).postDelayed({ ImageReaderState.autoOpenAfterLoad = false }, 30000)
}

/** Detects previous/next links on the page and describes them as reusable site rules. */
fun MainActivity.runImageReaderAutoMap(onResult: (AutoMapResult?) -> Unit) {
    val wv = tabManager.activeTab?.webView
    if (wv == null) { onResult(null); return }
    wv.evaluateJavascript(ImageReaderJs.AUTO_MAP) { raw ->
        val j = parseJson(raw)
        val result = j?.let {
            AutoMapResult(
                it.optString("prevUrl"), it.optString("prevSel"), it.optString("prevTpl"),
                it.optString("nextUrl"), it.optString("nextSel"), it.optString("nextTpl")
            )
        }
        runOnUiThread { onResult(result) }
    }
}

/** Re-resolves the previous/next URLs on the source page using the given manual rules. */
fun MainActivity.refreshImageReaderNav(prevRule: String, nextRule: String) {
    val wv = tabManager.activeTab?.webView ?: return
    wv.evaluateJavascript(ImageReaderJs.navOnly(prevRule, nextRule)) { raw ->
        val j = parseJson(raw) ?: return@evaluateJavascript
        runOnUiThread {
            val current = uiState.contentPreviewRequest ?: return@runOnUiThread
            if (!current.isImageReader) return@runOnUiThread
            uiState.contentPreviewRequest = current.copy(
                prevUrl = j.optString("prev", ""),
                nextUrl = j.optString("next", "")
            )
        }
    }
}
