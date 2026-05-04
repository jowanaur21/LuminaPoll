package company.luminapoll.core.base

import android.os.Bundle
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

/**
 * Base activity class to handle common logic across all activities.
 * Follows DRY principles for edge-to-edge support, keyboard handling, and mode-based theming.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected var mode: String = "LOCAL"
    protected var role: String = "JOINER"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Extract mode and role from intent if present
        mode = intent.getStringExtra("EXTRA_MODE") ?: "LOCAL"
        role = intent.getStringExtra("EXTRA_ROLE") ?: "JOINER"

        // Apply theme based on mode and role
        val themeRes = when {
            intent.getBooleanExtra("IS_DASHBOARD", false) -> R.style.Theme_LuminaPoll_OnlineHost
            mode == "ONLINE" && role == "HOST" -> R.style.Theme_LuminaPoll_OnlineHost
            mode == "ONLINE" && role == "JOINER" -> R.style.Theme_LuminaPoll_OnlineJoin
            mode == "LOCAL" && role == "HOST" -> R.style.Theme_LuminaPoll_LocalHost
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
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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
        // XML Theme handles background and primary colors via attributes.
        // This method now ensures specific components follow the theme precisely.
        
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

    protected fun showSnackbar(message: String) {
        val rootView = findViewById<View>(android.R.id.content)
        com.google.android.material.snackbar.Snackbar.make(rootView, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
    }
}
