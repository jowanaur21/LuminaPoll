package company.luminapoll.features.poll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import company.luminapoll.LuminaPollApp
import company.luminapoll.R
import company.luminapoll.features.poll.LivePollActivity
import company.luminapoll.core.network.Poll

class PollForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_POLL_JSON = "EXTRA_POLL_JSON"
        private const val CHANNEL_ID = "poll_service_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val pollJson = intent.getStringExtra(EXTRA_POLL_JSON)
                if (pollJson != null) {
                    val poll = (application as LuminaPollApp).localServer.parsePoll(pollJson)
                    if (poll != null) {
                        startForegroundService(poll)
                        (application as LuminaPollApp).localServer.start(poll)
                    }
                }
            }
            ACTION_STOP -> {
                (application as LuminaPollApp).localServer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService(poll: Poll) {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, LivePollActivity::class.java).apply {
            putExtra("EXTRA_MODE", "LOCAL")
            putExtra("EXTRA_POLL_CODE", poll.code)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LuminaPoll Active")
            .setContentText("Hosting poll: ${poll.title} (Code: ${poll.code})")
            .setSmallIcon(R.drawable.ic_live_badge)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Poll Hosting Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
