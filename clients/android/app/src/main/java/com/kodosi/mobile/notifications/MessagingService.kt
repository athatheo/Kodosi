package com.kodosi.mobile.notifications

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kodosi.mobile.KodosiApplication

class MessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val pushed = message.data["pushed"] ?: return
        (application as KodosiApplication).notifications.receive(PushedTerminal.list(pushed))
    }

    override fun onNewToken(token: String) {
        synchronize(force = true)
    }

    override fun onDeletedMessages() {
        synchronize(force = false)
    }

    private fun synchronize(force: Boolean) {
        val app = application as KodosiApplication
        if (!app.preferences.state.value.notifications || !app.notifications.hasAccount) return
        try {
            PushWorker.enqueue(app)
        } catch (failure: Exception) {
            Log.w("Kodosi", "Could not schedule push registration.", failure)
        }
        Handler(Looper.getMainLooper()).post {
            app.model.updatePush(force)
        }
    }
}
