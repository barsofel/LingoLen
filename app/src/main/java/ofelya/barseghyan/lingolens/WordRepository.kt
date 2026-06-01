package ofelya.barseghyan.lingolens

import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WordRepository(private val dao: WordDao) {

    val allWords: LiveData<List<WordEntry>> = dao.getAllWords()
    val distinctBooks: LiveData<List<String>> = dao.getDistinctBooks()

    fun wordsByBook(path: String): LiveData<List<WordEntry>> = dao.getWordsByBook(path)

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userWordsCollection() =
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid).collection("words")
        }

    suspend fun insert(word: WordEntry) {
        dao.insert(word)
        userWordsCollection()?.add(
            mapOf(
                "word"        to word.word,
                "translation" to word.translation,
                "dateSaved"   to word.dateSaved,
                "bookPath"    to word.bookPath
            )
        )
    }

    suspend fun delete(word: WordEntry) {
        dao.delete(word)
        userWordsCollection()
            ?.whereEqualTo("word", word.word)
            ?.whereEqualTo("bookPath", word.bookPath)
            ?.get()
            ?.addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { it.reference.delete() }
            }
    }

    suspend fun update(word: WordEntry) = dao.update(word)

    suspend fun deleteAll() {
        dao.deleteAll()
        userWordsCollection()?.get()?.addOnSuccessListener { snapshot ->
            snapshot.documents.forEach { it.reference.delete() }
        }
    }

    suspend fun findByWordAndBook(word: String, path: String): WordEntry? =
        dao.findByWordAndBook(word, path)

    suspend fun syncFromFirestore() {
        userWordsCollection()?.get()?.addOnSuccessListener { snapshot ->
            snapshot.documents.forEach { doc ->
                val word = WordEntry(
                    word        = doc.getString("word") ?: return@forEach,
                    translation = doc.getString("translation") ?: "",
                    dateSaved   = doc.getString("dateSaved") ?: "",
                    bookPath    = doc.getString("bookPath") ?: ""
                )
                CoroutineScope(Dispatchers.IO).launch {
                    if (dao.findByWordAndBook(word.word, word.bookPath) == null) {
                        dao.insert(word)
                    }
                }
            }
        }
    }
}