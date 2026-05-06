package company.luminapoll.features.poll

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.utils.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.text.Editable
import android.text.TextWatcher
import kotlinx.coroutines.delay

/**
 * TODO: Future Feature - Implement automatic poll joining via deep links and QR codes.
 * Potential formats:
 * - company.luminapoll://join/{pollCode}
 * - https://luminapoll.firebaseapp.com/join/{pollCode}
 */
class EnterCodeActivity : BaseActivity() {

    private lateinit var btnJoin: Button
    private lateinit var etPollCode: EditText
    private lateinit var tvInlineError: TextView
    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_enter_code)

        initViews()
        setupModeUI()
        observeLocalFlows()
    }

    private fun initViews() {
        btnJoin = findViewById(R.id.btn_join_poll)
        etPollCode = findViewById(R.id.et_poll_code)
        tvInlineError = findViewById(R.id.tv_inline_error)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnJoin.setOnClickListener {
            val code = etPollCode.text.toString().trim().uppercase()
            val expectedLength = if (mode == "LOCAL") 4 else 6
            
            if (code.length == expectedLength) {
                showAppMessage(AppMessage("Searching for poll...", MessageType.WARNING, severity = MessageSeverity.TOAST))
                if (mode == "LOCAL") {
                    joinLocalPoll(code)
                } else {
                    joinOnlinePoll(code)
                }
            } else {
                showAppMessage(AppMessage("Please enter a valid $expectedLength-character code", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
            }
        }

        // Initial button state
        updateSubmitButtonState(btnJoin, false)

        etPollCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val code = s.toString().trim()
                val expectedLength = if (mode == "LOCAL") 4 else 6
                updateSubmitButtonState(btnJoin, code.length == expectedLength)
                tvInlineError.visibility = View.GONE
                // Cancel any pending search if user starts typing again
                searchJob?.cancel()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
        btnJoin.isEnabled = false
        searchJob?.cancel()
        
        val userName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Android User"
        val deviceId = company.luminapoll.core.utils.DeviceIdProvider.getDeviceId(this)

        // Check if we are the host re-joining our own poll
        val activeLocalPoll = (application as LuminaPollApp).localServer.pollState.value
        if (activeLocalPoll != null && activeLocalPoll.code == code) {
            (application as LuminaPollApp).localClient.connect("127.0.0.1", userName, deviceId)
            return
        }

        (application as LuminaPollApp).nsdHelper.discoverServices { service ->
            if (service.serviceName == "LuminaPoll_$code") {
                val hostIp = service.host.hostAddress
                if (hostIp != null) {
                    runOnUiThread {
                        (application as LuminaPollApp).localClient.connect(hostIp, userName, deviceId)
                    }
                }
            }
        }

        val hostIpPrefix = NetworkUtils.getLocalIpAddress().substringBeforeLast(".")
        val lastByteHex = if (code.length >= 2) code.take(2) else ""
        val lastByte = try { Integer.parseInt(lastByteHex, 16) } catch (e: Exception) { -1 }
        
        searchJob = lifecycleScope.launch {
            if (lastByte != -1) {
                val hostIp = "$hostIpPrefix.$lastByte"
                (application as LuminaPollApp).localClient.connect(hostIp, userName, deviceId)
            }
            
            // Hotspot fallback: try the gateway IP if we haven't connected yet
            delay(1500)
            if ((application as LuminaPollApp).localClient.pollState.value == null) {
                NetworkUtils.getGatewayIpAddress()?.let { gatewayIp ->
                    if (gatewayIp != "127.0.0.1") {
                        (application as LuminaPollApp).localClient.connect(gatewayIp, userName, deviceId)
                    }
                }
            }

            // Wait to see if we connected (Extended to 5s total for slow radios/hotspots)
            delay(3500)

            if ((application as LuminaPollApp).localClient.pollState.value == null) {
                btnJoin.isEnabled = true
                showAppMessage(AppMessage("Poll not found. Please check the code or your connection.", MessageType.ERROR, ErrorType.NETWORK, MessageSeverity.INLINE), tvInlineError)
            }
        }
    }

    private fun joinOnlinePoll(code: String) {
        btnJoin.isEnabled = false
        
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        
        (application as LuminaPollApp).onlinePollManager.joinPoll(code, userId) { poll, error ->
            btnJoin.isEnabled = true
            if (poll != null) {
                showAppMessage(AppMessage("Joined poll successfully!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                if (poll.hostId == userId) {
                    navigateToHost(poll.code)
                } else {
                    navigateToVote()
                }
            } else {
                val errorMsg = if (error?.contains("not found", ignoreCase = true) == true) "Poll not found. Please check the code." else (error ?: "Unknown error")
                showAppMessage(AppMessage(errorMsg, MessageType.ERROR, ErrorType.NETWORK, MessageSeverity.INLINE), tvInlineError)
            }
        }
    }

    private fun observeLocalFlows() {
        if (mode == "LOCAL") {
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.errorFlow.collectLatest { error ->
                    // Cancel search job because network already gave us an answer
                    searchJob?.cancel()
                    btnJoin.isEnabled = true
                    
                    // Standardize join-related errors to INLINE for better UX
                    if (error.contains("refused", true) || error.contains("not found", true) || error.contains("ended", true)) {
                        showAppMessage(AppMessage("Poll not found. Please check the code.", MessageType.ERROR, ErrorType.NETWORK, MessageSeverity.INLINE), tvInlineError)
                    } else {
                        showAppMessage(AppMessage(error, MessageType.ERROR, ErrorType.NETWORK, MessageSeverity.MODAL))
                    }
                }
            }
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.pollState.collectLatest { poll ->
                    if (poll != null) {
                        // SUCCESS: Clear everything immediately
                        searchJob?.cancel()
                        tvInlineError.visibility = View.GONE
                        btnJoin.isEnabled = true
                        
                        (application as LuminaPollApp).nsdHelper.stopDiscovery()
                        showAppMessage(AppMessage("Connected to local poll!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                        
                        val currentDeviceId = company.luminapoll.core.utils.DeviceIdProvider.getDeviceId(this@EnterCodeActivity)
                        if (poll.hostId == currentDeviceId) {
                            navigateToHost(poll.code)
                        } else {
                            navigateToVote()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToHost(code: String) {
        val intent = Intent(this, LivePollActivity::class.java).apply {
            putExtra("EXTRA_MODE", mode)
            putExtra("EXTRA_ROLE", "HOST")
            putExtra("EXTRA_POLL_CODE", code)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToVote() {
        val intent = Intent(this, VoteActivity::class.java).apply {
            putExtra("EXTRA_MODE", mode)
            putExtra("EXTRA_ROLE", role)
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