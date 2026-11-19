package uz.jarvis

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class JarvisNotificationService : NotificationListenerService() {

    private val messengers = setOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.instagram.android",
        "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.packageName !in messengers) return
        val e = n.extras ?: return
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (text.isBlank()) return
        NotifStore.add(this, Item(sbn.packageName, title, text, sbn.postTime))
    }
}
