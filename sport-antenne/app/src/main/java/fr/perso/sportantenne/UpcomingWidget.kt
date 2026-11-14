package fr.perso.sportantenne

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.GuideStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget d'accueil : les prochains événements (favoris en priorité) et leur chaîne. */
class UpcomingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                val guide = GuideStore.load(context.filesDir)
                render(
                    context, guide?.let { Repo.buildEvents(context, it) },
                    guide?.channels?.associateBy { it.id } ?: emptyMap(), Settings.load(context),
                )
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private val ROWS = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6)

        /** [events] nul = guide jamais chargé. */
        fun render(ctx: Context, events: List<Event>?, channels: Map<String, Channel>, settings: Settings) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, UpcomingWidget::class.java))
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val all = settings.visible(events ?: emptyList(), now).filter { it.stop > now } // le widget n'annonce que l'à-venir
            val shown = all.take(ROWS.size)

            val views = RemoteViews(ctx.packageName, R.layout.widget)
            views.setTextViewText(R.id.widget_title, if (settings.favSports.isNotEmpty() || settings.hasFavorites) "MES DIRECTS À VENIR" else "EN DIRECT À LA TÉLÉ")
            val day = SimpleDateFormat("EEE", Locale.FRENCH)
            val hour = SimpleDateFormat("HH:mm", Locale.FRENCH)
            val today = day.format(Date(now))
            ROWS.forEachIndexed { i, id ->
                val e = shown.getOrNull(i)
                views.setTextViewText(
                    id,
                    when {
                        e == null && i == 0 -> if (events == null) "Ouvrez l'appli pour charger le programme" else "Rien à venir pour l'instant"
                        e == null -> ""
                        else -> {
                            val d = Date(e.start)
                            val whenText = when {
                                e.start <= now -> "En cours"
                                day.format(d) == today && e.start - now < 86_400_000L -> hour.format(d)
                                else -> "${day.format(d)} ${hour.format(d)}"
                            }
                            "$whenText  ${e.headline} · ${e.channels.joinToString(", ") { channels[it]?.name ?: it }}"
                        }
                    },
                )
            }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
