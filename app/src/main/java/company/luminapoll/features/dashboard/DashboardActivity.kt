package company.luminapoll.features.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.features.auth.LoginActivity
import company.luminapoll.features.local.ScanPollsActivity
import company.luminapoll.features.poll.CreatePollActivity
import company.luminapoll.features.poll.EnterCodeActivity

import android.widget.Button
import android.view.View
import androidx.lifecycle.lifecycleScope
import company.luminapoll.LuminaPollApp
import company.luminapoll.features.poll.LivePollActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_activity_main)

        updateUiForMode()

        findViewById<CardView>(R.id.card_join_poll).setOnClickListener {
            if (mode == "LOCAL") {
                val intent = Intent(this, ScanPollsActivity::class.java).apply {
                    putExtra("EXTRA_MODE", mode)
                    putExtra("EXTRA_ROLE", "JOINER")
                }
                startActivity(intent)
            } else {
                if (FirebaseAuth.getInstance().currentUser == null) {
                    startActivity(Intent(this, LoginActivity::class.java))
                } else {
                    val intent = Intent(this, EnterCodeActivity::class.java).apply {
                        putExtra("EXTRA_MODE", "ONLINE")
                        putExtra("EXTRA_ROLE", "JOINER")
                    }
                    startActivity(intent)
                }
            }
        }

        findViewById<CardView>(R.id.card_host_poll).setOnClickListener {
            if (mode == "LOCAL") {
                val intent = Intent(this, CreatePollActivity::class.java).apply {
                    putExtra("EXTRA_MODE", mode)
                    putExtra("EXTRA_ROLE", "HOST")
                }
                startActivity(intent)
            } else {
                if (FirebaseAuth.getInstance().currentUser == null) {
                    startActivity(Intent(this, LoginActivity::class.java))
                } else {
                    val intent = Intent(this, CreatePollActivity::class.java).apply {
                        putExtra("EXTRA_MODE", mode)
                        putExtra("EXTRA_ROLE", "HOST")
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun updateUiForMode() {
        val rootLayout = findViewById<View>(R.id.root_layout)
        val titleView = findViewById<TextView>(R.id.local_dashboard_title)
        val subtitleView = findViewById<TextView>(R.id.local_dashboard_sub)
        val cardJoin = findViewById<CardView>(R.id.card_join_poll)
        val cardHost = findViewById<CardView>(R.id.card_host_poll)
        val iconJoin = findViewById<ImageView>(R.id.icon_join)
        val iconHost = findViewById<ImageView>(R.id.icon_host)

        applyModeTheme()
        rootLayout?.let { consumeSystemBars(it) }

        if (mode == "ONLINE") {
            cardJoin.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_d300))
            cardHost.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_v300))
            iconJoin.setImageResource(R.drawable.ic_people_online)
            iconHost.setImageResource(R.drawable.ic_add_circle_online)
            titleView.text = getString(R.string.online_dashboard_title)
            subtitleView.text = getString(R.string.online_dashboard_subtitle)
        } else {
            cardJoin.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_l200))
            cardHost.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_p300))
            iconJoin.setImageResource(R.drawable.ic_people_local)
            titleView.text = getString(R.string.local_dashboard_title)
            subtitleView.text = getString(R.string.local_dashboard_subtitle)
        }
    }
}
