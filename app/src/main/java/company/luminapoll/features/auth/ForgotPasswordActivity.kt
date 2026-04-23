package company.luminapoll.features.auth

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity

class ForgotPasswordActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.et_email)
        val btnSend = findViewById<Button>(R.id.btn_send)
        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val tvBackToLogin = findViewById<TextView>(R.id.tv_back_to_login)
        val progressOverlay = findViewById<android.view.View>(R.id.forgot_progress_overlay)

        // Auth screens are always ONLINE theme (Purple)
        applyModeTheme(
            primaryButtons = listOf(btnSend),
            accentIcons = listOf(btnBack)
        )

        btnBack.setOnClickListener {
            finish()
        }

        tvBackToLogin.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://luminapoll.firebaseapp.com/reset-password")
                .setHandleCodeInApp(true)
                .setAndroidPackageName("company.luminapoll", true, "29")
                .build()

            progressOverlay.visibility = android.view.View.VISIBLE
            auth.sendPasswordResetEmail(email, actionCodeSettings)
                .addOnCompleteListener { task ->
                    progressOverlay.visibility = android.view.View.GONE
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset link sent! Please check your email.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}