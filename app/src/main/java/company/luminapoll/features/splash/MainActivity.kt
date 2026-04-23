package company.luminapoll.features.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.features.dashboard.HomeActivity

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startView: View = findViewById(R.id.start_view)
        startView.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
        }
    }
}
