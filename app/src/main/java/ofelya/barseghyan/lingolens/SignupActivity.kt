package ofelya.barseghyan.lingolens

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SignupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

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
                    // ✅ Account created → go straight to MainActivity
                    Toast.makeText(this, "Account created! Welcome, $name 🎉", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }

        // ✅ "Already have an account?" → go to LoginActivity
        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}