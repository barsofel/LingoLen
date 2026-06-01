package ofelya.barseghyan.lingolens

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null && auth.currentUser!!.isEmailVerified) {
            goToMain()
            return
        } else if (auth.currentUser != null) {
            auth.signOut()
        }

        setContentView(R.layout.activity_login)

        val etEmail    = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin   = findViewById<Button>(R.id.btnLogin)
        val tvGoSignup = findViewById<TextView>(R.id.tvGoSignup)

        btnLogin.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            when {
                email.isEmpty() || password.isEmpty() ->
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()

                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()

                else -> {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser

                                if (user != null && !user.isEmailVerified) {
                                    auth.signOut()
                                    showResendDialog(email, password)
                                    return@addOnCompleteListener
                                }

                                val dao = AppDatabase.getInstance(this).wordDao()
                                CoroutineScope(Dispatchers.IO).launch {
                                    dao.deleteAll()
                                    runOnUiThread { goToMain() }
                                }
                            } else {
                                val errorMessage = task.exception?.localizedMessage ?: "Login failed."
                                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
        }

        tvGoSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }


    private fun showResendDialog(email: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Email not verified")
            .setMessage(
                "You must verify your email before logging in.\n\n" +
                        "Please check your inbox for the verification link."
            )
            .setPositiveButton("Resend email") { _, _ ->
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        auth.currentUser?.sendEmailVerification()
                            ?.addOnCompleteListener { resendTask ->
                                auth.signOut()
                                if (resendTask.isSuccessful) {
                                    Toast.makeText(
                                        this,
                                        "Verification email resent. Check your inbox.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Failed to resend. Please try again later.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Could not resend. Check your credentials.", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}