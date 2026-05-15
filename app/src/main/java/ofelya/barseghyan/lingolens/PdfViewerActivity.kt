package ofelya.barseghyan.lingolens

import android.annotation.SuppressLint
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
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPdfTitle: TextView
    private lateinit var wordViewModel: WordViewModel

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val renderThread = newSingleThreadContext("PdfRender")

    private var totalPages = 0
    private val CHUNK_SIZE = 3
    private var currentBookPath = ""

    inner class PdfBridge {
        @JavascriptInterface
        fun requestMorePages(fromIndex: Int) {
            scope.launch { renderChunk(fromIndex, fromIndex + CHUNK_SIZE) }
        }

        @JavascriptInterface
        fun onPageLongPress() {
            runOnUiThread { showAddWordDialog("") }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        wordViewModel = ViewModelProvider(this)[WordViewModel::class.java]

        webView     = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        tvPdfTitle  = findViewById(R.id.tvPdfTitle)

        findViewById<View>(R.id.btnAddWord).setOnClickListener {
            showAddWordDialog("")
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        webView.settings.javaScriptEnabled    = true
        webView.settings.builtInZoomControls  = true
        webView.settings.displayZoomControls  = false
        webView.settings.useWideViewPort      = true
        webView.settings.loadWithOverviewMode = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(PdfBridge(), "PdfBridge")

        val uriString = intent.getStringExtra("pdf_uri")
        val filePath  = intent.getStringExtra("pdf_path")
        val uri = when {
            uriString != null -> Uri.parse(uriString)
            filePath  != null -> Uri.fromFile(File(filePath))
            else -> null
        }

        if (uri == null) {
            Toast.makeText(this, "No PDF provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentBookPath = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':') ?: "Unknown PDF"

        tvPdfTitle.text = currentBookPath
        loadPdf(uri)
    }

    private fun showAddWordDialog(prefill: String) {
        val sheet = BottomSheetDialog(this, R.style.BottomSheetTheme)
        val view  = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_word, null)
        sheet.setContentView(view)

        val etWord = view.findViewById<EditText>(R.id.etWord)
        etWord.setText(prefill)
        etWord.setSelection(prefill.length)

        view.findViewById<Button>(R.id.btnSaveWord).setOnClickListener {
            val word = etWord.text.toString().trim()
            if (word.isEmpty()) {
                Toast.makeText(this, "Please type a word first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            wordViewModel.insert(
                word = word,
                translation = "",
                bookPath = currentBookPath
            )
            Toast.makeText(this, "\"$word\" saved ✓", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            sheet.dismiss()
        }

        sheet.show()
        etWord.requestFocus()
    }

    private fun loadPdf(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        webView.visibility     = View.GONE

        scope.launch {
            try {
                withContext(renderThread) {
                    parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                        ?: throw Exception("Cannot open file")
                    pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
                }

                totalPages = pdfRenderer!!.pageCount
                webView.loadDataWithBaseURL(null, buildShellHtml(totalPages), "text/html", "UTF-8", null)

                progressBar.visibility = View.GONE
                webView.visibility     = View.VISIBLE

                renderChunk(0, CHUNK_SIZE)

            } catch (e: Exception) {
                Log.e("PdfViewer", "Failed", e)
                progressBar.visibility = View.GONE
                Toast.makeText(this@PdfViewerActivity,
                    "Could not open PDF: ${e.message}", Toast.LENGTH_LONG).show()
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
        val page   = pdfRenderer!!.openPage(index)
        val scale  = 1080f / page.width
        val width  = (page.width  * scale).toInt().coerceAtLeast(1)
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
var total=$pageCount,loading=false;
window.addEventListener('scroll',function(){
  if(loading)return;
  var phs=document.querySelectorAll('.ph');
  for(var i=0;i<phs.length;i++){
    var ph=phs[i];
    if(ph.style.display==='none')continue;
    var rect=ph.getBoundingClientRect();
    if(rect.top<window.innerHeight*2){
      var idx=parseInt(ph.id.replace('ph-',''));
      if(idx<total){
        loading=true;
        PdfBridge.requestMorePages(idx);
        setTimeout(function(){loading=false;},800);
      }
      break;
    }
  }
});
var longPressTimer;
document.addEventListener('touchstart',function(e){
  if(e.target.tagName==='IMG'){
    longPressTimer=setTimeout(function(){
      PdfBridge.onPageLongPress();
    },600);
  }
});
document.addEventListener('touchend',function(){clearTimeout(longPressTimer);});
document.addEventListener('touchmove',function(){clearTimeout(longPressTimer);});
</script>
</body></html>"""
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        renderThread.close()
        pdfRenderer?.close()
        try { parcelFileDescriptor?.close() } catch (_: Exception) {}
        webView.destroy()
    }
}