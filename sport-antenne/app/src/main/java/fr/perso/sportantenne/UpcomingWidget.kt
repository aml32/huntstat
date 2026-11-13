package fr.perso.sportantenne

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.Schedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget d'accueil : les prochains événements (favoris en priorité) et leur chaîne. */
class UpcomingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private val ROWS = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6)

        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, UpcomingWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, manager, ids)
        }

        private fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val now = System.currentTimeMillis()
            val settings = Settings.load(ctx)
            val guide = GuideStore.load(ctx.filesDir)
            val names = guide?.channels?.associate { it.id to it.name } ?: emptyMap()
            val all = settings.visible(Schedule.build(guide?.broadcasts ?: emptyList()), now)
            val favorites = all.filter { settings.isFavorite(it) }
            val shown = (if (settings.hasFavorites) favorites else all).take(ROWS.size)

            val views = RemoteViews(ctx.packageName, R.layout.widget)
            views.setTextViewText(
                R.id.widget_title,
                if (settings.hasFavorites) "MES FAVORIS À VENIR" else "À VENIR À LA TÉLÉ",
            )
            val day = SimpleDateFormat("EEE", Locale.FRENCH)
            val hour = SimpleDateFormat("HH:mm", Locale.FRENCH)
            val today = day.format(Date(now))
            ROWS.forEachIndexed { i, id ->
                val e = shown.getOrNull(i)
                views.setTextViewText(
                    id,
                    when {
                        e == null && i == 0 -> if (guide == null) "Ouvrez l'appli pour charger le guide" else "Rien à venir pour l'instant"
                        e == null -> ""
                        else -> {
                            val d = Date(e.start)
                            val whenText = when {
                                e.start <= now -> "En cours"
                                day.format(d) == today && e.start - now < 86_400_000L -> hour.format(d)
                                else -> "${day.format(d)} ${hour.format(d)}"
                            }
                            "$whenText  ${e.headline} · ${e.channels.joinToString(", ") { names[it] ?: it }}"
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
