package com.meow.music.data.internetroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class InternetListenTogetherActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val client = InternetListenTogetherClient.getInstance() ?: return
    val notifId = intent.getIntExtra(InternetListenTogetherClient.EXTRA_NOTIFICATION_ID, 0)

    // Dismiss the notification that triggered this action
    NotificationManagerCompat.from(context).cancel(notifId)

    when (intent.action) {
      InternetListenTogetherClient.ACTION_APPROVE_JOIN -> {
        val userId = intent.getStringExtra(InternetListenTogetherClient.EXTRA_USER_ID) ?: return
        client.approveJoin(userId)
      }
      InternetListenTogetherClient.ACTION_REJECT_JOIN -> {
        val userId = intent.getStringExtra(InternetListenTogetherClient.EXTRA_USER_ID) ?: return
        client.rejectJoin(userId, null)
      }
      InternetListenTogetherClient.ACTION_APPROVE_SUGGESTION -> {
        val suggestionId = intent.getStringExtra(InternetListenTogetherClient.EXTRA_SUGGESTION_ID) ?: return
        client.approveSuggestion(suggestionId)
      }
      InternetListenTogetherClient.ACTION_REJECT_SUGGESTION -> {
        val suggestionId = intent.getStringExtra(InternetListenTogetherClient.EXTRA_SUGGESTION_ID) ?: return
        client.rejectSuggestion(suggestionId, null)
      }
    }
  }
}
