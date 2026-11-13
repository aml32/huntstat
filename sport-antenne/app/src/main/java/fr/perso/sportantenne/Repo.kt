package fr.perso.sportantenne

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.Guide
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.Kind
import fr.perso.sportantenne.core.XmltvParser
import fr.perso.sportantenne.core.competitionKey
import fr.perso.sportantenne.core.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Téléchargement du guide, cache local et préférences (favoris, chaînes, filtres). */
object Repo {
    const val GUIDE_URL = "https://xmltvfr.fr/xmltv/xmltv_fr.xml.gz"
    const val STALE_MS = 6 * 60 * 60 * 1000L

    suspend fun refresh(ctx: Context): Guide = withContext(Dispatchers.IO) {
        val conn = URL(GUIDE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "SportAntenne/1.0 (Android)")
        try {
            if (conn.responseCode != 200) error("Le guide TV a répondu ${conn.responseCode}")
            val guide = GZIPInputStream(conn.inputStream, 1 shl 16).use {
                // Le passé récent est conservé : il sert à reconnaître les rediffusions.
                XmltvParser.parse(it)
            }
            if (guide.broadcasts.isEmpty()) error("Guide TV vide")
            GuideStore.save(ctx.filesDir, guide)
            UpcomingWidget.updateAll(ctx)
            guide
        } finally {
            conn.disconnect()
        }
    }

    suspend fun load(ctx: Context): Guide? = withContext(Dispatchers.IO) { GuideStore.load(ctx.filesDir) }

    fun lastUpdate(ctx: Context) = GuideStore.lastUpdate(ctx.filesDir)

    fun scheduleBackgroundRefresh(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("guide-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Repo.refresh(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

/**
 * Réglages persistants. Les favoris gardent le libellé affiché ; la comparaison se fait
 * sur des clés normalisées (casse, accents et sponsors ignorés).
 * Une compétition favorite est stockée sous la forme « Sport|Compétition ».
 */
data class Settings(
    val favSports: Set<String> = emptySet(),
    val favCompetitions: Set<String> = emptySet(),
    val favTeams: Set<String> = emptySet(),
    val myChannels: Set<String> = emptySet(),
    val hideReplays: Boolean = true,
    val showMagazines: Boolean = false,
) {
    private val sportKeys by lazy { favSports.map { norm(it) }.toSet() }
    private val competitionKeys by lazy { favCompetitions.map { competitionKeyOf(it) }.toSet() }
    private val teamKeys by lazy { favTeams.map { norm(it) }.toSet() }

    val hasFavorites get() = favSports.isNotEmpty() || favCompetitions.isNotEmpty() || favTeams.isNotEmpty()

    fun isFavSport(sport: String) = norm(sport) in sportKeys
    fun isFavCompetition(sport: String, competition: String) = competitionKey(sport, competition) in competitionKeys
    fun isFavTeam(team: String) = norm(team) in teamKeys

    fun isFavorite(e: Event): Boolean =
        isFavSport(e.sport) || isFavCompetition(e.sport, e.competition) ||
            (e.hasTeams && (isFavTeam(e.home) || isFavTeam(e.away)))

    fun toggleSport(sport: String) =
        copy(favSports = if (isFavSport(sport)) favSports.filter { norm(it) != norm(sport) }.toSet() else favSports + sport)

    fun toggleCompetition(sport: String, competition: String): Settings {
        val key = competitionKey(sport, competition)
        return copy(
            favCompetitions = if (key in competitionKeys) favCompetitions.filter { competitionKeyOf(it) != key }.toSet()
            else favCompetitions + "$sport|$competition",
        )
    }

    fun toggleTeam(team: String) =
        copy(favTeams = if (isFavTeam(team)) favTeams.filter { norm(it) != norm(team) }.toSet() else favTeams + team)

    fun toggleChannel(id: String) = copy(myChannels = if (id in myChannels) myChannels - id else myChannels + id)

    /** Filtre commun : à venir ou en cours, rediffusions/magazines selon réglages, chaînes possédées. */
    fun visible(events: List<Event>, now: Long): List<Event> = events.filter { e ->
        e.stop > now &&
            (showMagazines || e.kind == Kind.EVENT) &&
            (!hideReplays || !e.replay) &&
            (myChannels.isEmpty() || e.channels.any { it in myChannels })
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("favSports", favSports)
            .putStringSet("favCompetitions", favCompetitions)
            .putStringSet("favTeams", favTeams)
            .putStringSet("myChannels", myChannels)
            .putBoolean("hideReplays", hideReplays)
            .putBoolean("showMagazines", showMagazines)
            .apply()
    }

    companion object {
        private const val PREFS = "settings"

        fun competitionKeyOf(stored: String) = competitionKey(stored.substringBefore('|'), stored.substringAfter('|'))

        fun load(ctx: Context): Settings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun set(k: String) = p.getStringSet(k, emptySet())?.toSet() ?: emptySet()
            return Settings(
                set("favSports"), set("favCompetitions"), set("favTeams"), set("myChannels"),
                p.getBoolean("hideReplays", true), p.getBoolean("showMagazines", false),
            )
        }
    }
}
