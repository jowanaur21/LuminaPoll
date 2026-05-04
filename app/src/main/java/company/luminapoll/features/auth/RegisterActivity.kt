package company.luminapoll.features.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.features.dashboard.DashboardActivity

class RegisterActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_register)

        auth = FirebaseAuth.getInstance()

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val etConfirmPassword = findViewById<EditText>(R.id.et_confirm_password)
        val progressOverlay = findViewById<android.view.View>(R.id.register_progress_overlay)
        val btnRegister = findViewById<Button>(R.id.btn_register)
        val btnBack = findViewById<ImageView>(R.id.btn_back)

        // Auth screens have Lavender background (V75)
        intent.putExtra("IS_DASHBOARD", true)
        applyModeTheme(
            rootLayout = findViewById(R.id.main),
            primaryButtons = listOf(btnRegister),
            accentIcons = listOf(btnBack)
        )

        btnBack.setOnClickListener {
            finish()
        }

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressOverlay.visibility = android.view.View.VISIBLE
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                            displayName = username
                        }
                        user?.updateProfile(profileUpdates)
                            ?.addOnCompleteListener { profileTask ->
                                progressOverlay.visibility = android.view.View.GONE
                                if (profileTask.isSuccessful) {
                                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, DashboardActivity::class.java).apply {
                                        putExtra("EXTRA_MODE", "ONLINE")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this, "Profile update failed: ${profileTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        progressOverlay.visibility = android.view.View.GONE
                        Toast.makeText(baseContext, "Registration failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        }

        findViewById<TextView>(R.id.tv_login).setOnClickListener {
            finish()
        }
    }
}