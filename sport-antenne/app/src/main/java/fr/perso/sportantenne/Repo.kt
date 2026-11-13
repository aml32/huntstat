package fr.perso.sportantenne

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.perso.sportantenne.core.Catalogue
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.Fixture
import fr.perso.sportantenne.core.FixtureParsers
import fr.perso.sportantenne.core.Fixtures
import fr.perso.sportantenne.core.LiveCheck
import fr.perso.sportantenne.core.Guide
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.Kind
import fr.perso.sportantenne.core.Schedule
import fr.perso.sportantenne.core.XmltvParser
import fr.perso.sportantenne.core.competitionKey
import fr.perso.sportantenne.core.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Tout ce que l'appli affiche : programme, chaînes et catalogue des favoris possibles. */
class Data(val events: List<Event>, val channels: Map<String, Channel>, val catalogue: Catalogue, val updated: Long)

/** Téléchargement du guide, cache local et mise à jour en arrière-plan. */
object Repo {
    const val GUIDE_URL = "https://xmltvfr.fr/xmltv/xmltv_fr.xml.gz"
    const val STALE_MS = 3 * 60 * 60 * 1000L

    /** Télécharge le guide (3 essais), le met en cache, puis recale widget et alertes. */
    suspend fun refresh(ctx: Context): Data = withContext(Dispatchers.IO) {
        // Calendriers réels d'abord : ils servent à distinguer les directs des rediffusions.
        // En cas d'échec, les calendriers déjà en cache restent utilisés.
        try {
            val now = System.currentTimeMillis()
            val fresh = downloadFixtures(now)
            if (fresh.list.isNotEmpty()) Fixtures.load(ctx.filesDir).mergedWith(fresh, now - 2 * DAY).save(ctx.filesDir)
        } catch (e: Exception) {
            // rien : la vérification retombe sur le cache
        }
        var failure: Exception? = null
        repeat(3) { attempt ->
            try {
                val guide = download()
                GuideStore.save(ctx.filesDir, guide)
                return@withContext publish(ctx, guide)
            } catch (e: Exception) {
                failure = e
                delay(2_000L * (attempt + 1))
            }
        }
        throw failure ?: IllegalStateException("Téléchargement impossible")
    }

    private const val DAY = 86_400_000L
    private const val ESPN = "https://site.api.espn.com/apis/site/v2/sports"
    private val RUGBY_LEAGUES = listOf("270559", "270557", "180659", "271937", "289234", "270560", "242041", "244293", "267979")

    private fun get(url: String): String {
        var last: Exception? = null
        repeat(2) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "SportAntenne/1.2 (Android)")
            try {
                if (conn.responseCode == 200) return conn.inputStream.bufferedReader().use { it.readText() }
                last = IllegalStateException("HTTP ${conn.responseCode}")
            } catch (e: Exception) {
                last = e
            } finally {
                conn.disconnect()
            }
        }
        throw last ?: IllegalStateException("échec")
    }

    /** Télécharge les calendriers de la veille à J+7 ; chaque source qui échoue est simplement ignorée. */
    internal fun downloadFixtures(now: Long): Fixtures {
        fun fmt(p: String) = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val days = (-1..7).map { now + it * DAY }
        val dayKeys = days.map { Fixtures.dayOf(it) }
        val months = days.map { fmt("yyyyMM").format(it) }.distinct()

        class Job(val source: String, val covered: List<String>, val url: String, val f1: Boolean = false)
        val jobs = ArrayList<Job>()
        days.forEach { d -> jobs += Job("football", listOf(Fixtures.dayOf(d)), "$ESPN/soccer/all/scoreboard?dates=${fmt("yyyyMMdd").format(d)}&limit=900") }
        for (m in months) {
            val inMonth = dayKeys.filter { it.replace("-", "").startsWith(m) }
            jobs += Job("nba", inMonth, "$ESPN/basketball/nba/scoreboard?dates=$m&limit=1000")
            jobs += Job("nfl", inMonth, "$ESPN/football/nfl/scoreboard?dates=$m&limit=1000")
            jobs += Job("nhl", inMonth, "$ESPN/hockey/nhl/scoreboard?dates=$m&limit=1000")
            RUGBY_LEAGUES.forEach { jobs += Job("rugby", inMonth, "$ESPN/rugby/$it/scoreboard?dates=$m&limit=1000") }
        }
        jobs += Job("f1", dayKeys, "https://api.jolpi.ca/ergast/f1/current.json", f1 = true)

        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(jobs.map { job ->
                Callable<Pair<Job, List<Fixture>>?> {
                    try {
                        val body = get(job.url)
                        job to (if (job.f1) FixtureParsers.formula1(body) else FixtureParsers.espn(body, job.source))
                    } catch (e: Exception) {
                        null
                    }
                }
            }, 90, TimeUnit.SECONDS)
            val list = ArrayList<Fixture>()
            val covered = HashMap<String, MutableSet<String>>()
            for (r in results) {
                val (job, fixtures) = (try { if (r.isCancelled) null else r.get() } catch (e: Exception) { null }) ?: continue
                list += fixtures
                covered.getOrPut(job.source) { HashSet() } += job.covered
            }
            return Fixtures(list.distinct(), covered)
        } finally {
            pool.shutdownNow()
        }
    }

    /** Programme recoupé avec les calendriers en cache : à utiliser partout (écran, widget, alertes). */
    fun buildEvents(ctx: Context, guide: Guide): List<Event> =
        Schedule.build(guide.broadcasts, LiveCheck(Fixtures.load(ctx.filesDir)))

    private fun download(): Guide {
        val conn = URL(GUIDE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "SportAntenne/1.1 (Android)")
        try {
            if (conn.responseCode != 200) error("le guide TV a répondu ${conn.responseCode}")
            // Le passé récent est conservé : il sert à reconnaître les rediffusions.
            val guide = GZIPInputStream(conn.inputStream, 1 shl 16).use { XmltvParser.parse(it) }
            // Un guide tronqué ou vide ne doit jamais écraser un bon cache.
            if (guide.broadcasts.count { it.kind == Kind.EVENT } < 50 || guide.channels.size < 20) error("guide TV incomplet")
            return guide
        } finally {
            conn.disconnect()
        }
    }

    suspend fun load(ctx: Context): Data? = withContext(Dispatchers.IO) {
        GuideStore.load(ctx.filesDir)?.let { publish(ctx, it) }
    }

    private fun publish(ctx: Context, guide: Guide): Data {
        val events = buildEvents(ctx, guide)
        val before = Catalogue.load(ctx.filesDir)
        val catalogue = before.merged(events)
        if (catalogue.teams.size != before.teams.size || catalogue.competitions.size != before.competitions.size) {
            catalogue.save(ctx.filesDir)
        }
        val channels = guide.channels.associateBy { it.id }
        val settings = Settings.load(ctx)
        UpcomingWidget.render(ctx, events, channels, settings)
        Alerts.reschedule(ctx, events, channels, settings)
        return Data(events, channels, catalogue, GuideStore.lastUpdate(ctx.filesDir))
    }

    fun scheduleBackgroundRefresh(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(4, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("guide-refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Repo.refresh(applicationContext)
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
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
    val alertsEnabled: Boolean = true,
    val alertMinutes: Int = 15,
    /** Diffusions pour lesquelles l'alerte a été activée ou coupée à la main (identifiants Event.id). */
    val alertOn: Set<String> = emptySet(),
    val alertOff: Set<String> = emptySet(),
    /** N'afficher que les directs confirmés par un calendrier sportif. */
    val verifiedOnly: Boolean = false,
) {
    private val sportKeys by lazy { favSports.map { norm(it) }.toSet() }
    private val competitionKeys by lazy { favCompetitions.map { competitionKeyOf(it) }.toSet() }
    private val teamKeys by lazy { favTeams.map { norm(it) }.toSet() }

    /** Équipes ou compétitions suivies. Les sports cochés, eux, filtrent le programme. */
    val hasFavorites get() = favCompetitions.isNotEmpty() || favTeams.isNotEmpty()

    fun isFavSport(sport: String) = norm(sport) in sportKeys
    fun isFavCompetition(sport: String, competition: String) = competitionKey(sport, competition) in competitionKeys
    fun isFavTeam(team: String) = norm(team) in teamKeys

    fun isFavorite(e: Event): Boolean =
        isFavCompetition(e.sport, e.competition) || (e.hasTeams && (isFavTeam(e.home) || isFavTeam(e.away)))

    fun hasAlert(e: Event): Boolean =
        e.id in alertOn || (alertsEnabled && e.id !in alertOff && !e.replay && isFavorite(e))

    fun toggleAlert(e: Event): Settings =
        if (hasAlert(e)) copy(alertOn = alertOn - e.id, alertOff = alertOff + e.id)
        else copy(alertOn = alertOn + e.id, alertOff = alertOff - e.id)

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

    /**
     * Filtre commun : à venir ou en cours, rediffusions/magazines selon réglages,
     * uniquement les chaînes cochées (si au moins une) et les sports cochés (si au moins un).
     * Une équipe ou compétition suivie reste visible même si son sport n'est pas coché.
     */
    fun visible(events: List<Event>, now: Long): List<Event> = events.filter { e ->
        e.stop > now &&
            (showMagazines || e.kind == Kind.EVENT) &&
            (!hideReplays || !e.replay) &&
            (!verifiedOnly || e.verified) &&
            (myChannels.isEmpty() || e.channels.any { it in myChannels }) &&
            (favSports.isEmpty() || isFavSport(e.sport) || isFavorite(e))
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("favSports", favSports)
            .putStringSet("favCompetitions", favCompetitions)
            .putStringSet("favTeams", favTeams)
            .putStringSet("myChannels", myChannels)
            .putBoolean("hideReplays", hideReplays)
            .putBoolean("showMagazines", showMagazines)
            .putBoolean("alertsEnabled", alertsEnabled)
            .putInt("alertMinutes", alertMinutes)
            .putStringSet("alertOn", alertOn)
            .putStringSet("alertOff", alertOff)
            .putBoolean("verifiedOnly", verifiedOnly)
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
                p.getBoolean("alertsEnabled", true), p.getInt("alertMinutes", 15),
                set("alertOn"), set("alertOff"), p.getBoolean("verifiedOnly", false),
            )
        }
    }
}
