package com.ensemble.app.notifications

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Vrai tant que l'écran Messages est affiché ET au premier plan (voir MessagesScreen).
 * Sert à ne pas déclencher de notification pour un message qu'on est déjà en train de lire en
 * direct dans la conversation.
 */
object ConversationVisibilityTracker {
    private val visible = AtomicBoolean(false)

    fun setVisible(value: Boolean) {
        visible.set(value)
    }

    fun isVisible(): Boolean = visible.get()
}
