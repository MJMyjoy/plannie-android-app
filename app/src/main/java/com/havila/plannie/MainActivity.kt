package com.havila.plannie

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var splashView: LinearLayout
    private lateinit var rootLayout: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Modern edge-to-edge & status bar color
        WindowCompat.setDecorFitsSystemWindows(window, true)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.parseColor("#E8F5E9")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }

        // Root container
        rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F6F9F6"))
        }

        // ---- Splash Screen ----
        splashView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E8F5E9"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Plannie logo icon
        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            val size = (120 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
        }

        // Plannie title
        val titleView = TextView(this).apply {
            text = "Plannie"
            setTextColor(Color.parseColor("#2E7D32"))
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }

        // Subtitle
        val subtitleView = TextView(this).apply {
            text = "Votre assistant commercial intelligent"
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }

        splashView.addView(logoView)
        splashView.addView(titleView)
        splashView.addView(subtitleView)

        // ---- WebView ----
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.INVISIBLE
            setBackgroundColor(Color.parseColor("#F6F9F6"))

            @Suppress("DEPRECATION")
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = 100
            }

            addJavascriptInterface(PlannieBridge(), "PlannieBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Small delay for smooth transition
                    Handler(Looper.getMainLooper()).postDelayed({
                        view?.visibility = View.VISIBLE
                        // Fade out splash
                        splashView.animate()
                            .alpha(0f)
                            .setDuration(400)
                            .withEndAction {
                                splashView.visibility = View.GONE
                            }
                            .start()
                    }, 600)
                }
            }

            webChromeClient = WebChromeClient()
        }

        rootLayout.addView(webView)
        rootLayout.addView(splashView)
        setContentView(rootLayout)

        // Load the app
        webView.loadUrl("file:///android_asset/plannie.html")

        // Handle back button with modern callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // ================================================================
    //  JAVASCRIPT BRIDGE — PDF GENERATION
    // ================================================================
    inner class PlannieBridge {

        @JavascriptInterface
        fun generatePDF(jsonData: String) {
            try {
                val data = JSONObject(jsonData)
                val filename = data.getString("filename")
                val pdfFile = createPdfReport(data, filename)

                // Notify JS
                Handler(Looper.getMainLooper()).post {
                    webView.evaluateJavascript(
                        "if(typeof onPdfSaved==='function')onPdfSaved('${pdfFile.absolutePath}');",
                        null
                    )
                    // Show Android toast too
                    Toast.makeText(
                        this@MainActivity,
                        "📄 Rapport PDF sauvegardé !",
                        Toast.LENGTH_LONG
                    ).show()

                    // Open the PDF
                    openPdf(pdfFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    webView.evaluateJavascript(
                        "if(typeof onPdfError==='function')onPdfError('${e.message?.replace("'", "\\'")}');",
                        null
                    )
                }
            }
        }

        private fun createPdfReport(data: JSONObject, filename: String): File {
            val document = PdfDocument()
            val pageWidth = 595  // A4 width in points
            val pageHeight = 842 // A4 height in points
            val margin = 40f
            val contentWidth = pageWidth - 2 * margin

            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var y = 0f

            // Paints
            val paintTitle = Paint().apply {
                color = Color.WHITE; textSize = 22f; isFakeBoldText = true; isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val paintSubtitle = Paint().apply {
                color = Color.WHITE; textSize = 11f; isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val paintSectionTitle = Paint().apply {
                color = Color.parseColor("#2E7D32"); textSize = 14f; isFakeBoldText = true; isAntiAlias = true
            }
            val paintText = Paint().apply {
                color = Color.parseColor("#1A1A1A"); textSize = 10f; isAntiAlias = true
            }
            val paintTextBold = Paint().apply {
                color = Color.parseColor("#1A1A1A"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true
            }
            val paintGray = Paint().apply {
                color = Color.parseColor("#999999"); textSize = 9f; isAntiAlias = true
            }
            val paintHeaderBg = Paint().apply {
                color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL
            }
            val paintHeaderBgLight = Paint().apply {
                color = Color.parseColor("#4CAF50"); style = Paint.Style.FILL
            }
            val paintHeaderText = Paint().apply {
                color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true
            }
            val paintRowBg = Paint().apply {
                color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL
            }
            val paintFooter = Paint().apply {
                color = Color.parseColor("#999999"); textSize = 9f; isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            fun checkNewPage(needed: Float) {
                if (y + needed > pageHeight - 50f) {
                    document.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = 30f
                }
            }

            // ---- Green Header ----
            val headerPaint = Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 80f, headerPaint)
            canvas.drawText("Plannie", pageWidth / 2f, 35f, paintTitle)
            val dateStr = "Rapport de la journ\u00e9e \u2013 ${data.getString("date")}"
            canvas.drawText(dateStr, pageWidth / 2f, 58f, paintSubtitle)
            y = 100f

            // ---- Section 1: Données initiales ----
            canvas.drawText("Donn\u00e9es initiales", margin, y, paintSectionTitle)
            y += 18f

            val initialProducts = data.getJSONArray("initialProducts")
            if (initialProducts.length() > 0) {
                val colWidths = floatArrayOf(contentWidth * 0.35f, contentWidth * 0.18f, contentWidth * 0.25f, contentWidth * 0.22f)
                val headers = arrayOf("Article", "Qt\u00e9 initiale", "Prix d'achat (FC)", "Prix unitaire (FC)")

                canvas.drawRect(margin, y, margin + contentWidth, y + 20f, paintHeaderBg)
                var xOff = margin + 5f
                for (j in headers.indices) {
                    canvas.drawText(headers[j], xOff, y + 14f, paintHeaderText)
                    xOff += colWidths[j]
                }
                y += 20f

                for (i in 0 until initialProducts.length()) {
                    checkNewPage(16f)
                    val p = initialProducts.getJSONObject(i)
                    if (i % 2 == 1) {
                        canvas.drawRect(margin, y, margin + contentWidth, y + 16f, paintRowBg)
                    }
                    xOff = margin + 5f
                    canvas.drawText(p.getString("name"), xOff, y + 12f, paintText)
                    xOff += colWidths[0]
                    canvas.drawText(p.getInt("quantity").toString(), xOff, y + 12f, paintText)
                    xOff += colWidths[1]
                    canvas.drawText(p.getString("purchasePrice"), xOff, y + 12f, paintText)
                    xOff += colWidths[2]
                    canvas.drawText(p.getString("unitPrice"), xOff, y + 12f, paintText)
                    y += 16f
                }
                y += 15f
            }

            // ---- Section 2: Approvisionnements ----
            val restocks = data.getJSONArray("restocks")
            if (restocks.length() > 0) {
                checkNewPage(40f)
                canvas.drawText("Approvisionnements de la journ\u00e9e", margin, y, paintSectionTitle)
                y += 18f

                val colWidths2 = floatArrayOf(contentWidth * 0.2f, contentWidth * 0.25f, contentWidth * 0.15f, contentWidth * 0.2f, contentWidth * 0.2f)
                val headers2 = arrayOf("Type", "Article", "Qt\u00e9", "Prix d'achat", "Prix unit.")

                canvas.drawRect(margin, y, margin + contentWidth, y + 20f, paintHeaderBgLight)
                var xOff = margin + 5f
                for (j in headers2.indices) {
                    canvas.drawText(headers2[j], xOff, y + 14f, paintHeaderText)
                    xOff += colWidths2[j]
                }
                y += 20f

                for (i in 0 until restocks.length()) {
                    checkNewPage(16f)
                    val r = restocks.getJSONObject(i)
                    if (i % 2 == 1) {
                        canvas.drawRect(margin, y, margin + contentWidth, y + 16f, paintRowBg)
                    }
                    xOff = margin + 5f
                    canvas.drawText(r.getString("type"), xOff, y + 12f, paintText)
                    xOff += colWidths2[0]
                    canvas.drawText(r.getString("name"), xOff, y + 12f, paintText)
                    xOff += colWidths2[1]
                    canvas.drawText(r.getInt("quantity").toString(), xOff, y + 12f, paintText)
                    xOff += colWidths2[2]
                    canvas.drawText(r.getString("purchasePrice"), xOff, y + 12f, paintText)
                    xOff += colWidths2[3]
                    canvas.drawText(r.getString("unitPrice"), xOff, y + 12f, paintText)
                    y += 16f
                }
                y += 15f
            }

            // ---- Section 3: Ventes du jour ----
            checkNewPage(30f)
            canvas.drawText("Ventes du jour", margin, y, paintSectionTitle)
            y += 18f

            val salesByProduct = data.getJSONArray("salesByProduct")
            for (i in 0 until salesByProduct.length()) {
                val productSales = salesByProduct.getJSONObject(i)
                val productName = productSales.getString("name")
                val sales = productSales.getJSONArray("sales")

                checkNewPage(20f + sales.length() * 16f)
                canvas.drawText(productName, margin, y, paintTextBold)
                y += 14f

                if (sales.length() == 0) {
                    canvas.drawText("Aucune vente", margin + 10f, y, paintGray)
                    y += 14f
                } else {
                    val colWidths3 = floatArrayOf(contentWidth * 0.08f, contentWidth * 0.12f, contentWidth * 0.25f, contentWidth * 0.27f, contentWidth * 0.28f)
                    val headers3 = arrayOf("#", "Qt\u00e9", "Vente (FC)", "Co\u00fbt FIFO (FC)", "Gain/Perte (FC)")

                    canvas.drawRect(margin + 10f, y, margin + contentWidth, y + 18f, paintHeaderBgLight)
                    var xOff = margin + 15f
                    for (j in headers3.indices) {
                        canvas.drawText(headers3[j], xOff, y + 13f, paintHeaderText)
                        xOff += colWidths3[j]
                    }
                    y += 18f

                    for (si in 0 until sales.length()) {
                        checkNewPage(15f)
                        val s = sales.getJSONObject(si)
                        if (si % 2 == 1) {
                            canvas.drawRect(margin + 10f, y, margin + contentWidth, y + 15f, paintRowBg)
                        }
                        xOff = margin + 15f
                        canvas.drawText(s.getInt("num").toString(), xOff, y + 11f, paintText)
                        xOff += colWidths3[0]
                        canvas.drawText(s.getInt("quantity").toString(), xOff, y + 11f, paintText)
                        xOff += colWidths3[1]
                        canvas.drawText(s.getString("salePrice"), xOff, y + 11f, paintText)
                        xOff += colWidths3[2]
                        canvas.drawText(s.getString("fifoCost"), xOff, y + 11f, paintText)
                        xOff += colWidths3[3]
                        canvas.drawText(s.getString("gainLoss"), xOff, y + 11f, paintText)
                        y += 15f
                    }
                    y += 12f
                }
            }

            // ---- Section 4: Rapport Plannie ----
            checkNewPage(80f)
            canvas.drawText("Rapport Plannie", margin, y, paintSectionTitle)
            y += 18f

            val summaryLines = mutableListOf<String>()
            summaryLines.add("Caisse totale du jour : ${data.getString("totalCaisse")} FC")

            if (!data.isNull("articleVert")) {
                val av = data.getJSONObject("articleVert")
                summaryLines.add("Article Vert (meilleur revenu) : ${av.getString("name")} \u2013 Gain : ${av.getString("gain")} FC")
            } else {
                summaryLines.add("Article Vert : Aucun article \u00e9ligible")
            }

            if (!data.isNull("articleDore")) {
                val ad = data.getJSONObject("articleDore")
                summaryLines.add("Article Dor\u00e9 (meilleur %) : ${ad.getString("name")} \u2013 ${ad.getString("pct")}%")
            } else {
                summaryLines.add("Article Dor\u00e9 : Aucun article \u00e9ligible")
            }

            if (!data.isNull("articleOrange")) {
                val ao = data.getJSONObject("articleOrange")
                summaryLines.add("Article Orange (plus faible revenu) : ${ao.getString("name")} \u2013 Gain : ${ao.getString("gain")} FC")
            } else {
                summaryLines.add("Article Orange : Aucun article \u00e9ligible")
            }

            if (!data.isNull("articleRouge")) {
                val ar = data.getJSONObject("articleRouge")
                summaryLines.add("Article Rouge (plus faible %) : ${ar.getString("name")} \u2013 ${ar.getString("pct")}%")
            } else {
                summaryLines.add("Article Rouge : Aucun article \u00e9ligible")
            }

            for (line in summaryLines) {
                checkNewPage(14f)
                canvas.drawText(line, margin, y, paintText)
                y += 14f
            }

            // Footer
            y += 20f
            checkNewPage(20f)
            canvas.drawText("\u00a9 Plannie 2026 by Havila", pageWidth / 2f, y, paintFooter)

            document.finishPage(page)

            // Save to Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, filename)
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
            document.close()

            return file
        }

        private fun openPdf(file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                // No PDF viewer installed, just show toast
                Toast.makeText(
                    this@MainActivity,
                    "Le PDF est dans vos T\u00e9l\u00e9chargements : ${file.name}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
