package company.luminapoll.features.poll

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView

class HostIdentificationActivity : BaseActivity() {

    private lateinit var etHostName: EditText
    private lateinit var btnSave: Button
    private lateinit var tvInlineError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_host_identification)

        etHostName = findViewById(R.id.et_host_name)
        btnSave = findViewById(R.id.btn_save_continue)
        tvInlineError = findViewById(R.id.tv_inline_error)

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        btnSave.setOnClickListener {
            val name = etHostName.text.toString().trim()
            if (name.isNotEmpty()) {
                val data = android.content.Intent().apply {
                    putExtra("EXTRA_HOST_NAME", name)
                }
                setResult(RESULT_OK, data)
                finish()
            } else {
                showAppMessage(AppMessage("Please enter your name", MessageType.ERROR, ErrorType.VALIDATION, MessageSeverity.INLINE), tvInlineError)
            }
        }

        // Initial button state
        updateSubmitButtonState(btnSave, false)

        etHostName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSubmitButtonState(btnSave, s.toString().trim().isNotEmpty())
                tvInlineError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
