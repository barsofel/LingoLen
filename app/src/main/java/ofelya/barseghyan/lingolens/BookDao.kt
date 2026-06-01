package ofelya.barseghyan.lingolens

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun getAllBooks(): LiveData<List<Book>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("UPDATE books SET targetLanguageCode = :code, targetLanguageName = :name WHERE id = :id")
    suspend fun updateLanguage(id: Int, code: String, name: String)

    // ← new
    @Query("DELETE FROM books")
    suspend fun deleteAll()
}