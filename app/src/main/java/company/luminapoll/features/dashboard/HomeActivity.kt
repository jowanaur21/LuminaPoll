package company.luminapoll.features.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.features.auth.LoginActivity

class HomeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_activity_home)
        
        findViewById<android.view.View>(R.id.main)?.let { consumeSystemBars(it) }
        
        applyModeTheme(
            rootLayout = findViewById(R.id.main)
        )

        findViewById<CardView>(R.id.mode_local).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("EXTRA_MODE", "LOCAL")
                putExtra("IS_DASHBOARD", true)
            }
            startActivity(intent)
        }

        findViewById<CardView>(R.id.mode_online).setOnClickListener {
            if (FirebaseAuth.getInstance().currentUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                val intent = Intent(this, DashboardActivity::class.java).apply {
                    putExtra("EXTRA_MODE", "ONLINE")
                    putExtra("IS_DASHBOARD", true)
                }
                startActivity(intent)
            }
        }
    }
}