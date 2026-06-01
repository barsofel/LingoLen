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
import com.google.firebase.auth.UserProfileChangeRequest

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val etName     = findViewById<EditText>(R.id.etName)
        val etEmail    = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirm  = findViewById<EditText>(R.id.etConfirmPassword)
        val btnSignup  = findViewById<Button>(R.id.btnSignup)
        val tvGoLogin  = findViewById<TextView>(R.id.tvGoLogin)

        btnSignup.setOnClickListener {
            val name     = etName.text.toString().trim()
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirm  = etConfirm.text.toString().trim()

            when {
                name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty() ->
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()

                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()

                password.length < 6 ->
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()

                password != confirm ->
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()

                else -> {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser

                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build()

                                user?.updateProfile(profileUpdates)

                                user?.sendEmailVerification()
                                    ?.addOnCompleteListener { verifyTask ->
                                        if (verifyTask.isSuccessful) {
                                            auth.signOut()

                                            AlertDialog.Builder(this)
                                                .setTitle("Verify your email")
                                                .setMessage(
                                                    "A verification link has been sent to $email.\n\n" +
                                                            "Please check your inbox (and spam folder) and click the link, " +
                                                            "then come back to log in."
                                                )
                                                .setPositiveButton("Go to Login") { _, _ ->
                                                    startActivity(Intent(this, LoginActivity::class.java))
                                                    finish()
                                                }
                                                .setCancelable(false)
                                                .show()
                                        } else {
                                            Toast.makeText(
                                                this,
                                                "Account created but failed to send verification email. " +
                                                        "Try logging in and requesting a new one.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            auth.signOut()
                                            startActivity(Intent(this, LoginActivity::class.java))
                                            finish()
                                        }
                                    }
                            } else {
                                val errorMessage = task.exception?.localizedMessage ?: "Registration failed."
                                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
        }

        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}