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
import fr.perso.sportantenne.core.Competitions
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
class Data(
    val events: List<Event>, val channels: Map<String, Channel>, val catalogue: Catalogue, val updated: Long,
    /** Nombre d'entrées chargées par calendrier (diagnostic affiché dans les réglages). */
    val calendars: Map<String, Int> = emptyMap(),
)

/** Téléchargement du guide, cache local et mise à jour en arrière-plan. */
object Repo {
    const val GUIDE_URL = "https://xmltvfr.fr/xmltv/xmltv_fr.xml.gz"
    const val STALE_MS = 60 * 60 * 1000L

    /**
     * Mise à jour complète : calendriers réels (reports, annulations, changements d'horaire),
     * puis guide TV (changements de chaîne). Le guide n'est retéléchargé que s'il a changé.
     */
    suspend fun refresh(ctx: Context): Data = withContext(Dispatchers.IO) {
        // En cas d'échec, les calendriers déjà en cache restent utilisés.
        try {
            val now = System.currentTimeMillis()
            val fresh = downloadFixtures(now)
            if (fresh.list.isNotEmpty()) Fixtures.load(ctx.filesDir).mergedWith(fresh, now - 2 * DAY).save(ctx.filesDir)
        } catch (e: Exception) {
            // rien : la vérification retombe sur le cache
        }
        val prefs = ctx.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        var failure: Exception? = null
        repeat(3) { attempt ->
            try {
                val cached = GuideStore.load(ctx.filesDir)
                val download = download(if (cached != null) prefs.getString("etag", null) else null)
                val guide = if (download == null) cached!! else {
                    GuideStore.save(ctx.filesDir, download.first)
                    prefs.edit().putString("etag", download.second).apply()
                    download.first
                }
                prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
                return@withContext publish(ctx, guide)
            } catch (e: Exception) {
                failure = e
                delay(2_000L * (attempt + 1))
            }
        }
        throw failure ?: IllegalStateException("Téléchargement impossible")
    }

    /** Mise à jour des scores pendant les matchs ; les tests la coupent pour ne pas dépendre du réseau. */
    @Volatile var liveUpdates = true

    /**
     * Rafraîchissement léger pendant les matchs : seuls les calendriers des sources concernées
     * sont relus (aujourd'hui et la veille en heure universelle), le guide TV n'est pas retéléchargé.
     * Renvoie null si rien n'a pu être lu.
     */
    suspend fun refreshLive(ctx: Context, sources: Set<String>): Data? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext null
        val now = System.currentTimeMillis()
        val fresh = try { downloadFixtures(now, sources) } catch (e: Exception) { return@withContext null }
        if (fresh.list.isEmpty()) return@withContext null
        Fixtures.load(ctx.filesDir).mergedWith(fresh, now - 2 * DAY).save(ctx.filesDir)
        GuideStore.load(ctx.filesDir)?.let { publish(ctx, it) }
    }

    /** Heure de la dernière vérification réussie auprès des sources. */
    fun lastCheck(ctx: Context): Long =
        ctx.getSharedPreferences(STATE, Context.MODE_PRIVATE).getLong("checked", 0L)

    private const val STATE = "refresh"
    private const val DAY = 86_400_000L
    private const val ESPN = "https://site.api.espn.com/apis/site/v2/sports"
    private val RUGBY_LEAGUES = listOf("270559", "270557", "180659", "271937", "272073", "289234", "242041", "244293", "267979", "270563", "164205", "289237")

    private fun get(url: String): String {
        var last: Exception? = null
        repeat(2) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "SportAntenne/1.2 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            try {
                val code = conn.responseCode
                if (code == 200) return conn.inputStream.bufferedReader().use { it.readText() }
                last = IllegalStateException("réponse HTTP $code")
                // Trop de requêtes : on laisse souffler le serveur avant le second essai.
                if (code == 429 || code == 503) Thread.sleep(1500)
            } catch (e: Exception) {
                last = e
            } finally {
                conn.disconnect()
            }
        }
        throw last ?: IllegalStateException("échec")
    }

    /**
     * Télécharge les calendriers ; chaque source qui échoue est simplement ignorée.
     * Sans [live] : toutes les sources, de la veille à J+7. Avec : seulement ces sources, veille et jour même.
     */
    internal fun downloadFixtures(now: Long, live: Set<String>? = null): Fixtures {
        fun fmt(p: String) = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        if (live != null) return downloadLive(now, live, fmt("yyyyMMdd"))
        val days = (-1..7).map { now + it * DAY }
        val dayKeys = days.map { Fixtures.dayOf(it) }
        val months = days.map { fmt("yyyyMM").format(it) }.distinct()

        val jobs = ArrayList<Job>()
        days.forEach { d -> jobs += Job("football", listOf(Fixtures.dayOf(d)), "$ESPN/soccer/all/scoreboard?dates=${fmt("yyyyMMdd").format(d)}&limit=900&lang=en&region=us") }
        for (m in months) {
            val inMonth = dayKeys.filter { it.replace("-", "").startsWith(m) }
            jobs += Job("nba", inMonth, "$ESPN/basketball/nba/scoreboard?dates=$m&limit=1000&lang=en&region=us")
            jobs += Job("nfl", inMonth, "$ESPN/football/nfl/scoreboard?dates=$m&limit=1000&lang=en&region=us")
            jobs += Job("nhl", inMonth, "$ESPN/hockey/nhl/scoreboard?dates=$m&limit=1000&lang=en&region=us")
            RUGBY_LEAGUES.forEach { jobs += Job("rugby", inMonth, "$ESPN/rugby/$it/scoreboard?dates=$m&limit=1000&lang=en&region=us") }
        }
        jobs += Job("f1", dayKeys, "https://api.jolpi.ca/ergast/f1/current.json", f1 = true)
        for (m in months) jobs += Job("ufc", dayKeys.filter { it.replace("-", "").startsWith(m) }, "$ESPN/mma/ufc/scoreboard?dates=$m&limit=100&lang=en&region=us", kind = "ufc")
        // Tournois de tennis et de golf : un relevé tous les trois jours suffit, ils durent une semaine.
        for (d in listOf(days[0], days[1], days[4], days[7])) {
            val date = fmt("yyyyMMdd").format(d)
            for (tour in listOf("atp", "wta")) jobs += Job("tennis", dayKeys, "$ESPN/tennis/$tour/scoreboard?dates=$date&lang=en&region=us", kind = tour)
            for (tour in listOf("pga", "eur", "lpga")) jobs += Job("golf", dayKeys, "$ESPN/golf/$tour/scoreboard?dates=$date&lang=en&region=us", kind = tour)
        }
        // Sport universitaire américain : confirmation des directs uniquement.
        for (d in days) {
            val date = fmt("yyyyMMdd").format(d)
            jobs += Job("ncaab", listOf(Fixtures.dayOf(d)), "$ESPN/basketball/mens-college-basketball/scoreboard?dates=$date&groups=50&limit=400&lang=en&region=us")
            jobs += Job("ncaaf", listOf(Fixtures.dayOf(d)), "$ESPN/football/college-football/scoreboard?dates=$date&groups=80&limit=400&lang=en&region=us")
        }

        return run(jobs)
    }

    private class Job(val source: String, val covered: List<String>, val url: String, val f1: Boolean = false, val kind: String = "")

    private fun downloadLive(now: Long, sources: Set<String>, day: SimpleDateFormat): Fixtures {
        val jobs = ArrayList<Job>()
        for (d in listOf(now - DAY, now)) {
            val key = listOf(Fixtures.dayOf(d))
            val date = day.format(d)
            if ("football" in sources) jobs += Job("football", key, "$ESPN/soccer/all/scoreboard?dates=$date&limit=900&lang=en&region=us")
            if ("nba" in sources) jobs += Job("nba", key, "$ESPN/basketball/nba/scoreboard?dates=$date&limit=200&lang=en&region=us")
            if ("nfl" in sources) jobs += Job("nfl", key, "$ESPN/football/nfl/scoreboard?dates=$date&limit=200&lang=en&region=us")
            if ("nhl" in sources) jobs += Job("nhl", key, "$ESPN/hockey/nhl/scoreboard?dates=$date&limit=200&lang=en&region=us")
            if ("rugby" in sources) RUGBY_LEAGUES.forEach { jobs += Job("rugby", key, "$ESPN/rugby/$it/scoreboard?dates=$date&limit=200&lang=en&region=us") }
        }
        return run(jobs)
    }

    /** Dernier échec de lecture par calendrier (vide quand tout va bien). */
    val failures = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun run(jobs: List<Job>): Fixtures {
        jobs.map { it.source }.distinct().forEach { failures.remove(it) }
        // Trois lectures à la fois : assez rapide, sans rafale qui ferait refuser les requêtes.
        val pool = Executors.newFixedThreadPool(3)
        try {
            val results = pool.invokeAll(jobs.map { job ->
                Callable<Pair<Job, List<Fixture>>?> {
                    try {
                        val body = get(job.url)
                        job to when {
                            job.f1 -> FixtureParsers.formula1(body)
                            job.kind == "ufc" -> FixtureParsers.fightNights(body)
                            job.kind.isNotEmpty() -> FixtureParsers.tournaments(body, job.source, job.kind)
                            else -> FixtureParsers.espn(body, job.source)
                        }
                    } catch (e: Exception) {
                        // Gardé pour le diagnostic : quel calendrier a échoué, et pourquoi.
                        failures[job.source] = (e.message ?: e.javaClass.simpleName).take(80)
                        null
                    }
                }
            }, 120, TimeUnit.SECONDS)
            val list = ArrayList<Fixture>()
            val covered = HashMap<String, MutableSet<String>>()
            for (r in results) {
                val done = try { if (r.isCancelled) null else r.get() } catch (e: Exception) { null }
                if (done == null) continue
                val (job, fixtures) = done
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

    /**
     * Rapport lisible de l'état de l'appli sur ce téléphone : version, calendriers chargés ou en échec,
     * et ce que la vérification a conclu. Fait pour être copié et envoyé tel quel.
     */
    fun diagnostic(ctx: Context, events: List<Event>, settings: Settings): String {
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRENCH)
        val fixtures = Fixtures.load(ctx.filesDir)
        val counts = fixtures.list.groupingBy { it.source }.eachCount()
        val today = Fixtures.dayOf(now)
        val upcoming = events.filter { it.kind == Kind.EVENT && it.stop > now && it.start < now + DAY }
        val sb = StringBuilder()
        sb.appendLine("Sport Antenne ${BuildConfig.VERSION_NAME} — ${fmt.format(now)} — Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MODEL})")
        sb.appendLine("Guide : ${events.size} diffusions, vérifié le ${if (lastCheck(ctx) > 0) fmt.format(lastCheck(ctx)) else "jamais"}")
        sb.appendLine("Calendriers :")
        for (source in listOf("football", "rugby", "nba", "nfl", "nhl", "ncaab", "ncaaf", "f1", "tennis", "golf", "ufc")) {
            val covered = fixtures.coveredDays[source]
            sb.appendLine("  $source : ${counts[source] ?: 0} entrées, ${covered?.size ?: 0} jours couverts, aujourd'hui ${if (covered?.contains(today) == true) "oui" else "NON"}" +
                (failures[source]?.let { " — ÉCHEC : $it" } ?: ""))
        }
        sb.appendLine("Prochaines 24 h : ${upcoming.size} diffusions — ${upcoming.count { it.verified && it.precise }} directs confirmés, " +
            "${upcoming.count { it.verified && !it.precise }} tournois confirmés, ${upcoming.count { !it.verified && !it.replay }} non confirmés, ${upcoming.count { it.replay }} rediffusions")
        sb.appendLine("Par sport (confirmés/non confirmés/rediffusions) : " + upcoming.groupBy { it.sport }.entries.sortedByDescending { it.value.size }.take(12)
            .joinToString { "${it.key} ${it.value.count { e -> e.verified }}/${it.value.count { e -> !e.verified && !e.replay }}/${it.value.count { e -> e.replay }}" })
        sb.appendLine("Football, 6 premiers : " + upcoming.filter { it.sport == "Football" && it.hasTeams }.take(6).joinToString(" ; ") {
            "${it.headline} [${it.competition}] " + (if (it.verified) "confirmé" else if (it.replay) "rediffusion" else "non confirmé")
        })
        sb.appendLine("Réglages : sports=${settings.favSports.size} compétitions=${settings.favCompetitions.size} chaînes=${settings.myChannels.size} " +
            "confirmésSeulement=${settings.verifiedOnly} masquerRediffs=${settings.hideReplays} garder=${settings.keepFinishedMin}")
        sb.appendLine("Affichées dans les 24 h : ${settings.visible(upcoming, now).size} ; masquées : ${settings.hiddenCounts(upcoming, now)}")
        return sb.toString()
    }

    fun calendarCounts(ctx: Context): Map<String, Int> = Fixtures.load(ctx.filesDir).list.groupingBy { it.source }.eachCount()

    /** Renvoie le guide et son empreinte, ou null si le serveur indique qu'il n'a pas changé. */
    private fun download(etag: String?): Pair<Guide, String?>? {
        val conn = URL(GUIDE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "SportAntenne/1.3 (Android)")
        if (etag != null) conn.setRequestProperty("If-None-Match", etag)
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return null
            if (code != 200) error("le guide TV a répondu $code")
            // Le passé récent est conservé : il sert à reconnaître les rediffusions.
            val guide = GZIPInputStream(conn.inputStream, 1 shl 16).use { XmltvParser.parse(it) }
            // Un guide tronqué ou vide ne doit jamais écraser un bon cache.
            if (guide.broadcasts.count { it.kind == Kind.EVENT } < 50 || guide.channels.size < 20) error("guide TV incomplet")
            return guide to conn.getHeaderField("ETag")
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
        return Data(events, channels, catalogue, lastCheck(ctx).takeIf { it > 0 } ?: GuideStore.lastUpdate(ctx.filesDir), calendarCounts(ctx))
    }

    fun scheduleBackgroundRefresh(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
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
 * Réglages persistants. Les favoris sont des sports, des compétitions et des chaînes :
 * ils filtrent le programme.
 *  - aucun sport coché : tous les sports ; sinon uniquement les sports cochés ;
 *  - dans un sport, aucune compétition cochée : tout le sport ; sinon uniquement les compétitions cochées ;
 *  - aucune chaîne cochée : toutes les chaînes ; sinon uniquement les chaînes cochées.
 * Une compétition est stockée sous la forme « Sport|identifiant » (voir [Competitions.idOf]).
 */
data class Settings(
    val favSports: Set<String> = emptySet(),
    val favCompetitions: Set<String> = emptySet(),
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
    /** Durée de conservation des matchs terminés, en minutes ; [KEEP_ALL] = tant que le guide les contient. */
    val keepFinishedMin: Int = 60,
) {
    private val sportKeys by lazy { favSports.map { norm(it) }.toSet() }
    private val competitionsBySport: Map<String, Set<String>> by lazy {
        favCompetitions.groupBy({ norm(it.substringBefore('|')) }, { it.substringAfter('|') }).mapValues { it.value.toSet() }
    }

    /** Au moins une compétition cochée : elles pilotent les alertes. */
    val hasFavorites get() = favCompetitions.isNotEmpty()

    fun isFavSport(sport: String) = norm(sport) in sportKeys
    fun isFavCompetition(sport: String, id: String) = competitionsBySport[norm(sport)]?.contains(id) == true
    fun competitionCount(sport: String) = competitionsBySport[norm(sport)]?.size ?: 0

    /** La compétition de cette diffusion a été cochée explicitement. */
    fun isFavorite(e: Event): Boolean = isFavCompetition(e.sport, Competitions.idOf(e.sport, e.competition))

    fun hasAlert(e: Event): Boolean =
        e.id in alertOn || (alertsEnabled && e.id !in alertOff && !e.replay && isFavorite(e))

    fun toggleAlert(e: Event): Settings =
        if (hasAlert(e)) copy(alertOn = alertOn - e.id, alertOff = alertOff + e.id)
        else copy(alertOn = alertOn + e.id, alertOff = alertOff - e.id)

    /** Décocher un sport retire aussi ses compétitions. */
    fun toggleSport(sport: String): Settings =
        if (isFavSport(sport)) copy(
            favSports = favSports.filter { norm(it) != norm(sport) }.toSet(),
            favCompetitions = favCompetitions.filter { norm(it.substringBefore('|')) != norm(sport) }.toSet(),
        ) else copy(favSports = favSports + sport)

    /** Cocher une compétition coche aussi son sport. */
    fun toggleCompetition(sport: String, id: String): Settings =
        if (isFavCompetition(sport, id)) copy(
            favCompetitions = favCompetitions.filter { !(norm(it.substringBefore('|')) == norm(sport) && it.substringAfter('|') == id) }.toSet(),
        ) else copy(
            favCompetitions = favCompetitions + "$sport|$id",
            favSports = if (isFavSport(sport)) favSports else favSports + sport,
        )

    fun toggleChannel(id: String) = copy(myChannels = if (id in myChannels) myChannels - id else myChannels + id)

    private fun matchesFavorites(e: Event): Boolean {
        if (favSports.isNotEmpty() && !isFavSport(e.sport)) return false
        val chosen = competitionsBySport[norm(e.sport)]
        return chosen.isNullOrEmpty() || Competitions.idOf(e.sport, e.competition) in chosen
    }

    /**
     * Raison pour laquelle une diffusion n'est pas affichée, ou null si elle l'est.
     * L'ordre est celui des filtres : durée de conservation, magazines, rediffusions,
     * directs confirmés, chaînes, sports, compétitions.
     */
    fun hiddenReason(e: Event, now: Long): String? = when {
        keepFinishedMin != KEEP_ALL && e.stop <= now - keepFinishedMin * 60_000L -> HIDDEN_FINISHED
        !showMagazines && e.kind != Kind.EVENT -> HIDDEN_MAGAZINE
        hideReplays && e.replay -> HIDDEN_REPLAY
        verifiedOnly && !e.verified -> HIDDEN_UNVERIFIED
        myChannels.isNotEmpty() && e.channels.none { it in myChannels } -> HIDDEN_CHANNEL
        favSports.isNotEmpty() && !isFavSport(e.sport) -> HIDDEN_SPORT
        !matchesFavorites(e) -> HIDDEN_COMPETITION
        else -> null
    }

    /** Filtre commun : à venir ou en cours, directs, puis favoris (sports, compétitions, chaînes). */
    fun visible(events: List<Event>, now: Long): List<Event> = events.filter { hiddenReason(it, now) == null }

    /** Décompte des diffusions masquées, par raison, dans l'ordre des filtres. */
    fun hiddenCounts(events: List<Event>, now: Long): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (reason in HIDDEN_ORDER) out[reason] = 0
        for (e in events) hiddenReason(e, now)?.let { out[it] = (out[it] ?: 0) + 1 }
        return out.filterValues { it > 0 }
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("favSports", favSports)
            .putStringSet("favCompetitions", favCompetitions)
            .putStringSet("myChannels", myChannels)
            .putBoolean("hideReplays", hideReplays)
            .putBoolean("showMagazines", showMagazines)
            .putBoolean("alertsEnabled", alertsEnabled)
            .putInt("alertMinutes", alertMinutes)
            .putStringSet("alertOn", alertOn)
            .putStringSet("alertOff", alertOff)
            .putBoolean("verifiedOnly", verifiedOnly)
            .putInt("keepFinishedMin", keepFinishedMin)
            .apply()
    }

    companion object {
        private const val PREFS = "settings"
        const val KEEP_ALL = -1
        const val HIDDEN_FINISHED = "terminées depuis plus longtemps que votre réglage « Matchs terminés »"
        const val HIDDEN_MAGAZINE = "magazines et émissions (réglage « Afficher les magazines »)"
        const val HIDDEN_REPLAY = "rediffusions (réglage « Masquer les rediffusions »)"
        const val HIDDEN_UNVERIFIED = "non confirmées (réglage « Directs confirmés uniquement »)"
        const val HIDDEN_CHANNEL = "sur des chaînes que vous n'avez pas cochées"
        const val HIDDEN_SPORT = "dans des sports que vous n'avez pas cochés"
        const val HIDDEN_COMPETITION = "dans des compétitions que vous n'avez pas cochées"
        val HIDDEN_ORDER = listOf(HIDDEN_CHANNEL, HIDDEN_COMPETITION, HIDDEN_UNVERIFIED, HIDDEN_SPORT, HIDDEN_REPLAY, HIDDEN_MAGAZINE, HIDDEN_FINISHED)

        fun load(ctx: Context): Settings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun set(k: String) = p.getStringSet(k, emptySet())?.toSet() ?: emptySet()
            // Les favoris d'anciennes versions (noms libres, équipes) ne sont plus reconnus : on les écarte.
            val competitions = set("favCompetitions").filter { it.substringAfter('|').let { id -> id.startsWith("c:") || id.startsWith("dyn:") } }.toSet()
            return Settings(
                set("favSports"), competitions, set("myChannels"),
                p.getBoolean("hideReplays", true), p.getBoolean("showMagazines", false),
                p.getBoolean("alertsEnabled", true), p.getInt("alertMinutes", 15),
                set("alertOn"), set("alertOff"), p.getBoolean("verifiedOnly", false), p.getInt("keepFinishedMin", 60),
            )
        }
    }
}
