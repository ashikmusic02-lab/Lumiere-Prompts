package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Firebase Cloud Messaging service (Requirement E):
 * - Shows a system notification on channel "general" with title, body, and optional image
 *   when a message arrives in the foreground.
 * - Passes data keys "link" (e.g., promptlens://image/{id}) and "imageId" into the PendingIntent
 *   so tapping the notification opens that post INSIDE the app in all three states
 *   (killed, background, foreground).
 */
class PromptLensMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM registration token refreshed")
        PromptLensDeepLinkManager.ensureSubscribedToAllTopic()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: notification?.title?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)

        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: data["message"]?.takeIf { it.isNotBlank() }
            ?: notification?.body.orEmpty()

        val imageUrl = data["imageUrl"]?.takeIf { it.isNotBlank() }
            ?: data["image"]?.takeIf { it.isNotBlank() }
            ?: notification?.imageUrl?.toString().orEmpty()

        val linkValue = data["link"]?.takeIf { it.isNotBlank() }
            ?: notification?.link?.toString().orEmpty()

        val imageIdValue = data["imageId"]?.takeIf { it.isNotBlank() }
            ?: data["image_id"]?.takeIf { it.isNotBlank() }
            ?: PromptLensDeepLinkManager.extractImageIdFromLinkString(linkValue).orEmpty()

        showForegroundNotification(
            title = title,
            body = body,
            imageUrl = imageUrl,
            link = linkValue,
            imageId = imageIdValue
        )
    }

    private fun showForegroundNotification(
        title: String,
        body: String,
        imageUrl: String,
        link: String,
        imageId: String
    ) {
        ensureGeneralNotificationChannel(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (imageId.isNotBlank()) {
                putExtra("imageId", imageId)
            }
            if (link.isNotBlank()) {
                putExtra("link", link)
            }
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            Random.nextInt(1000, 999999),
            launchIntent,
            pendingIntentFlags
        )

        val bitmap = if (imageUrl.isNotBlank()) fetchBitmapSafely(imageUrl) else null

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_stat_onesignal_default)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF8B5CF6.toInt())
            .setContentIntent(pendingIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(body)
            )
        } else if (body.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(Random.nextInt(1000, 999999), builder.build())
        }
    }

    private fun fetchBitmapSafely(urlString: String): Bitmap? {
        return runCatching {
            val url = URL(urlString)
            val connection = (url.openConnection() as? HttpURLConnection) ?: return null
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.doInput = true
            connection.connect()
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "PromptLensFCM"
        const val CHANNEL_ID_GENERAL = "general"

        fun ensureGeneralNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
                val existing = nm.getNotificationChannel(CHANNEL_ID_GENERAL)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID_GENERAL,
                        context.getString(R.string.notifications_title),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.app_name)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }
    }
}
