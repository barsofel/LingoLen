package ofelya.barseghyan.lingolens

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPdfTitle: TextView
    private lateinit var wordViewModel: WordViewModel
    private lateinit var loadingLayout: View
    private lateinit var tvStatus: TextView
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val renderThread = newSingleThreadContext("PdfRender")

    private var totalPages = 0
    private val CHUNK_SIZE = 3
    private var currentBookPath = ""
    private var isPdfReady = false

    inner class PdfBridge {
        @JavascriptInterface
        fun requestMorePages(fromIndex: Int) {
            scope.launch { renderChunk(fromIndex, fromIndex + CHUNK_SIZE) }
        }

        @JavascriptInterface
        fun onPageLongPress() {
            if (!isPdfReady) return
            runOnUiThread { showAddWordDialog("") }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        wordViewModel = ViewModelProvider(this)[WordViewModel::class.java]

        webView = findViewById(R.id.webView)
        loadingLayout = findViewById(R.id.loadingLayout)
        tvStatus = findViewById(R.id.tvStatus)
        tvPdfTitle = findViewById(R.id.tvPdfTitle)

        findViewById<View>(R.id.btnAddWord).setOnClickListener {
            if (isPdfReady) showAddWordDialog("")
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        webView.settings.javaScriptEnabled = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(PdfBridge(), "PdfBridge")

        val uriString = intent.getStringExtra("pdf_uri")
        val filePath = intent.getStringExtra("pdf_path")

        val uri = when {
            uriString != null -> Uri.parse(uriString)
            filePath != null -> Uri.fromFile(File(filePath))
            else -> null
        }

        if (uri == null) {
            Toast.makeText(this, "No PDF provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentBookPath = intent.getStringExtra("pdf_name")
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.removeSuffix(".pdf")
                    ?: "Unknown PDF"

        tvPdfTitle.text = currentBookPath
        loadPdf(uri)
    }

    private fun showAddWordDialog(prefill: String) {
        if (!isPdfReady) return

        val sheet = BottomSheetDialog(this, R.style.BottomSheetTheme)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_word, null)
        sheet.setContentView(view)

        val etWord = view.findViewById<EditText>(R.id.etWord)
        val btnTranslateSave = view.findViewById<Button>(R.id.btnSaveWord)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        etWord.setText(prefill)
        etWord.setSelection(prefill.length)

        btnTranslateSave.setOnClickListener {
            val word = etWord.text.toString().trim()
            if (word.isEmpty()) {
                Toast.makeText(this, "Please type a word first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bookViewModel = ViewModelProvider(this)[BookViewModel::class.java]
            val currentBook = bookViewModel.allBooks.value?.find { it.name == currentBookPath }
            val targetLanguage = currentBook?.targetLanguageCode?.takeIf { it.isNotBlank() }
                ?: getSelectedLanguageCode()
                ?: return@setOnClickListener

            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(targetLanguage)
                    .build()
            )

            btnTranslateSave.isEnabled = false
            btnTranslateSave.text = "Translating..."

            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    translator.translate(word)
                        .addOnSuccessListener { translatedWord ->
                            translator.close()

                            if (translatedWord.trim().equals(word.trim(), ignoreCase = true)) {
                                btnTranslateSave.isEnabled = true
                                btnTranslateSave.text = "Translate & Save"
                                etWord.error = "This word wasn't found in the dictionary"
                                etWord.requestFocus()
                                return@addOnSuccessListener        // sheet stays open, nothing saved
                            }

                            sheet.dismiss()
                            showTranslationOverlay(word, translatedWord)
                        }
                        .addOnFailureListener {
                            btnTranslateSave.isEnabled = true
                            btnTranslateSave.text = "Translate & Save"
                            Toast.makeText(this, "Translation failed", Toast.LENGTH_SHORT).show()
                            translator.close()
                        }
                }
                .addOnFailureListener {
                    btnTranslateSave.isEnabled = true
                    btnTranslateSave.text = "Translate & Save"
                    Toast.makeText(this, "Model download failed", Toast.LENGTH_SHORT).show()
                    translator.close()
                }
        }

        btnCancel.setOnClickListener { sheet.dismiss() }
        sheet.show()
        etWord.requestFocus()
    }

    private fun showTranslationOverlay(word: String, translation: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_translation_overlay, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvWord).text = word
        view.findViewById<TextView>(R.id.tvTranslation).text = translation


        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            wordViewModel.insert(
                word = word,
                translation = translation,
                bookPath = currentBookPath
            )
            Toast.makeText(this, "\"$word\" → \"$translation\" saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getSelectedLanguageCode(): String? {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("selected_language_code", null)
    }

    private fun loadPdf(uri: Uri) {
        isPdfReady = false
        loadingLayout.visibility = View.VISIBLE
        webView.visibility = View.GONE

        scope.launch {
            try {
                withContext(renderThread) {
                    parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                        ?: throw Exception("Cannot open file")
                    pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
                }

                totalPages = pdfRenderer!!.pageCount
                webView.loadDataWithBaseURL(null, buildShellHtml(totalPages), "text/html", "UTF-8", null)

                loadingLayout.visibility = View.GONE
                webView.visibility = View.VISIBLE

                renderChunk(0, CHUNK_SIZE)
                isPdfReady = true

            } catch (e: Exception) {
                Log.e("PdfViewer", "Failed", e)
                loadingLayout.visibility = View.GONE
                Toast.makeText(this@PdfViewerActivity, "Could not open PDF: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun renderChunk(fromIndex: Int, toIndexExclusive: Int) {
        val end = minOf(toIndexExclusive, totalPages)
        if (fromIndex >= end) return

        for (i in fromIndex until end) {
            val base64 = withContext(renderThread) { renderPageToBase64(i) }
            val js = """(function(){
                var img=document.getElementById('page-$i');
                if(img){img.src='data:image/jpeg;base64,$base64';img.style.display='block';}
                var ph=document.getElementById('ph-$i');
                if(ph)ph.style.display='none';
            })();"""
            webView.evaluateJavascript(js, null)
        }
    }

    private fun renderPageToBase64(index: Int): String {
        val page = pdfRenderer!!.openPage(index)
        val scale = 1080f / page.width
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        bitmap.recycle()

        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildShellHtml(pageCount: Int): String {
        val placeholders = (0 until pageCount).joinToString("\n") { i ->
            """<div class="page">
                <div class="num">Page ${i + 1} of $pageCount</div>
                <div id="ph-$i" class="ph">Loading…</div>
                <img id="page-$i" src=""
                     style="width:100%;display:none;"
                     oncontextmenu="PdfBridge.onPageLongPress();return false;" />
               </div>"""
        }

        return """<!DOCTYPE html><html>
<head><meta name="viewport" content="width=device-width,initial-scale=1.0">
<style>
  *{margin:0;padding:0;box-sizing:border-box;}
  body{background:#1A1A2E;padding:8px;font-family:sans-serif;}
  .page{background:#fff;border-radius:6px;margin-bottom:12px;
        overflow:hidden;box-shadow:0 2px 8px #0006;}
  .num{text-align:center;padding:6px;font-size:12px;color:#999;background:#f5f5f5;}
  .ph{height:200px;display:flex;align-items:center;justify-content:center;
      color:#aaa;font-size:13px;background:#fafafa;}
  img{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;}
</style></head>
<body>
$placeholders
<script>
var total=$pageCount, loading=false;
window.addEventListener('scroll',function(){
  if(loading)return;
  var phs=document.querySelectorAll('.ph');
  for(var i=0;i<phs.length;i++){
    var ph=phs[i];
    if(ph.style.display==='none')continue;
    var rect=ph.getBoundingClientRect();
    if(rect.top<window.innerHeight*2){
      var idx=parseInt(ph.id.replace('ph-',''));
      if(idx<total){loading=true;PdfBridge.requestMorePages(idx);setTimeout(function(){loading=false;},800);}
      break;
    }
  }
});
var longPressTimer, touchMoved=false;
document.addEventListener('touchstart',function(e){
  if(e.target.tagName==='IMG'){touchMoved=false;longPressTimer=setTimeout(function(){if(!touchMoved)PdfBridge.onPageLongPress();},600);}
});
document.addEventListener('touchend',function(){clearTimeout(longPressTimer);});
document.addEventListener('touchmove',function(){touchMoved=true;clearTimeout(longPressTimer);});
</script>
</body></html>"""
    }

    override fun onDestroy() {
        super.onDestroy()
        isPdfReady = false
        scope.cancel()
        renderThread.close()
        pdfRenderer?.close()
        try { parcelFileDescriptor?.close() } catch (_: Exception) {}
        webView.destroy()
    }
}