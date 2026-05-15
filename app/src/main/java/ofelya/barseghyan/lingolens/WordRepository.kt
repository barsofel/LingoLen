package ofelya.barseghyan.lingolens

import androidx.lifecycle.LiveData
class WordRepository(private val dao: WordDao) {

    val allWords: LiveData<List<WordEntry>> = dao.getAllWords()
    val distinctBooks: LiveData<List<String>> = dao.getDistinctBooks()

    fun wordsByBook(path: String): LiveData<List<WordEntry>> = dao.getWordsByBook(path)

    suspend fun insert(word: WordEntry) = dao.insert(word)

    suspend fun delete(word: WordEntry) = dao.delete(word)
    suspend fun update(word: WordEntry) = dao.update(word)
    suspend fun deleteAll() = dao.deleteAll()

    suspend fun findByWordAndBook(word: String, path: String): WordEntry? =
        dao.findByWordAndBook(word, path)
}