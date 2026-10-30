package ke.co.khefkis.dukabubble

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Setup screen: app address, permissions, start/stop the bubble. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var url: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(if (primary) 0xFF032A26.toInt() else 0xFFE8EFF9.toInt())
        setBackgroundColor(if (primary) 0xFF14B8A6.toInt() else 0xFF17294A.toInt())
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF08101F.toInt()
        window.navigationBarColor = 0xFF08101F.toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
            setBackgroundColor(0xFF0A1424.toInt())
        }
        col.addView(TextView(this).apply {
            text = "🛒 Duka Quick Sale"; textSize = 26f; setTextColor(0xFFE8EFF9.toInt())
        })
        col.addView(TextView(this).apply {
            text = "A floating button that opens the Sales panel over any app."
            setTextColor(0xFF8FA5C4.toInt()); setPadding(0, dp(6), 0, dp(16))
        })
        col.addView(TextView(this).apply { text = "Your Duka app address"; setTextColor(0xFFE8EFF9.toInt()) })
        url = EditText(this).apply {
            hint = "https://your-duka-site"
            setText(Prefs.baseUrl(this@MainActivity))
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(0xFFE8EFF9.toInt()); setHintTextColor(0xFF5F7799.toInt())
        }
        col.addView(url)
        col.addView(btn("Save address") { Prefs.save(this, url.text.toString()); refresh(); toast("Saved") })
        col.addView(btn("1. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(btn("2. Allow notifications and camera") {
            val p = mutableListOf(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(p.toTypedArray(), 2)
        })
        col.addView(btn("3. Open the app and log in") {
            Prefs.save(this, url.text.toString())
            if (Prefs.baseUrl(this).isEmpty()) toast("Type your app address first")
            else startActivity(Intent(this, AppActivity::class.java))
        })
        col.addView(btn("Start the floating bubble", primary = true) {
            Prefs.save(this, url.text.toString())
            when {
                Prefs.baseUrl(this).isEmpty() -> toast("Type your app address first")
                !Settings.canDrawOverlays(this) -> toast("Do step 1 first")
                else -> { startForegroundService(Intent(this, OverlayService::class.java)); toast("Bubble started. Open any app.") }
            }
            refresh()
        })
        col.addView(btn("Stop the bubble") {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP)); refresh()
        })
        status = TextView(this).apply { setTextColor(0xFF8FA5C4.toInt()); setPadding(0, dp(20), 0, 0); textSize = 15f }
        col.addView(status)
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(0xFF0A1424.toInt()) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        fun tick(ok: Boolean) = if (ok) "✅" else "⬜"
        val cam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        status.text = "${tick(Prefs.baseUrl(this).isNotEmpty())} App address saved\n" +
            "${tick(Settings.canDrawOverlays(this))} Display over other apps allowed\n" +
            "${tick(cam)} Camera allowed\n" +
            "${tick(OverlayService.running)} Bubble running"
    }
}
