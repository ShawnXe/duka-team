package ke.co.khefkis.dukabubble

import android.content.Context
import android.net.Uri

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("duka", Context.MODE_PRIVATE)

    fun baseUrl(c: Context): String = sp(c).getString("url", "") ?: ""

    fun save(c: Context, raw: String) {
        var u = raw.trim()
        if (u.isNotEmpty() && !u.startsWith("http")) u = "https://$u"
        sp(c).edit().putString("url", u).apply()
    }

    /** The app address with one extra query parameter, e.g. ?quick=1 */
    fun withParam(c: Context, key: String, value: String): String {
        val b = baseUrl(c)
        if (b.isEmpty()) return ""
        return Uri.parse(b).buildUpon().appendQueryParameter(key, value).build().toString()
    }
}
