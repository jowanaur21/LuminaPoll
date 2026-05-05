package company.luminapoll.features.poll

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity
import company.luminapoll.core.network.Poll
import company.luminapoll.features.poll.PollForegroundService
import company.luminapoll.core.network.PollOption
import company.luminapoll.core.utils.DeviceIdProvider
import company.luminapoll.core.utils.NetworkUtils
import kotlinx.coroutines.launch
import java.util.UUID

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView

class CreatePollActivity : BaseActivity() {

    private lateinit var llOptionsContainer: LinearLayout
    private lateinit var etQuestion: EditText
    private lateinit var etMaxParticipants: EditText
    private lateinit var etDuration: EditText
    private lateinit var btnCreatePoll: Button
    private lateinit var tvInlineError: TextView

    companion object {
        const val REC_LOCAL_PARTICIPANTS = 50
        const val REC_LOCAL_DURATION = 120 // 2 hours
        const val REC_ONLINE_PARTICIPANTS = 500
        const val REC_ONLINE_DURATION = 1440 // 24 hours
    }

    private var hostNameForLocal: String? = null

    private val hostIdLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            hostNameForLocal = result.data?.getStringExtra("EXTRA_HOST_NAME")
            createPollFinal()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_create)

        initViews()
        
        findViewById<ImageView>(R.id.btn_back).imageTintList = android.content.res.ColorStateList.valueOf(
            android.util.TypedValue().apply {
                theme.resolveAttribute(R.attr.colorModePrimary, this, true)
            }.data
        )
    }

    private fun initViews() {
        llOptionsContainer = findViewById(R.id.ll_options_container)
        etQuestion = findViewById(R.id.et_question)
        etMaxParticipants = findViewById(R.id.et_max_participants)
        etDuration = findViewById(R.id.et_duration)
        btnCreatePoll = findViewById(R.id.btn_create_poll)
        tvInlineError = findViewById(R.id.tv_inline_error)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btn_add_option_container).setOnClickListener {
            addOption()
            validateFields()
        }

        btnCreatePoll.setOnClickListener {
            validateAndCreatePoll()
        }
        
        setupValidation()
        
        addOption()
        addOption()
    }

    private fun setupValidation() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateFields()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etQuestion.addTextChangedListener(watcher)
        etMaxParticipants.addTextChangedListener(watcher)
        etDuration.addTextChangedListener(watcher)
    }

    private fun validateFields() {
        val question = etQuestion.text.toString().trim()
        val maxParticipantsStr = etMaxParticipants.text.toString().trim()
        val durationStr = etDuration.text.toString().trim()
        
        var optionsValid = llOptionsContainer.childCount >= 2
        llOptionsContainer.children.forEach { view ->
            if (view.findViewById<EditText>(R.id.et_option).text.toString().trim().isEmpty()) {
                optionsValid = false
            }
        }
        
        val hasRequiredFields = question.isNotEmpty() && maxParticipantsStr.isNotEmpty() && durationStr.isNotEmpty() && optionsValid
        updateSubmitButtonState(btnCreatePoll, hasRequiredFields)

        // Show inline warnings if exceeding recommendations
        if (hasRequiredFields) {
            val participants = maxParticipantsStr.toIntOrNull() ?: 0
            val duration = durationStr.toIntOrNull() ?: 0
            
            val (recP, recD) = if (mode == "LOCAL") REC_LOCAL_PARTICIPANTS to REC_LOCAL_DURATION else REC_ONLINE_PARTICIPANTS to REC_ONLINE_DURATION
            
            if (participants > recP || duration > recD) {
                val warning = if (participants > recP && duration > recD) "Exceeding recommended participants & duration"
                             else if (participants > recP) "Exceeding recommended participants ($recP)"
                             else "Exceeding recommended duration (${recD/60}h)"
                
                showAppMessage(AppMessage(warning, MessageType.WARNING, severity = MessageSeverity.INLINE), tvInlineError)
            } else {
                tvInlineError.visibility = View.GONE
            }
        } else {
            tvInlineError.visibility = View.GONE
        }
    }

    private fun addOption() {
        val optionView = LayoutInflater.from(this).inflate(R.layout.poll_item_option, llOptionsContainer, false)
        val etOption = optionView.findViewById<EditText>(R.id.et_option)
        val btnRemove = optionView.findViewById<ImageView>(R.id.btn_remove_option)

        etOption.hint = "Option ${llOptionsContainer.childCount + 1}"
        
        etOption.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateFields()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnRemove.setOnClickListener {
            if (llOptionsContainer.childCount > 2) {
                llOptionsContainer.removeView(optionView)
                updateOptionHints()
                validateFields()
            } else {
                showAppMessage(AppMessage("Minimum 2 options required", MessageType.WARNING, severity = MessageSeverity.TOAST))
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
        val question = etQuestion.text.toString().trim()
        val maxParticipantsStr = etMaxParticipants.text.toString().trim()
        val durationStr = etDuration.text.toString().trim()
        
        if (question.isEmpty() || maxParticipantsStr.isEmpty() || durationStr.isEmpty()) {
            showAppMessage(AppMessage("Please fill in all fields", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
            return
        }

        var hasEmptyOption = false
        llOptionsContainer.children.forEach { view ->
            val text = view.findViewById<EditText>(R.id.et_option).text.toString().trim()
            if (text.isEmpty()) {
                hasEmptyOption = true
            }
        }
        if (hasEmptyOption) {
            showAppMessage(AppMessage("Please fill all options", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
            return
        }

        val participants = maxParticipantsStr.toIntOrNull() ?: 0
        val duration = durationStr.toIntOrNull() ?: 0
        val (recP, recD) = if (mode == "LOCAL") REC_LOCAL_PARTICIPANTS to REC_LOCAL_DURATION else REC_ONLINE_PARTICIPANTS to REC_ONLINE_DURATION

        if (participants > recP || duration > recD) {
            val warningMsg = if (mode == "LOCAL") {
                "Large local polls may cause your device to lag or drain battery quickly. Are you sure you want to proceed?"
            } else {
                "Exceeding 500 participants or 24 hours may affect live update performance for some users. Proceed anyway?"
            }
            
            showAppMessage(
                AppMessage(warningMsg, MessageType.WARNING, severity = MessageSeverity.MODAL),
                onConfirm = { checkHostAndCreate() }
            )
        } else {
            checkHostAndCreate()
        }
    }

    private fun checkHostAndCreate() {
        if (mode == "LOCAL" && hostNameForLocal == null) {
            val intent = Intent(this, HostIdentificationActivity::class.java).apply {
                putExtra("EXTRA_MODE", mode)
                putExtra("EXTRA_ROLE", role)
            }
            hostIdLauncher.launch(intent)
        } else {
            createPollFinal()
        }
    }

    private fun createPollFinal() {
        val question = etQuestion.text.toString().trim()
        val maxParticipants = etMaxParticipants.text.toString().toIntOrNull() ?: 50
        val durationMinutes = etDuration.text.toString().toIntOrNull() ?: 5
        val endTimeMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000)

        val optionsList = mutableListOf<PollOption>()
        llOptionsContainer.children.forEachIndexed { index, view ->
            val text = view.findViewById<EditText>(R.id.et_option).text.toString().trim()
            optionsList.add(PollOption(index, text))
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        val hostName = if (mode == "LOCAL") hostNameForLocal ?: "Unknown Host" else (currentUser?.displayName ?: currentUser?.email ?: "Unknown Host")
        val hostId = if (mode == "ONLINE") currentUser?.uid ?: "" else DeviceIdProvider.getDeviceId(this)

        if (mode == "LOCAL") {
            val ip = NetworkUtils.getLocalIpAddress()
            val lastByte = ip.split(".").last()
            val hexByte = Integer.toHexString(lastByte.toInt()).uppercase().padStart(2, '0')
            val randomPart = (('A'..'Z') + ('0'..'9')).shuffled().take(2).joinToString("")
            val code = "$hexByte$randomPart"

            val poll = Poll(
                id = UUID.randomUUID().toString(),
                title = question, 
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
        } else {
            val code = (('A'..'Z') + ('0'..'9')).shuffled().take(6).joinToString("")
            val poll = Poll(
                id = UUID.randomUUID().toString(),
                title = question, 
                code = code,
                question = question,
                options = optionsList,
                hostIp = "online",
                hostId = hostId,
                hostName = hostName,
                maxParticipants = maxParticipants,
                durationMinutes = durationMinutes,
                endTimeMillis = endTimeMillis,
                isOnline = true
            )
            
            lifecycleScope.launch {
                val success = (application as LuminaPollApp).onlinePollManager.createPoll(poll)
                if (success) {
                    showAppMessage(AppMessage("Poll created successfully!", MessageType.SUCCESS, severity = MessageSeverity.TOAST))
                    navigateToCodeScreen(code)
                } else {
                    showAppMessage(AppMessage("Failed to create online poll", MessageType.ERROR, ErrorType.SERVER, MessageSeverity.MODAL))
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
