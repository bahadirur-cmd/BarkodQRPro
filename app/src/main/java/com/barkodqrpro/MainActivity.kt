package com.barkodqrpro

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.OpenableColumns
import android.content.ContentValues
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
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
    private var running = false
    private var scrollThread: Thread? = null
    private val PICK_TXT = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        addSamples()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 10)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        val title = TextView(this).apply {
            text = "BarkodQR Pro"
            textSize = 25f
            setTextColor(Color.rgb(20, 35, 55))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        page.addView(title)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val barcodeTab = Button(this).apply { text = "BARKOD"; setOnClickListener { mode = Mode.BARCODE; refresh() } }
        val qrTab = Button(this).apply { text = "QR KOD"; setOnClickListener { mode = Mode.QR; refresh() } }
        tabs.addView(barcodeTab, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(qrTab, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(tabs)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val load = Button(this).apply { text = "TXT YÜKLE"; setOnClickListener { pickTxt() } }
        val sample = Button(this).apply { text = "ÖRNEK"; setOnClickListener { addSamples() } }
        actions.addView(load, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(sample, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(actions)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val start = Button(this).apply { text = "BAŞLAT"; setOnClickListener { startScroll() } }
        val stop = Button(this).apply { text = "DURDUR"; setOnClickListener { stopScroll() } }
        val save = Button(this).apply { text = "PNG KAYDET"; setOnClickListener { saveAll() } }
        controls.addView(start, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(save, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(controls)

        speedText = TextView(this).apply { text = "Akış hızı: 5"; textSize = 14f; setTextColor(Color.DKGRAY) }
        page.addView(speedText)
        speed = SeekBar(this).apply {
            max = 19
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { speedText.text = "Akış hızı: ${p + 1}" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        page.addView(speed)
        status = TextView(this).apply {
            text = "Hazır"
            textSize = 13f
            setTextColor(Color.rgb(60, 75, 90))
            setPadding(2, 4, 2, 8)
        }
        page.addView(status)

        scroller = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            isFillViewport = false
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(8, 8, 8, 24)
        }
        scroller.addView(container)
        scroller.setOnScrollChangeListener { _, _, _, _, _ -> renderVisibleItems() }
        page.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun addSamples() {
        if (values.isEmpty()) {
            values.addAll(listOf("123456789012", "987654321098", "BARKODQR-2026", "000123456789"))
        }
        refresh()
    }

    private fun pickTxt() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, PICK_TXT)
    }

    @Deprecated("Deprecated in Android API but retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_TXT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val incoming = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readLines()
                        .map { it.trim() }.filter { it.isNotEmpty() }
                    values.clear()
                    values.addAll(incoming)
                }
                refresh()
                status.text = "${values.size} kayıt yüklendi."
            } catch (e: Exception) {
                status.text = "Dosya okunamadı: ${e.localizedMessage}"
            }
        }
    }

    private fun makeBitmap(value: String): Bitmap {
        val format = if (mode == Mode.QR) BarcodeFormat.QR_CODE else BarcodeFormat.CODE_128
        val width = if (mode == Mode.QR) 420 else 900
        val height = if (mode == Mode.QR) 420 else 230
        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 2
        val matrix = MultiFormatWriter().encode(value, format, width, height, hints)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) for (y in 0 until height) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
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
                setPadding(8, 8, 8, 2)
                tag = null
            }
            imageViews.add(image)
            container.addView(image, LinearLayout.LayoutParams(-1, if (mode == Mode.QR) 330 else 140))
            val label = TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 8)
            }
            container.addView(label)
        }
        status.text = "${values.size} kod hazır. ${if (mode == Mode.QR) "QR" else "Code 128"} modu."
        renderVisibleItems()
    }

    // Yalnızca ekranda görünen kodları üretir. Böylece binlerce kayıtta bellek şişmez.
    private fun renderVisibleItems() {
        if (imageViews.isEmpty()) return
        val first = (scroller.scrollY / rowHeight).coerceAtLeast(0)
        val last = (first + scroller.height / rowHeight + 3).coerceAtMost(imageViews.size - 1)
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
        running = true
        status.text = "Otomatik akış başladı."
        scrollThread = Thread {
            while (running) {
                try {
                    runOnUiThread {
                        val distance = (speed.progress + 1) * 2
                        scroller.smoothScrollBy(0, distance)
                        if (scroller.scrollY >= container.height - scroller.height) {
                            scroller.postDelayed({ if (running) scroller.scrollTo(0, 0) }, 250)
                        }
                    }
                    Thread.sleep((55 - speed.progress * 2).toLong().coerceAtLeast(15))
                } catch (_: InterruptedException) { break }
            }
        }.also { it.start() }
    }

    private fun stopScroll() {
        running = false
        scrollThread?.interrupt()
        scrollThread = null
    }

    private fun saveAll() {
        if (values.isEmpty()) { status.text = "Kaydedilecek kod yok."; return }
        Thread {
            var saved = 0
            values.forEachIndexed { index, value ->
                try {
                    val bitmap = makeBitmap(value)
                    val name = String.format(Locale.US, "%s_%04d.png", if (mode == Mode.QR) "QR" else "BARKOD", index + 1)
                    val metadata = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BarkodQRPro")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, metadata)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        saved++
                    }
                    bitmap.recycle()
                } catch (_: Exception) { }
                if (index % 25 == 0) {
                    runOnUiThread { status.text = "PNG kaydediliyor: ${index + 1}/${values.size}" }
                }
            }
            runOnUiThread { status.text = "$saved PNG, Galeri > Pictures/BarkodQRPro klasörüne kaydedildi." }
        }.start()
    }

    override fun onDestroy() {
        stopScroll()
        super.onDestroy()
    }
}
