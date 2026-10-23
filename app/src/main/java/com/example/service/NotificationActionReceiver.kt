package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.MainActivity

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AgentNotificationController.ACTION_NOTIFICATION_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(AgentNotificationController.KEY_REPLY_TEXT)?.toString()

                if (!replyText.isNullOrBlank()) {
                    // Execute command in background
                    BackgroundAgentExecutor.executePrompt(context, replyText)
                }
            }

            AgentNotificationController.ACTION_TOGGLE_OVERLAY -> {
                FloatingAgentOverlayService.toggle(context)
            }

            AgentNotificationController.ACTION_NOTIFICATION_VOICE -> {
                val voiceIntent = Intent(context, MainActivity::class.java).apply {
                    action = AgentNotificationController.ACTION_NOTIFICATION_VOICE
                    putExtra("START_VOICE_LISTENING", true)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(voiceIntent)
            }
        }
    }
}
