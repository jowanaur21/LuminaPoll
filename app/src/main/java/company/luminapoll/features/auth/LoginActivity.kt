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

class LoginActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var progressOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        progressOverlay = findViewById(R.id.login_progress_overlay)

        val etEmail = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnGoogle = findViewById<View>(R.id.btn_google_login)
        val btnBack = findViewById<ImageView>(R.id.btn_back)

        // Auth screens are always ONLINE theme (Purple)
        applyModeTheme(
            primaryButtons = listOf(btnLogin),
            accentIcons = listOf(btnBack)
        )

        btnBack.setOnClickListener {
            finish()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressOverlay.visibility = View.VISIBLE
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressOverlay.visibility = View.GONE
                    if (task.isSuccessful) {
                        navigateToDashboard()
                    } else {
                        Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        }

        findViewById<View>(R.id.btn_google_login).setOnClickListener {
            signInWithGoogle()
        }

        findViewById<TextView>(R.id.tv_register).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
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
                    Toast.makeText(this@LoginActivity, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleGoogleSignInResult(result: androidx.credentials.GetCredentialResponse) {
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            val googleIdToken = credential.idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this@LoginActivity) { task ->
                    progressOverlay.visibility = View.GONE
                    if (task.isSuccessful) {
                        navigateToDashboard()
                    } else {
                        Toast.makeText(this@LoginActivity, "Firebase Auth failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            progressOverlay.visibility = View.GONE
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("EXTRA_MODE", "ONLINE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
