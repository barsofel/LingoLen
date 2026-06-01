package ofelya.barseghyan.lingolens

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DictionaryFragment : Fragment() {

    private lateinit var rvWords: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvWordCount: TextView
    private lateinit var layoutEmpty: View
    private lateinit var spinnerBook: Spinner

    private val viewModel: WordViewModel by activityViewModels()
    private val bookViewModel: BookViewModel by activityViewModels()

    private val allWords = mutableListOf<WordEntry>()
    private lateinit var adapter: WordAdapter

    private val bookList = mutableListOf<String>()
    private lateinit var bookAdapter: ArrayAdapter<String>

    private val translatingIds = mutableSetOf<Int>()
    private val translationJobs = mutableMapOf<Int, Job>()

    private var currentTranslator: Translator? = null
    private var currentLanguageCode: String? = null
    private var modelDownloaded = false
    private var isSavingTranslations = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_dictionary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvWords = view.findViewById(R.id.rvWords)
        etSearch = view.findViewById(R.id.etSearch)
        tvWordCount = view.findViewById(R.id.tvWordCount)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        spinnerBook = view.findViewById(R.id.spinnerBook)

        adapter = WordAdapter { word -> viewModel.delete(word) }
        rvWords.layoutManager = LinearLayoutManager(requireContext())
        rvWords.adapter = adapter

        bookAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            bookList
        )
        bookAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBook.adapter = bookAdapter

        spinnerBook.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = bookList[position]
                viewModel.setBookFilter(if (selected == "All books") null else selected)
                applyLanguageFor(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewModel.distinctBooks.observe(viewLifecycleOwner) { paths ->
            bookList.clear()
            bookList.add("All books")
            bookList.addAll(paths)
            bookAdapter.notifyDataSetChanged()

            val preselected = viewModel.currentBookPath.value
            if (!preselected.isNullOrBlank() && bookList.contains(preselected)) {
                spinnerBook.setSelection(bookList.indexOf(preselected))
            }
        }

        bookViewModel.allBooks.observe(viewLifecycleOwner) {
            val selected = bookList.getOrNull(spinnerBook.selectedItemPosition) ?: return@observe
            applyLanguageFor(selected)
        }

        viewModel.filteredWords.observe(viewLifecycleOwner) { words ->
            if (isSavingTranslations) return@observe

            val cached = allWords.associateBy({ it.id }, { it.translation })
            val newIds = words.map { it.id }.toSet()

            allWords.map { it.id }
                .filter { it !in newIds }
                .forEach { id ->
                    translationJobs[id]?.cancel()
                    translationJobs.remove(id)
                    translatingIds.remove(id)
                }

            allWords.clear()
            allWords.addAll(
                words.map { word ->
                    val t = cached[word.id]
                    if (!t.isNullOrBlank() && word.translation.isBlank()) {
                        word.copy(translation = t)
                    } else {
                        word
                    }
                }
            )

            tvWordCount.text = "${words.size} words"
            pushListToAdapter(etSearch.text.toString())

            if (spinnerBook.selectedItem?.toString() != "All books" && modelDownloaded) {
                translatePendingWords()
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                pushListToAdapter(s.toString())
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {}
        })
    }

    private fun applyLanguageFor(selectedBookName: String) {
        val books = bookViewModel.allBooks.value ?: return

        if (selectedBookName == "All books") {

            currentTranslator?.close()
            currentTranslator = null
            currentLanguageCode = null
            modelDownloaded = false

            pushListToAdapter(etSearch.text.toString())
            return
        }

        val selectedBook = books.find { it.name == selectedBookName } ?: return
        val code = selectedBook.targetLanguageCode ?: return

        if (code == currentLanguageCode) return

        currentLanguageCode = code
        buildAndDownload(code)
    }
    private fun buildAndDownload(languageCode: String) {
        currentTranslator?.close()

        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        translatingIds.clear()
        modelDownloaded = false

        pushListToAdapter(etSearch.text.toString())

        currentTranslator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
                .setTargetLanguage(languageCode)
                .build()
        )

        currentTranslator!!.downloadModelIfNeeded()
            .addOnSuccessListener {
                modelDownloaded = true
                translatePendingWords()
            }
            .addOnFailureListener {
                modelDownloaded = false
            }
    }
    private fun translatePendingWords() {
        val translator = currentTranslator ?: return

        allWords
            .filter {
                it.translation.isBlank() && it.id !in translatingIds
            }
            .forEach { wordEntry ->

                translatingIds.add(wordEntry.id)

                val job = viewLifecycleOwner.lifecycleScope.launch {
                    val result = try {
                        suspendCancellableCoroutine<String> { cont ->
                            translator.translate(wordEntry.word)
                                .addOnSuccessListener { cont.resume(it) }
                                .addOnFailureListener { cont.resumeWithException(it) }
                        }
                    } catch (e: Exception) {
                        "Error"
                    }

                    val idx = allWords.indexOfFirst { it.id == wordEntry.id }

                    if (idx != -1 && result != "Error") {
                        allWords[idx] = allWords[idx].copy(translation = result)
                        pushListToAdapter(etSearch.text.toString())


                        viewModel.update(allWords[idx])
                    }

                    translatingIds.remove(wordEntry.id)
                    translationJobs.remove(wordEntry.id)
                }

                translationJobs[wordEntry.id] = job
            }
    }

    private fun pushListToAdapter(query: String) {
        val snapshot =
            if (query.isEmpty()) {
                allWords.toList()
            } else {
                allWords.filter {
                    it.word.contains(query, ignoreCase = true) ||
                            it.translation.contains(query, ignoreCase = true)
                }
            }

        adapter.submitList(snapshot)

        layoutEmpty.visibility =
            if (snapshot.isEmpty()) View.VISIBLE else View.GONE

        rvWords.visibility =
            if (snapshot.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        translationJobs.values.forEach { it.cancel() }
        currentTranslator?.close()
        adapter.release()
    }
}