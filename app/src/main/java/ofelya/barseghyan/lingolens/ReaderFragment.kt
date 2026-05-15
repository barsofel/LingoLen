package ofelya.barseghyan.lingolens
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment

class ReaderFragment : Fragment(R.layout.fragment_reader) {

    private lateinit var webView: WebView


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.webViewReader)

        // 1. Configure WebView Settings
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            displayZoomControls = false
            builtInZoomControls = true
        }

        // 2. Add the interface to catch the clicked word
        webView.addJavascriptInterface(AndroidBridge(), "AndroidInterface")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // 3. Inject JavaScript to detect double-clicks on words
                injectWordDetector()
            }
        }

        // 4. Load your PDF (Replace with your actual PDF URI or path)
        // Note: For actual PDFs, you'd usually use PDF.js or a Google Docs viewer URL
        val sampleUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
        webView.loadUrl("https://docs.google.com/viewer?embedded=true&url=$sampleUrl")
    }

    private fun injectWordDetector() {
        val js = """
            document.addEventListener('dblclick', function() {
                var sel = window.getSelection().toString().trim();
                if (sel.length > 0) {
                    AndroidInterface.processWord(sel);
                }
            });
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // This class receives data FROM the WebView
    inner class AndroidBridge {
        @JavascriptInterface
        fun processWord(word: String) {
            activity?.runOnUiThread {
                // This is where you will eventually call your Dictionary/Translation API
                Toast.makeText(requireContext(), "Word selected: $word", Toast.LENGTH_SHORT).show()
                saveToDictionary(word)
            }
        }
    }

    private fun saveToDictionary(word: String) {
        // Logic for your "saved words" list goes here
    }
}