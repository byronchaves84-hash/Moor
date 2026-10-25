package com.moorty.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Minimal, card-based UI: one primary action per live platform and optional settings. */
class MainActivity : Activity() {
    private val bg = Color.rgb(14, 16, 22)
    private val panel = Color.rgb(27, 30, 39)
    private val accent = Color.rgb(255, 79, 112)
    private val muted = Color.rgb(171, 177, 191)
    private lateinit var username: EditText
    private lateinit var tiktokButton: Button
    private lateinit var tiktokStatus: TextView
    private lateinit var youtubeButton: Button
    private lateinit var youtubeStatus: TextView
    private lateinit var settingsPanel: LinearLayout
    private lateinit var apiKey: EditText
    private lateinit var channelId: EditText
    private lateinit var normalSound: TextView
    private lateinit var subscriberSound: TextView
    private lateinit var rate: SeekBar
    private lateinit var volume: SeekBar
    private var tiktokConnected = false
    private var youtubeConnected = false
    private var normalSoundUri: Uri? = null
    private var subscriberSoundUri: Uri? = null
    private var tiktokConnector: TikTokConnector? = null
    private var youtubeConnector: YouTubeConnector? = null
    private val prefs by lazy { getSharedPreferences("moorty", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 22, 20, 26) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(label("MOORTY VOICE", 29, Color.WHITE, true))
        content.addView(label("Tu voz para los directos", 15, muted))
        content.addView(space(18))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 15, 16, 15); background = rounded(panel, 18)
        }
        hero.addView(label("TIKTOK LIVE", 17, accent, true))
        hero.addView(label("Usuario del directo", 13, muted))
        username = EditText(this).apply {
            hint = "Ej. tocolita"; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 17f
            setSingleLine(true); setPadding(12, 8, 12, 8); background = rounded(Color.rgb(38, 41, 52), 10)
            setText(prefs.getString("tt_user", "tocolita"))
        }
        val userParams = LinearLayout.LayoutParams(-1, -2); userParams.topMargin = dp(5); userParams.bottomMargin = dp(12)
        hero.addView(username, userParams)
        tiktokButton = primaryButton("Conectar TikTok") { toggleTikTok() }
        hero.addView(tiktokButton, LinearLayout.LayoutParams(-1, dp(50)))
        tiktokStatus = label("Desconectado", 13, muted)
        hero.addView(tiktokStatus)
        content.addView(hero)
        content.addView(space(14))

        val ytCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 15, 16, 15); background = rounded(panel, 18) }
        ytCard.addView(label("YOUTUBE LIVE", 17, Color.WHITE, true))
        ytCard.addView(label("Conecta tu canal desde Configuración", 13, muted))
        youtubeButton = secondaryButton("Conectar YouTube") { toggleYouTube() }
        val ytButtonParams = LinearLayout.LayoutParams(-1, dp(46)); ytButtonParams.topMargin = dp(10)
        ytCard.addView(youtubeButton, ytButtonParams)
        youtubeStatus = label("Desconectado", 13, muted)
        ytCard.addView(youtubeStatus)
        content.addView(ytCard)
        content.addView(space(14))

        val settingsToggle = secondaryButton("⚙  Configuración y sonidos  ▾") {
            settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        content.addView(settingsToggle, LinearLayout.LayoutParams(-1, dp(46)))

        settingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(14, 12, 14, 14); background = rounded(panel, 16)
        }
        val settingsParams = LinearLayout.LayoutParams(-1, -2); settingsParams.topMargin = dp(8)
        content.addView(settingsPanel, settingsParams)
        settingsPanel.addView(label("VOZ", 15, Color.WHITE, true))
        settingsPanel.addView(label("Velocidad", 13, muted))
        rate = SeekBar(this).apply { max = 15; progress = prefs.getInt("rate", 5); progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent) }
        settingsPanel.addView(rate)
        settingsPanel.addView(label("Volumen", 13, muted))
        volume = SeekBar(this).apply { max = 100; progress = prefs.getInt("volume", 100); progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent) }
        settingsPanel.addView(volume)

        settingsPanel.addView(label("SONIDOS DE ENTRADA", 15, Color.WHITE, true))
        normalSound = clickableSetting("Entrada normal · elegir archivo") { chooseSound(false) }
        subscriberSound = clickableSetting("Suscriptor · elegir archivo") { chooseSound(true) }
        normalSound.text = if (prefs.getString("normal_sound", null) != null) "Entrada normal · sonido seleccionado" else "Entrada normal · elegir archivo"
        subscriberSound.text = if (prefs.getString("subscriber_sound", null) != null) "Suscriptor · sonido seleccionado" else "Suscriptor · elegir archivo"
        settingsPanel.addView(normalSound)
        settingsPanel.addView(subscriberSound)

        settingsPanel.addView(label("YOUTUBE · API", 15, Color.WHITE, true))
        apiKey = editField("YouTube Data API key", prefs.getString("yt_key", "").orEmpty())
        channelId = editField("ID del canal (UC…)", prefs.getString("yt_channel", "").orEmpty())
        settingsPanel.addView(apiKey)
        settingsPanel.addView(channelId)
        settingsPanel.addView(secondaryButton("Guardar configuración") { saveSettings() })
        settingsPanel.addView(secondaryButton("Probar voz") {
            saveSettings(showMessage = false)
            VoiceService.speak(this, "Prueba de voz. Moorty Voice está listo.", 0.5f + rate.progress / 10f, volume.progress / 100f)
        })
        settingsPanel.addView(label("Nota: los eventos de TikTok dependen de una librería comunitaria no oficial. El sonido de suscriptor solo se distingue cuando TikTok entrega ese dato.", 12, muted))

        setContentView(root)
    }

    private fun toggleTikTok() {
        if (tiktokConnected) {
            tiktokConnector?.stop(); tiktokConnector = null; tiktokConnected = false
            tiktokButton.text = "Conectar TikTok"; tiktokStatus.text = "Desconectado"; return
        }
        val handle = username.text.toString().trim().removePrefix("@").trim()
        if (handle.isBlank()) { tiktokStatus.text = "Escribe el usuario del directo."; return }
        prefs.edit().putString("tt_user", handle).putInt("rate", rate.progress).putInt("volume", volume.progress).apply()
        tiktokConnector?.stop()
        tiktokConnector = TikTokConnector(this, handle,
            { prefs.getString("normal_sound", null) },
            { prefs.getString("subscriber_sound", null) }) { message ->
            runOnUiThread {
                tiktokStatus.text = message
                if (message.contains("desconectado", true)) {
                    tiktokConnected = false; tiktokButton.text = "Conectar TikTok"
                } else if (message.contains("conectado", true) || message.contains("conectando", true) || message.contains("intentando conectar", true)) {
                    tiktokConnected = true; tiktokButton.text = "Desconectar TikTok"
                }
            }
        }
        tiktokConnected = true; tiktokButton.text = "Desconectar TikTok"; tiktokStatus.text = "Conectando a @$handle…"
        tiktokConnector?.start()
    }

    private fun toggleYouTube() {
        if (youtubeConnected) {
            youtubeConnector?.stop(); youtubeConnector = null; youtubeConnected = false
            youtubeButton.text = "Conectar YouTube"; youtubeStatus.text = "Desconectado"; return
        }
        val key = apiKey.text.toString().trim(); val channel = channelId.text.toString().trim()
        if (key.isBlank() || channel.isBlank()) {
            settingsPanel.visibility = View.VISIBLE
            youtubeStatus.text = "Añade la API key y el ID del canal en Configuración."; return
        }
        saveSettings(showMessage = false)
        youtubeConnector?.stop()
        youtubeConnector = YouTubeConnector(this, key, channel) { message ->
            runOnUiThread {
                youtubeStatus.text = message
                if (message.contains("desconectado", true)) { youtubeConnected = false; youtubeButton.text = "Conectar YouTube" }
                else { youtubeConnected = true; youtubeButton.text = "Desconectar YouTube" }
            }
        }
        youtubeConnected = true; youtubeButton.text = "Desconectar YouTube"; youtubeStatus.text = "Conectando…"
        youtubeConnector?.start()
    }

    private fun saveSettings(showMessage: Boolean = true) {
        prefs.edit().putString("yt_key", apiKey.text.toString().trim())
            .putString("yt_channel", channelId.text.toString().trim())
            .putInt("rate", rate.progress).putInt("volume", volume.progress).apply()
        if (showMessage) Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
    }

    private fun chooseSound(subscriber: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, if (subscriber) 42 else 41)
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        if (requestCode == 41) {
            prefs.edit().putString("normal_sound", uri.toString()).apply()
            normalSound.text = "Entrada normal · sonido seleccionado"
        } else if (requestCode == 42) {
            prefs.edit().putString("subscriber_sound", uri.toString()).apply()
            subscriberSound.text = "Suscriptor · sonido seleccionado"
        }
    }

    override fun onDestroy() {
        tiktokConnector?.stop(); youtubeConnector?.stop(); super.onDestroy()
    }

    private fun editField(hintText: String, value: String) = EditText(this).apply {
        hint = hintText; setHintTextColor(muted); setTextColor(Color.WHITE); setSingleLine(true); textSize = 14f
        setText(value); setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(Color.rgb(38, 41, 52), 10)
        val p = LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(6); p.bottomMargin = dp(5); layoutParams = p
    }

    private fun clickableSetting(textValue: String, action: () -> Unit) = TextView(this).apply {
        text = textValue; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(13), dp(12), dp(13))
        background = rounded(Color.rgb(38, 41, 52), 10); setOnClickListener { action() }
        val p = LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(5); p.bottomMargin = dp(4); layoutParams = p
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(2, dp(5), 2, dp(5))
    }

    private fun primaryButton(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue; textSize = 15f; isAllCaps = false; setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(accent); setOnClickListener { action() }
    }

    private fun secondaryButton(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue; textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(54, 58, 72)); setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
