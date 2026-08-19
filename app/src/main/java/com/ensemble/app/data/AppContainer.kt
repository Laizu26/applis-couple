package com.ensemble.app.data

import android.content.Context
import com.ensemble.app.data.crypto.CryptoManager
import com.ensemble.app.data.crypto.EncryptedImageLoader
import com.ensemble.app.data.repository.AuthRepository
import com.ensemble.app.data.repository.CoupleRepository
import com.ensemble.app.data.repository.EventRepository
import com.ensemble.app.data.repository.JournalRepository
import com.ensemble.app.data.repository.MessageRepository
import com.ensemble.app.data.repository.PhotoRepository
import com.ensemble.app.data.repository.UpdateRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage

/** Conteneur d'injection de dépendances manuel, simple et suffisant pour une app à 2 utilisateurs. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val cryptoManager = CryptoManager(appContext)

    val authRepository = AuthRepository(Firebase.auth)
    val coupleRepository = CoupleRepository(Firebase.firestore)
    val messageRepository = MessageRepository(Firebase.firestore)
    val photoRepository = PhotoRepository(Firebase.firestore, Firebase.storage)
    val eventRepository = EventRepository(Firebase.firestore)
    val journalRepository = JournalRepository(Firebase.firestore, photoRepository)
    val updateRepository = UpdateRepository(Firebase.firestore, Firebase.storage)
    val imageLoader = EncryptedImageLoader(photoRepository, cryptoManager)
}
