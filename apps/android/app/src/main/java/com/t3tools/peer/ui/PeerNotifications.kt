package com.t3tools.peer.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.t3tools.peer.MainActivity

/**
 * The notifications the app posts when the agent needs the user.
 *
 * The peer app holds its own connection to the host, so it hears about the work
 * as it happens and has no relay to ask. The notification is only posted while
 * the app is running: a background service would keep it alive, which is the
 * next step rather than this one.
 */
object PeerNotifications {
  /** The channel the notifications live in. */
  const val CHANNEL_ID = "t3-peer-agent"

  /** One notification at a time: the app speaks about the thread the user is in. */
  private const val NOTIFICATION_ID = 1

  /** Makes sure the channel exists, so a notification is not dropped. */
  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Agent activity",
        NotificationManager.IMPORTANCE_HIGH,
      )
    channel.description = "Says when the agent needs you or has finished its work."
    manager.createNotificationChannel(channel)
  }

  /** Whether the user has let the app post notifications. */
  fun allowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

  /** Says that the agent is waiting for the user. */
  fun postNeedsAttention(context: Context, threadId: String, threadTitle: String, action: String) {
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("$action needed")
        .setContentText(threadTitle)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_EVENT)
        .setAutoCancel(true)
        .setContentIntent(openThread(context, threadId))
        .build()
    post(context, notification)
  }

  /** Takes the notification away, for work the user has seen. */
  fun clear(context: Context) {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
  }

  private fun post(context: Context, notification: android.app.Notification) {
    if (!allowed(context)) return
    ensureChannel(context)
    runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
  }

  /** Opens the thread the notification is about. */
  private fun openThread(context: Context, threadId: String): PendingIntent {
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
      }
    return PendingIntent.getActivity(
      context,
      threadId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
