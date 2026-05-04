package company.luminapoll.features.poll

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import company.luminapoll.R
import company.luminapoll.core.base.BaseActivity

class HostIdentificationActivity : BaseActivity() {

    private lateinit var etHostName: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity_host_identification)

        etHostName = findViewById(R.id.et_host_name)

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        findViewById<Button>(R.id.btn_save_continue).setOnClickListener {
            val name = etHostName.text.toString().trim()
            if (name.isNotEmpty()) {
                val data = android.content.Intent().apply {
                    putExtra("EXTRA_HOST_NAME", name)
                }
                setResult(RESULT_OK, data)
                finish()
            } else {
                etHostName.error = "Please enter your name"
            }
        }
    }
}
