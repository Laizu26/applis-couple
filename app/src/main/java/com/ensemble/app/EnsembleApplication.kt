package com.ensemble.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ensemble.app.data.AppContainer
import com.ensemble.app.notifications.CHANNEL_ID

class EnsembleApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
    }

    /**
     * Créé une fois au démarrage (et non paresseusement au premier message reçu) pour que les
     * notifications affichées automatiquement par Android quand l'app est en arrière-plan/fermée
     * utilisent bien ce canal en importance haute, plutôt que le canal par défaut de Firebase.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
    }
}
