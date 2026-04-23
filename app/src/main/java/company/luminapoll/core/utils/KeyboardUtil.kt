package company.luminapoll.core.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object KeyboardUtil {
    /**
     * Sets up the view to adjust its padding when the keyboard (IME) appears.
     * Also accounts for system navigation bars to prevent overlap.
     */
    fun setupKeyboardHandling(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // We only want to update the bottom padding to account for the keyboard
            // and the navigation bar. 
            // If the keyboard is hidden, imeInsets.bottom will be 0, 
            // and we'll just have the navigation bar height.
            v.updatePadding(
                bottom = imeInsets.bottom + systemBarsInsets.bottom
            )
            
            insets
        }
    }
}
