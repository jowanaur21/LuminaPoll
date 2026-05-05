package company.luminapoll.features.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.features.dashboard.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.text.Editable
import android.text.TextWatcher

class LoginActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var progressOverlay: View
    private lateinit var tvInlineError: TextView
    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_login)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        progressOverlay = findViewById(R.id.login_progress_overlay)
        tvInlineError = findViewById(R.id.tv_inline_error)

        val etEmail = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        val btnBack = findViewById<ImageView>(R.id.btn_back)

        applyModeTheme(
            rootLayout = findViewById(R.id.main),
            primaryButtons = listOf(btnLogin),
            accentIcons = listOf(btnBack)
        )

        // Initial button state
        updateSubmitButtonState(btnLogin, false)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()
                updateSubmitButtonState(btnLogin, email.isNotEmpty() && password.isNotEmpty())
                tvInlineError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etEmail.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)

        btnBack.setOnClickListener {
            finish()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showAppMessage(AppMessage("Please fill in all fields", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
                return@setOnClickListener
            }

            progressOverlay.visibility = View.VISIBLE
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressOverlay.visibility = View.GONE
                    if (task.isSuccessful) {
                        showAppMessage(AppMessage("Login successful!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                        navigateToDashboard()
                    } else {
                        showAppMessage(AppMessage("Authentication failed: ${task.exception?.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                    }
                }
        }

        findViewById<View>(R.id.btn_google_login).setOnClickListener {
            signInWithGoogle()
        }

        findViewById<TextView>(R.id.tv_register).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java).apply {
                putExtra("IS_DASHBOARD", true)
            })
        }
        
        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java).apply {
                putExtra("IS_DASHBOARD", true)
            })
        }
    }

    private fun signInWithGoogle() {
        progressOverlay.visibility = View.VISIBLE
        
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )
                handleGoogleSignInResult(result)
            } catch (e: Exception) {
                progressOverlay.visibility = View.GONE
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    showAppMessage(AppMessage("Google Sign-In failed: ${e.message}", MessageType.ERROR, ErrorType.NETWORK, MessageSeverity.TOAST))
                }
            }
        }
    }

    private fun handleGoogleSignInResult(result: androidx.credentials.GetCredentialResponse) {
        val credential = result.credential
        
        try {
            val googleIdTokenCredential = if (credential is GoogleIdTokenCredential) {
                credential
            } else {
                GoogleIdTokenCredential.createFrom(credential.data)
            }

            val googleIdToken = googleIdTokenCredential.idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this@LoginActivity) { task ->
                    progressOverlay.visibility = View.GONE
                    if (task.isSuccessful) {
                        showAppMessage(AppMessage("Google Login successful!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                        navigateToDashboard()
                    } else {
                        showAppMessage(AppMessage("Firebase Auth failed: ${task.exception?.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                    }
                }
        } catch (e: Exception) {
            progressOverlay.visibility = View.GONE
            showAppMessage(AppMessage("Google Sign-In error: ${e.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("EXTRA_MODE", "ONLINE")
            putExtra("IS_DASHBOARD", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
