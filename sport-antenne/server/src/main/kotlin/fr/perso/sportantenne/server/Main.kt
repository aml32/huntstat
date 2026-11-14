package fr.perso.sportantenne.server

import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.ChannelLogos
import fr.perso.sportantenne.core.Competitions
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.Fixture
import fr.perso.sportantenne.core.FixtureParsers
import fr.perso.sportantenne.core.Fixtures
import fr.perso.sportantenne.core.Guide
import fr.perso.sportantenne.core.Kind
import fr.perso.sportantenne.core.LiveCheck
import fr.perso.sportantenne.core.Schedule
import fr.perso.sportantenne.core.XmltvParser
import fr.perso.sportantenne.core.competitionKey
import fr.perso.sportantenne.core.norm
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Agrégateur « Sport Antenne » — l'équivalent du backend que l'appli de référence héberge sur son serveur.
 *
 * Il interroge une seule fois, de façon centralisée, toutes les sources publiques (guide TV, calendriers
 * sportifs), applique exactement la même logique de vérification que l'appli (module `core` partagé),
 * et écrit un fichier `feed.json` prêt à être servi par un CDN. Les téléphones ne téléchargent plus que
 * ce fichier : une seule requête légère, et les corrections se font côté serveur sans republier l'appli.
 *
 * Lancé par une tâche planifiée (GitHub Actions, toutes les heures).
 */

private const val GUIDE_URL = "https://xmltvfr.fr/xmltv/xmltv_fr.xml.gz"
private const val ESPN = "https://site.api.espn.com/apis/site/v2/sports"
private const val SPORTSDB = "https://www.thesportsdb.com/api/v1/json/123"
private const val DAY = 86_400_000L

private val RUGBY_LEAGUES = listOf(
    "270559", "270557", "180659", "271937", "272073", "289234", "242041", "244293", "267979", "270563", "164205", "289237",
)
private val SPORTSDB_LEAGUES = listOf(
    "rugby" to "4430", "rugby" to "5172", "rugby" to "5852", "rugby" to "4714", "rugby" to "4550", "rugby" to "4446", "rugby" to "4414",
    "handball" to "4536", "handball" to "4980", "handball" to "5275", "handball" to "5274",
    "basket" to "4423", "basket" to "4546", "basket" to "5623", "volley" to "4582", "hockey" to "4927", "biathlon" to "5342",
)

private val log = StringBuilder()
private fun log(msg: String) {
    println(msg)
    log.appendLine(msg)
}

fun main(args: Array<String>) {
    val out = File(args.getOrNull(0) ?: "feed")
    out.mkdirs()
    val now = System.currentTimeMillis()
    val t0 = System.currentTimeMillis()

    val guide = downloadGuide()
    log("Guide : ${guide.channels.size} chaînes, ${guide.broadcasts.size} diffusions")

    val fixtures = buildFixtures(now)
    log("Calendriers : ${fixtures.list.size} entrées — " + fixtures.list.groupingBy { it.source }.eachCount())

    val events = Schedule.build(guide.broadcasts, LiveCheck(fixtures))
    val future = events.filter { it.stop > now - DAY } // on garde la veille pour les scores finaux
    val shown = future.filter { it.kind == Kind.EVENT }
    log(
        "Événements : ${events.size} — gardés ${future.size}, " +
            "directs confirmés ${shown.count { it.verified && it.precise }}, " +
            "tournois ${shown.count { it.verified && !it.precise }}, " +
            "non confirmés ${shown.count { !it.verified && !it.replay }}, rediffusions ${shown.count { it.replay }}",
    )

    val usedChannels = future.flatMap { it.channels }.toSet()
    val feed = buildFeed(now, guide, usedChannels, future)
    val json = feed.toString()
    File(out, "feed.json").writeText(json)
    gzip(File(out, "feed.json"), File(out, "feed.json.gz"))
    File(out, "build-log.txt").writeText(log.toString())
    log("Écrit ${out.resolve("feed.json").path} — ${json.length / 1024} Ko (gz ${File(out, "feed.json.gz").length() / 1024} Ko) en ${(System.currentTimeMillis() - t0) / 1000}s")
}

// ───────────────────────────── Téléchargements ─────────────────────────────

private fun http(url: String): String {
    var last: Exception? = null
    repeat(2) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 45_000
        conn.setRequestProperty("User-Agent", "SportAntenne/2.1")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code == 200) return conn.inputStream.bufferedReader().use { it.readText() }
            last = IllegalStateException("HTTP $code")
            if (code == 429 || code == 503) Thread.sleep(2000)
        } catch (e: Exception) {
            last = e
        } finally {
            conn.disconnect()
        }
    }
    throw last ?: IllegalStateException("échec")
}

private fun downloadGuide(): Guide {
    val conn = URL(GUIDE_URL).openConnection() as HttpURLConnection
    conn.connectTimeout = 20_000
    conn.readTimeout = 90_000
    conn.setRequestProperty("User-Agent", "SportAntenne/2.1")
    try {
        check(conn.responseCode == 200) { "guide HTTP ${conn.responseCode}" }
        return GZIPInputStream(conn.inputStream, 1 shl 16).use { XmltvParser.parse(it) }
    } finally {
        conn.disconnect()
    }
}

private class Job(val source: String, val covered: List<String>, val url: String, val kind: String = "")

private fun buildFixtures(now: Long): Fixtures {
    fun fmt(p: String) = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
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
        jobs += Job("ufc", inMonth, "$ESPN/mma/ufc/scoreboard?dates=$m&limit=100&lang=en&region=us", kind = "ufc")
    }
    for (d in listOf(days[0], days[1], days[4], days[7])) {
        val date = fmt("yyyyMMdd").format(d)
        for (tour in listOf("atp", "wta")) jobs += Job("tennis", dayKeys, "$ESPN/tennis/$tour/scoreboard?dates=$date&lang=en&region=us", kind = tour)
        for (tour in listOf("pga", "eur", "lpga")) jobs += Job("golf", dayKeys, "$ESPN/golf/$tour/scoreboard?dates=$date&lang=en&region=us", kind = tour)
    }
    for (d in days) {
        val date = fmt("yyyyMMdd").format(d)
        jobs += Job("ncaab", listOf(Fixtures.dayOf(d)), "$ESPN/basketball/mens-college-basketball/scoreboard?dates=$date&groups=50&limit=400&lang=en&region=us")
        jobs += Job("ncaaf", listOf(Fixtures.dayOf(d)), "$ESPN/football/college-football/scoreboard?dates=$date&groups=80&limit=400&lang=en&region=us")
    }
    jobs += Job("f1", dayKeys, "https://api.jolpi.ca/ergast/f1/current.json", kind = "f1")

    val list = ArrayList<Fixture>()
    val covered = HashMap<String, MutableSet<String>>()
    val pool = Executors.newFixedThreadPool(4)
    try {
        val results = pool.invokeAll(
            jobs.map { job ->
                java.util.concurrent.Callable {
                    try {
                        val body = http(job.url)
                        job to when {
                            job.kind == "f1" -> FixtureParsers.formula1(body)
                            job.kind == "ufc" -> FixtureParsers.fightNights(body)
                            job.kind.isNotEmpty() -> FixtureParsers.tournaments(body, job.source, job.kind)
                            else -> FixtureParsers.espn(body, job.source)
                        }
                    } catch (e: Exception) {
                        log("  ESPN ${job.source} échec : ${e.message}")
                        null
                    }
                }
            },
            150, TimeUnit.SECONDS,
        )
        for (r in results) {
            val done = try { if (r.isCancelled) null else r.get() } catch (e: Exception) { null } ?: continue
            list += done.second
            covered.getOrPut(done.first.source) { HashSet() } += done.first.covered
        }
    } finally {
        pool.shutdownNow()
    }

    // Deuxième source (TheSportsDB) : séquentielle, car l'accès gratuit est limité en débit.
    for ((source, id) in SPORTSDB_LEAGUES) {
        val key = "tsdb:$id"
        try {
            Thread.sleep(2200)
            val next = JSONObject(http("$SPORTSDB/eventsnextleague.php?id=$id")).optJSONArray("events")?.optJSONObject(0) ?: continue
            val season = next.optString("strSeason")
            val round = next.optString("intRound").toIntOrNull() ?: continue
            val found = ArrayList<Fixture>()
            for (r in listOf(round, round + 1)) {
                Thread.sleep(2200)
                found += try { FixtureParsers.sportsDb(http("$SPORTSDB/eventsround.php?id=$id&r=$r&s=$season"), source, key) } catch (e: Exception) { emptyList() }
            }
            if (found.isEmpty()) continue
            list += found
            val last = minOf(found.maxOf { it.start }, now + 8 * DAY)
            val set = covered.getOrPut(key) { HashSet() }
            var d = now - DAY
            while (d <= last) { set += Fixtures.dayOf(d); d += DAY }
        } catch (e: Exception) {
            log("  TheSportsDB $id échec : ${e.message}")
        }
    }
    return Fixtures(list.distinct(), covered)
}

// ───────────────────────────── Fabrication du flux ─────────────────────────────

private fun buildFeed(now: Long, guide: Guide, usedChannels: Set<String>, events: List<Event>): JSONObject {
    val channelsById = guide.channels.associateBy { it.id }
    val channels = JSONObject()
    for (id in usedChannels) {
        val ch = channelsById[id] ?: Channel(id, id, "")
        channels.put(
            id,
            JSONObject().apply {
                put("n", ch.name)
                // Le meilleur logo est résolu côté serveur : le téléphone reçoit une adresse prête à l'emploi.
                ChannelLogos.candidates(ch, id).firstOrNull()?.let { put("l", it) }
            },
        )
    }

    val arr = JSONArray()
    for (e in events.sortedBy { it.start }) {
        val id = Competitions.idOf(e.sport, e.competition)
        val known = Competitions.byId(id)
        val o = JSONObject()
        o.put("s", e.sport)
        if (e.competition.isNotBlank()) o.put("c", e.competition)
        o.put("ci", id)
        known?.let { o.put("co", it.country) }
        if (!e.hasTeams && e.title.isNotBlank() && e.title != e.competition) o.put("t", e.title)
        if (e.home.isNotBlank()) o.put("h", e.home)
        if (e.away.isNotBlank()) o.put("a", e.away)
        if (e.detail.isNotBlank()) o.put("d", e.detail)
        o.put("st", e.start)
        o.put("sp", e.stop)
        o.put("ch", JSONArray(e.channels))
        if (e.verified) o.put("vf", true)
        if (!e.precise) o.put("np", true) // "non précis" : tournoi confirmé au jour, pas à l'heure
        if (e.replay) o.put("rp", true)
        if (e.kickoff > 0) o.put("ko", e.kickoff)
        if (e.homeLogo.isNotEmpty()) o.put("hl", e.homeLogo)
        if (e.awayLogo.isNotEmpty()) o.put("al", e.awayLogo)
        e.score?.let { o.put("sc", JSONArray(listOf(it.home, it.away, it.state, it.clock))) }
        if (!e.race.isEmpty) {
            o.put("rc", JSONArray(listOf(e.race.gender, e.race.format, e.race.distance, e.race.place)))
        }
        if (e.desc.isNotBlank()) o.put("de", e.desc)
        arr.put(o)
    }

    return JSONObject().apply {
        put("v", 1)
        put("updated", now)
        put("source", "Guide xmltvfr.fr + calendriers ESPN, Jolpica (F1), TheSportsDB")
        put("channels", channels)
        put("events", arr)
    }
}

private fun gzip(src: File, dst: File) {
    java.util.zip.GZIPOutputStream(dst.outputStream()).use { it.write(src.readBytes()) }
}
