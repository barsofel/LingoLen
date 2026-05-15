package ofelya.barseghyan.lingolens

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class LibraryFragment : Fragment() {

    private val sharedViewModel: WordViewModel by activityViewModels()

    private val pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult

            // Open in our own WebView-based viewer
            val intent = Intent(requireContext(), PdfViewerActivity::class.java).apply {
                putExtra("pdf_uri", uri.toString())
            }
            startActivity(intent)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_library, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnAddPdf).setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }
}