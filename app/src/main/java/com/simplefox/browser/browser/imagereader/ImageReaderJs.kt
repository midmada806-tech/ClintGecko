package com.simplefox.browser.browser.imagereader

import org.json.JSONObject

/** JavaScript snippets run in the page behind the reader. All return a JSON string. */
object ImageReaderJs {

    /** Scrolls the page to trigger lazy loading / infinite scroll, then restores position. */
    const val SCROLL = """(function(){
if(window.__ddRun)return;window.__ddRun=1;window.__ddDone=0;
var y0=window.scrollY,stuck=0;
var t=setInterval(function(){
 window.scrollBy(0,Math.round(window.innerHeight*0.9));
 var cur=window.scrollY+window.innerHeight,h=document.documentElement.scrollHeight;
 if(cur>=h-5){stuck++;}else{stuck=0;}
 if(stuck>=4){clearInterval(t);window.scrollTo(0,y0);window.__ddDone=1;window.__ddRun=0;}
},300);
})()"""

    // Helpers shared by collect / navOnly / autoMap.
    private const val COMMON = """
function abs(u){try{return new URL(u,document.baseURI).href;}catch(e){return '';}}
function esc(s){return s.replace(/[-\/\\^.*+?()|[\]{}]/g,'\\$&');}
function ruleUrl(rule,kind){
 rule=(rule||'').trim();
 if(!rule)return null;
 if(/^https?:/i.test(rule)){
  if(rule.indexOf('{n}')<0)return rule;
  var parts=rule.split('{n}');
  var re=new RegExp('^'+parts.map(esc).join('(\\d+)')+'$');
  var m=location.href.match(re);
  if(!m)return '';
  var n=parseInt(m[1],10)+(kind==='next'?1:-1);
  if(n<0)return '';
  return parts.join(String(n));
 }
 var el=null;
 try{el=document.querySelector(rule);}catch(e){return '';}
 if(!el)return '';
 var a=el.closest?el.closest('a[href]'):null;
 if(!a&&el.querySelector)a=el.querySelector('a[href]');
 return a?a.href:'';
}
function autoEl(kind){
 var re=kind==='next'
  ?/^(next|next chapter|next ep\w*|ต่อไป|ถัดไป|ตอนต่อไป|ตอนถัดไป|siguiente|suivant|[>›»→]+)/i
  :/^(prev|previous|prev chapter|previous chapter|ก่อนหน้า|ตอนก่อนหน้า|ย้อนกลับ|anterior|précédent|[<‹«←]+)/i;
 var r=document.querySelector('a[rel~="'+kind+'"][href]');
 if(r&&r.href!==location.href)return r;
 var as=document.querySelectorAll('a[href]');
 for(var i=0;i<as.length;i++){var a=as[i];var h=a.getAttribute('href')||'';
  if(!h||h.charAt(0)==='#'||/^javascript:/i.test(h))continue;
  var t=(a.textContent||a.getAttribute('aria-label')||a.title||'').replace(/\s+/g,' ').trim();
  var m=t.match(re);
  if(m&&m[0].length>=t.length-2&&a.href!==location.href)return a;}
 return null;
}
function autoUrl(kind){
 var a=autoEl(kind);
 if(a)return a.href;
 var l=document.querySelector('link[rel~="'+kind+'"][href]');
 return (l&&l.href&&l.href!==location.href)?l.href:'';
}
function nav(kind){
 var rule=kind==='next'?RULES.next:RULES.prev;
 var u=ruleUrl(rule,kind);
 if(u!==null)return u;          // manual rule wins, even if it finds nothing
 return autoUrl(kind);
}
"""

    private fun rulesVar(prevRule: String, nextRule: String) =
        "var RULES={prev:${JSONObject.quote(prevRule)},next:${JSONObject.quote(nextRule)}};\n"

    fun navOnly(prevRule: String, nextRule: String): String =
        "(function(){" + rulesVar(prevRule, nextRule) + COMMON +
            "return JSON.stringify({prev:nav('prev'),next:nav('next')});})()"

    fun collect(rules: SiteRules): String =
        "(function(){" + rulesVar(rules.prev, rules.next) +
            "var INC=${JSONObject.quote(rules.include)}.split('\\n'),BLK=${JSONObject.quote(rules.block)}.split('\\n');\n" +
            COMMON + """
function best(ss){if(!ss)return '';var b='',bw=-1;ss.split(',').forEach(function(p){var t=p.trim().split(/\s+/);if(!t[0])return;var w=parseFloat(t[1])||0;if(w>=bw){bw=w;b=t[0];}});return b;}
function mk(line){line=(line||'').trim();if(!line)return null;
 if(line.indexOf('css:')===0)return {css:line.slice(4).trim()};
 if(line.indexOf('re:')===0){try{return {re:new RegExp(line.slice(3).trim(),'i')};}catch(e){return null;}}
 if(line.indexOf('*')>=0){try{return {re:new RegExp('^'+line.split('*').map(esc).join('.*')+'$','i')};}catch(e){return null;}}
 return {sub:line.toLowerCase()};}
function hit(m,url,el){
 if(m.css){try{return !!el&&(el.matches(m.css)||(el.closest&&!!el.closest(m.css)));}catch(e){return false;}}
 if(m.re)return m.re.test(url);
 return url.toLowerCase().indexOf(m.sub)>=0;}
var IM=INC.map(mk).filter(Boolean),BM=BLK.map(mk).filter(Boolean);
var ICON_RE=/(^|[\/_.-])(icon|icons|logo|avatar|sprite|favicon|emoji|banner|button|badge|loading|placeholder)([\/_.?-]|$)/i;
var seen={},out=[],all=[];
function add(u,el){u=abs(u);if(!/^https?:/i.test(u)||seen[u])return;seen[u]=1;all.push(u);
 for(var k=0;k<BM.length;k++){if(hit(BM[k],u,el))return;}
 if(IM.length){var ok=false;for(var q=0;q<IM.length;q++){if(hit(IM[q],u,el)){ok=true;break;}}if(!ok)return;}
 else if(ICON_RE.test(u))return;
 out.push(u);}
var els=document.querySelectorAll('img,div,section,a,span,li,figure'),n=0;
for(var i=0;i<els.length&&n<6000;i++,n++){var el=els[i];
 if(el.tagName==='IMG'){
  var lazy=el.getAttribute('data-src')||el.getAttribute('data-original')||el.getAttribute('data-lazy-src')||el.getAttribute('data-url')||best(el.getAttribute('data-srcset'));
  var src=lazy||best(el.getAttribute('srcset'))||el.currentSrc||el.src;
  if(!src||/^data:/i.test(src))continue;
  var loaded=el.complete&&el.naturalWidth>0;
  if(loaded&&(el.naturalWidth<250||el.naturalHeight<150))continue;
  if(!loaded&&!lazy&&(el.width&&el.width<250))continue;
  add(src,el);
 } else {
  var r=el.getBoundingClientRect();
  if(r.width<250||r.height<250)continue;
  var bg=getComputedStyle(el).backgroundImage;
  var m=bg&&bg!=='none'?bg.match(/url\(["']?([^"')]+)["']?\)/):null;
  if(m&&!/^data:/i.test(m[1]))add(m[1],el);
 }
}
return JSON.stringify({title:document.title||'',images:out,all:all,done:!!window.__ddDone,prev:nav('prev'),next:nav('next')});
})()"""

    /**
     * Auto map: detect previous/next links and describe them as reusable rules
     * (CSS selector and/or URL pattern with {n}) the user can keep for the whole site.
     */
    val AUTO_MAP: String = "(function(){var RULES={prev:'',next:''};" + COMMON + """
function cssPath(el){
 if(el.id){
  try{var idSel='#'+CSS.escape(el.id);if(document.querySelectorAll(idSel).length===1)return idSel;}catch(e){}
 }
 var parts=[],e=el,depth=0;
 while(e&&e.nodeType===1&&e!==document.body&&depth<5){
  var s=e.tagName.toLowerCase();
  var rel=e.tagName==='A'?(e.getAttribute('rel')||'').split(' ')[0]:'';
  if(rel){s+='[rel~="'+rel+'"]';}
  else{
   var cls=(typeof e.className==='string'?e.className.split(/\s+/):[]).filter(function(c){return c&&!/\d/.test(c);}).slice(0,2);
   cls.forEach(function(c){try{s+='.'+CSS.escape(c);}catch(x){}});
  }
  var test=parts.concat([s]).join(' > ');
  var uniq=false;try{uniq=document.querySelectorAll(test).length===1;}catch(x){}
  if(!uniq&&e.parentElement){
   var idx=1,sib=e;while((sib=sib.previousElementSibling)){if(sib.tagName===e.tagName)idx++;}
   s+=':nth-of-type('+idx+')';
  }
  parts.unshift(s);
  try{if(document.querySelectorAll(parts.join(' > ')).length===1)return parts.join(' > ');}catch(x){}
  e=e.parentElement;depth++;
 }
 return parts.join(' > ');
}
function tpl(u){
 if(!u)return '';
 var cur=location.href,dc=cur.match(/\d+/g)||[],du=u.match(/\d+/g)||[];
 if(!dc.length||dc.length!==du.length)return '';
 var pc=cur.split(/\d+/),pu=u.split(/\d+/);
 if(pc.join('\u0001')!==pu.join('\u0001'))return '';
 var idx=-1;
 for(var i=0;i<dc.length;i++){if(dc[i]!==du[i]){if(idx>=0)return '';idx=i;}}
 if(idx<0)return '';
 var out='';
 for(var j=0;j<pc.length;j++){out+=pc[j];if(j<dc.length)out+=(j===idx?'{n}':dc[j]);}
 return out;
}
function describe(kind){
 var a=autoEl(kind),url=a?a.href:autoUrl(kind);
 return {url:url||'',sel:a?cssPath(a):'',tpl:tpl(url)};
}
var p=describe('prev'),n=describe('next');
return JSON.stringify({prevUrl:p.url,prevSel:p.sel,prevTpl:p.tpl,nextUrl:n.url,nextSel:n.sel,nextTpl:n.tpl});
})()"""
}
