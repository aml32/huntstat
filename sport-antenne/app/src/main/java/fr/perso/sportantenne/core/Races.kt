package fr.perso.sportantenne.core

/** Ce qu'on sait d'une épreuve individuelle : qui court, quel format, quelle distance, où. */
data class RaceInfo(val gender: String = "", val format: String = "", val distance: String = "", val place: String = "") {
    val isEmpty get() = gender.isEmpty() && format.isEmpty() && distance.isEmpty() && place.isEmpty()
}

/**
 * Lecture du détail d'une épreuve à partir des libellés du guide TV :
 * hommes / femmes / mixte pour tous les sports, format précis pour le biathlon,
 * le ski, le cyclisme, les sports mécaniques et les tournois à élimination.
 */
object Races {
    const val MEN = "Hommes"
    const val WOMEN = "Femmes"
    const val MIXED = "Mixte"

    private val MIXED_RE = Regex("\\b(mixte|mixtes|mixed)\\b")
    private val WOMEN_RE = Regex("\\b(dames|femmes|feminin|feminine|feminines|feminins|women|womens|ladies|filles)\\b")
    private val MEN_RE = Regex("\\b(messieurs|hommes|masculin|masculine|masculins|masculines|men|mens|garcons)\\b")

    // L'ordre compte : le libellé le plus précis d'abord.
    private val BIATHLON: List<Pair<Regex, String>> = listOf(
        "relais mixte simple|relais simple mixte|single mixte|single mixed|relais mixte en simple" to "Relais mixte simple",
        "relais mixte|mixed relay" to "Relais mixte",
        "relais|relay" to "Relais",
        "mass start|depart groupe|depart en ligne|depart en masse" to "Mass start",
        "poursuite|pursuit" to "Poursuite",
        "super sprint" to "Super sprint",
        "sprint" to "Sprint",
        "individuel court|individuelle courte|short individual" to "Individuel court",
        "individuel|individuelle|individual" to "Individuel",
    ).map { Regex("\\b(${it.first})\\b") to it.second }

    private val SKI: List<Pair<Regex, String>> = listOf(
        "slalom geant|geant|giant" to "Slalom géant", "slalom parallele|parallele" to "Parallèle", "slalom" to "Slalom",
        "super g|super geant" to "Super-G", "descente|downhill" to "Descente", "combine" to "Combiné",
        "skiathlon" to "Skiathlon", "sprint" to "Sprint", "relais" to "Relais", "mass start|depart en ligne" to "Mass start",
        "poursuite" to "Poursuite", "grand tremplin" to "Grand tremplin", "petit tremplin|tremplin normal" to "Tremplin normal",
        "vol a ski" to "Vol à ski", "par equipes?" to "Par équipes", "bosses" to "Bosses", "ski ?cross" to "Skicross",
        "slopestyle" to "Slopestyle", "big air" to "Big air", "half ?pipe" to "Half-pipe",
    ).map { Regex("\\b(${it.first})\\b") to it.second }

    private val CYCLING: List<Pair<Regex, String>> = listOf(
        "contre la montre par equipes" to "Contre-la-montre par équipes", "contre la montre|chrono" to "Contre-la-montre",
        "(\\d+)(?:re|e|eme)? etape" to "Étape", "prologue" to "Prologue", "course en ligne" to "Course en ligne",
        "elites?" to "Élite", "espoirs" to "Espoirs", "juniors?" to "Juniors",
    ).map { Regex("\\b(${it.first})\\b") to it.second }

    private val MOTOR: List<Pair<Regex, String>> = listOf(
        "essais libres ?(\\d)?" to "Essais libres", "qualifications sprint|sprint shootout|qualifs sprint" to "Qualifications sprint",
        "qualifications?|qualifs" to "Qualifications", "course sprint|sprint" to "Course sprint", "warm up" to "Warm-up",
        "la course|course|grand prix" to "Course",
    ).map { Regex("\\b(${it.first})\\b") to it.second }

    private val ROUNDS: List<Pair<Regex, String>> = listOf(
        "petite finale|match pour la 3e place" to "Petite finale", "demi finales?|demies?" to "Demi-finale", "quarts? de finale|quarts" to "Quart de finale",
        "huitiemes? de finale|8es? de finale" to "Huitième de finale", "seiziemes? de finale|16es? de finale" to "Seizième de finale",
        "finales?" to "Finale", "barrages?" to "Barrage", "(\\d+)(?:re|er|e|eme)? tour" to "Tour", "phase de poules?|poules?" to "Phase de poules",
    ).map { Regex("\\b(${it.first})\\b") to it.second }

    private val DISTANCE = Regex("\\b(\\d+ ?x ?\\d+(?: \\d)? ?km|\\d+(?: \\d)? ?km)\\b")
    private val BIATHLON_PLACES = listOf(
        "Östersund", "Kontiolahti", "Hochfilzen", "Le Grand-Bornand", "Annecy", "Oberhof", "Ruhpolding", "Antholz", "Anterselva", "Nove Mesto",
        "Oslo", "Holmenkollen", "Soldier Hollow", "Canmore", "Pokljuka", "Lenzerheide", "Otepää", "Otepaa", "Brezno", "Osrblie", "Arber",
        "Ridnaun", "Idre", "Sjusjøen", "Sjusjoen", "Obertilliach", "Martell", "Geilo", "Minsk", "Tyumen", "Khanty-Mansiysk", "Presque Isle",
        "Pyeongchang", "Pékin", "Milan", "Cortina", "Val di Fiemme", "Les Saisies", "Prémanon", "La Féclaz", "Bessans", "Méribel",
    )

    fun parse(b: Broadcast): RaceInfo = parse(b.sport, b.competition, b.title, b.detail, b.desc)

    fun parse(sport: String, competition: String, title: String, detail: String, desc: String = ""): RaceInfo {
        val text = norm("$competition $title $detail")
        // La description n'est consultée que si le reste ne dit rien : elle parle souvent d'autre chose.
        val wide = norm(desc.substringBefore('.'))
        val gender = gender(text).ifEmpty { if (norm(sport) == "biathlon") gender(wide) else "" }
        val table = when (norm(sport)) {
            "biathlon" -> BIATHLON
            "ski", "patinage" -> SKI
            "cyclisme" -> CYCLING
            "formule 1", "moto", "monoplace circuits", "sport auto", "rallye" -> MOTOR
            else -> ROUNDS
        }
        var format = formatOf(table, text)
        if (format.isEmpty() && norm(sport) == "biathlon") format = formatOf(BIATHLON, wide)
        if (format.isEmpty() && table !== ROUNDS) format = formatOf(ROUNDS, text)
        val distance = (DISTANCE.find(text) ?: DISTANCE.find(wide))?.value?.let { tidyDistance(it) } ?: ""
        val place = if (norm(sport) == "biathlon") {
            val raw = "$competition $title $detail $desc"
            BIATHLON_PLACES.firstOrNull { norm(raw).contains(norm(it)) } ?: ""
        } else ""
        // Un relais mixte est forcément mixte, même si le guide ne le redit pas.
        val finalGender = if (format.startsWith("Relais mixte")) MIXED else gender
        return RaceInfo(finalGender, format, distance, place)
    }

    private fun gender(text: String): String = when {
        MIXED_RE.containsMatchIn(text) -> MIXED
        WOMEN_RE.containsMatchIn(text) -> WOMEN
        MEN_RE.containsMatchIn(text) -> MEN
        else -> ""
    }

    private fun formatOf(table: List<Pair<Regex, String>>, text: String): String {
        for ((re, label) in table) {
            val m = re.find(text) ?: continue
            // Numéro d'étape, de tour ou de séance d'essais conservé : « Étape 12 », « Essais libres 2 ».
            val number = m.groupValues.drop(2).firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
            return if (number != null) "$label $number" else label
        }
        return ""
    }

    /** « 7 5 km » (virgule perdue à la normalisation) → « 7,5 km » ; « 4x6 km » → « 4 × 6 km ». */
    private fun tidyDistance(d: String): String {
        val relay = Regex("(\\d+) ?x ?(\\d+)(?: (\\d))? ?km").matchEntire(d)
        if (relay != null) {
            val (n, a, dec) = relay.destructured
            return "$n × $a${if (dec.isNotEmpty()) ",$dec" else ""} km"
        }
        val single = Regex("(\\d+)(?: (\\d))? ?km").matchEntire(d) ?: return d
        val (a, dec) = single.destructured
        return "$a${if (dec.isNotEmpty()) ",$dec" else ""} km"
    }
}
