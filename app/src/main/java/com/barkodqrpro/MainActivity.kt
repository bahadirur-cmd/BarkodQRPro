
package com.barkodqrpro

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.ContentValues
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : Activity() {

    private enum class Mode { BARCODE, QR }

    private var mode = Mode.BARCODE
    private val values = mutableListOf<String>()
    private val imageViews = mutableListOf<ImageView>()

    private var rowHeight = 190

    private lateinit var container: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var status: TextView
    private lateinit var speed: SeekBar
    private lateinit var speedText: TextView
    private lateinit var barcodeTab: TextView
    private lateinit var qrTab: TextView
    private lateinit var startButton: TextView

    private var running = false
    private var scrollThread: Thread? = null

    private val PICK_TXT = 41

    // PETROL FÜMESİ + TURUNCU RENK PALETİ
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
        addSamples()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

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

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
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
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun buildUi() {

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            setBackgroundColor(backgroundColor)
        }

        // ÜST LOGO VE BAŞLIK

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(16))
        }

        val logo = TextView(this).apply {
            text = "▥"
            textSize = 37f
            setTextColor(orange)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(cardColor, 16)
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }

        header.addView(logo)

        val titleArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        val title = text("BARKODQR", 23f, white, true)
        val subtitle = text("PRO  •  OFFLINE GENERATOR", 10f, orangeLight, true)

        titleArea.addView(title)
        titleArea.addView(subtitle)

        header.addView(
            titleArea,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        page.addView(header)

        // MOD SEÇİM KARTI

        val modeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        modeCard.addView(text("OLUŞTURMA MODU", 11f, muted, true))
        addGap(modeCard, 8)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        barcodeTab = makeButton("▥   BARKOD", orange)
        qrTab = makeButton("▦   QR KOD", backgroundColor, white)

        barcodeTab.setOnClickListener {
            mode = Mode.BARCODE
            updateTabs()
            refresh()
        }

        qrTab.setOnClickListener {
            mode = Mode.QR
            updateTabs()
            refresh()
        }

        tabs.addView(
            barcodeTab,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(6)
            }
        )

        tabs.addView(
            qrTab,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(6)
            }
        )

        modeCard.addView(tabs)
        page.addView(modeCard)

        addGap(page, 12)

        // DOSYA YÜKLEME ALANI

        val fileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        fileCard.addView(text("VERİ YÖNETİMİ", 11f, muted, true))
        addGap(fileCard, 8)

        val fileButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val loadButton = makeButton("＋  TXT DOSYASI YÜKLE", orange)
        val sampleButton = makeButton("ÖRNEK VERİ", backgroundColor)

        loadButton.setOnClickListener { pickTxt() }
        sampleButton.setOnClickListener { addSamples() }

        fileButtons.addView(
            loadButton,
            LinearLayout.LayoutParams(0, -2, 1.5f).apply {
                marginEnd = dp(6)
            }
        )

        fileButtons.addView(
            sampleButton,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(6)
            }
        )

        fileCard.addView(fileButtons)
        page.addView(fileCard)

        addGap(page, 12)

        // HIZ AYARI

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

        addGap(page, 12)

        // ÇALIŞTIRMA KONTROLLERİ

        val controlCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 18)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        controlCard.addView(text("AKIŞ KONTROLLERİ", 11f, muted, true))
        addGap(controlCard, 8)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        startButton = makeButton("▶  BAŞLAT", orange)
        val stopButton = makeButton("■  DURDUR", backgroundColor)
        val saveButton = makeButton("↓  PNG KAYDET", backgroundColor)

        startButton.setOnClickListener { startScroll() }
        stopButton.setOnClickListener { stopScroll() }
        saveButton.setOnClickListener { saveAll() }

        controls.addView(
            startButton,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            }
        )

        // DÜZELTİLDİ: marginHorizontal yerine iki ayrı kenar boşluğu

        controls.addView(
            stopButton,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            }
        )

        controls.addView(
            saveButton,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
            }
        )

        controlCard.addView(controls)
        page.addView(controlCard)

        addGap(page, 10)

        // DURUM BİLGİSİ

        status = text("Hazır", 12f, orangeLight, true)
        status.setPadding(dp(4), dp(4), dp(4), dp(8))
        page.addView(status)

        // KODLARIN GÖRÜNTÜLENDİĞİ ALAN

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

        scroller.setOnScrollChangeListener {
            _, _, _, _, _ -> renderVisibleItems()
        }

        page.addView(
            scroller,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(page)
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

    private fun addSamples() {

        stopScroll()

        values.clear()

        values.addAll(
            listOf(
                "123456789012",
                "987654321098",
                "BARKODQR-2026",
                "000123456789",
                "456789123456",
                "QR-PRO-2026"
            )
        )

        refresh()
        status.text = "${values.size} örnek kayıt hazır."
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

                    val incoming = BufferedReader(
                        InputStreamReader(stream, Charsets.UTF_8)
                    ).readLines()
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

        val format = if (mode == Mode.QR)
            BarcodeFormat.QR_CODE
        else
            BarcodeFormat.CODE_128

        val width = if (mode == Mode.QR) 420 else 900
        val height = if (mode == Mode.QR) 420 else 230

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

        rowHeight = if (mode == Mode.QR) 390 else 190

        values.forEach { value ->

            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(8), dp(8), dp(8), dp(2))
                tag = null
            }

            imageViews.add(image)

            container.addView(
                image,
                LinearLayout.LayoutParams(
                    -1,
                    if (mode == Mode.QR) dp(330) else dp(140)
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

        status.text =
            "${values.size} kod hazır • ${if (mode == Mode.QR) "QR KOD" else "CODE 128"}"

        scroller.post {
            scroller.scrollTo(0, 0)
            renderVisibleItems()
        }
    }

    // Yalnızca görünür kodların bitmap'leri oluşturulur.

    private fun renderVisibleItems() {

        if (imageViews.isEmpty()) return

        val first = (scroller.scrollY / dp(rowHeight)).coerceAtLeast(0)

        val last = (
            first + scroller.height / dp(rowHeight) + 3
        ).coerceAtMost(imageViews.size - 1)

        imageViews.forEachIndexed { index, view ->

            if (index in first..last) {

                val value = values.getOrNull(index)
                    ?: return@forEachIndexed

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
            status.text = "Önce TXT dosyası yüklemelisin."
            return
        }

        running = true
        startButton.text = "▶  AKIŞ AKTİF"

        status.text = "Otomatik akış başladı."

        scrollThread = Thread {

            while (running) {

                try {

                    runOnUiThread {

                        val distance = (speed.progress + 1) * 2

                        scroller.smoothScrollBy(0, distance)

                        if (
                            scroller.scrollY >=
                            container.height - scroller.height
                        ) {

                            scroller.postDelayed({

                                if (running) {
                                    scroller.scrollTo(0, 0)
                                }

                            }, 250)
                        }
                    }

                    Thread.sleep(
                        (55 - speed.progress * 2)
                            .toLong()
                            .coerceAtLeast(15)
                    )

                } catch (_: InterruptedException) {

                    break
                }
            }
        }.also { it.start() }
    }

    private fun stopScroll() {

        running = false

        scrollThread?.interrupt()
        scrollThread = null

        if (::startButton.isInitialized) {
            startButton.text = "▶  BAŞLAT"
        }

        if (::status.isInitialized && values.isNotEmpty()) {
            status.text = "${values.size} kod hazır."
        }
    }

    private fun saveAll() {

        if (values.isEmpty()) {
            status.text = "Kaydedilecek kod bulunamadı."
            return
        }

        val selectedMode = mode
        val items = values.toList()

        Thread {

            var saved = 0

            items.forEachIndexed { index, value ->

                try {

                    val bitmap = makeBitmapForMode(value, selectedMode)

                    val name = String.format(
                        Locale.US,
                        "%s_%04d.png",
                        if (selectedMode == Mode.QR) "QR" else "BARKOD",
                        index + 1
                    )

                    val metadata = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "Pictures/BarkodQRPro"
                        )
                    }

                    val uri = contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        metadata
                    )

                    if (uri != null) {

                        contentResolver.openOutputStream(uri)?.use {

                            bitmap.compress(
                                Bitmap.CompressFormat.PNG,
                                100,
                                it
                            )
                        }

                        saved++
                    }

                    bitmap.recycle()

                } catch (_: Exception) {
                }

                if (index % 25 == 0) {

                    runOnUiThread {
                        status.text =
                            "PNG kaydediliyor: ${index + 1}/${items.size}"
                    }
                }
            }

            runOnUiThread {

                status.text =
                    "$saved PNG kaydedildi. Galeri > Pictures/BarkodQRPro"
            }

        }.start()
    }

    private fun makeBitmapForMode(
        value: String,
        selectedMode: Mode
    ): Bitmap {

        val oldMode = mode

        return try {

            mode = selectedMode
            makeBitmap(value)

        } finally {

            mode = oldMode
        }
    }

    override fun onDestroy() {

        stopScroll()
        super.onDestroy()
    }
}
