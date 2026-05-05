package company.luminapoll.features.poll

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity

class PollCodeActivity : BaseActivity() {

    private var pollCode: String = "1A2B"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_code)
        
        findViewById<View>(R.id.main)?.let { consumeSystemBars(it) }

        pollCode = intent.getStringExtra("EXTRA_POLL_CODE") ?: "1A2B"

        initViews()
        
        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(findViewById(R.id.btn_dashboard)),
            secondaryButtons = listOf(findViewById(R.id.btn_copy)),
            accentTexts = listOf(findViewById(R.id.tv_poll_code))
        )
    }

    private fun initViews() {
        val tvPollCode = findViewById<TextView>(R.id.tv_poll_code)
        tvPollCode.text = pollCode

        val btnDashboard = findViewById<Button>(R.id.btn_dashboard)
        val btnCopy = findViewById<Button>(R.id.btn_copy)
        val btnBack = findViewById<View>(R.id.btn_back)

        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(btnDashboard),
            secondaryButtons = listOf(btnCopy),
            accentTexts = listOf(tvPollCode),
            accentIcons = listOf(btnBack as android.widget.ImageView)
        )

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Poll Code", pollCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnDashboard.setOnClickListener {
            if (mode == "LOCAL") {
                // Host joins their own poll as a client to see live results
                val userName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Host"
                val deviceId = company.luminapoll.core.utils.DeviceIdProvider.getDeviceId(this)
                (application as LuminaPollApp).localClient.connect("127.0.0.1", userName, deviceId)
                val intent = Intent(this, LivePollActivity::class.java).apply {
                    putExtra("EXTRA_MODE", mode)
                    putExtra("EXTRA_ROLE", "HOST")
                    putExtra("EXTRA_POLL_CODE", pollCode)
                }
                startActivity(intent)
            } else {
                // Start observing the online poll as host
                (application as LuminaPollApp).onlinePollManager.startObserving(pollCode)
                val intent = Intent(this, LivePollActivity::class.java).apply {
                    putExtra("EXTRA_MODE", mode)
                    putExtra("EXTRA_ROLE", "HOST")
                    putExtra("EXTRA_POLL_CODE", pollCode)
                }
                startActivity(intent)
            }
            finish()
        }
        
        btnBack.setOnClickListener {
            finish()
        }
    }
}