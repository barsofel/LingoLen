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
import com.google.mlkit.nl.translate.TranslateLanguage
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
    private lateinit var spinnerSourceLang: Spinner
    private lateinit var spinnerTargetLang: Spinner

    private val viewModel: WordViewModel by activityViewModels()

    private val allWords = mutableListOf<WordEntry>()
    private val filteredWords = mutableListOf<WordEntry>()

    private lateinit var adapter: WordAdapter

    private val bookList = mutableListOf<String>()
    private lateinit var bookAdapter: ArrayAdapter<String>

    // --- FIX 1: Track which word IDs are currently being translated
    //     so we never launch duplicate coroutines for the same word.
    private val translatingIds = mutableSetOf<Int>()

    // --- FIX 2: Keep track of pending translation jobs so we can
    //     cancel them cleanly when the view is destroyed.
    private val translationJobs = mutableMapOf<Int, Job>()

    // Supported languages
    private val supportedLanguages = linkedMapOf(
        "English"    to TranslateLanguage.ENGLISH,
        "Spanish"    to TranslateLanguage.SPANISH,
        "French"     to TranslateLanguage.FRENCH,
        "German"     to TranslateLanguage.GERMAN,
        "Russian"    to TranslateLanguage.RUSSIAN,
        "Arabic"     to TranslateLanguage.ARABIC,
        "Chinese"    to TranslateLanguage.CHINESE,
        "Japanese"   to TranslateLanguage.JAPANESE,
        "Korean"     to TranslateLanguage.KOREAN,
        "Italian"    to TranslateLanguage.ITALIAN,
        "Portuguese" to TranslateLanguage.PORTUGUESE,
        "Turkish"    to TranslateLanguage.TURKISH,
        "Hindi"      to TranslateLanguage.HINDI
    )

    private val languageNames = supportedLanguages.keys.toList()
    private val languageCodes = supportedLanguages.values.toList()

    private var sourceLangCode = TranslateLanguage.ENGLISH
    private var targetLangCode = TranslateLanguage.SPANISH

    private lateinit var currentTranslator: Translator
    private var modelDownloaded = false

    // --- FIX 3: Flag so the language spinner doesn't fire on first
    //     selection (which would needlessly rebuild the translator).
    private var spinnersInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_dictionary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvWords        = view.findViewById(R.id.rvWords)
        etSearch       = view.findViewById(R.id.etSearch)
        tvWordCount    = view.findViewById(R.id.tvWordCount)
        layoutEmpty    = view.findViewById(R.id.layoutEmpty)
        spinnerBook    = view.findViewById(R.id.spinnerBook)
        spinnerSourceLang = view.findViewById(R.id.spinnerSourceLang)
        spinnerTargetLang = view.findViewById(R.id.spinnerTargetLang)

        // RecyclerView
        adapter = WordAdapter(filteredWords) { word -> viewModel.delete(word) }
        rvWords.layoutManager = LinearLayoutManager(requireContext())
        rvWords.adapter = adapter

        // Book Spinner
        bookAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            bookList
        )
        bookAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBook.adapter = bookAdapter

        spinnerBook.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = bookList[position]
                viewModel.setBookFilter(if (selected == "All books") null else selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Language adapters
        val langAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languageNames
        )
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSourceLang.adapter = langAdapter
        spinnerTargetLang.adapter = langAdapter

        // Default selections — these fire onItemSelected immediately,
        // but spinnersInitialized is still false so no rebuild happens.
        spinnerSourceLang.setSelection(languageNames.indexOf("English"))
        spinnerTargetLang.setSelection(languageNames.indexOf("Spanish"))
        spinnersInitialized = true          // ← now real user changes will rebuild

        // Language change listener
        val languageListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnersInitialized) return
                sourceLangCode = languageCodes[spinnerSourceLang.selectedItemPosition]
                targetLangCode = languageCodes[spinnerTargetLang.selectedItemPosition]
                rebuildTranslator()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerSourceLang.onItemSelectedListener = languageListener
        spinnerTargetLang.onItemSelectedListener = languageListener

        // Build first translator AFTER spinners are wired up
        currentTranslator = buildTranslator()
        downloadModel()

        // Observe book list
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

        // Observe words
        observeWords()

        // Search filter
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterWords(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // -------------------------------------------------------------------------
    // Translator helpers
    // -------------------------------------------------------------------------

    private fun buildTranslator(): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build()
        return Translation.getClient(options)
    }

    private fun rebuildTranslator() {
        // Cancel all in-flight translation jobs — they used the old translator.
        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        translatingIds.clear()

        currentTranslator.close()
        modelDownloaded = false
        currentTranslator = buildTranslator()

        // --- FIX 4: After rebuilding, wipe stored translations so every word
        //     gets re-translated into the new target language.
        //     We update our local copy only; DB rows are patched lazily below.
        allWords.replaceAll { it.copy(translation = "") }
        filterWords(etSearch.text.toString())   // refresh UI with "Translating…"

        downloadModel()
    }

    private fun downloadModel() {
        currentTranslator
            .downloadModelIfNeeded()
            .addOnSuccessListener {
                modelDownloaded = true
                // Kick off translation for any word that still has no translation.
                translatePendingWords()
            }
            .addOnFailureListener {
                modelDownloaded = false
            }
    }

    // -------------------------------------------------------------------------
    // Word observation
    // -------------------------------------------------------------------------

    private fun observeWords() {
        viewModel.filteredWords.observe(viewLifecycleOwner) { words ->
            // --- FIX 5: Merge incoming DB words with any in-memory translations
            //     we already have, so a "save → translate → DB write → re-observe"
            //     cycle never shows a word flickering back to blank.
            val inMemoryTranslations = allWords.associateBy({ it.id }, { it.translation })

            allWords.clear()
            allWords.addAll(words.map { word ->
                val cached = inMemoryTranslations[word.id]
                if (!cached.isNullOrBlank() && word.translation.isBlank()) {
                    word.copy(translation = cached)   // keep our in-memory translation
                } else {
                    word
                }
            })

            filterWords(etSearch.text.toString())
            tvWordCount.text = "${words.size} words"

            if (modelDownloaded) translatePendingWords()
        }
    }

    // -------------------------------------------------------------------------
    // Translation
    // -------------------------------------------------------------------------

    /** Translates every word in [allWords] that still has a blank translation,
     *  skipping any word that is already being translated. */
    private fun translatePendingWords() {
        allWords.forEach { wordEntry ->
            if (wordEntry.translation.isBlank() && wordEntry.id !in translatingIds) {
                translatingIds.add(wordEntry.id)

                val job = viewLifecycleOwner.lifecycleScope.launch {
                    val translated = translateWord(wordEntry.word)

                    // --- FIX 6: Update in-memory list FIRST so the UI refreshes
                    //     immediately, then persist to DB separately.
                    val idx = allWords.indexOfFirst { it.id == wordEntry.id }
                    if (idx != -1 && translated != "Error") {
                        allWords[idx] = allWords[idx].copy(translation = translated)
                        filterWords(etSearch.text.toString())   // refresh RecyclerView

                        // Persist to DB (will re-trigger observer, but merge above
                        // prevents the translation from being lost).
                        viewModel.update(allWords[idx])
                    }

                    translatingIds.remove(wordEntry.id)
                    translationJobs.remove(wordEntry.id)
                }

                translationJobs[wordEntry.id] = job
            }
        }
    }

    private suspend fun translateWord(word: String): String {
        return try {
            suspendCancellableCoroutine { cont ->
                currentTranslator.translate(word)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error"
        }
    }

    // -------------------------------------------------------------------------
    // Filtering / UI
    // -------------------------------------------------------------------------

    private fun filterWords(query: String) {
        filteredWords.clear()
        filteredWords.addAll(
            if (query.isEmpty()) allWords
            else allWords.filter {
                it.word.contains(query, ignoreCase = true) ||
                        it.translation.contains(query, ignoreCase = true)
            }
        )
        adapter.submitList(filteredWords.toList())

        if (filteredWords.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvWords.visibility     = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvWords.visibility     = View.VISIBLE
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onDestroyView() {
        super.onDestroyView()
        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        translatingIds.clear()
        currentTranslator.close()
    }
}