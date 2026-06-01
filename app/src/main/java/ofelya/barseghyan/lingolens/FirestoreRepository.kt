import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userWordsCollection() =
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid).collection("words")
        }

    fun saveWord(word: WordModel, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
        val collection = userWordsCollection() ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        collection.add(word)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun getWords(onResult: (List<WordModel>) -> Unit, onError: (Exception) -> Unit = {}) {
        val collection = userWordsCollection() ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        collection.get()
            .addOnSuccessListener { snapshot ->
                val words = snapshot.toObjects(WordModel::class.java)
                onResult(words)
            }
            .addOnFailureListener { onError(it) }
    }

    fun deleteWord(wordId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
        val collection = userWordsCollection() ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        collection.document(wordId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
}