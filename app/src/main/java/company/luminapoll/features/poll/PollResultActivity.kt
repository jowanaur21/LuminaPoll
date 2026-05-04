package company.luminapoll.features.poll

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.network.Poll
import kotlinx.serialization.json.Json

class PollResultActivity : BaseActivity() {

    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvPollTitle: TextView
    private lateinit var tvTotalVotes: TextView
    private var poll: Poll? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_result)

        val pollJson = intent.getStringExtra("EXTRA_POLL_JSON")
        poll = pollJson?.let { Json.decodeFromString<Poll>(it) }

        initViews()
        
        findViewById<View>(R.id.main)?.let { consumeSystemBars(it) }
        
        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(findViewById(R.id.btn_share)),
            accentIcons = listOf(findViewById(R.id.btn_back))
        )

        displayResults()
    }

    private fun initViews() {
        llResultsContainer = findViewById(R.id.ll_results_container)
        tvPollTitle = findViewById(R.id.tv_poll_title)
        tvTotalVotes = findViewById(R.id.tv_total_votes)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_dashboard).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_share).setOnClickListener {
            // Placeholder for share logic
        }
    }

    private fun displayResults() {
        val currentPoll = poll ?: return
        tvPollTitle.text = currentPoll.title
        
        val totalVotes = currentPoll.options.sumOf { it.votes }
        tvTotalVotes.text = "Total Votes: $totalVotes"

        llResultsContainer.removeAllViews()
        currentPoll.options.sortedByDescending { it.votes }.forEach { option ->
            val resultView = LayoutInflater.from(this).inflate(R.layout.poll_item_live_result, llResultsContainer, false)
            updateResultItem(resultView, option, totalVotes)
            llResultsContainer.addView(resultView)
        }
    }

    private fun updateResultItem(view: View, option: company.luminapoll.core.network.PollOption, totalVotes: Int) {
        val tvName = view.findViewById<TextView>(R.id.tv_option_name)
        val progressBar = view.findViewById<ProgressBar>(R.id.pb_votes)
        val tvStats = view.findViewById<TextView>(R.id.tv_vote_stats)

        tvName.text = option.text
        
        val percentage = if (totalVotes > 0) (option.votes.toFloat() / totalVotes * 100).toInt() else 0
        progressBar.progress = percentage
        
        val tintColor = if (mode == "ONLINE") R.color.online_purple_light else R.color.local_blue_light
        progressBar.progressTintList = ContextCompat.getColorStateList(this, tintColor)
        
        tvStats.text = "$percentage% (${option.votes})"
    }
}
