package com.simplefox.browser.browser.imagereader

import org.json.JSONArray

object ImageReaderHtml {
    private fun bgColor(p: ImageReaderPrefs) = when (p.background) {
        ImageReaderPrefs.BG_WHITE -> "#ffffff"
        ImageReaderPrefs.BG_DARK -> "#1b1b1f"
        else -> "#000000"
    }

    private fun fgColor(p: ImageReaderPrefs) = if (p.background == ImageReaderPrefs.BG_WHITE) "#555555" else "#aaaaaa"

    fun build(images: List<String>, p: ImageReaderPrefs): String =
        if (p.paged) buildPaged(images, p) else buildWebtoon(images, p)

    private fun esc(u: String) = u.replace("&", "&amp;").replace("\"", "&quot;")

    private fun buildWebtoon(images: List<String>, p: ImageReaderPrefs): String {
        val tags = StringBuilder()
        images.forEach { tags.append("<img src=\"").append(esc(it)).append("\" loading=\"lazy\" alt=\"\">\n") }
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer">
<style>
html,body{margin:0;padding:0;background:${bgColor(p)};}
img{display:block;width:100%;height:auto;margin:0 auto ${p.gapPx}px auto;min-height:120px;}
@media (min-width:900px){img{max-width:820px;}}
.end{color:${fgColor(p)};font:14px sans-serif;text-align:center;padding:28px 0 80px;}
</style>
</head>
<body>$tags<div class="end">${images.size} images</div></body>
</html>"""
    }

    private fun buildPaged(images: List<String>, p: ImageReaderPrefs): String {
        val json = JSONArray(images).toString().replace("</", "<\\/")
        val fit = if (p.fitWidth) "width:100%;height:auto;" else "max-width:100%;max-height:100vh;width:auto;height:auto;"
        val align = if (p.fitWidth) "" else "display:flex;align-items:center;justify-content:center;height:100vh;"
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer">
<style>
html,body{margin:0;padding:0;background:${bgColor(p)};}
#v{$align}
#im{display:block;margin:0 auto;$fit}
#pg{position:fixed;left:50%;bottom:10px;transform:translateX(-50%);background:rgba(0,0,0,.6);color:#fff;font:13px sans-serif;padding:3px 12px;border-radius:12px;display:${if (p.showPageNumber) "block" else "none"};pointer-events:none;}
</style>
</head>
<body>
<div id="v"><img id="im" alt=""></div>
<div id="pg"></div>
<script>
var L=$json,i=0,RTL=${p.rtl},TAP=${p.tapZones};
var im=document.getElementById('im'),pg=document.getElementById('pg');
function pre(k){if(k>=0&&k<L.length){var o=new Image();o.src=L[k];}}
function show(){
  if(i<0)i=0;if(i>=L.length)i=L.length-1;
  im.src=L[i];window.scrollTo(0,0);
  pg.textContent=(i+1)+' / '+L.length;
  pre(i+1);pre(i+2);
}
function step(d){var n=i+d;if(n<0||n>=L.length)return;i=n;show();}
document.addEventListener('click',function(e){
  if(!TAP)return;
  var x=e.clientX/window.innerWidth;
  if(x<0.35){step(RTL?1:-1);}
  else if(x>0.65){step(RTL?-1:1);}
});
show();
</script>
</body>
</html>"""
    }
}
