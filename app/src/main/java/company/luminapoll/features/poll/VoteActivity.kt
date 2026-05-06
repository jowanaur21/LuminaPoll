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
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.network.Poll
import company.luminapoll.core.utils.DeviceIdProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VoteActivity : BaseActivity() {

    private var selectedOptionIndex: Int = -1
    private lateinit var llOptionsContainer: LinearLayout
    private lateinit var tvQuestion: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSubmit: Button
    private lateinit var tvInlineError: TextView
    private var currentPoll: Poll? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_vote)

        initViews()
        
        applyModeTheme(
            rootLayout = findViewById(R.id.vote_scroll_view),
            primaryButtons = listOf(btnSubmit),
            accentIcons = listOf(findViewById(R.id.btn_back))
        )
        
        updateSubmitButtonState(btnSubmit, false)

        observePollUpdates()
        observeErrors()
        observeVoteSuccess()
    }

    private fun observeVoteSuccess() {
        if (mode == "LOCAL") {
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.voteSuccessFlow.collectLatest { success ->
                    btnSubmit.isEnabled = true
                    if (success) {
                        showAppMessage(AppMessage("Vote submitted successfully!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                        finish()
                    } else {
                        showAppMessage(AppMessage("Failed to submit vote", MessageType.ERROR, ErrorType.NETWORK, MessageSeverity.MODAL))
                    }
                }
            }
        }
    }

    private fun initViews() {
        llOptionsContainer = findViewById(R.id.ll_options_container)
        tvQuestion = findViewById(R.id.tv_question)
        btnSubmit = findViewById(R.id.btn_submit_vote)
        tvInlineError = findViewById(R.id.tv_inline_error)
        
        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
        }
        (findViewById<View>(R.id.root_layout) as LinearLayout).addView(progressBar, 4)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnSubmit.setOnClickListener {
            if (selectedOptionIndex != -1) {
                submitVote()
            } else {
                showAppMessage(AppMessage("Please select an option", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
            }
        }
    }

    private fun submitVote() {
        val voterId = if (mode == "LOCAL") {
            DeviceIdProvider.getDeviceId(this)
        } else {
            FirebaseAuth.getInstance().currentUser?.uid ?: ""
        }

        if (voterId.isEmpty()) {
            showAppMessage(AppMessage("Unable to identify user", MessageType.ERROR, ErrorType.PERMISSION, MessageSeverity.MODAL))
            return
        }

        if (currentPoll?.votedUserIds?.contains(voterId) == true) {
            showAppMessage(AppMessage("You have already voted in this poll", MessageType.WARNING, severity = MessageSeverity.MODAL))
            return
        }

        btnSubmit.isEnabled = false
        lifecycleScope.launch {
            if (mode == "LOCAL") {
                (application as LuminaPollApp).localClient.vote(selectedOptionIndex, voterId)
            } else {
                val success = (application as LuminaPollApp).onlinePollManager.vote(selectedOptionIndex, voterId)
                btnSubmit.isEnabled = true
                if (success) {
                    showAppMessage(AppMessage("Vote submitted successfully!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                    finish()
                } else {
                    showAppMessage(AppMessage("Failed to submit vote", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
                }
            }
        }
    }

    private fun observePollUpdates() {
        if (mode == "LOCAL") {
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.pollState.collectLatest { poll ->
                    poll?.let { handlePollUpdate(it) }
                }
            }
        } else {
            lifecycleScope.launch {
                (application as LuminaPollApp).onlinePollManager.currentPoll.collectLatest { poll ->
                    poll?.let { handlePollUpdate(it) }
                }
            }
        }
    }

    private fun handlePollUpdate(poll: Poll) {
        currentPoll = poll
        progressBar.visibility = View.GONE

        val currentUserId = if (mode == "LOCAL") {
            DeviceIdProvider.getDeviceId(this)
        } else {
            FirebaseAuth.getInstance().currentUser?.uid ?: ""
        }

        // 1. If host, go to LivePollActivity
        if (poll.hostId == currentUserId) {
            val intent = Intent(this, LivePollActivity::class.java).apply {
                putExtra("EXTRA_MODE", mode)
                putExtra("EXTRA_ROLE", "HOST")
                putExtra("EXTRA_POLL_CODE", poll.code)
            }
            startActivity(intent)
            finish()
            return
        }

        // 2. If already voted or poll ended, redirect
        if (poll.votedUserIds.contains(currentUserId) || poll.status == company.luminapoll.core.network.PollStatus.ENDED) {
            if (poll.status == company.luminapoll.core.network.PollStatus.ENDED) {
                val intent = Intent(this, PollResultActivity::class.java).apply {
                    val pollJson = (application as LuminaPollApp).localServer.serializePoll(poll)
                    putExtra("EXTRA_POLL_JSON", pollJson)
                    putExtra("EXTRA_MODE", mode)
                    putExtra("EXTRA_ROLE", "JOINER")
                }
                startActivity(intent)
                finish()
            } else {
                // Already voted, see live results
                val intent = Intent(this, LivePollActivity::class.java).apply {
                    putExtra("EXTRA_MODE", mode)
                    putExtra("EXTRA_ROLE", "VOTER")
                    putExtra("EXTRA_POLL_CODE", poll.code)
                }
                startActivity(intent)
                finish()
            }
            return
        }

        updatePollUI(poll)
    }

    private fun observeErrors() {
        if (mode == "LOCAL") {
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.errorFlow.collectLatest { error ->
                    progressBar.visibility = View.GONE
                    if (error.contains("ended", true)) {
                        // If poll ended while we were voting, we might get an error from WS
                        // handlePollUpdate will take care of redirection if state updates, 
                        // but if we are disconnected, we might need manual action.
                    }
                    showAppMessage(AppMessage(error, MessageType.ERROR, ErrorType.NETWORK, MessageSeverity.TOAST))
                }
            }
        }
    }

    private fun updatePollUI(poll: Poll) {
        tvQuestion.text = poll.question
        tvQuestion.visibility = View.VISIBLE
        
        if (llOptionsContainer.childCount != poll.options.size) {
            llOptionsContainer.removeAllViews()
            poll.options.forEachIndexed { index, option ->
                val optionView = LayoutInflater.from(this).inflate(R.layout.poll_item_vote_option, llOptionsContainer, false)
                val tvOptionText = optionView.findViewById<TextView>(R.id.tv_option_text)
                tvOptionText.text = option.text

                optionView.setOnClickListener {
                    selectOption(index)
                }

                llOptionsContainer.addView(optionView)
            }
        } else {
            poll.options.forEachIndexed { index, option ->
                val view = llOptionsContainer.getChildAt(index)
                val tvOptionText = view.findViewById<TextView>(R.id.tv_option_text)
                tvOptionText.text = option.text
            }
        }
        
        if (selectedOptionIndex != -1) {
            selectOption(selectedOptionIndex)
        }
    }

    private fun selectOption(index: Int) {
        selectedOptionIndex = index
        
        llOptionsContainer.children.forEachIndexed { i, view ->
            val container = view.findViewById<View>(R.id.v_radio_container)
            val check = view.findViewById<ImageView>(R.id.iv_radio_check)
            if (i == index) {
                check.visibility = View.VISIBLE
                // Make the checkmark green as requested
                check.imageTintList = androidx.core.content.ContextCompat.getColorStateList(this, R.color.result_green)
                // Keep the circular container background neutral/white
                container.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, R.color.white)
            } else {
                check.visibility = View.INVISIBLE
                container.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, R.color.result_bar_bg)
            }
            // Ensure the main option background remains unchanged (neutral)
            view.setBackgroundResource(R.drawable.bg_edittext)
            view.backgroundTintList = null
        }
        updateSubmitButtonState(btnSubmit, true)
        tvInlineError.visibility = View.GONE
    }
}
