package company.luminapoll.core.utils

import android.content.Context
import java.util.UUID
import androidx.core.content.edit

object DeviceIdProvider {
    private const val PREFS_NAME = "device_prefs"
    private const val KEY_DEVICE_ID = "internal_device_uuid"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_DEVICE_ID, deviceId) }
        }
        
        return deviceId
    }
}
