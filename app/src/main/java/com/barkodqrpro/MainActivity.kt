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

    private var rowHeight = 165

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

    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            val stepDp = 1.0f + speed.progress * 0.35f
            scroller.scrollBy(0, dp(stepDp.toInt().coerceAtLeast(1)))

            val maxScroll = (container.height - scroller.height).coerceAtLeast(0)
            if (maxScroll > 0 && scroller.scrollY >= maxScroll) {
                scroller.scrollTo(0, 0)
            }

            scrollHandler.postDelayed(this, 16L)
        }
    }

    private val PICK_TXT = 41

    private val backgroundColor = Color.rgb(38, 50, 56)
    private val cardColor = Color.rgb(55, 71, 79)
    private val orange = Color.rgb(255, 133, 0)
    private val orangeLight = Color.rgb(255, 165, 65)
    private val white = Color.WHITE
    private val muted = Color.rgb(190, 202, 207)

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
            if (strokeColor != Color.TRANSPARENT) setStroke(dp(1), strokeColor)
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
            textSize = 13f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = rounded(color, radius)
            setPadding(dp(8), dp(14), dp(8), dp(14))
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
            setPadding(dp(16), dp(12), dp(16), dp(10))
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(16))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.barkodqr_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(cardColor, 16)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }
        header.addView(logo)

        val titleArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titleArea.addView(text("UR52 CODEX", 23f, white, true))
        titleArea.addView(text("CODE GENERATION SYSTEM", 10f, orangeLight, true))
        header.addView(titleArea, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(header)

        val modeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        modeCard.addView(text("OLUŞTURMA MODU", 11f, muted, true))
        addGap(modeCard, 8)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        barcodeTab = makeButton("▥   CODE128", orange)
        qrTab = makeButton("▦   QR KOD", backgroundColor, white)

        barcodeTab.setOnClickListener {
            mode = Mode.BARCODE
            updateTabs()
            numberInput.hint = "CODE128 için numara gir"
            refresh()
        }
        qrTab.setOnClickListener {
            mode = Mode.QR
            updateTabs()
            numberInput.hint = "QR kod için numara gir"
            refresh()
        }

        tabs.addView(barcodeTab, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(6)
        })
        tabs.addView(qrTab, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(6)
        })
        modeCard.addView(tabs)
        page.addView(modeCard)

        addGap(page, 12)

        val fileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        fileCard.addView(text("TOPLU VERİ YÜKLEME", 11f, muted, true))
        addGap(fileCard, 8)
        val loadButton = makeButton("＋  TXT DOSYASI YÜKLE", orange)
        loadButton.setOnClickListener { pickTxt() }
        fileCard.addView(loadButton)
        page.addView(fileCard)

        addGap(page, 12)

        val manualCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        manualCard.addView(text("TEKLİ KOD OLUŞTUR", 11f, muted, true))
        addGap(manualCard, 8)

        numberInput = EditText(this).apply {
            hint = "Sadece rakam gir"
            textSize = 16f
            setTextColor(white)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
                val digitsOnly = source.toString().all { it.isDigit() }
                if (digitsOnly) null else ""
            })
            singleLine = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(backgroundColor, 12)
        }
        manualCard.addView(numberInput)
        addGap(manualCard, 8)

        val createButton = makeButton("＋  OLUŞTUR", orange)
        createButton.setOnClickListener { createSingleCode() }
        manualCard.addView(createButton)
        page.addView(manualCard)

        addGap(page, 12)

        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val speedHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        speedHeader.addView(
            text("OTOMATİK AKIŞ HIZI", 11f, muted, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        speedText = text("5 / 20", 13f, orangeLight, true)
        speedHeader.addView(speedText)
        speedCard.addView(speedHeader)

        speed = SeekBar(this).apply {
            max = 19
            progress = 4
            progressTintList = android.content.res.ColorStateList.valueOf(orange)
            thumbTintList = android.content.res.ColorStateList.valueOf(orangeLight)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    speedText.text = "${progress + 1} / 20"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        speedCard.addView(speed)
        page.addView(speedCard)

        addGap(page, 12)

        val controlCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        controlCard.addView(text("AKIŞ KONTROLLERİ", 11f, muted, true))
        addGap(controlCard, 8)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = makeButton("▶  BAŞLAT", orange)
        val stopButton = makeButton("■  DURDUR", backgroundColor)
        startButton.setOnClickListener { startScroll() }
        stopButton.setOnClickListener { stopScroll() }

        controls.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(5)
        })
        controls.addView(stopButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(5)
        })
        controlCard.addView(controls)
        page.addView(controlCard)

        addGap(page, 10)

        status = text("Hazır", 12f, orangeLight, true)
        status.setPadding(dp(4), dp(4), dp(4), dp(8))
        page.addView(status)

        scroller = ScrollView(this).apply {
            background = rounded(Color.WHITE, 18)
            isFillViewport = false
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(24))
        }
        scroller.addView(container)
        scroller.setOnScrollChangeListener { _, _, _, _, _ -> renderVisibleItems() }

        page.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun createSingleCode() {
        val number = numberInput.text.toString().trim()
        if (number.isEmpty()) {
            status.text = "Lütfen yalnızca rakamlardan oluşan bir numara gir."
            numberInput.requestFocus()
            return
        }

        stopScroll()
        values.clear()
        values.add(number)
        refresh()
        status.text = "1 adet ${if (mode == Mode.QR) "QR kod" else "CODE128 barkod"} oluşturuldu."
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(numberInput.windowToken, 0)
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
                    val incoming = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
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
        val format = if (mode == Mode.QR) BarcodeFormat.QR_CODE else BarcodeFormat.CODE_128
        val width = if (mode == Mode.QR) 280 else 900
        val height = if (mode == Mode.QR) 280 else 230

        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 2

        val matrix = MultiFormatWriter().encode(value, format, width, height, hints)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun refresh() {
        stopScroll()
        container.removeAllViews()
        imageViews.clear()

        val qrMode = mode == Mode.QR
        rowHeight = if (qrMode) 270 else 165

        values.forEach { value ->
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(8), dp(6), dp(8), dp(2))
            }
            imageViews.add(image)

            container.addView(
                image,
                LinearLayout.LayoutParams(
                    -1,
                    if (qrMode) dp(220) else dp(120)
                )
            )

            val label = TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(8))
            }
            container.addView(label)
        }

        status.text = "${values.size} kod hazır • ${if (qrMode) "QR KOD" else "CODE128"}"
        scroller.post {
            scroller.scrollTo(0, 0)
            renderVisibleItems()
        }
    }

    private fun renderVisibleItems() {
        if (imageViews.isEmpty()) return

        val rowPx = dp(rowHeight).coerceAtLeast(1)
        val first = (scroller.scrollY / rowPx).coerceAtLeast(0)
        val last = (first + scroller.height / rowPx + 3)
            .coerceAtMost(imageViews.size - 1)

        imageViews.forEachIndexed { index, view ->
            if (index in first..last) {
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
            status.text = "Önce TXT dosyası yükle veya tekli kod oluştur."
            return
        }

        running = true
        startButton.text = "▶  AKIŞ AKTİF"
        status.text = "Akıcı otomatik akış başladı."
        scrollHandler.removeCallbacks(scrollRunnable)
        scrollHandler.post(scrollRunnable)
    }

    private fun stopScroll() {
        running = false
        scrollHandler.removeCallbacks(scrollRunnable)

        if (::startButton.isInitialized) startButton.text = "▶  BAŞLAT"
        if (::status.isInitialized && values.isNotEmpty()) {
            status.text = "${values.size} kod hazır."
        }
    }

    override fun onDestroy() {
        stopScroll()
        super.onDestroy()
    }
}
