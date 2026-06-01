package ofelya.barseghyan.lingolens

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnSettingsBack = findViewById<ImageButton>(R.id.btnSettingsBack)
        val btnMockAccount  = findViewById<Button>(R.id.btnMockAccount)
        val btnLogout       = findViewById<Button>(R.id.btnLogout)
        val txtUsername     = findViewById<TextView>(R.id.txtUsername)

        val auth = FirebaseAuth.getInstance()
        txtUsername.text = auth.currentUser?.displayName ?: "Guest User"

        btnSettingsBack.setOnClickListener { finish() }

        btnMockAccount.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Enter new name"
                setText(auth.currentUser?.displayName ?: "")
            }

            AlertDialog.Builder(this)
                .setTitle("Edit Name")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build()

                    auth.currentUser?.updateProfile(profileUpdates)
                        ?.addOnSuccessListener {
                            txtUsername.text = newName
                            Toast.makeText(this, "Name updated!", Toast.LENGTH_SHORT).show()
                        }
                        ?.addOnFailureListener {
                            Toast.makeText(this, "Failed to update name", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out? All your saved words and PDFs will be removed from this device.")
                .setPositiveButton("Yes, Log Out") { _, _ ->
                    deleteAllDataAndLogout(auth)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun deleteAllDataAndLogout(auth: FirebaseAuth) {
        val db = AppDatabase.getInstance(this)
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()

        CoroutineScope(Dispatchers.IO).launch {

            wordDao.deleteAll()

            bookDao.deleteAll()

            val uid = auth.currentUser?.uid
            if (uid != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .collection("words")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { it.reference.delete() }
                    }
            }


            contentResolver.persistedUriPermissions.forEach { permission ->
                contentResolver.releasePersistableUriPermission(
                    permission.uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            runOnUiThread {
                auth.signOut()
                val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}