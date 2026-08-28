package com.ensemble.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ensemble.app.MainActivity
import com.ensemble.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

const val CHANNEL_ID = "ensemble_messages"

/**
 * Reçoit les notifications push envoyées par la Cloud Function lors d'un nouveau message.
 * Le contenu réel des messages est chiffré et n'est jamais inclus dans la notification :
 * seul un titre générique ("Nouveau message 💌") est affiché.
 *
 * La Cloud Function envoie un message FCM "data" pur (pas de bloc "notification") : c'est
 * volontaire. Un message "notification" est affiché directement par le système quand l'app est
 * en arrière-plan ou fermée, SANS jamais passer par onMessageReceived — ce qui rendait le
 * comportement incohérent entre premier plan et arrière-plan (canal, icône, ouverture au tap...
 * tout différait). En "data" pur, onMessageReceived est systématiquement appelé quel que soit
 * l'état de l'app, donc l'affichage est toujours le même, et on peut aussi choisir de ne rien
 * afficher si la conversation est déjà ouverte à l'écran.
 */
class EnsembleMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["type"] == "message" && ConversationVisibilityTracker.isVisible()) return
        val title = message.data["title"] ?: getString(R.string.app_name)
        val body = message.data["body"] ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
