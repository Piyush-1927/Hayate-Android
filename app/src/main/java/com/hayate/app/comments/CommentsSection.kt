package com.hayate.app.comments

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val ALLOWED = setOf("piyush-1927.github.io", "giscus.app", "github.com")

// Reports the page's content height (CSS px == dp) so the card grows instead of scrolling inside itself.
private const val HEIGHT_JS = """
(function(){
  if (window.__hh) return; window.__hh = 1;
  function send(){
    var h = Math.max(document.body.getBoundingClientRect().height, document.body.scrollHeight);
    document.querySelectorAll('iframe').forEach(function(e){
      var b = e.getBoundingClientRect().bottom + window.scrollY;
      if (b > h) h = b;
    });
    h = Math.ceil(h) + 2;
    if (h > 0) HayateH.h(h);
  }
  new ResizeObserver(send).observe(document.body);
  window.addEventListener('message', function(){ setTimeout(send, 60); });
  send(); setInterval(send, 500);
})();
"""

private class HeightBridge(private val onHeight: (Int) -> Unit) {
    @JavascriptInterface
    fun h(px: Int) {
        android.util.Log.d("HAYATE_C", "page height reported: " + px)
        Handler(Looper.getMainLooper()).post { onHeight(px) }
    }
}

@Composable
fun CommentsSection(
    anilistId: Int,
    episode: Int?,
    bg: Color,
    panel: Color,
    border: Color,
    accent: Color,
    text: Color,
    muted: Color,
) {
    val ctx = LocalContext.current
    val url = CommentsWeb.pageUrl(anilistId, episode, dark = bg.luminance() < 0.5f)
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Comments", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = text)
            Spacer(Modifier.weight(1f))
            if (episode != null) Text("Episode $episode", fontSize = 12.sp, color = accent)
        }
        if (url != null) {
            Box(Modifier.fillMaxWidth().clip(shape).background(panel).border(1.dp, border, shape)) {
                CommentsWebView(url, panel.toArgb())
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(shape).background(panel).border(1.dp, border, shape).padding(14.dp)) {
                Text("No discussion for this anime yet.", fontSize = 13.sp, color = muted)
                Text(
                    "Request one", fontSize = 12.sp, color = accent,
                    modifier = Modifier.clickable {
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(CommentsWeb.REQUEST_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, CommentsWeb.requestSubject(anilistId))
                            putExtra(Intent.EXTRA_TEXT, CommentsWeb.requestBody(anilistId))
                        }
                        try { ctx.startActivity(i) } catch (_: Exception) {}
                    }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CommentsWebView(url: String, bgColor: Int) {
    val ctx = LocalContext.current
    var heightDp by remember { mutableIntStateOf(300) }
    fun external(u: Uri) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        factory = { c ->
            WebView(c).apply {
                setBackgroundColor(bgColor)
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(true)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(HeightBridge { px ->
                    val n = px.coerceAtLeast(120)
                    if (kotlin.math.abs(n - heightDp) > 1) heightDp = n
                }, "HayateH")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, u: String?) {
                        v.evaluateJavascript(HEIGHT_JS, null)
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        if (r.url.host in ALLOWED) return false
                        external(r.url); return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    // links that open a new window (avatars, "view on GitHub") go to the browser
                    override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, msg: Message): Boolean {
                        val t = WebView(v.context)
                        t.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(w: WebView, r: WebResourceRequest): Boolean {
                                external(r.url); return true
                            }
                        }
                        (msg.obj as WebView.WebViewTransport).webView = t
                        msg.sendToTarget()
                        return true
                    }
                }
                tag = url
                loadUrl(url)
            }
        },
        update = { if (it.tag != url) { it.tag = url; it.loadUrl(url) } },
    )
}
