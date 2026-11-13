package fr.perso.sportantenne

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.Schedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Alertes avant les matchs : une alarme système par diffusion suivie. */
object Alerts {
    private const val PREFS = "alerts"
    private const val MAX_ALARMS = 80
    const val CHANNEL_ID = "matchs"

    private fun intent(ctx: Context, code: Int, fill: (Intent) -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, AlertReceiver::class.java).also(fill),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Annule les alarmes précédentes puis programme celles des diffusions suivies à venir. */
    @Synchronized
    fun reschedule(ctx: Context, events: List<Event>, channels: Map<String, Channel>, settings: Settings) {
        val alarms = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet("codes", emptySet())?.forEach { c -> c.toIntOrNull()?.let { alarms.cancel(intent(ctx, it)) } }

        val now = System.currentTimeMillis()
        val upcoming = settings.visible(events, now).filter { it.start > now && settings.hasAlert(it) }
        val notified = (prefs.getStringSet("notified", emptySet()) ?: emptySet()).intersect(upcoming.map { it.id }.toSet())
        val hour = SimpleDateFormat("HH:mm", Locale.FRENCH)
        val codes = HashSet<String>()
        for (e in upcoming.filter { it.id !in notified }.take(MAX_ALARMS)) {
            val code = e.id.hashCode()
            val names = e.channels.joinToString(", ") { channels[it]?.name ?: it }
            val pending = intent(ctx, code) {
                it.putExtra("id", e.id)
                it.putExtra("code", code)
                it.putExtra("title", e.headline)
                it.putExtra("text", "${hour.format(Date(e.start))} sur $names · ${e.competition}")
            }
            val at = maxOf(e.start - settings.alertMinutes * 60_000L, now + 3_000L)
            val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            codes += code.toString()
        }
        prefs.edit().putStringSet("codes", codes).putStringSet("notified", notified).apply()
    }

    /** Recalage complet à partir du cache (démarrage du téléphone, mise à jour de l'appli). */
    fun rescheduleFromDisk(ctx: Context) {
        val guide = GuideStore.load(ctx.filesDir) ?: return
        reschedule(ctx, Schedule.build(guide.broadcasts), guide.channels.associateBy { it.id }, Settings.load(ctx))
    }

    fun markNotified(ctx: Context, id: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("notified", (prefs.getStringSet("notified", emptySet()) ?: emptySet()) + id).apply()
    }
}

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(Alerts.CHANNEL_ID, "Alertes matchs", NotificationManager.IMPORTANCE_HIGH),
        )
        intent.getStringExtra("id")?.let { Alerts.markNotified(ctx, it) }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(ctx, Alerts.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(intent.getStringExtra("title") ?: "Match à venir")
            .setContentText(intent.getStringExtra("text") ?: "")
            .setStyle(Notification.BigTextStyle().bigText(intent.getStringExtra("text") ?: ""))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(intent.getIntExtra("code", 1), notification)
    }
}

/** Les alarmes sont perdues au redémarrage du téléphone : on les reprogramme. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        Thread {
            try {
                Alerts.rescheduleFromDisk(ctx.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
