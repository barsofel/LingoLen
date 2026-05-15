package ofelya.barseghyan.lingolens

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WordRepository
    val allWords: LiveData<List<WordEntry>>
    val distinctBooks: LiveData<List<String>>
    val filteredWords: LiveData<List<WordEntry>>

    private val _currentBookPath = MutableLiveData<String?>()
    val currentBookPath: LiveData<String?> = _currentBookPath

    init {
        val dao = AppDatabase.getInstance(application).wordDao()
        repository = WordRepository(dao)

        allWords = repository.allWords
        distinctBooks = repository.distinctBooks

        filteredWords = _currentBookPath.switchMap { path ->
            if (path.isNullOrBlank()) {
                repository.allWords
            } else {
                repository.wordsByBook(path)
            }
        }
    }

    fun insert(word: String, translation: String, bookPath: String) = viewModelScope.launch {
        if (repository.findByWordAndBook(word, bookPath) == null) {
            val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
            repository.insert(
                WordEntry(
                    word = word,
                    translation = translation,
                    dateSaved = date,
                    bookPath = bookPath
                )
            )
        }
    }

    fun update(word: WordEntry) = viewModelScope.launch {
        repository.update(word)
    }

    fun setBookFilter(bookPath: String?) {
        _currentBookPath.value = bookPath
    }

    fun delete(word: WordEntry) = viewModelScope.launch {
        repository.delete(word)
    }

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }
}