package fr.perso.sportantenne.core

import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Un match réellement programmé, d'après un calendrier sportif indépendant du guide TV. */
data class Fixture(
    /** Clé de couverture : football, nba, nfl, nhl, rugby, f1. */
    val source: String,
    val start: Long,
    val home: String,
    val away: String,
    val league: String,
    /** Adresses des logos des deux équipes, quand le calendrier les fournit. */
    val homeLogo: String = "",
    val awayLogo: String = "",
    /** Score et état du match : « pre » (à venir), « in » (en cours), « post » (terminé). */
    val homeScore: String = "",
    val awayScore: String = "",
    val state: String = "",
    /** Repère de jeu donné par le calendrier : « 67' », « HT », « FT », « Q3 4:12 »… */
    val clock: String = "",
    /** Fin de l'épreuve pour un tournoi ou une soirée (0 pour un match). */
    val end: Long = 0L,
)

/** Les calendriers téléchargés et les jours qu'ils couvrent réellement (source → jours AAAA-MM-JJ, UTC). */
class Fixtures(val list: List<Fixture>, val coveredDays: Map<String, Set<String>>) {
    fun save(dir: File) {
        val tmp = File(dir, "fixtures.tsv.tmp")
        tmp.bufferedWriter().use { w ->
            coveredDays.forEach { (source, days) -> days.forEach { w.write("D\t$source\t$it\n") } }
            list.forEach { f ->
                w.write(listOf("F", f.source, f.start, clean(f.home), clean(f.away), clean(f.league), clean(f.homeLogo), clean(f.awayLogo), clean(f.homeScore), clean(f.awayScore), clean(f.state), clean(f.clock), f.end).joinToString("\t"))
                w.write("\n")
            }
        }
        val target = File(dir, "fixtures.tsv")
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
    }

    /** Fusionne avec un téléchargement plus récent : les jours re-téléchargés remplacent les anciens. */
    fun mergedWith(fresh: Fixtures, keepAfter: Long): Fixtures {
        val kept = list.filter { f -> f.start >= keepAfter && fresh.coveredDays[f.source]?.contains(dayOf(f.start)) != true }
        val days = HashMap<String, Set<String>>()
        (coveredDays.keys + fresh.coveredDays.keys).forEach { s ->
            days[s] = (coveredDays[s] ?: emptySet()).filter { it >= dayOf(keepAfter) }.toSet() + (fresh.coveredDays[s] ?: emptySet())
        }
        return Fixtures(kept + fresh.list, days)
    }

    companion object {
        val EMPTY = Fixtures(emptyList(), emptyMap())
        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        fun dayOf(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(t)

        fun load(dir: File): Fixtures {
            val f = File(dir, "fixtures.tsv")
            if (!f.exists()) return EMPTY
            val list = ArrayList<Fixture>()
            val days = HashMap<String, MutableSet<String>>()
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p[0] == "D" && p.size >= 3) days.getOrPut(p[1]) { HashSet() } += p[2]
                else if (p[0] == "F" && p.size >= 6) p[2].toLongOrNull()?.let { list += Fixture(p[1], it, p[3], p[4], p[5], p.getOrElse(6) { "" }, p.getOrElse(7) { "" }, p.getOrElse(8) { "" }, p.getOrElse(9) { "" }, p.getOrElse(10) { "" }, p.getOrElse(11) { "" }, p.getOrNull(12)?.toLongOrNull() ?: 0L) }
            }
            return Fixtures(list, days)
        }
    }
}

/** Lecture des réponses des calendriers publics (ESPN pour les matchs, Jolpica/Ergast pour la F1). */
object FixtureParsers {
    private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun isoTime(s: String): Long? = try {
        val t = s.trim().removeSuffix("Z")
        (if (t.length <= 16) utc("yyyy-MM-dd'T'HH:mm") else utc("yyyy-MM-dd'T'HH:mm:ss")).parse(t)?.time
    } catch (e: Exception) { null }

    /** Tableau des scores ESPN : un événement = un match avec ses deux équipes et son heure de coup d'envoi. */
    fun espn(json: String, source: String): List<Fixture> {
        val out = ArrayList<Fixture>()
        val events = JSONObject(json).optJSONArray("events") ?: return out
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val status = comp.optJSONObject("status")?.optJSONObject("type")?.optString("name") ?: ""
            if (status.contains("POSTPONED") || status.contains("CANCEL") || status.contains("ABANDON")) continue
            val start = isoTime(comp.optString("date", e.optString("date"))) ?: continue
            val teams = comp.optJSONArray("competitors") ?: continue
            if (teams.length() != 2) continue
            var home = ""
            var away = ""
            var homeLogo = ""
            var awayLogo = ""
            var homeScore = ""
            var awayScore = ""
            val type = comp.optJSONObject("status")?.optJSONObject("type")
            val state = type?.optString("state") ?: ""
            val clock = if (state == "pre") "" else type?.optString("shortDetail") ?: ""
            for (k in 0 until 2) {
                val c = teams.optJSONObject(k) ?: continue
                val team = c.optJSONObject("team")
                val name = team?.optString("displayName") ?: ""
                val logo = team?.optString("logo")?.takeIf { it.startsWith("https://") } ?: ""
                // Avant le coup d'envoi le calendrier renvoie « 0 » : on n'affiche un score qu'une fois le match lancé.
                val score = if (state == "pre") "" else c.optString("score", "")
                if (c.optString("homeAway") == "away" || home.isNotEmpty()) { away = name; awayLogo = logo; awayScore = score } else { home = name; homeLogo = logo; homeScore = score }
            }
            if (home.isBlank() || away.isBlank()) continue
            out += Fixture(source, start, home, away, comp.optString("altGameNote", ""), homeLogo, awayLogo, homeScore, awayScore, state, clock)
        }
        return out
    }

    /** Tournois de tennis ou de golf : un événement = un tournoi avec ses dates de début et de fin. */
    fun tournaments(json: String, source: String, tour: String): List<Fixture> {
        val out = ArrayList<Fixture>()
        val events = JSONObject(json).optJSONArray("events") ?: return out
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val name = e.optString("name")
            val start = isoTime(e.optString("date")) ?: continue
            val end = isoTime(e.optString("endDate")) ?: (start + 4 * 86_400_000L)
            if (name.isNotBlank()) out += Fixture(source, start, name, "", tour, end = end)
        }
        return out
    }

    /** Soirées UFC : l'heure donnée est celle du début des combats préliminaires. */
    fun fightNights(json: String): List<Fixture> {
        val out = ArrayList<Fixture>()
        val events = JSONObject(json).optJSONArray("events") ?: return out
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val start = isoTime(e.optString("date")) ?: continue
            out += Fixture("ufc", start, e.optString("name"), "", "ufc", end = start + 8 * 3_600_000L)
        }
        return out
    }

    /** Calendrier F1 de la saison : chaque séance (essais, qualifications, sprint, course) devient une entrée. */
    fun formula1(json: String): List<Fixture> {
        val out = ArrayList<Fixture>()
        val races = JSONObject(json).optJSONObject("MRData")?.optJSONObject("RaceTable")?.optJSONArray("Races") ?: return out
        for (i in 0 until races.length()) {
            val r = races.optJSONObject(i) ?: continue
            val name = r.optString("raceName")
            fun add(o: JSONObject?, label: String) {
                val date = o?.optString("date") ?: return
                val time = o.optString("time")
                if (date.isEmpty() || time.isEmpty()) return
                isoTime("${date}T$time")?.let { out += Fixture("f1", it, label, "", name) }
            }
            add(r, "Race")
            add(r.optJSONObject("FirstPractice"), "Practice 1")
            add(r.optJSONObject("SecondPractice"), "Practice 2")
            add(r.optJSONObject("ThirdPractice"), "Practice 3")
            add(r.optJSONObject("Qualifying"), "Qualifying")
            add(r.optJSONObject("Sprint"), "Sprint")
            add(r.optJSONObject("SprintQualifying"), "Sprint Qualifying")
            add(r.optJSONObject("SprintShootout"), "Sprint Qualifying")
        }
        return out
    }
}
