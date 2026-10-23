package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R

object AgentNotificationController {

    const val CHANNEL_ID = "agentflow_persistent_channel"
    const val CHANNEL_NAME = "AgentFlow Assistant Control"
    const val NOTIFICATION_ID = 1001

    const val ACTION_NOTIFICATION_REPLY = "com.example.ACTION_NOTIFICATION_REPLY"
    const val ACTION_TOGGLE_OVERLAY = "com.example.ACTION_TOGGLE_OVERLAY"
    const val ACTION_NOTIFICATION_VOICE = "com.example.ACTION_NOTIFICATION_VOICE"
    const val KEY_REPLY_TEXT = "key_agent_reply_text"

    private var currentStatusMessage: String = "Ready for command • Tap or reply below"
    private var isBusyState: Boolean = false

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AgentFlow on-screen assistant controls and notification commands"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        statusMessage: String = currentStatusMessage,
        isOverlayActive: Boolean = FloatingAgentOverlayService.isOverlayRunning,
        isBusy: Boolean = isBusyState
    ): Notification {
        createNotificationChannel(context)
        currentStatusMessage = statusMessage
        isBusyState = isBusy

        // 1. Content Intent (Tap notification to open app)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Direct Reply Action: Command / talk to AI from notification panel
        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_NOTIFICATION_REPLY
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Command AgentFlow…")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_get,
            "Command AI",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // 3. Toggle Floating Agent Button Action
        val toggleOverlayIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_OVERLAY
        }
        val toggleOverlayPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            toggleOverlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val overlayActionTitle = if (isOverlayActive) "Deactivate Floating Agent" else "Activate Floating Agent"
        val overlayAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_slideshow,
            overlayActionTitle,
            toggleOverlayPendingIntent
        ).build()

        // 4. Voice Command Action
        val voiceIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_NOTIFICATION_VOICE
            putExtra("START_VOICE_LISTENING", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val voicePendingIntent = PendingIntent.getActivity(
            context,
            3,
            voiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val voiceAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_btn_speak_now,
            "Voice Talk",
            voicePendingIntent
        ).build()

        val title = if (isBusy) "AgentFlow • Executing Task" else "AgentFlow AI Assistant"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusMessage)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(replyAction)
            .addAction(overlayAction)
            .addAction(voiceAction)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$statusMessage\n\n• Command AI: Type and send directly here\n• Floating Button: $overlayActionTitle\n• Voice Talk: Tap to speak hands-free")
            )
            .build()
    }

    fun updateStatus(context: Context, statusMessage: String, isBusy: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(context, statusMessage = statusMessage, isBusy = isBusy)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun updateOverlayState(context: Context, isOverlayActive: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(context, isOverlayActive = isOverlayActive)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
