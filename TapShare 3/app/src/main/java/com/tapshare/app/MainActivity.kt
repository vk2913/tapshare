package com.tapshare.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var nfcBtn: Button
    private lateinit var user: EditText
    private lateinit var custom: EditText
    private lateinit var instaRb: RadioButton
    private lateinit var customRb: RadioButton

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val p = Store.prefs(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        fun tv(t: String, sz: Float, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = sz; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (8 * d).toInt())
        }
        root.addView(tv("TapShare", 28f, true))
        root.addView(tv("Hold your phone to another phone and your Instagram profile opens on theirs. Nothing to install on their side.", 15f))

        val group = RadioGroup(this)
        instaRb = RadioButton(this).apply { text = "Instagram profile"; id = 1 }
        customRb = RadioButton(this).apply { text = "Custom link (reel, post, other page)"; id = 2 }
        group.addView(instaRb); group.addView(customRb)
        root.addView(group)

        user = EditText(this).apply {
            hint = "Instagram username"
            setText(p.getString("user", Store.DEFAULT_USER))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        custom = EditText(this).apply {
            hint = "https://..."
            setText(p.getString("custom", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        root.addView(user); root.addView(custom)
        if (p.getString("mode", "insta") == "custom") customRb.isChecked = true else instaRb.isChecked = true
        group.setOnCheckedChangeListener { _, _ -> updateFields() }

        val saveBtn = Button(this).apply { text = "Save"; setOnClickListener { save() } }
        val testBtn = Button(this).apply {
            text = "Test link"
            setOnClickListener {
                val u = Store.url(this@MainActivity)
                if (u.isEmpty()) return@setOnClickListener
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                catch (e: ActivityNotFoundException) { toast("No app can open this link") }
            }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(saveBtn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(testBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)

        status = tv("", 15f).apply { setPadding(0, (16 * d).toInt(), 0, (8 * d).toInt()) }
        root.addView(status)
        nfcBtn = Button(this).apply { text = "Turn on NFC"; setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } }
        root.addView(nfcBtn)
        root.addView(tv("Keep the screen on and unlocked while tapping. Touch the backs of the phones together and hold for a second.", 13f))

        setContentView(ScrollView(this).apply { addView(root) })
        updateFields()
    }

    private fun updateFields() {
        user.visibility = if (customRb.isChecked) View.GONE else View.VISIBLE
        custom.visibility = if (customRb.isChecked) View.VISIBLE else View.GONE
    }

    private fun save() {
        val mode = if (customRb.isChecked) "custom" else "insta"
        val c = custom.text.toString().trim()
        val u = Store.cleanUser(user.text.toString())
        if (mode == "custom" && !(c.startsWith("http://") || c.startsWith("https://"))) { toast("Link must start with http:// or https://"); return }
        if (mode == "insta" && u.isEmpty()) { toast("Enter your username"); return }
        Store.prefs(this).edit().putString("mode", mode).putString("user", u).putString("custom", c).apply()
        if (mode == "insta") user.setText(u)
        if (!Store.fits(this)) { toast("That link is too long to share by tap"); }
        else toast("Saved")
        refresh()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val nfc = NfcAdapter.getDefaultAdapter(this)
        val url = Store.url(this)
        nfcBtn.visibility = View.GONE
        status.text = when {
            nfc == null -> "This phone has no NFC."
            !nfc.isEnabled -> { nfcBtn.visibility = View.VISIBLE; "NFC is off. Turn it on to share." }
            url.isEmpty() -> "Enter your details and tap Save."
            !Store.fits(this) -> "This link is too long to share by tap. Use a shorter one."
            else -> "Ready. Tapping opens:\n$url"
        }
    }
}
