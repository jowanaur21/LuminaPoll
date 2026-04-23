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
        setContentView(R.layout.activity_dashboard)

        val btnViewActive = findViewById<Button>(R.id.btn_view_active)
        updateUiForMode()

        findViewById<ImageView>(R.id.btn_back2).setOnClickListener {
            finish()
        }

        findViewById<CardView>(R.id.card_join_poll).setOnClickListener {
            if (mode == "LOCAL") {
                startActivity(Intent(this, ScanPollsActivity::class.java))
            } else {
                if (FirebaseAuth.getInstance().currentUser == null) {
                    startActivity(Intent(this, LoginActivity::class.java))
                } else {
                    Toast.makeText(this, "Online Join coming soon...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<CardView>(R.id.card_host_poll).setOnClickListener {
            val intent = Intent(this, CreatePollActivity::class.java).apply {
                putExtra("EXTRA_MODE", mode)
            }
            startActivity(intent)
        }

        btnViewActive.setOnClickListener {
            val intent = Intent(this, LivePollActivity::class.java).apply {
                putExtra("EXTRA_MODE", mode)
            }
            startActivity(intent)
        }

        observeActivePolls(btnViewActive)
    }

    private fun observeActivePolls(btnViewActive: Button) {
        val app = application as LuminaPollApp
        if (mode == "LOCAL") {
            lifecycleScope.launch {
                app.localServer.pollState.collectLatest { poll ->
                    btnViewActive.visibility = if (poll != null) View.VISIBLE else View.GONE
                }
            }
        } else {
            lifecycleScope.launch {
                app.onlinePollManager.currentPoll.collectLatest { poll ->
                    btnViewActive.visibility = if (poll != null) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun updateUiForMode() {
        val rootLayout = findViewById<android.view.View>(R.id.root_layout)
        val titleView = findViewById<TextView>(R.id.local_dashboard_title)
        val subtitleView = findViewById<TextView>(R.id.local_dashboard_sub)
        val cardJoin = findViewById<CardView>(R.id.card_join_poll)
        val cardHost = findViewById<CardView>(R.id.card_host_poll)
        val iconJoin = findViewById<ImageView>(R.id.icon_join)
        val iconHost = findViewById<ImageView>(R.id.icon_host)
        val btnViewActive = findViewById<Button>(R.id.btn_view_active)

        applyModeTheme(
            rootLayout = rootLayout,
            primaryButtons = listOf(btnViewActive)
        )

        if (mode == "ONLINE") {
            cardJoin.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_purple))
            cardHost.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_purple))
            iconJoin.setImageResource(R.drawable.ic_people_online)
            iconHost.setImageResource(R.drawable.ic_add_circle_online)
            titleView.text = getString(R.string.online_dashboard_title)
            subtitleView.text = getString(R.string.online_dashboard_subtitle)
        } else {
            cardJoin.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_blue))
            cardHost.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_blue))
            iconJoin.setImageResource(R.drawable.ic_people_local)
            iconHost.setImageResource(R.drawable.ic_add_circle_local)
            titleView.text = getString(R.string.local_dashboard_title)
            subtitleView.text = getString(R.string.local_dashboard_subtitle)
        }
    }
}
