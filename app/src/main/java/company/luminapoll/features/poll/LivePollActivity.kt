package company.luminapoll.features.poll

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LivePollActivity : BaseActivity() {

    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvParticipantCount: TextView
    private lateinit var tvTimer: TextView
    private var timerJob: Job? = null
    private var initialLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_poll)

        initViews()
        
        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(findViewById(R.id.btn_back_dashboard)),
            accentIcons = listOf(findViewById(R.id.btn_back))
        )
        
        observePollUpdates()
    }

    private fun initViews() {
        llResultsContainer = findViewById(R.id.ll_results_container)
        tvParticipantCount = findViewById(R.id.tv_participant_count)
        tvTimer = findViewById(R.id.tv_timer)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_back_dashboard).setOnClickListener {
            finish()
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
                    updateUI(it)
                    startTimer(it.endTimeMillis, it.status)
                }
            }
        }
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
        
        val totalVotes = poll.options.sumOf { it.votes }

        if (llResultsContainer.childCount != poll.options.size) {
            llResultsContainer.removeAllViews()
            poll.options.forEach { option ->
                val resultView = LayoutInflater.from(this).inflate(R.layout.item_live_result, llResultsContainer, false)
                updateResultItem(resultView, option, totalVotes)
                llResultsContainer.addView(resultView)
            }
        } else {
            poll.options.forEachIndexed { index, option ->
                val resultView = llResultsContainer.getChildAt(index)
                updateResultItem(resultView, option, totalVotes)
            }
        }
    }

    private fun updateResultItem(view: View, option: company.luminapoll.core.network.PollOption, totalVotes: Int) {
        val tvName = view.findViewById<TextView>(R.id.tv_option_name)
        val progressBar = view.findViewById<ProgressBar>(R.id.pb_votes)
        val tvStats = view.findViewById<TextView>(R.id.tv_vote_stats)

        tvName.text = option.text
        
        val percentage = if (totalVotes > 0) (option.votes.toFloat() / totalVotes * 100).toInt() else 0
        progressBar.progress = percentage
        
        val tintColor = if (mode == "ONLINE") R.color.login_btn else R.color.card_blue
        progressBar.progressTintList = androidx.core.content.ContextCompat.getColorStateList(this, tintColor)
        
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