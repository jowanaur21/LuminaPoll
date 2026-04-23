package company.luminapoll.core.base

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import company.luminapoll.R
import company.luminapoll.core.utils.KeyboardUtil

/**
 * Base activity class to handle common logic across all activities.
 * Follows DRY principles for edge-to-edge support, keyboard handling, and mode-based theming.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected var mode: String = "LOCAL"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Extract mode from intent if present
        mode = intent.getStringExtra("EXTRA_MODE") ?: "LOCAL"
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
     * Applies the theme based on the current mode.
     * LOCAL: Blue theme
     * ONLINE: Purple theme
     */
    protected fun applyModeTheme(
        rootLayout: View? = null,
        primaryButtons: List<Button>? = null,
        secondaryButtons: List<Button>? = null,
        accentTexts: List<TextView>? = null,
        accentIcons: List<ImageView>? = null,
        accentButtons: List<Button>? = null
    ) {
        val (bgColor, primaryColor, secondaryColor) = if (mode == "ONLINE") {
            Triple(R.color.login_bg, R.color.card_purple, R.color.white)
        } else {
            Triple(R.color.local_dashboard_bg, R.color.card_blue, R.color.white)
        }

        // Only set background if provided and it's not a FrameLayout with a custom background view
        rootLayout?.let {
            if (it.background == null || mode == "ONLINE") {
                 it.setBackgroundColor(ContextCompat.getColor(this, bgColor))
            }
        }
        
        primaryButtons?.forEach { button ->
            button.backgroundTintList = ContextCompat.getColorStateList(this, primaryColor)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        secondaryButtons?.forEach { button ->
            button.backgroundTintList = ContextCompat.getColorStateList(this, secondaryColor)
            button.setTextColor(ContextCompat.getColor(this, primaryColor))
        }

        accentTexts?.forEach { textView ->
            textView.setTextColor(ContextCompat.getColor(this, primaryColor))
        }

        accentIcons?.forEach { imageView ->
            imageView.imageTintList = ContextCompat.getColorStateList(this, primaryColor)
        }

        accentButtons?.forEach { button ->
            button.backgroundTintList = ContextCompat.getColorStateList(this, primaryColor)
        }
    }

    protected fun showSnackbar(message: String) {
        val rootView = findViewById<View>(android.R.id.content)
        com.google.android.material.snackbar.Snackbar.make(rootView, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
    }
}
