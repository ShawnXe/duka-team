package ke.co.khefkis.dukabubble

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object Web {
    @SuppressLint("SetJavaScriptEnabled")
    fun setup(w: WebView, ctx: Context) {
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        w.setBackgroundColor(0xFF0A1424.toInt())
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val host = Uri.parse(Prefs.baseUrl(ctx)).host
                val h = r.url.host ?: return false
                if (h == host || h.endsWith("firebaseapp.com") || h.endsWith("googleapis.com") || h.endsWith("google.com")) return false
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, r.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
                return true
            }
        }
    }
}
