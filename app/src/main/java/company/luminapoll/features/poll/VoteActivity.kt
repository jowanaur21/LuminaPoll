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
    private var currentPoll: Poll? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_vote)

        initViews()
        
        applyModeTheme(
            rootLayout = findViewById(R.id.main),
            primaryButtons = listOf(btnSubmit),
            accentIcons = listOf(findViewById(R.id.btn_back))
        )
        
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
                        showSnackbar("Vote submitted successfully!")
                        finish()
                    } else {
                        showSnackbar("Failed to submit vote")
                    }
                }
            }
        }
    }

    private fun initViews() {
        llOptionsContainer = findViewById(R.id.ll_options_container)
        tvQuestion = findViewById(R.id.tv_question)
        btnSubmit = findViewById(R.id.btn_submit_vote)
        
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
                showSnackbar("Please select an option")
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
            showSnackbar("Unable to identify user")
            return
        }

        if (currentPoll?.votedUserIds?.contains(voterId) == true) {
            showSnackbar("You have already voted in this poll")
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
                    showSnackbar("Vote submitted successfully!")
                    finish()
                } else {
                    showSnackbar("Failed to submit vote")
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

        // Prevent host from voting, redirect to results
        val currentUserId = if (mode == "LOCAL") {
            DeviceIdProvider.getDeviceId(this)
        } else {
            FirebaseAuth.getInstance().currentUser?.uid ?: ""
        }

        if (poll.hostId == currentUserId) {
            val intent = Intent(this, LivePollActivity::class.java).apply {
                putExtra("EXTRA_MODE", mode)
                putExtra("EXTRA_ROLE", role)
                putExtra("EXTRA_POLL_CODE", poll.code)
            }
            startActivity(intent)
            finish()
            return
        }

        updatePollUI(poll)
    }

    private fun observeErrors() {
        if (mode == "LOCAL") {
            lifecycleScope.launch {
                (application as LuminaPollApp).localClient.errorFlow.collectLatest { error ->
                    progressBar.visibility = View.GONE
                    showSnackbar(error)
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
        val tintColor = if (mode == "ONLINE") R.color.login_btn else R.color.card_blue
        
        llOptionsContainer.children.forEachIndexed { i, view ->
            val indicator = view.findViewById<View>(R.id.v_radio_indicator)
            if (i == index) {
                indicator.alpha = 1.0f
                indicator.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, tintColor)
                view.setBackgroundResource(R.drawable.bg_google_btn)
                view.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, tintColor)
            } else {
                indicator.alpha = 0.2f
                indicator.backgroundTintList = null
                view.setBackgroundResource(R.drawable.bg_edittext)
                view.backgroundTintList = null
            }
        }
    }
}
