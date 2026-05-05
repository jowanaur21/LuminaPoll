package company.luminapoll.features.poll

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.network.Poll
import company.luminapoll.core.network.PollStatus
import company.luminapoll.core.utils.DeviceIdProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LivePollActivity : BaseActivity() {

    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvQuestion: TextView
    private lateinit var tvParticipantCount: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnStopPoll: Button
    private var timerJob: Job? = null
    private var currentPoll: Poll? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_live)

        initViews()
        
        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(findViewById(R.id.btn_back_dashboard)),
            accentIcons = listOf(findViewById(R.id.btn_back))
        )
        
        // Ensure online observation starts if we have a code but no active poll
        if (mode == "ONLINE") {
            val app = application as LuminaPollApp
            val code = intent.getStringExtra("EXTRA_POLL_CODE")
            if (code != null && app.onlinePollManager.currentPoll.value == null) {
                app.onlinePollManager.startObserving(code)
            }
        }
        
        observePollUpdates()
    }

    private fun initViews() {
        llResultsContainer = findViewById(R.id.ll_results_container)
        tvQuestion = findViewById(R.id.tv_question)
        tvParticipantCount = findViewById(R.id.tv_participant_count)
        tvTimer = findViewById(R.id.tv_timer)
        btnStopPoll = findViewById(R.id.btn_stop_poll)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            // If host, just finish this activity to go back to dashboard.
            // The poll will keep running in the background.
            finish()
        }

        findViewById<Button>(R.id.btn_back_dashboard).setOnClickListener {
            finish()
        }

        btnStopPoll.setOnClickListener {
            showStopConfirmation()
        }
    }

    private fun showStopConfirmation() {
        showAppMessage(
            AppMessage(
                "Are you sure you want to end this poll now? No more votes will be accepted.",
                MessageType.WARNING,
                severity = MessageSeverity.MODAL
            ),
            onConfirm = { stopPollEarly() }
        )
    }

    private fun stopPollEarly() {
        val app = application as LuminaPollApp
        if (mode == "LOCAL") {
            app.localServer.stop()
        } else {
            currentPoll?.let {
                lifecycleScope.launch {
                    app.onlinePollManager.stopPollEarly(it.code)
                }
            }
        }
    }

    private fun observePollUpdates() {
        val flow = if (mode == "LOCAL") {
            (application as LuminaPollApp).localClient.pollState
        } else {
            (application as LuminaPollApp).onlinePollManager.currentPoll
        }

        lifecycleScope.launch {
            flow.collectLatest { poll ->
                poll?.let { 
                    currentPoll = it
                    if (it.status == PollStatus.ENDED) {
                        navigateToResults(it)
                        return@collectLatest
                    }
                    tvQuestion.text = it.question
                    updateUI(it)
                    startTimer(it.endTimeMillis, it.status)
                }
            }
        }
    }

    private fun navigateToResults(poll: Poll) {
        val intent = Intent(this, PollResultActivity::class.java).apply {
            val pollJson = (application as LuminaPollApp).localServer.serializePoll(poll)
            putExtra("EXTRA_POLL_JSON", pollJson)
            putExtra("EXTRA_MODE", mode)
            putExtra("EXTRA_ROLE", role)
        }
        startActivity(intent)
        finish()
    }

    private fun startTimer(endTimeMillis: Long, status: PollStatus) {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                if (status == PollStatus.ENDED) {
                    tvTimer.text = "Poll has ended"
                    break
                }
                
                val remaining = endTimeMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    tvTimer.text = "Poll has ended"
                    break
                }
                
                val minutes = (remaining / 1000) / 60
                val seconds = (remaining / 1000) % 60
                tvTimer.text = String.format("Poll ends in %02d:%02d", (minutes % 60), seconds)
                delay(1000)
            }
        }
    }

    private fun updateUI(poll: Poll) {
        tvParticipantCount.text = poll.participantCount.toString()
        
        // Host check for Stop button
        val currentUserId = if (mode == "LOCAL") {
            DeviceIdProvider.getDeviceId(this)
        } else {
            FirebaseAuth.getInstance().currentUser?.uid ?: ""
        }

        if (poll.hostId == currentUserId && poll.status == PollStatus.ACTIVE) {
            btnStopPoll.visibility = View.VISIBLE
        } else {
            btnStopPoll.visibility = View.GONE
        }
        
        val totalVotes = poll.options.sumOf { it.votes }

        val resultColors = listOf(
            R.color.result_green,
            R.color.result_blue,
            R.color.result_yellow,
            R.color.result_red
        )

        if (llResultsContainer.childCount != poll.options.size) {
            llResultsContainer.removeAllViews()
            poll.options.forEachIndexed { index, option ->
                val resultView = LayoutInflater.from(this).inflate(R.layout.poll_item_live_result, llResultsContainer, false)
                updateResultItem(resultView, option, totalVotes, resultColors[index % resultColors.size])
                llResultsContainer.addView(resultView)
            }
        } else {
            poll.options.forEachIndexed { index, option ->
                val resultView = llResultsContainer.getChildAt(index)
                updateResultItem(resultView, option, totalVotes, resultColors[index % resultColors.size])
            }
        }
    }

    private fun updateResultItem(view: View, option: company.luminapoll.core.network.PollOption, totalVotes: Int, colorRes: Int) {
        val tvName = view.findViewById<TextView>(R.id.tv_option_name)
        val progressBar = view.findViewById<ProgressBar>(R.id.pb_votes)
        val tvStats = view.findViewById<TextView>(R.id.tv_vote_stats)

        tvName.text = option.text
        
        val percentage = if (totalVotes > 0) (option.votes.toFloat() / totalVotes * 100).toInt() else 0
        progressBar.progress = percentage
        
        progressBar.progressTintList = androidx.core.content.ContextCompat.getColorStateList(this, colorRes)
        
        tvStats.text = "$percentage% (${option.votes})"
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        if (mode == "ONLINE") {
            (application as LuminaPollApp).onlinePollManager.leavePoll()
        }
    }
}