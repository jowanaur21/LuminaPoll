package company.luminapoll.features.poll

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.network.Poll
import company.luminapoll.core.network.PollForegroundService
import company.luminapoll.core.network.PollOption
import company.luminapoll.core.utils.DeviceIdProvider
import company.luminapoll.core.utils.NetworkUtils
import kotlinx.coroutines.launch
import java.util.UUID

class CreatePollActivity : BaseActivity() {

    private lateinit var llOptionsContainer: LinearLayout
    private lateinit var etPollName: EditText
    private lateinit var etQuestion: EditText
    private lateinit var etMaxParticipants: EditText
    private lateinit var etDuration: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_create)

        initViews()
        
        applyModeTheme(
            rootLayout = findViewById(R.id.root_layout),
            primaryButtons = listOf(findViewById(R.id.btn_create_poll)),
            accentTexts = listOf(findViewById(R.id.tv_add_option)),
            accentIcons = listOf(
                findViewById(R.id.btn_back),
                findViewById(R.id.btn_add_option_icon)
            )
        )
    }

    private fun initViews() {
        llOptionsContainer = findViewById(R.id.ll_options_container)
        etPollName = findViewById(R.id.et_poll_name)
        etQuestion = findViewById(R.id.et_question)
        etMaxParticipants = findViewById(R.id.et_max_participants)
        etDuration = findViewById(R.id.et_duration)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<android.view.View>(R.id.btn_add_option_container).setOnClickListener {
            addOption()
        }

        findViewById<Button>(R.id.btn_create_poll).setOnClickListener {
            validateAndCreatePoll()
        }
        
        addOption()
        addOption()
    }

    private fun addOption() {
        val optionView = LayoutInflater.from(this).inflate(R.layout.poll_item_option, llOptionsContainer, false)
        val etOption = optionView.findViewById<EditText>(R.id.et_option)
        val btnRemove = optionView.findViewById<ImageView>(R.id.btn_remove_option)

        etOption.hint = "Option ${llOptionsContainer.childCount + 1}"

        btnRemove.setOnClickListener {
            if (llOptionsContainer.childCount > 2) {
                llOptionsContainer.removeView(optionView)
                updateOptionHints()
            } else {
                Toast.makeText(this, "Minimum 2 options required", Toast.LENGTH_SHORT).show()
            }
        }

        llOptionsContainer.addView(optionView)
    }

    private fun updateOptionHints() {
        llOptionsContainer.children.forEachIndexed { index, view ->
            val etOption = view.findViewById<EditText>(R.id.et_option)
            etOption.hint = "Option ${index + 1}"
        }
    }

    private fun validateAndCreatePoll() {
        val name = etPollName.text.toString().trim()
        val question = etQuestion.text.toString().trim()
        
        if (name.isEmpty()) {
            etPollName.error = "Poll name is required"
            return
        }
        if (question.isEmpty()) {
            etQuestion.error = "Question is required"
            return
        }

        val maxParticipants = etMaxParticipants.text.toString().toIntOrNull() ?: 50
        val durationMinutes = etDuration.text.toString().toIntOrNull() ?: 5
        val endTimeMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000)

        val optionsList = mutableListOf<PollOption>()
        var hasEmpty = false
        llOptionsContainer.children.forEachIndexed { index, view ->
            val text = view.findViewById<EditText>(R.id.et_option).text.toString().trim()
            if (text.isEmpty()) {
                view.findViewById<EditText>(R.id.et_option).error = "Option required"
                hasEmpty = true
            } else {
                optionsList.add(PollOption(index, text))
            }
        }

        if (hasEmpty) return

        val currentUser = FirebaseAuth.getInstance().currentUser
        val hostName = currentUser?.displayName ?: currentUser?.email ?: "Unknown Host"
        val hostId = if (mode == "ONLINE") currentUser?.uid ?: "" else DeviceIdProvider.getDeviceId(this)

        if (mode == "LOCAL") {
            val ip = NetworkUtils.getLocalIpAddress()
            val lastByte = ip.split(".").last()
            val hexByte = Integer.toHexString(lastByte.toInt()).uppercase().padStart(2, '0')
            val randomPart = (('A'..'Z') + ('0'..'9')).shuffled().take(2).joinToString("")
            val code = "$hexByte$randomPart"

            val poll = Poll(
                id = UUID.randomUUID().toString(),
                title = name,
                code = code,
                question = question,
                options = optionsList,
                hostIp = ip,
                hostId = hostId,
                hostName = hostName,
                maxParticipants = maxParticipants,
                durationMinutes = durationMinutes,
                endTimeMillis = endTimeMillis
            )

            startPollService(poll)
            navigateToCodeScreen(code)
        } else {            val code = (('A'..'Z') + ('0'..'9')).shuffled().take(6).joinToString("")
            val poll = Poll(
                id = UUID.randomUUID().toString(),
                title = name,
                code = code,
                question = question,
                options = optionsList,
                hostIp = "online",
                hostId = hostId,
                hostName = hostName,
                maxParticipants = maxParticipants,
                durationMinutes = durationMinutes,
                endTimeMillis = endTimeMillis
            )
            
            lifecycleScope.launch {
                val success = (application as LuminaPollApp).onlinePollManager.createPoll(poll)
                if (success) {
                    navigateToCodeScreen(code)
                } else {
                    Toast.makeText(this@CreatePollActivity, "Failed to create online poll", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startPollService(poll: Poll) {
        val pollJson = (application as LuminaPollApp).localServer.serializePoll(poll)
        val serviceIntent = Intent(this, PollForegroundService::class.java).apply {
            action = PollForegroundService.ACTION_START
            putExtra(PollForegroundService.EXTRA_POLL_JSON, pollJson)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun navigateToCodeScreen(code: String) {
        val intent = Intent(this, PollCodeActivity::class.java).apply {
            putExtra("EXTRA_MODE", mode)
            putExtra("EXTRA_ROLE", role)
            putExtra("EXTRA_POLL_CODE", code)
        }
        startActivity(intent)
        finish()
    }
}