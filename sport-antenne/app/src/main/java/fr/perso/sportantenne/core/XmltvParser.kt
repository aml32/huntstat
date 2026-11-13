package fr.perso.sportantenne.core

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Calendar
import java.util.TimeZone

class Guide(val channels: List<Channel>, val broadcasts: List<Broadcast>)

/**
 * Lecture en flux d'un guide XMLTV : seuls les programmes sportifs sont conservés
 * et convertis en diffusions structurées (sport, compétition, équipes).
 */
object XmltvParser {
    private val CHANNEL_ID = Regex("<channel id=\"([^\"]*)\"")
    private val DISPLAY_NAME = Regex("<display-name[^>]*>([^<]*)<")
    private val ICON = Regex("<icon src=\"([^\"]*)\"")
    private val ATTR_START = Regex("start=\"([^\"]*)\"")
    private val ATTR_STOP = Regex("stop=\"([^\"]*)\"")
    private val ATTR_CHANNEL = Regex("channel=\"([^\"]*)\"")
    private val TITLE = Regex("<title[^>]*>([^<]*)<")
    private val SUBTITLE = Regex("<sub-title[^>]*>([^<]*)<")
    private val DESC = Regex("<desc[^>]*>([^<]*)<")
    private val CATEGORY = Regex("<category[^>]*>([^<]*)<")
    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-z]+);")

    private val SPORTY = Regex("sport|multisports|omnisports")
    private val MAGAZINE_CAT = Regex("\\bmag\\b|magazine|doc|journal|fitness|poker|emissions auto")
    private val JUNK_TITLE = Regex(
        "^(programme (termine|a venir|indetermine)|pas de diffusion|fin des programmes)" +
            "|vivez en direct|a bientot sur|chaine evenementielle|le plus grand des spectacles" +
            "|ca se passe sur|regardez les meilleurs|^la premier league sur",
    )
    private val MAG_TITLE = Regex("\\b(le mag|l emission|le debrief|avant match|apres match|le journal|le jt|zap)\\b")
    private val ROUND = Regex(
        "^\\(?(\\d+(er|re|e|eme|nd|nde)?\\b|finale|demi|quart|huitieme|seizieme|barrage|phase|groupe|poule" +
            "|journee|match aller|match retour|la course|course|essais|qualif|sprint|etape|tour\\b|carte)",
    )
    private val MATCHDAY_TAIL = Regex("\\s*[-,]?\\s*\\(?\\d+\\s?(?:er|re|e|ème|eme)\\s+journée.*$", RegexOption.IGNORE_CASE)
    private val EPISODE = Regex("S\\d+E\\d+|[!?]|^Episode|^Saison", RegexOption.IGNORE_CASE)
    private val LEADING_COMPETITION = Regex("^([^:|/]{3,40}?) - ([^:|]{2,40} / [^:|]{2,40})$")
    private val SEGMENTS = Regex("\\s*\\.\\s+|\\s*\\.$|\\s+:\\s+|\\s+\\|\\s+")

    fun parse(input: InputStream, keepAfter: Long = 0L): Guide {
        val channels = ArrayList<Channel>()
        val broadcasts = ArrayList<Broadcast>()
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16)
        val block = StringBuilder()
        var inProgramme = false
        var inChannel = false
        while (true) {
            val line = reader.readLine() ?: break
            if (!inProgramme && !inChannel) {
                when {
                    line.contains("<programme ") -> inProgramme = true
                    line.contains("<channel ") -> inChannel = true
                    else -> continue
                }
                block.setLength(0)
            }
            block.append(line).append('\n')
            if (inProgramme && line.contains("</programme>")) {
                inProgramme = false
                parseProgramme(block, keepAfter)?.let { broadcasts += it }
            } else if (inChannel && line.contains("</channel>")) {
                inChannel = false
                val id = CHANNEL_ID.find(block)?.groupValues?.get(1) ?: continue
                channels += Channel(
                    id = unescape(id),
                    name = unescape(DISPLAY_NAME.find(block)?.groupValues?.get(1) ?: id).trim(),
                    logo = unescape(ICON.find(block)?.groupValues?.get(1) ?: ""),
                )
            }
        }
        return Guide(channels, broadcasts)
    }

    private fun parseProgramme(block: CharSequence, keepAfter: Long): Broadcast? {
        val cats = CATEGORY.findAll(block).map { unescape(it.groupValues[1]).trim() }.toList()
        if (cats.isEmpty()) return null
        val catNorms = cats.map { norm(it) }
        val catSport = cats.firstNotNullOfOrNull { Sports.detect(it) }
        if (catSport == null && catNorms.none { SPORTY.containsMatchIn(it) }) return null

        val stop = parseTime(ATTR_STOP.find(block)?.groupValues?.get(1)) ?: return null
        if (stop <= keepAfter) return null
        val start = parseTime(ATTR_START.find(block)?.groupValues?.get(1)) ?: return null
        val channelId = unescape(ATTR_CHANNEL.find(block)?.groupValues?.get(1) ?: return null)
        val title = clean(TITLE.find(block)?.groupValues?.get(1) ?: return null)
        val sub = clean(SUBTITLE.find(block)?.groupValues?.get(1) ?: "")
        val desc = clean(DESC.find(block)?.groupValues?.get(1) ?: "")
        if (title.isEmpty()) return null
        val titleNorm = norm(title)
        if (JUNK_TITLE.containsMatchIn(titleNorm)) return null

        val magazineCat = catNorms.any { MAGAZINE_CAT.containsMatchIn(it) }
        val parsed = classify(title, titleNorm, sub, catSport, magazineCat)
            ?: return magazine(start, stop, channelId, catSport, title, sub, desc, block)
        if (norm(parsed.competition) == norm(parsed.sport) && parsed.home.isEmpty() && parsed.detail.isEmpty()) {
            return null // simple case « Football » sans autre information : remplissage de grille
        }
        return Broadcast(
            start, stop, channelId, parsed.sport, parsed.competition, parsed.title,
            parsed.home, parsed.away, parsed.detail, desc, Kind.EVENT,
            block.contains("<previously-shown"),
        )
    }

    private fun magazine(
        start: Long, stop: Long, channelId: String, catSport: String?,
        title: String, sub: String, desc: String, block: CharSequence,
    ) = Broadcast(
        start, stop, channelId, catSport ?: "Magazines", "", title, "", "",
        sub, desc, Kind.MAGAZINE, block.contains("<previously-shown"),
    )

    private class Parsed(
        val sport: String, val competition: String, val title: String,
        val home: String, val away: String, val detail: String,
    )

    private fun classify(rawTitle: String, titleNorm: String, rawSub: String, catSport: String?, magazineCat: Boolean): Parsed? {
        if (MAG_TITLE.containsMatchIn(titleNorm)) return null
        // « Ligue 1 - Nice / Paris-SG » : la compétition précède l'affiche dans le titre.
        val lead = LEADING_COMPETITION.matchEntire(rawTitle)
        val title = lead?.groupValues?.get(2) ?: rawTitle
        val sub = if (lead != null) lead.groupValues[1] + (if (rawSub.isBlank()) "" else " - $rawSub") else rawSub

        // A/B — « Sport : Compétition | Affiche » ou « Sport : Compétition » + sous-titre
        val colon = title.indexOf(" : ")
        if (colon in 1..30) {
            val prefix = title.substring(0, colon)
            val prefixSport = Sports.detect(prefix)
            val sport = prefixSport ?: catSport?.takeIf { !magazineCat }
            if (sport != null) {
                // Préfixe inconnu mais catégorie précise : « Bundesliga : Les temps forts » → compétition = préfixe
                val body = if (prefixSport != null) title.substring(colon + 3) else prefix + " | " + title.substring(colon + 3)
                val rest = body.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }
                val competition = rest.firstOrNull() ?: return null
                var home = ""
                var away = ""
                val details = ArrayList<String>()
                for (part in rest.drop(1)) {
                    val t = if (home.isEmpty()) splitTeams(part, allowDash = false) else null
                    if (t != null) { home = t.first; away = t.second } else if (norm(part) != norm(competition)) details += part
                }
                for (seg in segments(sub)) {
                    val t = if (home.isEmpty()) splitTeams(seg, allowDash = true) else null
                    when {
                        t != null -> { home = t.first; away = t.second }
                        details.size < 2 && isNewInfo(seg, sport, competition, details) -> details += seg
                    }
                }
                return Parsed(sport, competition, competition, home, away, details.joinToString(" · "))
            }
        }

        // C/D — « Équipe A / Équipe B » + sous-titre « Compétition - 4e journée » ou « Sport. Compétition. »
        val segs = segments(sub)
        val subSport = segs.firstOrNull()?.takeIf { it.length <= 25 }?.let { Sports.detect(it) }
        val teams = splitTeams(title, allowDash = subSport != null || (catSport != null && !magazineCat && sub.isNotBlank() && title.length <= 45))
        if (teams != null && !magazineCatOnly(magazineCat, subSport)) {
            val sport = subSport ?: catSport ?: return null
            var competition = ""
            val details = ArrayList<String>()
            val infos = if (subSport != null) segs.drop(1) else sub.split(" - ").map { it.trim() }
            for (seg in infos) {
                if (seg.isEmpty()) continue
                if (competition.isEmpty()) {
                    val tail = MATCHDAY_TAIL.find(seg)
                    if (tail != null) {
                        competition = seg.substring(0, tail.range.first).trim()
                        details += tail.value.trim(' ', '-', ',', '.')
                    } else competition = seg
                } else if (details.size < 2) details += seg.trim('.', ' ')
            }
            if (competition.isEmpty()) competition = sport
            return Parsed(sport, competition, competition, teams.first, teams.second, details.joinToString(" · "))
        }

        // E — épreuve sans affiche, portée par une catégorie de sport précise (golf, tennis, patinage…)
        if (catSport != null && !magazineCat && sub.isNotEmpty()) {
            val titleIsRound = ROUND.containsMatchIn(titleNorm)
            val competition = (if (titleIsRound) sub else title).split(" | ").first().trim()
            val detail = if (titleIsRound) title else sub.takeIf { norm(it) != norm(catSport) && !norm(it).startsWith(norm(competition)) } ?: ""
            if (norm(competition) == norm(catSport)) return null
            return Parsed(catSport, competition, competition, "", "", detail.trim('.', ' '))
        }
        return null
    }

    private fun magazineCatOnly(magazineCat: Boolean, subSport: String?) = magazineCat && subSport == null

    private fun isNewInfo(seg: String, sport: String, competition: String, seen: List<String>): Boolean {
        val n = norm(seg)
        if (n.length < 3 || n == norm(sport) || n == norm(competition)) return false
        if (n.endsWith("...") || seg.endsWith("…")) return false
        return seen.none { norm(it) == n } && ROUND.containsMatchIn(n)
    }

    private fun segments(s: String): List<String> =
        if (s.isBlank()) emptyList() else SEGMENTS.split(s).map { it.trim() }.filter { it.isNotEmpty() }

    /** Découpe « A / B » (ou « A - B » si autorisé) en deux adversaires plausibles. */
    private fun splitTeams(s: String, allowDash: Boolean): Pair<String, String>? {
        val sep = when {
            s.contains(" / ") -> " / "
            allowDash && s.contains(" - ") -> " - "
            else -> return null
        }
        val parts = s.split(sep)
        if (parts.size != 2) return null
        val a = parts[0].substringAfterLast(" : ").trim()
        val b = parts[1].trim().trimEnd('.')
        if (a.length !in 2..40 || b.length !in 2..40) return null
        if (!a.first().isUpperCase() && !a.first().isDigit()) return null
        if (!b.first().isUpperCase() && !b.first().isDigit()) return null
        if (EPISODE.containsMatchIn(a) || EPISODE.containsMatchIn(b)) return null
        if (sep == " - " && (ROUND.containsMatchIn(norm(b)) || ROUND.containsMatchIn(norm(a)))) return null
        return a to b
    }

    private fun clean(s: String) = unescape(s).replace(' ', ' ').replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    private fun unescape(s: String): String {
        if (s.indexOf('&') < 0) return s
        return ENTITY.replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
                else -> if (e.startsWith("#")) {
                    val code = if (e[1] == 'x' || e[1] == 'X') e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
                    code?.let { String(Character.toChars(it)) } ?: m.value
                } else m.value
            }
        }
    }

    /** « 20261112060000 +0100 » → millisecondes epoch. */
    internal fun parseTime(s: String?): Long? {
        if (s == null || s.length < 14) return null
        val d = s.substring(0, 14)
        if (!d.all(Char::isDigit)) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(
            d.substring(0, 4).toInt(), d.substring(4, 6).toInt() - 1, d.substring(6, 8).toInt(),
            d.substring(8, 10).toInt(), d.substring(10, 12).toInt(), d.substring(12, 14).toInt(),
        )
        var offsetMs = 0L
        val tz = s.substring(14).trim()
        if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
            val h = tz.substring(1, 3).toIntOrNull() ?: 0
            val m = tz.substring(3, 5).toIntOrNull() ?: 0
            offsetMs = (h * 60 + m) * 60_000L * (if (tz[0] == '-') -1 else 1)
        }
        return cal.timeInMillis - offsetMs
    }
}
