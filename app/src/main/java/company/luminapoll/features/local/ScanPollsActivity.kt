package company.luminapoll.features.local

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.network.Poll
import company.luminapoll.core.network.PollStatus
import company.luminapoll.features.poll.EnterCodeActivity
import company.luminapoll.features.poll.VoteActivity
import kotlinx.coroutines.launch

class ScanPollsActivity : BaseActivity() {

    private lateinit var rvPolls: RecyclerView
    private lateinit var radarIcon: ImageView
    private val discoveredPolls = mutableListOf<Poll>()
    private lateinit var adapter: ScannedPollsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.local_activity_scan)

        initViews()
        startRadarAnimation()
        startDiscovery()
    }

    private fun initViews() {
        rvPolls = findViewById(R.id.rv_polls)
        radarIcon = findViewById(R.id.radar_icon)
        
        adapter = ScannedPollsAdapter(discoveredPolls) { poll ->
            joinPoll(poll)
        }
        
        rvPolls.layoutManager = LinearLayoutManager(this)
        rvPolls.adapter = adapter

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
            })
        }
    }

    private fun startRadarAnimation() {
        val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
        }
        radarIcon.startAnimation(rotate)
    }

    private fun startDiscovery() {
        (application as LuminaPollApp).nsdHelper.discoverServices { service ->
            lifecycleScope.launch {
                val hostIp = service.host.hostAddress
                if (hostIp != null) {
                    val poll = (application as LuminaPollApp).localClient.fetchPollDetails(hostIp)
                    if (poll != null && discoveredPolls.none { it.id == poll.id }) {
                        runOnUiThread {
                            discoveredPolls.add(poll)
                            adapter.notifyDataSetChanged()
                            radarIcon.clearAnimation()
                            radarIcon.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun joinPoll(poll: Poll) {
        // If this is the host clicking their own poll
        val currentDeviceId = company.luminapoll.core.utils.DeviceIdProvider.getDeviceId(this)
        if (poll.hostId == currentDeviceId) {
            val intent = Intent(this, company.luminapoll.features.poll.LivePollActivity::class.java).apply {
                putExtra("EXTRA_MODE", mode)
                putExtra("EXTRA_ROLE", "HOST")
            }
            startActivity(intent)
            finish()
            return
        }

        val userName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Android User"
        (application as LuminaPollApp).localClient.connect(poll.hostIp, userName, currentDeviceId)
        val intent = Intent(this, VoteActivity::class.java).apply {
            putExtra("EXTRA_MODE", mode)
            putExtra("EXTRA_ROLE", role)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        (application as LuminaPollApp).nsdHelper.stopDiscovery()
    }
}

class ScannedPollsAdapter(
    private val polls: List<Poll>,
    private val onJoinClick: (Poll) -> Unit
) : RecyclerView.Adapter<ScannedPollsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_poll_name)
        val tvHost: TextView = view.findViewById(R.id.tv_host_name)
        val tvParticipants: TextView = view.findViewById(R.id.tv_participant_count)
        val tvStatus: TextView = view.findViewById(R.id.tv_status_badge)
        val btnJoin: Button = view.findViewById(R.id.btn_join)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.local_item_scanned_poll, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val poll = polls[position]
        holder.tvName.text = poll.title // Show title instead of question
        holder.tvHost.text = poll.hostName
        holder.tvParticipants.text = "${poll.participantCount}/${poll.maxParticipants} Participants"
        
        holder.tvStatus.text = when(poll.status) {
            PollStatus.ACTIVE -> "On going"
            PollStatus.FULL -> "Full"
            PollStatus.ENDED -> "Finished"
        }
        
        val badgeColor = when(poll.status) {
            PollStatus.ACTIVE -> 0xFF4CAF50.toInt()
            PollStatus.FULL -> 0xFFFF5252.toInt()
            PollStatus.ENDED -> 0xFF9E9E9E.toInt()
        }
        holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(badgeColor)
        
        holder.btnJoin.setOnClickListener { onJoinClick(poll) }
        holder.btnJoin.isEnabled = poll.status == PollStatus.ACTIVE
    }

    override fun getItemCount() = polls.size
}
