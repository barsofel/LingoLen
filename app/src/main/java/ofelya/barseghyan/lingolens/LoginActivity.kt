package ofelya.barseghyan.lingolens

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    // ✅ Login success → go to MainActivity, clear back stack
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }

        // ✅ "Don't have an account?" → go to SignupActivity
        tvGoSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }
}