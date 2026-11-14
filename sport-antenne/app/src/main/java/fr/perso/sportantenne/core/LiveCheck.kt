package fr.perso.sportantenne.core

enum class Verdict { LIVE, ONGOING, REPLAY, UNKNOWN }

/**
 * Recoupe une diffusion du guide TV avec les calendriers réels :
 *  - LIVE    : un match de ces équipes démarre vraiment pendant la diffusion ;
 *  - ONGOING : le tournoi (tennis, golf) se joue bien ce jour-là, sans heure précise de match ;
 *  - REPLAY  : la compétition est couverte par le calendrier et aucun match ne correspond ;
 *  - UNKNOWN : le calendrier ne permet pas de trancher (sport ou compétition non couverts).
 */
class LiveCheck(private val fixtures: Fixtures) {
    private class Side(val name: String, val tokens: Set<String>, val women: Boolean, val youth: Boolean)
    private class Entry(val f: Fixture, val home: Side, val away: Side, val women: Boolean)

    private val bySource: Map<String, List<Entry>> = fixtures.list.map { f ->
        val h = side(f.home)
        val a = side(f.away)
        Entry(f, h, a, h.women || a.women || WOMEN.containsMatchIn(norm(f.league)))
    }.groupBy { it.f.source }

    /** Toutes les équipes connues d'une source, pour savoir si une correspondance exacte existe. */
    private val teamsBySource: Map<String, List<Side>> = bySource.mapValues { (_, l) -> l.flatMap { listOf(it.home, it.away) } }
    private val bestCache = HashMap<String, Int>()

    /** Verdict et, pour un direct confirmé, les logos des deux équipes dans l'ordre du guide. */
    class Resolution(val verdict: Verdict, val homeLogo: String = "", val awayLogo: String = "", val score: Score? = null)

    fun verdict(b: Broadcast): Verdict = resolve(b).verdict

    fun resolve(b: Broadcast): Resolution {
        val source = sourceOf(b) ?: return UNKNOWN
        val entries = bySource[source] ?: emptyList()
        if (source == "f1") return Resolution(formula1(b, entries))
        if (source == "tennis" || source == "golf") return Resolution(tournament(b, entries))
        if (source == "ufc") return Resolution(fightNight(b, entries))
        if (b.home.isBlank() || b.away.isBlank()) return UNKNOWN

        val context = norm(b.competition + " " + b.detail)
        val women = WOMEN.containsMatchIn(context)
        val youth = YOUTH.containsMatchIn(context)
        val gh = side(b.home)
        val ga = side(b.away)
        val bestH = best(source, gh)
        val bestA = best(source, ga)

        var pairElsewhere = false
        for (e in entries) {
            if (e.women != women || (e.home.youth || e.away.youth) != youth) continue
            // Les deux ordres : le guide n'indique pas toujours l'équipe à domicile en premier.
            val direct = score(gh, e.home) to score(ga, e.away)
            val swapped = score(gh, e.away) to score(ga, e.home)
            val straight = direct.first + direct.second >= swapped.first + swapped.second
            val (sh, sa) = if (straight) direct else swapped
            val strongH = sh >= 2 && sh >= bestH
            val strongA = sa >= 2 && sa >= bestA
            val samePair = (sh >= 1 && sa >= 1 && (strongH || strongA)) ||
                (strongH && bestA < 2) || (strongA && bestH < 2)
            if (!samePair) continue
            if (inWindow(b, e.f.start)) {
                val f = e.f
                val known = f.state == "in" || f.state == "post"
                return if (straight) Resolution(Verdict.LIVE, f.homeLogo, f.awayLogo, if (known) Score(f.homeScore, f.awayScore, f.state, f.clock) else null)
                else Resolution(Verdict.LIVE, f.awayLogo, f.homeLogo, if (known) Score(f.awayScore, f.homeScore, f.state, f.clock) else null)
            }
            if (strongH && strongA) pairElsewhere = true
        }
        // Même affiche programmée à un autre moment : cette diffusion n'est pas le direct.
        if (pairElsewhere) return REPLAY
        // Compétition entièrement couverte ce jour-là et aucun match trouvé : rediffusion.
        if (comprehensive(source, b) && covered(source, b.start)) return REPLAY
        return UNKNOWN
    }

    private fun formula1(b: Broadcast, entries: List<Entry>): Verdict {
        if (entries.isEmpty()) return Verdict.UNKNOWN // calendrier de la saison indisponible
        return if (entries.any { inWindow(b, it.f.start) }) Verdict.LIVE else Verdict.REPLAY
    }

    /**
     * Tennis et golf : on retrouve le tournoi par son nom ; s'il se joue ce jour-là la diffusion est plausible,
     * s'il est connu mais à d'autres dates c'est une rediffusion.
     */
    private fun tournament(b: Broadcast, entries: List<Entry>): Verdict {
        if (entries.isEmpty()) return Verdict.UNKNOWN
        val guide = norm(translate(norm("${b.competition} ${b.title} ${b.detail}")))
        val guideTokens = guide.split(' ').filter { it.length >= 4 && it !in GENERIC }.toSet()
        val tour = TOURS.firstOrNull { Regex("\\b$it\\b").containsMatchIn(guide) }
        var known = false
        for (e in entries) {
            if (tour != null && e.f.league != tour && !(tour == "pga" && e.f.league == "eur")) continue
            val name = norm(e.f.home)
            val same = name.split(' ').any { it in guideTokens } ||
                (guide.contains("finals") && name.contains("finals") && tour != null) ||
                (norm(b.competition).length >= 8 && (name.contains(norm(b.competition)) || norm(b.competition).contains(name)))
            if (!same) continue
            known = true
            if (b.start >= e.f.start - 12 * 60 * MIN && b.start <= e.f.end + 12 * 60 * MIN) return Verdict.ONGOING
        }
        return if (known) Verdict.REPLAY else Verdict.UNKNOWN
    }

    /** UFC : toutes les soirées sont au calendrier ; hors d'une soirée, c'est une rediffusion ou un magazine. */
    private fun fightNight(b: Broadcast, entries: List<Entry>): Verdict {
        if (entries.isEmpty() || !covered("ufc", b.start)) return Verdict.UNKNOWN
        return if (entries.any { b.stop > it.f.start - 30 * MIN && b.start < it.f.end }) Verdict.LIVE else Verdict.REPLAY
    }

    /** Le coup d'envoi tombe pendant la diffusion (avant-match compris), pas avant qu'elle commence. */
    private fun inWindow(b: Broadcast, kickoff: Long): Boolean =
        kickoff >= b.start - 20 * MIN && kickoff <= b.start + 120 * MIN && kickoff < b.stop

    private fun covered(source: String, t: Long) = fixtures.coveredDays[source]?.contains(Fixtures.dayOf(t)) == true

    private fun best(source: String, g: Side): Int = bestCache.getOrPut(source + "|" + g.name) {
        teamsBySource[source]?.maxOfOrNull { score(g, it) } ?: 0
    }

    /** 3 = mêmes mots, 2 = l'un contient l'autre, 1 = un mot long en commun, 0 = rien. */
    private fun score(g: Side, f: Side): Int {
        if (g.tokens.isEmpty() || f.tokens.isEmpty()) return 0
        if (g.tokens == f.tokens) return 3
        val small = if (g.tokens.size <= f.tokens.size) g.tokens else f.tokens
        val large = if (small === g.tokens) f.tokens else g.tokens
        if (large.containsAll(small) && small.any { it.length >= 4 }) return 2
        return if (g.tokens.any { it.length >= 5 && it in f.tokens }) 1 else 0
    }

    companion object {
        private val TOURS = listOf("wta", "atp", "lpga", "pga")
        private val GENERIC = setOf(
            "open", "tournoi", "tournament", "championship", "championnat", "championnats", "masters", "classic", "international", "internationaux",
            "coupe", "trophy", "trophee", "tour", "world", "series", "grand", "prix", "pres", "presented", "presentado", "finals", "finale", "final",
            "tennis", "golf", "simple", "double", "dames", "messieurs", "hommes", "femmes", "circuit", "americain", "europeen", "partie", "quart",
            "demi", "phase", "poules", "jour", "session", "night", "match", "play", "offs", "club", "country", "national", "invitational",
        )
        private val PLACES = listOf(
            "roland garros" to "french open", "open d australie" to "australian open", "pekin" to "beijing", "shanghai" to "shanghai", "dubai" to "dubai",
            "bale" to "basel", "vienne" to "vienna", "anvers" to "antwerp", "geneve" to "geneva", "hambourg" to "hamburg", "barcelone" to "barcelona",
            "rome" to "italian", "montreal" to "canadian", "toronto" to "canadian", "monte carlo" to "monte carlo", "bercy" to "paris", "turin" to "finals",
            "londres" to "london", "moscou" to "moscow", "lisbonne" to "lisbon", "seville" to "sevilla", "british open" to "the open championship",
            "ecosse" to "scottish", "irlande" to "irish", "espagne" to "spain", "italie" to "italian", "allemagne" to "german", "coree" to "korea",
            "japon" to "japan", "chine" to "china", "afrique du sud" to "south african", "arabie saoudite" to "saudi",
        ).map { Regex("\\b${it.first}\\b") to it.first + " " + it.second }

        /** Ajoute au libellé français les mots anglais équivalents, pour le comparer au nom officiel du tournoi. */
        private fun translate(text: String): String {
            var out = text
            for ((re, both) in PLACES) out = re.replace(out, both)
            return out
        }

        private val UNKNOWN = Resolution(Verdict.UNKNOWN)
        private val REPLAY = Resolution(Verdict.REPLAY)
        private const val MIN = 60_000L
        private val WOMEN = Regex("\\b(women|womens|w|feminin|feminine|feminines|femenil|femenina|frauen|vrouwen|dames|arkema|premiere ligue|wsl|nwsl|liga f|reina|ncaaw|wnba|lyonnes)\\b")
        private val YOUTH = Regex("\\b(u ?1[5-9]|u ?2[0-3]|espoirs|moins de \\d+|youth|jeunes|juniors|reserve|primavera)\\b")
        private val STOP = setOf(
            "fc", "cf", "ac", "as", "sc", "afc", "cd", "ud", "sv", "fk", "sk", "bk", "if", "club", "de", "del", "da", "do", "la", "le",
            "les", "the", "of", "and", "et", "calcio", "football", "stade", "us", "rc", "so", "ogc", "losc", "aj", "ea", "sm", "sporting club",
            "women", "womens", "w", "feminines", "feminin", "u15", "u16", "u17", "u18", "u19", "u20", "u21", "u23", "1", "04", "05", "29", "1899", "1846",
        )

        private val FOOT_UNCOVERED = Regex(
            "\\b(ligue 3|national|national 2|national 3|n2|n3|regional|u ?1\\d|moins de \\d+|reserve|gambardella|caf|futsal|beach|youth league)\\b",
        )

        /** Forme canonique d'un nom d'équipe (alias et mots vides retirés), pour regrouper les variantes. */
        fun canon(name: String): String = side(name).tokens.sorted().joinToString(" ").ifEmpty { norm(name) }

        @Suppress("unused")
        private val FOOT_MAJOR = Regex(
            "\\b(ligue 1|ligue 2|premier league|championship|liga|laliga|serie a|serie b|bundesliga|eredivisie|ligue des champions" +
                "|champions league|ligue europa|europa league|ligue conference|conference league|coupe de france|coupe d angleterre" +
                "|fa cup|coupe du roi|copa del rey|coupe d italie|coupe d allemagne|ligue des nations|nations league|qualifications" +
                "|eliminatoires|amical|coupe d afrique|can|coupe du monde|euro|championnat d europe|mls|saudi|arabie saoudite" +
                "|championnat du portugal|liga portugal|championnat de belgique|pro league|super lig|brasileirao|championnat du bresil" +
                "|championnat d argentine|copa libertadores|arkema|premiere ligue|wsl|super league)\\b",
        )

        private val ALIASES: List<Pair<Regex, String>> = listOf(
            // Clubs
            "paris saint germain|paris sg|paris s g" to "psg", "olympique de marseille|olympique marseille" to "marseille",
            "olympique lyonnais|ol lyonnes" to "lyon", "stade rennais" to "rennes", "stade brestois" to "brest",
            "internazionale|inter milan|inter de milan" to "inter", "milan ac|ac milan" to "milan", "juventus turin" to "juventus",
            "barcelone|fc barcelona" to "barcelona", "seville" to "sevilla", "betis sevilla|real betis" to "betis", "naples" to "napoli",
            "as rome|rome" to "roma", "lazio roma" to "lazio", "genes" to "genoa", "florence" to "fiorentina", "bologne" to "bologna",
            "venise" to "venezia", "parme" to "parma", "verone|hellas verona" to "verona", "atalanta bergame" to "atalanta", "turin" to "torino",
            "francfort|eintracht frankfurt" to "frankfurt", "fribourg" to "freiburg", "mayence" to "mainz", "breme|werder bremen" to "bremen",
            "hambourg|hamburg sv" to "hamburg", "cologne" to "koln", "bayer leverkusen" to "leverkusen", "borussia dortmund" to "dortmund",
            "bayern munchen|bayern de munich" to "bayern munich", "rb leipzig" to "leipzig",
            "sporting portugal|sporting cp|sporting lisbonne" to "sportingcp", "benfica lisbonne" to "benfica", "ajax amsterdam" to "ajax",
            "psv eindhoven" to "psv", "bruges|club brugge" to "brugge", "olympiakos" to "olympiacos", "etoile rouge de belgrade|etoile rouge|red star belgrade" to "redstar",
            "dynamo kiev|dynamo kyiv" to "dynamokyiv", "chakhtar" to "shakhtar", "celtic glasgow" to "celtic", "glasgow rangers" to "rangers",
            "athletic bilbao|athletic club" to "athletic", "valence" to "valencia", "gerone" to "girona", "majorque" to "mallorca",
            "tottenham hotspur" to "tottenham", "wolverhampton wanderers|wolves" to "wolverhampton", "newcastle united" to "newcastle",
            "brighton hove albion|brighton and hove albion" to "brighton", "west ham united" to "west ham", "leeds united" to "leeds",
            "saint etienne|st etienne" to "saintetienne", "paris fc" to "parisfc", "red star" to "redstarfc",
            // Sélections
            "allemagne" to "germany", "angleterre" to "england", "espagne" to "spain", "italie" to "italy", "pays bas" to "netherlands",
            "belgique" to "belgium", "suisse" to "switzerland", "autriche" to "austria", "ecosse" to "scotland", "pays de galles" to "wales",
            "irlande du nord" to "northern ireland", "republic of ireland|republique d irlande|irlande|eire" to "ireland", "croatie" to "croatia",
            "serbie" to "serbia", "pologne" to "poland", "suede" to "sweden", "norvege" to "norway", "danemark" to "denmark",
            "finlande" to "finland", "islande" to "iceland", "grece" to "greece", "turquie|turkey" to "turkiye", "russie" to "russia",
            "roumanie" to "romania", "hongrie" to "hungary", "republique tcheque|tchequie|czech republic" to "czechia", "slovaquie" to "slovakia",
            "slovenie" to "slovenia", "bulgarie" to "bulgaria", "albanie" to "albania", "bosnie herzegovine|bosnia herzegovina|bosnie" to "bosnia",
            "macedoine du nord" to "north macedonia", "georgie" to "georgia", "armenie" to "armenia", "azerbaidjan" to "azerbaijan",
            "chypre" to "cyprus", "malte" to "malta", "etats unis|united states|usa" to "unitedstates", "mexique" to "mexico",
            "bresil" to "brazil", "argentine" to "argentina", "chili" to "chile", "colombie" to "colombia", "perou" to "peru",
            "equateur" to "ecuador", "bolivie" to "bolivia", "japon" to "japan", "coree du sud|south korea|korea republic" to "southkorea",
            "chine|china pr" to "china", "australie" to "australia", "nouvelle zelande|new zealand" to "newzealand",
            "arabie saoudite|saudi arabia" to "saudiarabia", "irak" to "iraq", "maroc" to "morocco", "algerie" to "algeria",
            "tunisie" to "tunisia", "egypte" to "egypt", "cote d ivoire|ivory coast" to "ivorycoast", "cameroun" to "cameroon",
            "guinee equatoriale|equatorial guinea" to "equatorialguinea", "guinee bissau|guinea bissau" to "guineabissau", "guinee" to "guinea",
            "rd congo|republique democratique du congo|congo dr|dr congo" to "congodr", "afrique du sud|south africa" to "southafrica",
            "cap vert|cape verde|cabo verde" to "capeverde", "gambie" to "gambia", "namibie" to "namibia", "zambie" to "zambia",
            "ouganda" to "uganda", "tanzanie" to "tanzania", "mauritanie" to "mauritania", "ethiopie" to "ethiopia", "libye" to "libya",
            "soudan" to "sudan", "comores" to "comoros", "jamaique" to "jamaica", "estonie" to "estonia", "lettonie" to "latvia",
            "lituanie" to "lithuania", "bielorussie" to "belarus", "moldavie" to "moldova", "andorre" to "andorra", "saint marin" to "san marino",
            "iles feroe|faroe islands" to "faroe", "fidji" to "fiji", "burkina faso" to "burkina", "sierra leone" to "sierraleone",
        ).map { Regex("\\b(${it.first})\\b") to it.second }

        private fun side(name: String): Side {
            var n = norm(name)
            val women = WOMEN.containsMatchIn(n)
            val youth = YOUTH.containsMatchIn(n)
            for ((re, to) in ALIASES) n = re.replace(n, to)
            val tokens = n.split(' ').filter { it.isNotEmpty() && it !in STOP && !it.all(Char::isDigit) }.toSet()
            return Side(name, tokens, women, youth)
        }

        /** Quelle source de calendrier peut vérifier cette diffusion, le cas échéant. */
        fun sourceOf(b: Broadcast): String? = sourceOf(b.sport, b.competition)

        fun sourceOf(sport: String, competition: String): String? {
            val comp = norm(competition)
            return when (norm(sport)) {
                "football" -> "football"
                "basket ball" -> when {
                    comp.contains("wnba") -> null
                    Regex("\\bnba\\b").containsMatchIn(comp) -> "nba"
                    comp.contains("ncaa") -> "ncaab"
                    else -> null
                }
                "football americain" -> if (Regex("\\bnfl\\b").containsMatchIn(comp)) "nfl" else if (comp.contains("ncaa")) "ncaaf" else null
                "tennis" -> "tennis"
                "golf" -> "golf"
                "mma" -> if (Regex("\\bufc\\b").containsMatchIn(comp)) "ufc" else null
                "hockey" -> if (Regex("\\bnhl\\b").containsMatchIn(comp)) "nhl" else null
                "rugby" -> "rugby"
                "formule 1" -> "f1"
                else -> null
            }
        }

        private fun comprehensive(source: String, b: Broadcast): Boolean = when (source) {
            "nba", "nfl", "nhl" -> true
            // Le calendrier football couvre presque tout : seules quelques compétitions y échappent.
            "football" -> !FOOT_UNCOVERED.containsMatchIn(norm(b.competition))
            else -> false
        }
    }
}
