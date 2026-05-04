package company.luminapoll.features.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.features.auth.NewPasswordActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(company.luminapoll.R.layout.splash_activity_splash)

        // Check for deep link before regular transition
        val data = intent.data
        if (data != null && data.host == "luminapoll.firebaseapp.com" && data.path?.contains("/__/auth") == true) {
            val nextIntent = Intent(this, NewPasswordActivity::class.java).apply {
                setData(data)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(nextIntent)
            finish()
            return
        }

        // Wait for splashDelay then transition to Main or Dashboard
        val splashDelay: Long = 2000
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val nextIntent = Intent(this, company.luminapoll.features.dashboard.DashboardActivity::class.java).apply {
                    putExtra("EXTRA_MODE", "ONLINE")
                    putExtra("IS_DASHBOARD", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(nextIntent)
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }, splashDelay)
    }
}