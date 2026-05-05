package company.luminapoll.features.auth

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity

import android.text.Editable
import android.text.TextWatcher

class   ForgotPasswordActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var btnSend: Button
    private lateinit var tvInlineError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.et_email)
        btnSend = findViewById(R.id.btn_send)
        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val tvBackToLogin = findViewById<TextView>(R.id.tv_back_to_login)
        val progressOverlay = findViewById<View>(R.id.forgot_progress_overlay)
        tvInlineError = findViewById(R.id.tv_inline_error)

        applyModeTheme(
            rootLayout = findViewById(R.id.main),
            primaryButtons = listOf(btnSend),
            accentIcons = listOf(btnBack)
        )

        // Initial button state
        updateSubmitButtonState(btnSend, false)

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSubmitButtonState(btnSend, etEmail.text.toString().trim().isNotEmpty())
                tvInlineError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnBack.setOnClickListener {
            finish()
        }

        tvBackToLogin.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty()) {
                showAppMessage(AppMessage("Please enter your email", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
                return@setOnClickListener
            }

            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://luminapoll.firebaseapp.com/reset-password")
                .setHandleCodeInApp(true)
                .setAndroidPackageName("company.luminapoll", true, "29")
                .build()

            progressOverlay.visibility = View.VISIBLE
            auth.sendPasswordResetEmail(email, actionCodeSettings)
                .addOnCompleteListener { task ->
                    progressOverlay.visibility = View.GONE
                    if (task.isSuccessful) {
                        showAppMessage(AppMessage("Reset link sent! Please check your email.", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                        finish()
                    } else {
                        showAppMessage(AppMessage("Error: ${task.exception?.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                    }
                }
        }
    }
}