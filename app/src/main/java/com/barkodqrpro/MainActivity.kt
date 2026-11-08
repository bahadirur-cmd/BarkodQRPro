
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
import android.view.View
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

    // DEEP PULSE SES SİSTEMİ
    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var soundLoaded = false
    private var soundEnabled = true

    private var currentIndex = 0
    private var running = false
    private var generationToken = 0

    private val handler = Handler(Looper.getMainLooper())
    private val imageExecutor = Executors.newSingleThreadExecutor()

    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val PICK_TXT = 41

    private val backgroundColor = Color.rgb(38, 50, 56)
    private val cardColor = Color.rgb(55, 71, 79)
    private val orange = Color.rgb(255, 133, 0)
    private val orangeLight = Color.rgb(255, 165, 65)
    private val white = Color.WHITE
    private val muted = Color.rgb(190, 202, 207)

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

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        initializeSound()
        buildUi()
        refresh()
    }

    // DEEP PULSE SESİNİ YÜKLE
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

        soundPool?.play(
            soundId,
            0.85f,
            0.85f,
            1,
            0,
            1.0f
        )
    }

    private fun updateSoundButton() {
        if (!::soundButton.isInitialized) return

        if (soundEnabled) {
            soundButton.text = "♫  SES AÇIK"
            soundButton.background = rounded(orange, 12)
            soundButton.setTextColor(white)
        } else {
            soundButton.text = "♫  SES KAPALI"
            soundButton.background = rounded(backgroundColor, 12)
            soundButton.setTextColor(muted)
        }
    }

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

    private fun makeButton(
        label: String,
        color: Int,
        textColor: Int = white,
        radius: Int = 14
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
        }
    }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun buildUi() {

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(backgroundColor)
        }

        // HEADER

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(8))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.barkodqr_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(cardColor, 16)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
        }

        header.addView(logo)

        val titleArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
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
                ForegroundColorSpan(white),
                5, 10, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD_ITALIC),
                0, 10, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val brandTitle = text("", 24f, white, true).apply {
            text = brandText
            typeface = Typeface.create(
                "sans-serif-condensed",
                Typeface.BOLD_ITALIC
            )
            letterSpacing = 0.04f
        }

        titleArea.addView(brandTitle)
        titleArea.addView(
            text("CODE GENERATION SYSTEM", 10f, orangeLight, true)
        )

        header.addView(
            titleArea,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        page.addView(header)

        // MODE CARD

        val modeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 16)
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }

        modeCard.addView(text("OLUŞTURMA MODU", 10f, muted, true))
        addGap(modeCard, 6)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        barcodeTab = makeButton("▥  CODE128", orange)
        qrTab = makeButton("▦  QR KOD", backgroundColor)

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
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            }
        )

        tabs.addView(
            qrTab,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
            }
        )

        modeCard.addView(tabs)
        page.addView(modeCard)

        addGap(page, 8)

        // FILE AND MANUAL CARDS

        val inputCards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val fileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 16)
            setPadding(dp(9), dp(10), dp(9), dp(10))
        }

        fileCard.addView(text("TOPLU VERİ", 10f, muted, true))
        addGap(fileCard, 7)

        val loadButton = makeButton("＋ TXT YÜKLE", orange)
        loadButton.setOnClickListener { pickTxt() }
        fileCard.addView(loadButton)

        inputCards.addView(
            fileCard,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            }
        )

        val manualCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 16)
            setPadding(dp(9), dp(10), dp(9), dp(10))
        }

        manualCard.addView(text("TEKLİ KOD", 10f, muted, true))
        addGap(manualCard, 7)

        val manualRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        numberInput = EditText(this).apply {
            hint = "Numara"
            textSize = 13f
            setTextColor(white)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_NUMBER

            filters = arrayOf(
                InputFilter { source, _, _, _, _, _ ->
                    if (source.toString().all { it.isDigit() }) null else ""
                }
            )

            setSingleLine(true)
            setPadding(dp(7), dp(10), dp(7), dp(10))
            background = rounded(backgroundColor, 10)
        }

        manualRow.addView(
            numberInput,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(5)
            }
        )

        val createButton = makeButton("＋ OLUŞTUR", orange)
        createButton.textSize = 10f
        createButton.setOnClickListener { createSingleCode() }

        manualRow.addView(
            createButton,
            LinearLayout.LayoutParams(dp(82), dp(42))
        )

        manualCard.addView(manualRow)

        inputCards.addView(
            manualCard,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
            }
        )

        page.addView(inputCards)

        addGap(page, 8)

        // SPEED CARD

        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 16)
            setPadding(dp(11), dp(8), dp(11), dp(5))
        }

        val speedHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        speedHeader.addView(
            text("OTOMATİK GEÇİŞ HIZI", 10f, muted, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        speedText = text("5 / 20", 11f, orangeLight, true)
        speedHeader.addView(speedText)
        speedCard.addView(speedHeader)

        speed = SeekBar(this).apply {
            max = 19
            progress = 4
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
                        speedText.text = "${progress + 1} / 20"
                        if (running) restartTimer()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        speedCard.addView(speed)
        page.addView(speedCard)

        addGap(page, 7)

        // NAVIGATION CONTROLS

        val controlCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 16)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        controlCard.addView(
            text("BARKOD KONTROLLERİ", 10f, muted, true)
        )

        addGap(controlCard, 7)

        val navigationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        previousButton = makeButton("◀ ÖNCEKİ", backgroundColor)
        nextButton = makeButton("SONRAKİ ▶", backgroundColor)

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
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            }
        )

        counterText = text("000 / 000", 12f, orangeLight, true).apply {
            gravity = Gravity.CENTER
        }

        navigationRow.addView(
            counterText,
            LinearLayout.LayoutParams(dp(90), -2)
        )

        navigationRow.addView(
            nextButton,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
            }
        )

        controlCard.addView(navigationRow)

        addGap(controlCard, 7)

        val playbackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        startButton = makeButton("▶ BAŞLAT", orange)
        val stopButton = makeButton("■ DURDUR", backgroundColor)

        startButton.setOnClickListener { startPlayback() }
        stopButton.setOnClickListener { stopPlayback() }

        playbackRow.addView(
            startButton,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            }
        )

        playbackRow.addView(
            stopButton,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
            }
        )

        controlCard.addView(playbackRow)

        // SES AÇMA / KAPAMA DÜĞMESİ

        addGap(controlCard, 7)

        soundButton = makeButton("♫  SES AÇIK", orange)
        soundButton.setOnClickListener {
            soundEnabled = !soundEnabled
            updateSoundButton()

            status.text = if (soundEnabled) {
                "Deep Pulse sesi açıldı."
            } else {
                "Deep Pulse sesi kapatıldı."
            }
        }

        controlCard.addView(
            soundButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        updateSoundButton()

        page.addView(controlCard)

        addGap(page, 5)

        // STATUS

        status = text("Hazır", 11f, orangeLight, true)
        status.setPadding(dp(3), dp(2), dp(3), dp(3))
        page.addView(status)

        // SINGLE RESULT AREA

        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 16)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        resultImage = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }

        resultCard.addView(
            resultImage,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        resultLabel = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(7), dp(4), dp(4))
        }

        resultCard.addView(resultLabel)

        page.addView(
            resultCard,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(page)
    }

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

    private fun updateTabs() {
        if (mode == Mode.BARCODE) {
            barcodeTab.background = rounded(orange, 14)
            barcodeTab.setTextColor(white)
            qrTab.background = rounded(backgroundColor, 14)
            qrTab.setTextColor(white)
        } else {
            qrTab.background = rounded(orange, 14)
            qrTab.setTextColor(white)
            barcodeTab.background = rounded(backgroundColor, 14)
            barcodeTab.setTextColor(white)
        }
    }

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

    private fun getCacheKey(value: String): String {
        return "${mode.name}_$value"
    }

    private fun makeBitmap(value: String, selectedMode: Mode): Bitmap {
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

    private fun showIndex(index: Int, playSound: Boolean = false) {
        if (values.isEmpty()) return

        currentIndex = index.coerceIn(0, values.lastIndex)

        // Yalnızca gerçek barkod geçişlerinde ses çal.
        if (playSound) {
            playTransitionSound()
        }

        val value = values[currentIndex]
        val selectedMode = mode
        val token = ++generationToken
        val cacheKey = getCacheKey(value)

        counterText.text =
            "${currentIndex + 1} / ${values.size}"

        resultLabel.text = value
        resultImage.setImageDrawable(null)

        updateNavigationButtons()

        val cachedBitmap = bitmapCache.get(cacheKey)

        if (cachedBitmap != null) {
            resultImage.setImageBitmap(cachedBitmap)
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
                        resultImage.setImageBitmap(bitmap)
                        status.text =
                            "${currentIndex + 1} / ${values.size} kayıt gösteriliyor."
                        prefetchNext()
                    }
                }

            } catch (e: Exception) {
                handler.post {
                    if (token == generationToken) {
                        status.text = "Kod oluşturulamadı: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

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
                // Sonraki kod gerektiğinde yeniden hazırlanır.
            }
        }
    }

    private fun updateNavigationButtons() {
        if (!::previousButton.isInitialized) return

        previousButton.alpha =
            if (currentIndex > 0 && values.isNotEmpty()) 1f else 0.4f

        nextButton.alpha =
            if (currentIndex < values.lastIndex && values.isNotEmpty()) 1f else 0.4f
    }

    // HIZ AYARI
    // 1. kademe: 1700 ms
    // 10. kademe: 350 ms
    // 20. kademe: 80 ms

    private fun getInterval(): Long {
        val level = speed.progress + 1

        return if (level <= 10) {
            1700L - ((level - 1) * 150L)
        } else {
            350L - ((level - 10) * 27L)
        }
    }

    private fun startPlayback() {
        if (values.isEmpty()) {
            status.text = "Önce TXT yükle veya tekli kod oluştur."
            return
        }

        if (currentIndex >= values.lastIndex) {
            showIndex(0)
        }

        running = true
        startButton.text = "▶ AKIŞ AKTİF"
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

        if (::startButton.isInitialized) {
            startButton.text = "▶ BAŞLAT"
        }
    }

    override fun onDestroy() {
        stopPlayback()
        generationToken++
        imageExecutor.shutdownNow()

        soundPool?.release()
        soundPool = null

        super.onDestroy()
    }
}
