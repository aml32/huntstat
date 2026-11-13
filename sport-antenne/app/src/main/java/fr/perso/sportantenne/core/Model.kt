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
) {
    val hasTeams get() = home.isNotBlank() && away.isNotBlank()
    val headline get() = if (hasTeams) "$home – $away" else title
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

    private fun keyOf(b: Broadcast): String =
        if (b.home.isNotBlank() && b.away.isNotBlank()) "${norm(b.sport)}|${norm(b.home)}|${norm(b.away)}"
        else "${competitionKey(b.sport, b.competition)}|${norm(b.title)}|${norm(b.detail)}"

    /**
     * Regroupe les diffusions simultanées d'un même événement (plusieurs chaînes)
     * et marque comme rediffusion toute diffusion ultérieure du même match.
     */
    fun build(broadcasts: List<Broadcast>): List<Event> {
        val out = ArrayList<Event>()
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
                    replay = e.replay && b.previouslyShown,
                    desc = e.desc.ifBlank { b.desc },
                )
            } else {
                lastIndex[key] = out.size
                out += Event(
                    key = key, start = b.start, stop = b.stop, sport = b.sport,
                    competition = b.competition, title = b.title, home = b.home, away = b.away,
                    detail = b.detail, desc = b.desc, kind = b.kind,
                    replay = b.previouslyShown || (teams && idx != null),
                    channels = listOf(b.channelId),
                )
            }
        }
        return out
    }
}
