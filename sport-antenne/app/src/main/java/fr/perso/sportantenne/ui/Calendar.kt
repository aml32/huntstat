package fr.perso.sportantenne.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Event

/** Ouvre l'agenda du téléphone avec l'événement pré-rempli (aucune permission requise). */
fun addToCalendar(ctx: Context, e: Event, channels: Map<String, Channel>) {
    val names = e.channels.joinToString(", ") { channels[it]?.name ?: it }
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "${e.headline} — $names")
        .putExtra(CalendarContract.Events.EVENT_LOCATION, names)
        .putExtra(
            CalendarContract.Events.DESCRIPTION,
            listOf(e.sport, e.competition, e.detail, e.desc).filter { it.isNotBlank() }.distinct().joinToString("\n"),
        )
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.stop)
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "Aucune appli d'agenda installée", Toast.LENGTH_LONG).show()
    }
}
