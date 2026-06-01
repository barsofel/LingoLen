import com.google.firebase.firestore.DocumentId

data class WordModel(
    @DocumentId val id: String = "",
    val word: String = "",
    val definition: String = "",
    val timestamp: Long = System.currentTimeMillis()
)