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

class CreatePollActivity : BaseActivity() {

    private lateinit var llOptionsContainer: LinearLayout
    private lateinit var etQuestion: EditText
    private lateinit var etMaxParticipants: EditText
    private lateinit var etDuration: EditText

    private var hostNameForLocal: String? = null

    private val hostIdLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            hostNameForLocal = result.data?.getStringExtra("EXTRA_HOST_NAME")
            // After getting host name, proceed to create poll
            createPollFinal()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_create)

        initViews()
        
        // XML Theme handles background and button colors now.
        // We only need to tint icons that are not themed by default.
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

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btn_add_option_container).setOnClickListener {
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
        val question = etQuestion.text.toString().trim()
        val maxParticipantsStr = etMaxParticipants.text.toString().trim()
        val durationStr = etDuration.text.toString().trim()
        
        if (question.isEmpty()) {
            etQuestion.error = "Question is required"
            return
        }
        if (maxParticipantsStr.isEmpty()) {
            etMaxParticipants.error = "Max participants required"
            return
        }
        if (durationStr.isEmpty()) {
            etDuration.error = "Duration required"
            return
        }

        // Validate options
        var hasEmpty = false
        llOptionsContainer.children.forEach { view ->
            val text = view.findViewById<EditText>(R.id.et_option).text.toString().trim()
            if (text.isEmpty()) {
                view.findViewById<EditText>(R.id.et_option).error = "Option required"
                hasEmpty = true
            }
        }
        if (hasEmpty) return

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
                title = question, // Use question as title
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
                title = question, // Use question as title
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
