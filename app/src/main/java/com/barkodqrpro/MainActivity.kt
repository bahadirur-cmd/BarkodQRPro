
package com.barkodqrpro

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {

    private enum class Mode { BARCODE, QR }

    private var mode = Mode.BARCODE
    private val values = mutableListOf<String>()
    private val imageViews = mutableListOf<ImageView>()

    private lateinit var container: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var status: TextView
    private lateinit var speed: SeekBar
    private lateinit var speedText: TextView
    private lateinit var barcodeTab: TextView
    private lateinit var qrTab: TextView
    private lateinit var startButton: TextView
    private lateinit var numberInput: EditText

    private var running = false
    private val scrollHandler = Handler(Looper.getMainLooper())

    private val PICK_TXT = 41

    private val backgroundColor = Color.rgb(38, 50, 56)
    private val cardColor = Color.rgb(55, 71, 79)
    private val orange = Color.rgb(255, 133, 0)
    private val orangeLight = Color.rgb(255, 165, 65)
    private val white = Color.WHITE
    private val muted = Color.rgb(190, 202, 207)

    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            val step = dp((1.0f + speed.progress * 0.35f).toInt().coerceAtLeast(1))
            scroller.scrollBy(0, step)

            val maxScroll = (container.height - scroller.height).coerceAtLeast(0)

            if (maxScroll > 0 && scroller.scrollY >= maxScroll) {
                scroller.scrollTo(0, 0)
            }

            scrollHandler.postDelayed(this, 16L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        buildUi()
        refresh()
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
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
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

        // FILE AND MANUAL CARDS SIDE BY SIDE

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
            text("OTOMATİK AKIŞ HIZI", 10f, muted, true),
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
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        speedCard.addView(speed)
        page.addView(speedCard)

        addGap(page, 7)

        // FLOW CONTROLS

        val controlCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(cardColor, 16)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        controlCard.addView(
            text("AKIŞ KONTROLLERİ", 10f, muted, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        startButton = makeButton("▶ BAŞLAT", orange)
        val stopButton = makeButton("■ DURDUR", backgroundColor)

        startButton.setOnClickListener { startScroll() }
        stopButton.setOnClickListener { stopScroll() }

        controls.addView(
            startButton,
            LinearLayout.LayoutParams(dp(104), -2).apply {
                marginEnd = dp(5)
            }
        )

        controls.addView(
            stopButton,
            LinearLayout.LayoutParams(dp(104), -2)
        )

        controlCard.addView(controls)
        page.addView(controlCard)

        addGap(page, 5)

        // STATUS

        status = text("Hazır", 11f, orangeLight, true)
        status.setPadding(dp(3), dp(2), dp(3), dp(3))
        page.addView(status)

        // BARCODE RESULT AREA

        scroller = ScrollView(this).apply {
            background = rounded(Color.WHITE, 16)
            isFillViewport = false
            clipToPadding = false
            setPadding(dp(3), dp(3), dp(3), dp(3))
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            minimumHeight = dp(130)
        }

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(12))
        }

        scroller.addView(container)

        scroller.setOnScrollChangeListener {
                _, _, _, _, _ ->
            renderVisibleItems()
        }

        page.addView(
            scroller,
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

        stopScroll()

        values.clear()
        values.add(number)

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
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                            .readLines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                    values.clear()
                    values.addAll(incoming)
                }

                refresh()
                status.text = "${values.size} kayıt başarıyla yüklendi."

            } catch (e: Exception) {
                status.text = "Dosya okunamadı: ${e.localizedMessage}"
            }
        }
    }

    private fun makeBitmap(value: String): Bitmap {
        val format =
            if (mode == Mode.QR) BarcodeFormat.QR_CODE
            else BarcodeFormat.CODE_128

        val width = if (mode == Mode.QR) 280 else 900
        val height = if (mode == Mode.QR) 280 else 230

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
        stopScroll()

        container.removeAllViews()
        imageViews.clear()

        val qrMode = mode == Mode.QR

        values.forEach { value ->

            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(5), dp(4), dp(5), dp(4))
            }

            imageViews.add(image)

            container.addView(
                image,
                LinearLayout.LayoutParams(
                    -1,
                    dp(if (qrMode) 210 else 100)
                )
            )

            val label = TextView(this).apply {
                text = value
                textSize = 12f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), dp(7))
            }

            container.addView(label)
        }

        status.text =
            "${values.size} kod hazır • ${if (qrMode) "QR KOD" else "CODE128"}"

        scroller.post {
            scroller.scrollTo(0, 0)
            renderVisibleItems()
        }
    }

    private fun renderVisibleItems() {
        if (imageViews.isEmpty()) return

        val viewportTop = scroller.scrollY
        val viewportBottom = viewportTop + scroller.height

        val extra = dp(100)

        imageViews.forEachIndexed { index, view ->

            val isVisible =
                view.bottom >= viewportTop - extra &&
                view.top <= viewportBottom + extra

            if (isVisible) {
                val value = values.getOrNull(index) ?: return@forEachIndexed

                if (view.tag != value) {
                    try {
                        view.setImageBitmap(makeBitmap(value))
                        view.tag = value
                    } catch (_: Exception) {
                        view.setImageDrawable(null)
                    }
                }

            } else if (view.tag != null) {
                view.setImageDrawable(null)
                view.tag = null
            }
        }
    }

    private fun startScroll() {
        if (running) return

        if (values.isEmpty()) {
            status.text = "Önce TXT yükle veya tekli kod oluştur."
            return
        }

        running = true
        startButton.text = "▶ AKIŞ AKTİF"
        status.text = "Akıcı otomatik akış başladı."

        scrollHandler.removeCallbacks(scrollRunnable)
        scrollHandler.post(scrollRunnable)
    }

    private fun stopScroll() {
        running = false
        scrollHandler.removeCallbacks(scrollRunnable)

        if (::startButton.isInitialized) {
            startButton.text = "▶ BAŞLAT"
        }

        if (::status.isInitialized && values.isNotEmpty()) {
            status.text = "${values.size} kod hazır."
        }
    }

    override fun onDestroy() {
        stopScroll()
        super.onDestroy()
    }
}
