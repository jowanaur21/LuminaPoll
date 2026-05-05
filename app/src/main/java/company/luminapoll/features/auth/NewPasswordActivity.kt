package company.luminapoll.features.auth

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import androidx.core.net.toUri

class NewPasswordActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private var isCharsValid = false
    private var isNumSymValid = false
    private var isMatchValid = false

    private lateinit var strengthBars: List<View>
    private lateinit var tvStrengthLabel: TextView
    private lateinit var tvInlineError: TextView
    private lateinit var btnReset: Button
    private var oobCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_new_password)

        auth = FirebaseAuth.getInstance()
        handleIntent(intent)

        val etNewPassword = findViewById<EditText>(R.id.et_new_password)
        val etConfirmPassword = findViewById<EditText>(R.id.et_confirm_password)
        btnReset = findViewById(R.id.btn_reset)
        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val progressOverlay = findViewById<View>(R.id.new_password_progress_overlay)
        tvInlineError = findViewById(R.id.tv_inline_error)

        val ivCheckChars = findViewById<ImageView>(R.id.iv_check_chars)
        val ivCheckNumSym = findViewById<ImageView>(R.id.iv_check_num_sym)
        val ivCheckMatch = findViewById<ImageView>(R.id.iv_check_match)

        tvStrengthLabel = findViewById(R.id.tv_strength_label)
        strengthBars = listOf(
            findViewById(R.id.v_strength_1),
            findViewById(R.id.v_strength_2),
            findViewById(R.id.v_strength_3),
            findViewById(R.id.v_strength_4),
            findViewById(R.id.v_strength_5)
        )

        applyModeTheme(
            rootLayout = findViewById(R.id.main),
            primaryButtons = listOf(btnReset),
            accentIcons = listOf(btnBack)
        )

        // Initial button state
        updateSubmitButtonState(btnReset, false)

        btnBack.setOnClickListener {
            finish()
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val password = etNewPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()

                updateStrengthUI(calculateStrength(password))

                val charsValid = password.length >= 8
                if (charsValid != isCharsValid) {
                    isCharsValid = charsValid
                    updateCheckIcon(ivCheckChars, isCharsValid)
                }

                val numSymValid = password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }
                if (numSymValid != isNumSymValid) {
                    isNumSymValid = numSymValid
                    updateCheckIcon(ivCheckNumSym, numSymValid)
                }

                val matchValid = password.isNotEmpty() && password == confirmPassword
                if (matchValid != isMatchValid) {
                    isMatchValid = matchValid
                    updateCheckIcon(ivCheckMatch, isMatchValid)
                }
                
                updateSubmitButtonState(btnReset, isCharsValid && isNumSymValid && isMatchValid)
                tvInlineError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etNewPassword.addTextChangedListener(textWatcher)
        etConfirmPassword.addTextChangedListener(textWatcher)

        btnReset.setOnClickListener {
            if (isCharsValid && isNumSymValid && isMatchValid) {
                if (oobCode != null) {
                    progressOverlay.visibility = android.view.View.VISIBLE
                    auth.confirmPasswordReset(oobCode!!, etNewPassword.text.toString())
                        .addOnCompleteListener { task ->
                            progressOverlay.visibility = android.view.View.GONE
                            if (task.isSuccessful) {
                                showAppMessage(AppMessage("Password updated successfully!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            } else {
                                showAppMessage(AppMessage("Reset failed: ${task.exception?.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                            }
                        }
                } else {
                    showAppMessage(AppMessage("Invalid or missing reset code. Please try again.", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
                }
            } else {
                showAppMessage(AppMessage("Please meet all requirements", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
            }
        }

        updateStrengthUI(0)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.host == "luminapoll.firebaseapp.com") {
            oobCode = data.getQueryParameter("oobCode")
            if (oobCode == null) {
                val deepLink = data.getQueryParameter("link")
                if (deepLink != null) {
                    val deepUri = deepLink.toUri()
                    oobCode = deepUri.getQueryParameter("oobCode")
                }
            }

            if (oobCode != null) {
                auth.verifyPasswordResetCode(oobCode!!)
                    .addOnFailureListener { e ->
                        showAppMessage(AppMessage("Invalid or expired link: ${e.message}", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                        finish()
                    }
            }
        }
    }

    private fun calculateStrength(password: String): Int {
        if (password.isEmpty()) return 0
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score
    }

    private fun updateStrengthUI(strength: Int) {
        val label: String
        val colorRes: Int
        
        when (strength) {
            1 -> { label = "Very Weak"; colorRes = R.color.strength_very_weak }
            2 -> { label = "Weak"; colorRes = R.color.strength_weak }
            3 -> { label = "Fair"; colorRes = R.color.strength_fair }
            4 -> { label = "Good"; colorRes = R.color.strength_good }
            5 -> { label = "Strong"; colorRes = R.color.strength_strong }
            else -> { label = ""; colorRes = R.color.strength_default }
        }

        tvStrengthLabel.text = label
        if (strength > 0) {
            tvStrengthLabel.setTextColor(ContextCompat.getColor(this, colorRes))
        } else {
            tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.header_text_secondary))
        }

        for (i in strengthBars.indices) {
            val color = if (i < strength) {
                ContextCompat.getColor(this, colorRes)
            } else {
                ContextCompat.getColor(this, R.color.strength_default)
            }
            strengthBars[i].backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun updateCheckIcon(imageView: ImageView, isValid: Boolean) {
        if (isValid) {
            imageView.setImageResource(R.drawable.avd_check)
            val drawable = imageView.drawable
            if (drawable is AnimatedVectorDrawable) {
                drawable.start()
            }
        } else {
            imageView.setImageResource(R.drawable.ic_check_path)
        }
    }
}