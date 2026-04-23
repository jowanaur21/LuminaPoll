package company.luminapoll.features.poll

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.utils.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EnterCodeActivity : BaseActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var btnJoin: Button
    private lateinit var etPollCode: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter_code)

        initViews()
        setupModeUI()
        observeLocalFlows()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.pb_joining)
        btnJoin = findViewById(R.id.btn_join_poll)
        etPollCode = findViewById(R.id.et_poll_code)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnJoin.setOnClickListener {
            val code = etPollCode.text.toString().trim().uppercase()
            val expectedLength = if (mode == "LOCAL") 4 else 6
            
            if (code.length == expectedLength) {
                if (mode == "LOCAL") {
                    joinLocalPoll(code)
                } else {
                    joinOnlinePoll(code)
                }
            } else {
                showSnackbar("Please enter a valid $expectedLength-character code")
            }
        }
    }

    private fun setupModeUI() {
        val btnBack = findViewById<ImageView>(R.id.btn_back)
        if (mode == "ONLINE") {
            findViewById<TextView>(R.id.tv_enter_desc).text = "Enter the 6-character code\nshared by the host"
            etPollCode.hint = "Enter code (e.g. 1A2B3C)"
            etPollCode.filters = arrayOf(InputFilter.LengthFilter(6))
        } else {
            etPollCode.filters = arrayOf(InputFilter.LengthFilter(4))
        }

        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(btnJoin),
            accentIcons = listOf(btnBack)
        )
    }

    private fun joinLocalPoll(code: String) {
        progressBar.visibility = View.VISIBLE
        btnJoin.isEnabled = false
        
        val userName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Android User"

        (application as LuminaPollApp).nsdHelper.discoverServices { service ->
            if (service.serviceName == "LuminaPoll_$code") {
                val hostIp = service.host.hostAddress
                if (hostIp != null) {
                    runOnUiThread {
                        (application as LuminaPollApp).localClient.connect(hostIp, userName)
                    }
                }
            }
        }

        val hostIpPrefix = NetworkUtils.getLocalIpAddress().substringBeforeLast(".")
        val lastByteHex = code.take(2)
        val lastByte = try { Integer.parseInt(lastByteHex, 16) } catch (e: Exception) { -1 }
        
        if (lastByte != -1) {
            val hostIp = "$hostIpPrefix.$lastByte"
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                if ((application as LuminaPollApp).localClient.pollState.value == null) {
                    (application as LuminaPollApp).localClient.connect(hostIp, userName)
                }
            }
        }
    }

    private fun joinOnlinePoll(code: String) {
        progressBar.visibility = View.VISIBLE
        btnJoin.isEnabled = false
        
        (application as LuminaPollApp).onlinePollManager.joinPoll(code) { poll, error ->
            progressBar.visibility = View.GONE
            btnJoin.isEnabled = true
            if (poll != null) {
                navigateToVote()
            } else {
                showSnackbar(error ?: "Unknown error")
            }
        }
    }

    private fun observeLocalFlows() {
        if (mode == "LOCAL") {
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.errorFlow.collectLatest { error ->
                    progressBar.visibility = View.GONE
                    btnJoin.isEnabled = true
                    showSnackbar(error)
                }
            }
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.pollState.collectLatest { poll ->
                    if (poll != null) {
                        progressBar.visibility = View.GONE
                        (application as LuminaPollApp).nsdHelper.stopDiscovery()
                        navigateToVote()
                    }
                }
            }
        }
    }

    private fun navigateToVote() {
        val intent = Intent(this, VoteActivity::class.java).apply {
            putExtra("EXTRA_MODE", mode)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mode == "LOCAL") {
            (application as LuminaPollApp).nsdHelper.stopDiscovery()
        }
    }
}
