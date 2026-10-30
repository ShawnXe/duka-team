package ke.co.khefkis.dukabubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/** Draws the floating bubble and the Quick Sale panel over other apps. */
class OverlayService : Service() {
    companion object {
        const val ACTION_STOP = "ke.co.khefkis.dukabubble.STOP"
        const val ACTION_TOGGLE = "ke.co.khefkis.dukabubble.TOGGLE"
        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: PanelLayout? = null
    private var web: WebView? = null
    private var panelShown = false
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (bubble == null) addBubble()
        if (intent?.action == ACTION_TOGGLE) togglePanel()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bubble", "Quick sale bubble", NotificationManager.IMPORTANCE_LOW))
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, OverlayService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "bubble")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Duka Quick Sale is on")
            .setContentText("Tap Quick sale to open the panel from any app")
            .setOngoing(true)
            .addAction(0, "Quick sale", action(ACTION_TOGGLE, 1))
            .addAction(0, "Stop", action(ACTION_STOP, 2))
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addBubble() {
        val size = dp(58)
        val tv = TextView(this).apply {
            text = "🛒"
            textSize = 26f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF14B8A6.toInt())
                setStroke(dp(2), 0xFF0B1220.toInt())
            }
            elevation = dp(6).toFloat()
        }
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(4); y = dp(240) }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) moved = true
                    if (moved) { lp.x = (sx + dx).toInt(); lp.y = (sy + dy).toInt(); wm.updateViewLayout(v, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel() else {
                        val w = resources.displayMetrics.widthPixels
                        lp.x = if (lp.x + size / 2 < w / 2) dp(4) else w - size - dp(4)
                        wm.updateViewLayout(v, lp)
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(tv, lp)
        bubble = tv; bubbleLp = lp
    }

    /** Back key closes the panel. */
    private class PanelLayout(ctx: Context, val onBack: () -> Unit) : LinearLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    inner class Bridge {
        @JavascriptInterface fun saleDone() { main.post { hidePanel() } }
        @JavascriptInterface fun close() { main.post { hidePanel() } }
        @JavascriptInterface fun openScanner() { main.post { openApp("scan") } }
    }

    private fun openApp(param: String?) {
        val url = if (param != null) Prefs.withParam(this, param, "1") else Prefs.baseUrl(this)
        hidePanel()
        startActivity(Intent(this, AppActivity::class.java).putExtra("url", url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun headerButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(0xFFE8EFF9.toInt())
        textSize = 20f
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun buildPanel(): Boolean {
        val quickUrl = Prefs.withParam(this, "quick", "1")
        if (quickUrl.isEmpty()) {
            Toast.makeText(this, "Set your app address first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val root = PanelLayout(this) { hidePanel() }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A1424.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF08101F.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "⚡ Quick sale"
            setTextColor(0xFF2DD4BF.toInt())
            textSize = 16f
            setPadding(dp(16), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        header.addView(headerButton("⤢") { openApp(null) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        header.addView(headerButton("✕") { hidePanel() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        val w = WebView(this)
        Web.setup(w, this)
        w.addJavascriptInterface(Bridge(), "AndroidBridge")
        w.loadUrl(quickUrl)
        root.addView(w, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        panel = root; web = w
        return true
    }

    private fun togglePanel() = if (panelShown) hidePanel() else showPanel()

    private fun showPanel() {
        if (panelShown) return
        if (panel == null && !buildPanel()) return
        val h = (resources.displayMetrics.heightPixels * 0.78).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(panel, lp)
        panelShown = true
        bubble?.visibility = View.INVISIBLE
        web?.onResume()
    }

    private fun hidePanel() {
        if (!panelShown) return
        try { wm.removeView(panel) } catch (e: Exception) { }
        panelShown = false
        bubble?.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        running = false
        hidePanel()
        try { bubble?.let { wm.removeView(it) } } catch (e: Exception) { }
        bubble = null
        web?.destroy(); web = null; panel = null
        super.onDestroy()
    }
}
