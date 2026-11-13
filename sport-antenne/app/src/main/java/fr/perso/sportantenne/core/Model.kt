package fr.perso.sportantenne.core

import java.text.Normalizer

enum class Kind { EVENT, MAGAZINE }

data class Channel(val id: String, val name: String, val logo: String)

/** Une diffusion brute, telle que lue dans le guide TV. */
data class Broadcast(
    val start: Long,
    val stop: Long,
    val channelId: String,
    val sport: String,
    val competition: String,
    val title: String,
    val home: String,
    val away: String,
    val detail: String,
    val desc: String,
    val kind: Kind,
    val previouslyShown: Boolean,
)

/** Un événement affiché : une ou plusieurs diffusions simultanées regroupées. */
data class Event(
    val key: String,
    val start: Long,
    val stop: Long,
    val sport: String,
    val competition: String,
    val title: String,
    val home: String,
    val away: String,
    val detail: String,
    val desc: String,
    val kind: Kind,
    val replay: Boolean,
    val channels: List<String>,
    /** Direct confirmé par un calendrier sportif indépendant du guide TV. */
    val verified: Boolean = false,
) {
    val hasTeams get() = home.isNotBlank() && away.isNotBlank()
    val headline get() = if (hasTeams) "$home – $away" else title
    /** Identifiant stable d'une diffusion précise (alertes). */
    val id get() = "$key@$start"
}

private val DIACRITICS = Regex("\\p{Mn}+")
private val NON_ALNUM = Regex("[^a-z0-9]+")

/** Clé de comparaison : minuscules, sans accents ni ponctuation. */
fun norm(s: String): String =
    NON_ALNUM.replace(
        DIACRITICS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), ""),
        " ",
    ).trim()

private val SPONSORS = Regex("\\b(mcdonald s|bkt|uber eats|betclic|daikin|conforama|butagaz|lidl|uefa|de l uefa)\\b")

/** Clé d'une compétition, indépendante du sponsor et de la casse. */
fun competitionKey(sport: String, competition: String): String =
    norm(sport) + "|" + SPONSORS.replace(norm(competition), " ").replace(Regex(" +"), " ").trim()

object Schedule {
    private const val SIMULTANEOUS_MS = 40 * 60 * 1000L
    private val PARIS = java.util.TimeZone.getTimeZone("Europe/Paris")

    // Sports collectifs joués en Europe : jamais de direct entre 0h45 et 9h30 (heure de Paris)…
    private val EUROPEAN_HOURS = setOf("football", "rugby", "handball", "volley ball", "basket ball", "hockey", "futsal")
    // …sauf pour les compétitions disputées sur d'autres fuseaux.
    private val OVERSEAS = Regex(
        "\\b(nba|wnba|ncaa|nfl|nhl|mlb|mls|bresil|brazil|brasil|argentin|libertadores|sudamericana|copa|mexi|liga mx" +
            "|concacaf|gold cup|amerique|americ|etats unis|usa|canada|australi|zeland|pacifi|super rugby|rugby championship" +
            "|bledisloe|nrl|japon|j league|chine|coree|asie|asian|asiatique|afc|saoudi|saudi|mondial|coupe du monde|world cup" +
            "|monde|intercontinental|olympi|tournee|test match)\\b",
    )

    /** Diffusion nocturne d'un match forcément joué plus tôt : rediffusion probable. */
    private fun nightReplay(b: Broadcast): Boolean {
        if (norm(b.sport) !in EUROPEAN_HOURS) return false
        if (OVERSEAS.containsMatchIn(norm(b.competition + " " + b.home + " " + b.away + " " + b.detail))) return false
        val cal = java.util.Calendar.getInstance(PARIS).apply { timeInMillis = b.start }
        val minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        // Aucun coup d'envoi de football en Europe avant midi.
        return minutes in 45..(if (norm(b.sport) == "football") 705 else 570)
    }
    private val TEAM_SPORTS = EUROPEAN_HOURS + setOf("football americain", "rugby a xiii", "baseball", "beach volley")
    private val MULTIPLEX = Regex("\\b(multiplex|multi|redzone|red zone|soiree|toutes les rencontres)\\b")

    private val REPLAY_WORDS = Regex(
        "\\b(rediffusion|rediff|temps forts|meilleurs moments|resume|retour sur|best of|retro|classic|classique|legende|legendes" +
            "|grands matchs|les plus beaux|replay|highlights|inside|countdown|conference de presse|avant course|avant match|le podium)\\b",
    )
    private val YEAR = Regex("\\b(19|20)\\d{2}\\b")
    private val DAYTIME_SPORTS = setOf("cyclisme", "biathlon", "equitation", "athletisme", "trail", "triathlon", "petanque")

    /** Sans calendrier pour trancher : indices tirés du guide lui-même. */
    private fun guessReplay(b: Broadcast, seenBefore: Boolean): Boolean {
        if (b.previouslyShown || seenBefore) return true
        val text = norm(b.title + " " + b.detail + " " + b.competition)
        if (REPLAY_WORDS.containsMatchIn(text)) return true
        // Une année citée qui n'est ni celle de la diffusion ni une saison à cheval : archive.
        val year = java.util.Calendar.getInstance(PARIS).apply { timeInMillis = b.start }.get(java.util.Calendar.YEAR)
        if (YEAR.findAll(text).any { it.value.toInt() !in (year - 1)..(year + 1) }) return true
        val teams = b.home.isNotBlank() && b.away.isNotBlank()
        if (teams && nightReplay(b)) return true
        // Sport collectif sans affiche : résumé, magazine ou remplissage, pas un match en direct.
        if (!teams && norm(b.sport) in TEAM_SPORTS && !MULTIPLEX.containsMatchIn(text)) return true
        if (norm(b.sport) in DAYTIME_SPORTS) {
            val cal = java.util.Calendar.getInstance(PARIS).apply { timeInMillis = b.start }
            val minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            if (minutes in 30..420) return true
        }
        return false
    }

    private fun keyOf(b: Broadcast): String =
        if (b.home.isNotBlank() && b.away.isNotBlank()) "${norm(b.sport)}|${LiveCheck.canon(b.home)}|${LiveCheck.canon(b.away)}"
        else "${competitionKey(b.sport, b.competition)}|${norm(b.title)}|${norm(b.detail)}"

    /**
     * Regroupe les diffusions simultanées d'un même événement (plusieurs chaînes)
     * et marque comme rediffusion toute diffusion ultérieure du même match.
     */
    fun build(broadcasts: List<Broadcast>, check: LiveCheck? = null): List<Event> {
        val out = ArrayList<Event>()
        val verdicts = ArrayList<Verdict>()
        val lastIndex = HashMap<String, Int>()
        for (b in broadcasts.sortedBy { it.start }) {
            val key = keyOf(b)
            val idx = lastIndex[key]
            val teams = b.home.isNotBlank() && b.away.isNotBlank()
            if (idx != null && b.start - out[idx].start <= SIMULTANEOUS_MS) {
                val e = out[idx]
                out[idx] = e.copy(
                    stop = maxOf(e.stop, b.stop),
                    channels = if (b.channelId in e.channels) e.channels else e.channels + b.channelId,
                    // Un verdict du calendrier (direct confirmé ou rediffusion) ne se rediscute pas.
                    replay = if (e.verified || verdicts[idx] == Verdict.REPLAY) e.replay else e.replay && guessReplay(b, false),
                    desc = e.desc.ifBlank { b.desc },
                )
            } else {
                lastIndex[key] = out.size
                val verdict = check?.verdict(b) ?: Verdict.UNKNOWN
                verdicts += verdict
                out += Event(
                    key = key, start = b.start, stop = b.stop, sport = b.sport,
                    competition = b.competition, title = b.title, home = b.home, away = b.away,
                    detail = b.detail, desc = b.desc, kind = b.kind,
                    replay = when (verdict) {
                        Verdict.LIVE -> false
                        Verdict.REPLAY -> true
                        Verdict.UNKNOWN -> guessReplay(b, idx != null && (teams || b.detail.isNotBlank()))
                    },
                    channels = listOf(b.channelId),
                    verified = verdict == Verdict.LIVE,
                )
            }
        }
        return out
    }
}
