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

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            displayZoomControls = false
            builtInZoomControls = true
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidInterface")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectWordDetector()
            }
        }

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

    inner class AndroidBridge {
        @JavascriptInterface
        fun processWord(word: String) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Word selected: $word", Toast.LENGTH_SHORT).show()
                saveToDictionary(word)
            }
        }
    }

    private fun saveToDictionary(word: String) {
    }
}