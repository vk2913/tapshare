package com.tapshare.app

import android.content.Context

object Store {
    const val DEFAULT_USER = "vshalkamat"

    fun prefs(c: Context) = c.getSharedPreferences("tapshare", Context.MODE_PRIVATE)

    /** Accepts "vshalkamat", "@vshalkamat" or a pasted instagram.com link and returns just the username. */
    fun cleanUser(raw: String): String {
        var u = raw.trim().removePrefix("@")
        u = u.substringBefore("?").substringBefore("#").trimEnd('/')
        if (u.contains("instagram.com/")) u = u.substringAfter("instagram.com/").substringBefore("/")
        return u.removePrefix("@").filter { it.isLetterOrDigit() || it == '.' || it == '_' }
    }

    /** The link that will be shared when someone taps your phone. */
    fun url(c: Context): String {
        val p = prefs(c)
        if (p.getString("mode", "insta") == "custom") return (p.getString("custom", "") ?: "").trim()
        val u = cleanUser(p.getString("user", DEFAULT_USER) ?: DEFAULT_USER)
        return if (u.isEmpty()) "" else "https://www.instagram.com/$u"
    }

    /** True if the current link fits in the NDEF tag we emulate. */
    fun fits(c: Context): Boolean = payload(url(c)).size <= 240

    private val PREFIXES = listOf(
        "https://www." to 2, "http://www." to 1, "https://" to 4, "http://" to 3
    )

    private fun payload(url: String): ByteArray {
        var code = 0
        var rest = url
        for ((pre, id) in PREFIXES) {
            if (url.startsWith(pre)) { code = id; rest = url.removePrefix(pre); break }
        }
        return byteArrayOf(code.toByte()) + rest.toByteArray(Charsets.UTF_8)
    }

    /** Builds the NDEF file: 2-byte length + one URI record. */
    fun ndef(c: Context): ByteArray {
        val url = url(c)
        if (url.isBlank()) return byteArrayOf(0, 0)
        val payload = payload(url)
        if (payload.size > 240) return byteArrayOf(0, 0)
        val rec = byteArrayOf(0xD1.toByte(), 0x01, payload.size.toByte(), 0x55) + payload
        return byteArrayOf((rec.size shr 8).toByte(), rec.size.toByte()) + rec
    }
}
