package ofelya.barseghyan.lingolens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class BookViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).bookDao()

    val allBooks = dao.getAllBooks()

    fun getBookByName(name: String): Book? {
        return allBooks.value?.find { it.name == name }
    }

    fun insert(
        name: String,
        uriString: String,
        targetLanguageCode: String = "",
        targetLanguageName: String = ""
    ) = viewModelScope.launch {
        dao.insert(
            Book(
                name               = name,
                uriString          = uriString,
                targetLanguageCode = targetLanguageCode,
                targetLanguageName = targetLanguageName
            )
        )
    }

    fun delete(book: Book) = viewModelScope.launch {
        dao.delete(book)
    }
}