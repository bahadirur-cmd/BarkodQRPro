package com.barkodqrpro

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ValueAnimator
import android.widget.*
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private enum class Mode { BARCODE, QR }

    private var mode = Mode.BARCODE
    private val values = mutableListOf<String>()

    private lateinit var resultImage: ImageView
    private lateinit var resultLabel: TextView
    private lateinit var counterText: TextView
    private lateinit var status: TextView
    private lateinit var speed: SeekBar
    private lateinit var speedText: TextView
    private lateinit var barcodeTab: TextView
    private lateinit var qrTab: TextView
    private lateinit var startButton: TextView
    private lateinit var numberInput: EditText
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var soundButton: TextView
    private lateinit var themeButton: TextView

    // PREMIUM AKIŞ ANİMASYONU
    private lateinit var flowTrack: FrameLayout
    private lateinit var flowSegment: View

    private var flowAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var soundLoaded = false
    private var soundEnabled = true

    private var isDarkTheme = true
    private var currentIndex = 0
    private var running = false
    private var generationToken = 0

    private val handler = Handler(Looper.getMainLooper())
    private val imageExecutor = Executors.newSingleThreadExecutor()

    private val preferences by lazy {
        getSharedPreferences("ur52_preferences", MODE_PRIVATE)
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val PICK_TXT = 41

    // TEMA RENKLERİ

    private var backgroundColor = Color.rgb(15, 19, 24)
    private var cardColor = Color.rgb(29, 36, 44)
    private var innerColor = Color.rgb(20, 26, 33)
    private val orange = Color.rgb(255, 133, 0)
    private val orangeLight = Color.rgb(255, 165, 65)
    private var white = Color.WHITE
    private var muted = Color.rgb(150, 163, 174)
    private var borderColor = Color.rgb(54, 65, 76)
    private var primaryText = Color.WHITE
    private var secondaryText = Color.rgb(220, 225, 230)
    private var statusBarColor = Color.rgb(15, 19, 24)

    private fun applyThemeColors() {
        if (isDarkTheme) {
            backgroundColor = Color.rgb(15, 19, 24)
            cardColor = Color.rgb(29, 36, 44)
            innerColor = Color.rgb(20, 26, 33)
            white = Color.WHITE
            muted = Color.rgb(150, 163, 174)
            borderColor = Color.rgb(54, 65, 76)
            primaryText = Color.WHITE
            secondaryText = Color.rgb(220, 225, 230)
            statusBarColor = Color.rgb(15, 19, 24)
        } else {
            backgroundColor = Color.rgb(242, 245, 248)
            cardColor = Color.WHITE
            innerColor = Color.rgb(235, 239, 243)
            white = Color.rgb(31, 39, 48)
            muted = Color.rgb(105, 117, 128)
            borderColor = Color.rgb(215, 222, 229)
            primaryText = Color.rgb(28, 36, 45)
            secondaryText = Color.rgb(65, 76, 87)
            statusBarColor = Color.rgb(242, 245, 248)
        }

        window.statusBarColor = statusBarColor
        window.navigationBarColor = backgroundColor

        if (android.os.Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility =
                if (isDarkTheme) 0
                else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            window.decorView.systemUiVisibility =
                if (isDarkTheme) 0
                else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun loadThemePreference() {
        isDarkTheme = preferences.getBoolean("dark_theme", true)
        applyThemeColors()
    }

    private fun saveThemePreference() {
        preferences.edit()
            .putBoolean("dark_theme", isDarkTheme)
            .apply()
    }

    // HIZ AYARI KAYDI

    private fun loadSpeedPreference(): Int {
        return preferences.getInt("speed_level", 9).coerceIn(1, 20)
    }

    private fun saveSpeedPreference(level: Int) {
        preferences.edit()
            .putInt("speed_level", level.coerceIn(1, 20))
            .apply()
    }

    private fun toggleTheme() {
        val oldSpeed = if (::speed.isInitialized) speed.progress else 8
        val oldInput = if (::numberInput.isInitialized) {
            numberInput.text.toString()
        } else ""

        val wasRunning = running

        stopPlayback()

        isDarkTheme = !isDarkTheme
        saveThemePreference()
        applyThemeColors()

        buildUi()

        speed.progress = oldSpeed
        numberInput.setText(oldInput)

        updateTabs()
        updateSoundButton()
        updateThemeButton()

        refresh()

        if (wasRunning && values.isNotEmpty()) {
            startPlayback()
        } else {
            status.text = if (isDarkTheme) {
                "Koyu tema etkin."
            } else {
                "Açık tema etkin."
            }
        }
    }

    private val autoRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            if (currentIndex < values.lastIndex) {
                showIndex(currentIndex + 1, true)
                handler.postDelayed(this, getInterval())
            } else {
                stopPlayback()
                status.text = "Son kayda ulaşıldı."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadThemePreference()
        initializeSound()
        showSplashScreen()
    }

    // AÇILIŞ EKRANI

    private fun showSplashScreen() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(backgroundColor)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.barkodqr_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.75f
            scaleY = 0.75f
        }

        splash.addView(logo, LinearLayout.LayoutParams(dp(150), dp(150)))

        val brand = TextView(this).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            typeface = Typeface.create(
                "sans-serif-condensed",
                Typeface.BOLD_ITALIC
            )
            letterSpacing = 0.08f
            alpha = 0f

            text = SpannableString("UR52 CODEX").apply {
                setSpan(
                    ForegroundColorSpan(Color.rgb(220, 225, 230)),
                    0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(orange),
                    2, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(primaryText),
                    5, 10, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        splash.addView(
            brand,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(18)
            }
        )

        val subtitle = TextView(this).apply {
            text = "CODE GENERATION SYSTEM"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(orangeLight)
            letterSpacing = 0.18f
            alpha = 0f
        }

        splash.addView(
            subtitle,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            }
        )

        val lightLine = View(this).apply {
            background = rounded(orange, 4)
            alpha = 0f
            scaleX = 0f
        }

        splash.addView(
            lightLine,
            LinearLayout.LayoutParams(dp(190), dp(3)).apply {
                topMargin = dp(24)
            }
        )

        setContentView(splash)

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(650).start()

        brand.animate().alpha(1f)
            .setStartDelay(350).setDuration(550).start()

        subtitle.animate().alpha(1f)
            .setStartDelay(550).setDuration(550).start()

        lightLine.animate().alpha(1f).scaleX(1f)
            .setStartDelay(650).setDuration(500).start()

        handler.postDelayed({
            if (!isFinishing) {
                buildUi()
                refresh()
            }
        }, 2100)
    }

    // SES SİSTEMİ

    private fun initializeSound() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == soundId) {
                soundLoaded = true
            }
        }

        soundId = soundPool?.load(this, R.raw.ur52_deep_pulse, 1) ?: 0
    }

    private fun playTransitionSound() {
        if (!soundEnabled || !soundLoaded || soundId == 0) return

        soundPool?.play(soundId, 0.85f, 0.85f, 1, 0, 1.0f)
    }

    private fun updateSoundButton() {
        if (!::soundButton.isInitialized) return

        soundButton.text = if (soundEnabled) "♫" else "♪"

        soundButton.background = rounded(
            if (soundEnabled) {
                if (isDarkTheme) Color.rgb(65, 43, 24)
                else Color.rgb(255, 239, 220)
            } else innerColor,
            12,
            if (soundEnabled) orange else borderColor
        )

        soundButton.setTextColor(
            if (soundEnabled) orangeLight else muted
        )

        soundButton.contentDescription =
            if (soundEnabled) "Sesi kapat" else "Sesi aç"
    }

    private fun updateThemeButton() {
        if (!::themeButton.isInitialized) return

        themeButton.text = if (isDarkTheme) "☀" else "☾"
        themeButton.background = rounded(innerColor, 12, borderColor)
        themeButton.setTextColor(if (isDarkTheme) orangeLight else orange)

        themeButton.contentDescription =
            if (isDarkTheme) "Açık temaya geç" else "Koyu temaya geç"
    }

    // TASARIM ARAÇLARI

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int = 16,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()

            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun addPressAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.96f).scaleY(0.96f)
                        .alpha(0.82f).setDuration(90).start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f)
                        .alpha(1f).setDuration(130).start()
                }
            }
            false
        }
    }

    private fun makeButton(
        label: String,
        color: Int,
        textColor: Int = white,
        radius: Int = 12
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = rounded(color, radius)
            setPadding(dp(5), dp(11), dp(5), dp(11))
            isClickable = true
            isFocusable = true
            addPressAnimation(this)
        }
    }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 17, borderColor)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            elevation = dp(if (isDarkTheme) 1 else 2).toFloat()
        }
    }

    private fun sectionTitle(value: String): TextView {
        return text(value, 10f, muted, true).apply {
            letterSpacing = 0.08f
        }
    }

    // ANA ARAYÜZ

    private fun buildUi() {
        val scrollView = ScrollView(this).apply {
            setFillViewport(true)
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            setBackgroundColor(backgroundColor)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(18))
            setBackgroundColor(backgroundColor)
        }

        scrollView.addView(
            page,
            android.widget.FrameLayout.LayoutParams(-1, -1)
        )

        // HEADER

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(13))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.barkodqr_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }

        header.addView(logo)

        val titleArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }

        val brandText = SpannableString("UR52 CODEX").apply {
            setSpan(
                ForegroundColorSpan(Color.rgb(220, 225, 230)),
                0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(orange),
                2, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(primaryText),
                5, 10, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD_ITALIC),
                0, 10, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val brandTitle = text("", 23f, primaryText, true).apply {
            text = brandText
            typeface = Typeface.create(
                "sans-serif-condensed",
                Typeface.BOLD_ITALIC
            )
            letterSpacing = 0.04f
        }

        titleArea.addView(brandTitle)
        titleArea.addView(
            text("CODE GENERATION SYSTEM", 9f, orangeLight, true)
        )

        header.addView(
            titleArea,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        themeButton = TextView(this).apply {
            textSize = 23f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(42)).apply {
                marginEnd = dp(6)
            }
            addPressAnimation(this)
        }

        themeButton.setOnClickListener { toggleTheme() }
        header.addView(themeButton)
        updateThemeButton()

        soundButton = TextView(this).apply {
            textSize = 23f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            addPressAnimation(this)
        }

        soundButton.setOnClickListener {
            soundEnabled = !soundEnabled
            updateSoundButton()
            status.text = if (soundEnabled) {
                "Deep Pulse sesi açıldı."
            } else {
                "Deep Pulse sesi kapatıldı."
            }
        }

        header.addView(soundButton)
        updateSoundButton()
        page.addView(header)

        // MOD SEÇİMİ

        val modeCard = makeCard()

        val modeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        modeHeader.addView(
            sectionTitle("OLUŞTURMA MODU"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        modeHeader.addView(text("01 / 02", 9f, orangeLight, true))
        modeCard.addView(modeHeader)
        addGap(modeCard, 9)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        barcodeTab = makeButton("▥  CODE128", orange)
        qrTab = makeButton("▦  QR KOD", innerColor)

        barcodeTab.setOnClickListener {
            mode = Mode.BARCODE
            updateTabs()
            numberInput.hint = "Numara gir"
            refresh()
        }

        qrTab.setOnClickListener {
            mode = Mode.QR
            updateTabs()
            numberInput.hint = "Numara gir"
            refresh()
        }

        tabs.addView(
            barcodeTab,
            LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                marginEnd = dp(5)
            }
        )

        tabs.addView(
            qrTab,
            LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                marginStart = dp(5)
            }
        )

        modeCard.addView(tabs)
        page.addView(modeCard)
        addGap(page, 10)

        // VERİ GİRİŞİ

        val inputCards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val fileCard = makeCard()
        fileCard.addView(sectionTitle("TOPLU VERİ"))
        addGap(fileCard, 9)

        val loadButton = makeButton("＋  TXT YÜKLE", innerColor)
        loadButton.background = rounded(innerColor, 11, orange)
        loadButton.setTextColor(orangeLight)
        loadButton.setOnClickListener { pickTxt() }

        fileCard.addView(
            loadButton,
            LinearLayout.LayoutParams(-1, dp(43))
        )

        inputCards.addView(
            fileCard,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            }
        )

        val manualCard = makeCard()
        manualCard.addView(sectionTitle("TEKLİ KOD"))
        addGap(manualCard, 9)

        val manualRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        numberInput = EditText(this).apply {
            hint = "Numara"
            textSize = 12f
            setTextColor(primaryText)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_NUMBER

            filters = arrayOf(
                InputFilter { source, _, _, _, _, _ ->
                    if (source.toString().all { it.isDigit() }) null else ""
                }
            )

            setSingleLine(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(innerColor, 10, borderColor)
        }

        manualRow.addView(
            numberInput,
            LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                marginEnd = dp(5)
            }
        )

        val createButton = makeButton("＋", orange)
        createButton.textSize = 18f
        createButton.setOnClickListener { createSingleCode() }

        manualRow.addView(
            createButton,
            LinearLayout.LayoutParams(dp(43), dp(43))
        )

        manualCard.addView(manualRow)

        inputCards.addView(
            manualCard,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
            }
        )

        page.addView(inputCards)
        addGap(page, 10)

        // BARKOD / QR SONUÇ ALANI

        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 17)
            setPadding(dp(11), dp(10), dp(11), dp(9))
            elevation = dp(3).toFloat()
        }

        val resultHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        resultHeader.addView(
            text("CANLI GÖRÜNTÜLEME", 10f, Color.DKGRAY, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        counterText = text(
            "000 / 000",
            11f,
            Color.rgb(210, 105, 0),
            true
        )

        resultHeader.addView(counterText)
        resultCard.addView(resultHeader)
        addGap(resultCard, 6)

        resultImage = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }

        resultCard.addView(
            resultImage,
            LinearLayout.LayoutParams(-1, dp(190))
        )

        // HAREKETLİ TURUNCU AKIŞ ÇİZGİSİ

        flowTrack = FrameLayout(this).apply {
            background = rounded(innerColor, 3)
            clipToOutline = true
            alpha = 0f
        }

        flowSegment = View(this).apply {
            background = rounded(orange, 3)
            visibility = View.INVISIBLE
        }

        flowTrack.addView(
            flowSegment,
            FrameLayout.LayoutParams(dp(56), -1)
        )

        resultCard.addView(
            flowTrack,
            LinearLayout.LayoutParams(-1, dp(3)).apply {
                topMargin = dp(7)
            }
        )

        resultLabel = TextView(this).apply {
            text = "Henüz kod oluşturulmadı"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }

        resultCard.addView(resultLabel)

        page.addView(
            resultCard,
            LinearLayout.LayoutParams(-1, -2)
        )

        addGap(page, 10)

        // KONTROLLER

        val controlCard = makeCard()

        val controlHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        controlHeader.addView(
            sectionTitle("BARKOD KONTROLLERİ"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        controlHeader.addView(text("PLAYBACK", 9f, orangeLight, true))
        controlCard.addView(controlHeader)
        addGap(controlCard, 10)

        val navigationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        previousButton = makeButton("◀  ÖNCEKİ", innerColor)
        nextButton = makeButton("SONRAKİ  ▶", innerColor)

        previousButton.setOnClickListener {
            if (currentIndex > 0) {
                showIndex(currentIndex - 1, true)
                if (running) restartTimer()
            }
        }

        nextButton.setOnClickListener {
            if (currentIndex < values.lastIndex) {
                showIndex(currentIndex + 1, true)
                if (running) restartTimer()
            }
        }

        navigationRow.addView(
            previousButton,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(5)
            }
        )

        navigationRow.addView(
            nextButton,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(5)
            }
        )

        controlCard.addView(navigationRow)
        addGap(controlCard, 8)

        val playbackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        startButton = makeButton("▶  BAŞLAT", orange)
        val stopButton = makeButton("■  DURDUR", innerColor)

        startButton.setOnClickListener { startPlayback() }
        stopButton.setOnClickListener { stopPlayback() }

        playbackRow.addView(
            startButton,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(5)
            }
        )

        playbackRow.addView(
            stopButton,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(5)
            }
        )

        controlCard.addView(playbackRow)
        page.addView(controlCard)
        addGap(page, 10)

        // HIZ AYARI

        val speedCard = makeCard()

        val speedHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        speedHeader.addView(
            sectionTitle("OTOMATİK GEÇİŞ HIZI"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        speedText = text(
            "${loadSpeedPreference()} / 20",
            12f,
            orangeLight,
            true
        )

        speedHeader.addView(speedText)
        speedCard.addView(speedHeader)
        addGap(speedCard, 3)

        speed = SeekBar(this).apply {
            max = 19
            progress = loadSpeedPreference() - 1

            progressTintList =
                android.content.res.ColorStateList.valueOf(orange)

            thumbTintList =
                android.content.res.ColorStateList.valueOf(orangeLight)

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        val selectedLevel = progress + 1
                        speedText.text = "$selectedLevel / 20"

                        if (fromUser) {
                            saveSpeedPreference(selectedLevel)
                        }

                        if (running) restartTimer()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        speedCard.addView(speed)
        page.addView(speedCard)
        addGap(page, 9)

        // DURUM BİLGİSİ

        status = text("Hazır", 11f, orangeLight, true).apply {
            setPadding(dp(9), dp(8), dp(9), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(innerColor, 10, borderColor)
        }

        page.addView(status)

        // ALT İMZA

        val footer = TextView(this).apply {
            text = "UR52 CODEX  •  CODE GENERATION SYSTEM"
            textSize = 8f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(14), 0, dp(5))
        }

        page.addView(footer)

        setContentView(scrollView)
    }

    // TEKLİ KOD OLUŞTURMA

    private fun createSingleCode() {
        val number = numberInput.text.toString().trim()

        if (number.isEmpty()) {
            status.text = "Lütfen bir numara gir."
            numberInput.requestFocus()
            return
        }

        stopPlayback()

        values.clear()
        values.add(number)
        currentIndex = 0

        refresh()

        status.text =
            "1 adet ${if (mode == Mode.QR) "QR kod" else "CODE128 barkod"} oluşturuldu."

        val inputMethod =
            getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

        inputMethod.hideSoftInputFromWindow(numberInput.windowToken, 0)
    }

    // MOD GÖRÜNÜMÜ

    private fun updateTabs() {
        if (mode == Mode.BARCODE) {
            barcodeTab.background = rounded(orange, 12)
            barcodeTab.setTextColor(Color.WHITE)

            qrTab.background = rounded(innerColor, 12)
            qrTab.setTextColor(muted)
        } else {
            qrTab.background = rounded(orange, 12)
            qrTab.setTextColor(Color.WHITE)

            barcodeTab.background = rounded(innerColor, 12)
            barcodeTab.setTextColor(muted)
        }
    }

    // TXT DOSYASI

    private fun pickTxt() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }

        startActivityForResult(intent, PICK_TXT)
    }

    @Deprecated("Deprecated Android API retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_TXT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return

            try {
                contentResolver.openInputStream(uri)?.use { stream ->

                    val incoming =
                        BufferedReader(
                            InputStreamReader(stream, Charsets.UTF_8)
                        )
                            .readLines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                    stopPlayback()

                    values.clear()
                    values.addAll(incoming)
                    currentIndex = 0

                    refresh()
                    status.text = "${values.size} kayıt başarıyla yüklendi."
                }

            } catch (e: Exception) {
                status.text = "Dosya okunamadı: ${e.localizedMessage}"
            }
        }
    }

    // BARKOD VE QR ÜRETİMİ

    private fun getCacheKey(value: String): String {
        return "${mode.name}_$value"
    }

    private fun makeBitmap(
        value: String,
        selectedMode: Mode
    ): Bitmap {

        val format =
            if (selectedMode == Mode.QR) BarcodeFormat.QR_CODE
            else BarcodeFormat.CODE_128

        val width = if (selectedMode == Mode.QR) 280 else 900
        val height = if (selectedMode == Mode.QR) 280 else 230

        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 2

        val matrix = MultiFormatWriter().encode(
            value,
            format,
            width,
            height,
            hints
        )

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.RGB_565
        )

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (matrix[x, y]) Color.BLACK else Color.WHITE
                )
            }
        }

        return bitmap
    }

    // EKRANI YENİLEME

    private fun refresh() {
        stopPlayback()

        if (values.isEmpty()) {
            generationToken++
            resultImage.setImageDrawable(null)
            resultLabel.text = "Henüz kod oluşturulmadı"
            counterText.text = "000 / 000"
            updateNavigationButtons()
            status.text = "Hazır"
            return
        }

        currentIndex = currentIndex.coerceIn(0, values.lastIndex)
        showIndex(currentIndex)

        status.text =
            "${values.size} kayıt hazır • ${if (mode == Mode.QR) "QR KOD" else "CODE128"}"
    }

    // YUMUŞAK GÖRÜNTÜ GEÇİŞİ

    private fun animateResultImage(bitmap: Bitmap) {
        resultImage.animate().cancel()

        resultImage.alpha = 0f
        resultImage.scaleX = 0.985f
        resultImage.scaleY = 0.985f

        resultImage.setImageBitmap(bitmap)

        resultImage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun showIndex(
        index: Int,
        playSound: Boolean = false
    ) {
        if (values.isEmpty()) return

        currentIndex = index.coerceIn(0, values.lastIndex)

        if (playSound) playTransitionSound()

        val value = values[currentIndex]
        val selectedMode = mode
        val token = ++generationToken
        val cacheKey = getCacheKey(value)

        counterText.text = "${currentIndex + 1} / ${values.size}"
        resultLabel.text = value
        resultImage.animate().cancel()
        resultImage.alpha = 1f
        resultImage.setImageDrawable(null)

        updateNavigationButtons()

        val cachedBitmap = bitmapCache.get(cacheKey)

        if (cachedBitmap != null) {
            animateResultImage(cachedBitmap)
            prefetchNext()
            return
        }

        status.text = "Kod hazırlanıyor..."

        imageExecutor.execute {
            try {
                val bitmap = makeBitmap(value, selectedMode)
                bitmapCache.put(cacheKey, bitmap)

                handler.post {
                    if (
                        !isFinishing &&
                        token == generationToken &&
                        currentIndex in values.indices &&
                        values[currentIndex] == value &&
                        mode == selectedMode
                    ) {
                        animateResultImage(bitmap)

                        status.text =
                            "${currentIndex + 1} / ${values.size} kayıt gösteriliyor."

                        prefetchNext()
                    }
                }

            } catch (e: Exception) {
                handler.post {
                    if (token == generationToken) {
                        status.text =
                            "Kod oluşturulamadı: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    // SONRAKİ KODU ÖNCEDEN HAZIRLAMA

    private fun prefetchNext() {
        val nextIndex = currentIndex + 1

        if (nextIndex !in values.indices) return

        val nextValue = values[nextIndex]
        val selectedMode = mode
        val cacheKey = getCacheKey(nextValue)

        if (bitmapCache.get(cacheKey) != null) return

        imageExecutor.execute {
            try {
                val bitmap = makeBitmap(nextValue, selectedMode)
                bitmapCache.put(cacheKey, bitmap)
            } catch (_: Exception) {
                // Gerektiğinde yeniden hazırlanır.
            }
        }
    }

    // GEZİNME BUTONLARI

    private fun updateNavigationButtons() {
        if (!::previousButton.isInitialized) return

        previousButton.alpha =
            if (currentIndex > 0 && values.isNotEmpty()) 1f else 0.4f

        nextButton.alpha =
            if (currentIndex < values.lastIndex && values.isNotEmpty()) 1f else 0.4f
    }

    // HIZ AYARI

    private fun getInterval(): Long {
        val level = speed.progress + 1

        return if (level <= 10) {
            1700L - ((level - 1) * 150L)
        } else {
            350L - ((level - 10) * 27L)
        }
    }

    // PREMIUM AKIŞ ANİMASYONLARI

    private fun startFlowAnimation() {
        if (!::flowTrack.isInitialized) return

        flowAnimator?.cancel()

        flowTrack.animate().cancel()
        flowTrack.animate()
            .alpha(1f)
            .setDuration(180)
            .start()

        flowSegment.visibility = View.VISIBLE

        flowTrack.post {
            if (!running || flowTrack.width <= flowSegment.width) {
                return@post
            }

            val travelDistance =
                (flowTrack.width - flowSegment.width).toFloat()

            flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1050
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()

                addUpdateListener { animator ->
                    flowSegment.translationX =
                        travelDistance * (animator.animatedValue as Float)
                }

                start()
            }
        }
    }

    private fun stopFlowAnimation() {
        flowAnimator?.cancel()
        flowAnimator = null

        if (!::flowTrack.isInitialized) return

        flowTrack.animate().cancel()
        flowTrack.animate()
            .alpha(0f)
            .setDuration(160)
            .start()

        flowSegment.translationX = 0f
        flowSegment.visibility = View.INVISIBLE
    }

    private fun startPlaybackPulse() {
        if (!::startButton.isInitialized) return

        pulseAnimator?.cancel()

        pulseAnimator = ValueAnimator.ofFloat(1f, 1.025f).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float

                startButton.scaleX = scale
                startButton.scaleY = scale
            }

            start()
        }
    }

    private fun stopPlaybackPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null

        if (::startButton.isInitialized) {
            startButton.animate().cancel()
            startButton.scaleX = 1f
            startButton.scaleY = 1f
            startButton.alpha = 1f
        }
    }

    // OTOMATİK OYNATMA

    private fun startPlayback() {
        if (values.isEmpty()) {
            status.text = "Önce TXT yükle veya tekli kod oluştur."
            return
        }

        if (currentIndex >= values.lastIndex) {
            showIndex(0)
        }

        running = true

        startButton.text = "●  AKIŞ AKTİF"
        startButton.background = rounded(
            Color.rgb(190, 91, 0),
            12
        )

        startFlowAnimation()
        startPlaybackPulse()

        status.text = "Otomatik sıralama başladı."

        restartTimer()
    }

    private fun restartTimer() {
        handler.removeCallbacks(autoRunnable)

        if (running) {
            handler.postDelayed(autoRunnable, getInterval())
        }
    }

    private fun stopPlayback() {
        running = false
        handler.removeCallbacks(autoRunnable)

        stopFlowAnimation()
        stopPlaybackPulse()

        if (::startButton.isInitialized) {
            startButton.text = "▶  BAŞLAT"
            startButton.background = rounded(orange, 12)
        }
    }

    // KAPANIŞ

    override fun onDestroy() {
        stopPlayback()
        generationToken++

        handler.removeCallbacksAndMessages(null)
        imageExecutor.shutdownNow()

        soundPool?.release()
        soundPool = null

        super.onDestroy()
    }
}
