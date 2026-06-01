package ofelya.barseghyan.lingolens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

class LibraryFragment : Fragment() {

    private val sharedViewModel: WordViewModel by activityViewModels()
    private lateinit var bookViewModel: BookViewModel
    private lateinit var adapter: BookAdapter
    private lateinit var tvEmpty: View
    private lateinit var rvBooks: RecyclerView

    private val pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            showNameDialog(uri)
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_library, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGlobalHeader(view, "LingoLens")

        bookViewModel = ViewModelProvider(this)[BookViewModel::class.java]

        tvEmpty = view.findViewById(R.id.tvEmptyLibrary)
        rvBooks = view.findViewById(R.id.rvBooks)

        adapter = BookAdapter(
            onClick = { book ->
                openPdfViewer(
                    Uri.parse(book.uriString),
                    book.name,
                    book.targetLanguageCode
                )
            },
            onDelete = { book ->
                bookViewModel.delete(book)
            }
        )
        rvBooks.layoutManager = LinearLayoutManager(requireContext())
        rvBooks.adapter       = adapter

        bookViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            adapter.submitList(books)
            tvEmpty.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
            rvBooks.visibility = if (books.isEmpty()) View.GONE   else View.VISIBLE
        }

        view.findViewById<View>(R.id.btnAddPdf).setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }


    private fun showNameDialog(uri: Uri) {
        val sheet = BottomSheetDialog(requireContext(), R.style.BottomSheetTheme)
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_pdf_name, null)
        sheet.setContentView(sheetView)

        val suggestedName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.removeSuffix(".pdf") ?: ""

        val etName = sheetView.findViewById<EditText>(R.id.etPdfName)
        etName.setText(suggestedName)
        etName.setSelection(suggestedName.length)
        sheetView.findViewById<Button>(R.id.btnConfirmName).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nameExists = bookViewModel.allBooks.value
                ?.any { it.name.equals(name, ignoreCase = true) } == true
            if (nameExists) {
                etName.error = "A book with this name already exists"
                etName.requestFocus()
                return@setOnClickListener
            }

            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            sheet.dismiss()
            showLanguageSheet(name, uri)
        }

        sheetView.findViewById<Button>(R.id.btnCancelName).setOnClickListener {
            sheet.dismiss()
        }

        sheet.show()
        etName.requestFocus()
    }

    private fun showLanguageSheet(name: String, uri: Uri) {
        LanguageSelectionSheet(bookName = name) { langCode, langName ->
            requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("selected_language_code", langCode)
                .apply()

            bookViewModel.insert(
                name               = name,
                uriString          = uri.toString(),
                targetLanguageCode = langCode,
                targetLanguageName = langName
            )
            openPdfViewer(uri, name, langCode)
        }.show(parentFragmentManager, "lang_sheet")
    }

    private fun openPdfViewer(
        uri: Uri,
        bookName: String,
        langCode: String?
    ) {
        val hasPermission = requireContext().contentResolver
            .persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }

        if (!hasPermission) {
            Toast.makeText(
                requireContext(),
                "Permission lost for this file. Please re-add it.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        startActivity(
            Intent(requireContext(), PdfViewerActivity::class.java).apply {
                putExtra("pdf_uri", uri.toString())
                putExtra("pdf_name", bookName)
                putExtra("pdf_lang", langCode ?: "")
            }
        )
    }
}