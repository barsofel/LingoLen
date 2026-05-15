package ofelya.barseghyan.lingolens

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WordDao {

    @Query("SELECT * FROM words ORDER BY id DESC")
    fun getAllWords(): LiveData<List<WordEntry>>

    @Query("SELECT * FROM words WHERE bookPath = :path ORDER BY id DESC")
    fun getWordsByBook(path: String): LiveData<List<WordEntry>>

    @Query("SELECT DISTINCT bookPath FROM words")
    fun getDistinctBooks(): LiveData<List<String>>

    @Query("SELECT COUNT(*) FROM words WHERE bookPath = :path")
    suspend fun getWordCountForBook(path: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: WordEntry)

    @Update
    suspend fun update(word: WordEntry)

    @Delete
    suspend fun delete(word: WordEntry)

    @Query("DELETE FROM words")
    suspend fun deleteAll()

    @Query("SELECT * FROM words WHERE word = :word AND bookPath = :path LIMIT 1")
    suspend fun findByWordAndBook(word: String, path: String): WordEntry?
}