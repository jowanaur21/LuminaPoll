package company.luminapoll.features.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.features.dashboard.DashboardActivity

import android.text.Editable
import android.text.TextWatcher

class RegisterActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var tvInlineError: TextView
    private lateinit var btnRegister: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_register)

        auth = FirebaseAuth.getInstance()

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val etConfirmPassword = findViewById<EditText>(R.id.et_confirm_password)
        val progressOverlay = findViewById<View>(R.id.register_progress_overlay)
        btnRegister = findViewById(R.id.btn_register)
        val btnBack = findViewById<ImageView>(R.id.btn_back)
        tvInlineError = findViewById(R.id.tv_inline_error)

        applyModeTheme(
            rootLayout = findViewById(R.id.main),
            primaryButtons = listOf(btnRegister),
            accentIcons = listOf(btnBack)
        )

        // Initial button state
        updateSubmitButtonState(btnRegister, false)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val username = etUsername.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()
                
                val isValid = username.isNotEmpty() && email.isNotEmpty() && 
                             password.length >= 6 && password == confirmPassword
                
                updateSubmitButtonState(btnRegister, isValid)
                tvInlineError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etUsername.addTextChangedListener(textWatcher)
        etEmail.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)
        etConfirmPassword.addTextChangedListener(textWatcher)

        btnBack.setOnClickListener {
            finish()
        }

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                showAppMessage(AppMessage("Please fill in all fields", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                showAppMessage(AppMessage("Passwords do not match", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
                return@setOnClickListener
            }

            if (password.length < 6) {
                showAppMessage(AppMessage("Password must be at least 6 characters", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
                return@setOnClickListener
            }

            progressOverlay.visibility = View.VISIBLE
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                            displayName = username
                        }
                        user?.updateProfile(profileUpdates)
                            ?.addOnCompleteListener { profileTask ->
                                progressOverlay.visibility = View.GONE
                                if (profileTask.isSuccessful) {
                                    showAppMessage(AppMessage("Registration successful!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                                    val intent = Intent(this, DashboardActivity::class.java).apply {
                                        putExtra("EXTRA_MODE", "ONLINE")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    startActivity(intent)
                                    finish()
                                } else {
                                    showAppMessage(AppMessage("Profile update failed: ${profileTask.exception?.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                                }
                            }
                    } else {
                        progressOverlay.visibility = View.GONE
                        showAppMessage(AppMessage("Registration failed: ${task.exception?.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                    }
                }
        }

        findViewById<TextView>(R.id.tv_login).setOnClickListener {
            finish()
        }
    }
}