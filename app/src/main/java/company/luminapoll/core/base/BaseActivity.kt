package company.luminapoll.core.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import company.luminapoll.R
import company.luminapoll.core.utils.KeyboardUtil
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar

/**
 * Base activity class to handle common logic across all activities.
 * Follows DRY principles for edge-to-edge support, keyboard handling, and mode-based theming.
 */
abstract class BaseActivity : AppCompatActivity() {

    enum class MessageType { SUCCESS, WARNING, ERROR }
    enum class ErrorType { VALIDATION, NETWORK, SERVER, PERMISSION, NONE }
    enum class MessageSeverity { INLINE, TOAST, MODAL }

    data class AppMessage(
        val text: String,
        val type: MessageType = MessageType.ERROR,
        val errorType: ErrorType = ErrorType.NONE,
        val severity: MessageSeverity = MessageSeverity.TOAST
    )

    protected var mode: String = "LOCAL"
    protected var role: String = "JOINER"

    override fun onCreate(savedInstanceState: Bundle?) {
        // ... (rest of onCreate remains same)
        mode = intent.getStringExtra("EXTRA_MODE") ?: "LOCAL"
        role = intent.getStringExtra("EXTRA_ROLE") ?: "JOINER"

        // Apply theme based on mode and role
        val themeRes = when {
            mode == "ONLINE" && role == "HOST" -> R.style.Theme_LuminaPoll_OnlineHost
            mode == "ONLINE" && role == "JOINER" -> R.style.Theme_LuminaPoll_OnlineJoin
            mode == "LOCAL" && role == "HOST" -> R.style.Theme_LuminaPoll_LocalHost
            mode == "LOCAL" && role == "JOINER" -> R.style.Theme_LuminaPoll_LocalJoin
            // Fallback for dashboards if role isn't explicitly set yet
            intent.getBooleanExtra("IS_DASHBOARD", false) && mode == "ONLINE" -> R.style.Theme_LuminaPoll_OnlineHost
            intent.getBooleanExtra("IS_DASHBOARD", false) && mode == "LOCAL" -> R.style.Theme_LuminaPoll_LocalHost
            else -> R.style.Theme_LuminaPoll_LocalJoin
        }
        setTheme(themeRes)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setupKeyboard()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        setupKeyboard()
    }

    private fun setupKeyboard() {
        findViewById<View>(android.R.id.content)?.let {
            KeyboardUtil.setupKeyboardHandling(it)
        }
    }

    /**
     * Helper to apply system bar insets to a specific view.
     * This avoids double padding on API 35+ where edge-to-edge is forced.
     */
    protected fun consumeSystemBars(view: View) {
        val initialPaddingLeft = view.paddingLeft
        val initialPaddingTop = view.paddingTop
        val initialPaddingRight = view.paddingRight
        val initialPaddingBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )
            insets
        }
    }

    /**
     * Applies UI theme based on Role and Mode according to design specs.
     * Most of this is now handled by XML themes, but this helper remains for
     * specific view updates that can't be easily themed (like background tints).
     */
    protected fun applyModeTheme(
        rootLayout: View? = null,
        primaryButtons: List<Button>? = null,
        secondaryButtons: List<Button>? = null,
        accentTexts: List<TextView>? = null,
        accentIcons: List<ImageView>? = null,
        accentButtons: List<Button>? = null
    ) {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(R.attr.colorModePrimary, typedValue, true)
        val primaryColorVal = typedValue.data

        primaryButtons?.forEach { button ->
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColorVal)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        secondaryButtons?.forEach { button ->
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            button.setTextColor(primaryColorVal)
        }

        accentTexts?.forEach { textView ->
            textView.setTextColor(primaryColorVal)
        }

        accentIcons?.forEach { imageView ->
            imageView.imageTintList = android.content.res.ColorStateList.valueOf(primaryColorVal)
        }

        accentButtons?.forEach { button ->
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColorVal)
        }
    }

    /**
     * Centralized message display logic.
     */
    protected fun showAppMessage(message: AppMessage, targetInlineView: TextView? = null, onConfirm: (() -> Unit)? = null) {
        when (message.severity) {
            MessageSeverity.INLINE -> setInlineMessage(targetInlineView, message)
            MessageSeverity.TOAST -> showToastMessage(message)
            MessageSeverity.MODAL -> showModalMessage(message, onConfirm)
        }
    }

    private fun setInlineMessage(targetView: TextView?, message: AppMessage) {
        targetView?.apply {
            text = message.text
            visibility = View.VISIBLE
            setTextColor(getMessageColor(message.type))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private fun showToastMessage(message: AppMessage) {
        val rootView = findViewById<View>(android.R.id.content)
        val snackbar = Snackbar.make(rootView, message.text, Snackbar.LENGTH_LONG)
        
        // Use bright colors for Snackbar background, but ensure text is readable
        val bgColor = when (message.type) {
            MessageType.SUCCESS -> ContextCompat.getColor(this, R.color.result_green)
            MessageType.WARNING -> ContextCompat.getColor(this, R.color.result_yellow)
            MessageType.ERROR -> ContextCompat.getColor(this, R.color.result_red)
        }
        snackbar.setBackgroundTint(bgColor)
        
        // Dark text for yellow warning snackbar, white for others
        val textColor = if (message.type == MessageType.WARNING) 
            ContextCompat.getColor(this, R.color.black) 
        else 
            ContextCompat.getColor(this, R.color.white)
            
        snackbar.setTextColor(textColor)
        snackbar.show()
    }

    private fun showModalMessage(message: AppMessage, onConfirm: (() -> Unit)? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_message, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val ivIcon = dialogView.findViewById<ImageView>(R.id.iv_dialog_icon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        val btnPositive = dialogView.findViewById<Button>(R.id.btn_dialog_positive)
        val btnNegative = dialogView.findViewById<Button>(R.id.btn_dialog_negative)

        tvTitle.text = when (message.type) {
            MessageType.SUCCESS -> "Success"
            MessageType.WARNING -> "Warning"
            MessageType.ERROR -> "Error: ${if (message.errorType != ErrorType.NONE) message.errorType.name else ""}"
        }
        tvMessage.text = message.text

        // Use high-contrast colors for title and icon circle
        val color = getMessageColor(message.type)
        
        // Swap icon based on type
        val iconRes = when (message.type) {
            MessageType.SUCCESS -> R.drawable.ic_status_success
            MessageType.WARNING -> R.drawable.ic_status_warning
            MessageType.ERROR -> R.drawable.ic_status_error
        }
        ivIcon.setImageResource(iconRes)
        ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        ivIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        
        tvTitle.setTextColor(color)

        btnPositive.text = if (onConfirm != null) "Confirm" else "OK"
        btnPositive.setOnClickListener {
            onConfirm?.invoke()
            dialog.dismiss()
        }

        if (onConfirm != null) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.setOnClickListener {
                dialog.dismiss()
            }
        }

        // Apply theme colors to buttons
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(R.attr.colorModePrimary, typedValue, true)
        btnPositive.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)

        dialog.show()
        
        // Force width to prevent "thin" appearance on some devices
        dialog.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(android.view.Gravity.CENTER)
        }
    }

    protected fun getMessageColor(type: MessageType): Int {
        val attr = when (type) {
            MessageType.SUCCESS -> R.attr.colorStatusSuccess
            MessageType.WARNING -> R.attr.colorStatusWarning
            MessageType.ERROR -> R.attr.colorStatusError
        }
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /**
     * Updates submit button state (enabled/disabled and visual feedback).
     */
    protected fun updateSubmitButtonState(button: Button, isValid: Boolean) {
        button.isEnabled = isValid
        button.alpha = if (isValid) 1.0f else 0.5f
    }
}
