package ofelya.barseghyan.lingolens

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val uriString: String,
    val addedAt: Long = System.currentTimeMillis(),
    val targetLanguageCode: String = "",
    val targetLanguageName: String = ""
)