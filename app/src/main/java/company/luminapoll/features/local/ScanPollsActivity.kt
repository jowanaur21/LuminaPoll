package company.luminapoll.features.local

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.network.Poll
import company.luminapoll.core.network.PollStatus
import company.luminapoll.features.poll.EnterCodeActivity
import company.luminapoll.features.poll.VoteActivity
import kotlinx.coroutines.launch

class ScanPollsActivity : BaseActivity() {

    private lateinit var llPollsContainer: LinearLayout
    private lateinit var radarIcon: ImageView
    private val discoveredPolls = mutableListOf<Poll>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.local_activity_scan)

        initViews()
        startRadarAnimation()
        startDiscovery()
    }

    private fun initViews() {
        llPollsContainer = findViewById(R.id.ll_polls_container)
        radarIcon = findViewById(R.id.radar_icon)
        
        val btnEnterCode = findViewById<Button>(R.id.btn_enter_code)
        val btnBack = findViewById<ImageView>(R.id.btn_back)

        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(btnEnterCode),
            accentIcons = listOf(btnBack)
        )

        btnBack.setOnClickListener {
            finish()
        }

        btnEnterCode.setOnClickListener {
            startActivity(Intent(this, EnterCodeActivity::class.java).apply {
                putExtra("EXTRA_MODE", mode)
                putExtra("EXTRA_ROLE", role)
            })
        }
        
        radarIcon.setOnClickListener {
            restartDiscovery()
        }
    }

    private fun restartDiscovery() {
        (application as LuminaPollApp).nsdHelper.stopDiscovery()
        discoveredPolls.clear()
        llPollsContainer.removeAllViews()
        startRadarAnimation()
        startDiscovery()
        showAppMessage(AppMessage("Refreshing poll list...", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
    }

    private fun startRadarAnimation() {
        val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
        }
        radarIcon.startAnimation(rotate)
        radarIcon.visibility = View.VISIBLE
    }

    private fun startDiscovery() {
        (application as LuminaPollApp).nsdHelper.discoverServices { service ->
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val hostIp = service.host?.hostAddress
                val port = service.port
                if (hostIp != null) {
                    val poll = (application as LuminaPollApp).localClient.fetchPollDetails(hostIp, port)
                    if (poll != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (discoveredPolls.none { it.id == poll.id }) {
                                discoveredPolls.add(poll)
                                addPollToLayout(poll)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addPollToLayout(poll: Poll) {
        val view = LayoutInflater.from(this).inflate(R.layout.local_item_scanned_poll, llPollsContainer, false)
        
        val tvName: TextView = view.findViewById(R.id.tv_poll_name)
        val tvHost: TextView = view.findViewById(R.id.tv_host_name)
        val tvParticipants: TextView = view.findViewById(R.id.tv_participant_count)
        val tvStatus: TextView = view.findViewById(R.id.tv_status_badge)
        val btnJoin: Button = view.findViewById(R.id.btn_join)
        val rootLayout: View = view.findViewById(R.id.cl_item_root)

        tvName.text = poll.title
        tvHost.text = poll.hostName
        tvParticipants.text = "${poll.participantCount}/${poll.maxParticipants} Participants"
        
        tvStatus.text = when(poll.status) {
            PollStatus.ACTIVE -> "On going"
            PollStatus.FULL -> "Full"
            PollStatus.ENDED -> "Finished"
        }
        
        val badgeColor = when(poll.status) {
            PollStatus.ACTIVE -> 0xFF4CAF50.toInt()
            PollStatus.FULL -> 0xFFFF5252.toInt()
            PollStatus.ENDED -> 0xFF9E9E9E.toInt()
        }
        tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(badgeColor)
        
        btnJoin.text = if (poll.status == PollStatus.ENDED) "View Results" else "Join"
        
        val onJoinClick = { joinPoll(poll) }
        btnJoin.setOnClickListener { onJoinClick() }
        rootLayout.setOnClickListener { onJoinClick() }

        llPollsContainer.addView(view)
    }

    private fun joinPoll(poll: Poll) {
        val currentDeviceId = company.luminapoll.core.utils.DeviceIdProvider.getDeviceId(this)
        val userName = "User"

        // Connect first
        (application as LuminaPollApp).localClient.connect(poll.hostIp, userName, currentDeviceId)

        if (poll.status == PollStatus.ENDED) {
            val intent = Intent(this, company.luminapoll.features.poll.PollResultActivity::class.java).apply {
                val pollJson = (application as LuminaPollApp).localServer.serializePoll(poll)
                putExtra("EXTRA_POLL_JSON", pollJson)
                putExtra("EXTRA_MODE", "LOCAL")
                putExtra("EXTRA_ROLE", if (poll.hostId == currentDeviceId) "HOST" else "JOINER")
            }
            startActivity(intent)
        } else if (poll.hostId == currentDeviceId) {
            val intent = Intent(this, company.luminapoll.features.poll.LivePollActivity::class.java).apply {
                putExtra("EXTRA_MODE", "LOCAL")
                putExtra("EXTRA_ROLE", "HOST")
                putExtra("EXTRA_POLL_CODE", poll.code)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, VoteActivity::class.java).apply {
                putExtra("EXTRA_MODE", "LOCAL")
                putExtra("EXTRA_ROLE", "JOINER")
            }
            startActivity(intent)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        (application as LuminaPollApp).nsdHelper.stopDiscovery()
    }
}