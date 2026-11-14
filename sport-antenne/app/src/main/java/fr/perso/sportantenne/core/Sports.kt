package fr.perso.sportantenne.core

/** Reconnaissance du sport à partir d'un libellé (catégorie, préfixe de titre…). */
object Sports {
    // L'ordre compte : les libellés les plus précis d'abord.
    private val TABLE: List<Pair<Regex, String>> = listOf(
        "football americain|\\bnfl\\b|super bowl" to "Football américain",
        "futsal" to "Futsal",
        "beach soccer" to "Beach soccer",
        "foot|soccer" to "Football",
        "rugby a xiii|rugby a 13|super league" to "Rugby à XIII",
        "rugby" to "Rugby",
        "tennis de table|ping" to "Tennis de table",
        "padel" to "Padel",
        "tennis" to "Tennis",
        "basket|\\bnba\\b" to "Basket-ball",
        "handball|\\bhand\\b" to "Handball",
        "beach volley" to "Beach-volley",
        "volley" to "Volley-ball",
        "formule 1|formula 1|formula one|\\bf1\\b" to "Formule 1",
        "monoplace|formule e|formula e|formule 2|formule 3|\\bf2\\b|\\bf3\\b|indycar|nascar" to "Monoplace & circuits",
        "moto ?gp|moto ?2|moto ?3|motocycl|superbike|supercross|motocross|speedway|\\bmoto\\b|motoball|enduro" to "Moto",
        "rallye|\\bwrc\\b|rally|dakar" to "Rallye",
        "endurance|24 ?h|wec\\b|automobil|sport mecanique|sports mecaniques|\\bauto\\b|gt world|dtm|tourisme" to "Sport auto",
        "cyclo cross|cyclisme|cycling|\\bvtt\\b|\\bbmx\\b" to "Cyclisme",
        "golf" to "Golf",
        "kick ?boxing|muay|k 1\\b" to "Kickboxing",
        "boxe|boxing" to "Boxe",
        "\\bmma\\b|\\bufc\\b|\\bksw\\b|arts martiaux mixtes|bellator|\\bpfl\\b" to "MMA",
        "judo" to "Judo",
        "catch|\\bwwe\\b|\\baew\\b" to "Catch",
        "karate|taekwondo|lutte|sports? de combat|sport nl combat" to "Sports de combat",
        "athletisme|marathon|diamond league" to "Athlétisme",
        "trail" to "Trail",
        "triathlon" to "Triathlon",
        "natation|plongeon|water polo|waterpolo" to "Natation",
        "biathlon" to "Biathlon",
        "ski de fond|ski nordique|combine nordique" to "Ski de fond",
        "saut a ski|ski|snowboard|combine nordique" to "Ski",
        "patinage|short track" to "Patinage",
        "hockey sur gazon|hockey en salle" to "Hockey sur gazon",
        "hockey sur glace|\\bnhl\\b|hockey" to "Hockey",
        "curling" to "Curling",
        "bobsleigh|luge|skeleton" to "Bobsleigh, luge et skeleton",
        "cricket" to "Cricket",
        "tir a l arc|archerie" to "Tir à l'arc",
        "tir sportif|\\btir\\b|ball trap|carabine|pistolet|skeet|issf" to "Tir sportif",
        "pelote basque|pelote|cesta punta" to "Pelote basque",
        "pentathlon" to "Pentathlon moderne",
        "echecs" to "Échecs",
        "snooker|billard" to "Snooker",
        "flechettes|darts" to "Fléchettes",
        "voile|motonautisme|powerboat|sports nautiques|aviron|canoe|kayak|surf" to "Sports nautiques",
        "hippisme|hippique|equitation|equestre|courses? de chevaux" to "Équitation",
        "baseball|\\bmlb\\b" to "Baseball",
        "petanque" to "Pétanque",
        "e sport|esport" to "E-sport",
        "squash" to "Squash",
        "badminton" to "Badminton",
        "gymnastique" to "Gymnastique",
        "escrime" to "Escrime",
        "halterophilie|sport de force|force athletique|^force$" to "Force",
        "escalade" to "Escalade",
        "skate|sports extremes" to "Sports extrêmes",
        "jeux olympiques|olympi|multisports|omnisports" to "Multisports",
        "handisport|paralymp" to "Handisport",
        "poker" to "Poker",
    ).map { Regex(it.first) to it.second }

    /** Renvoie le nom canonique du sport, ou null si le libellé n'en désigne aucun. */
    fun detect(label: String): String? {
        val n = norm(label)
        if (n.isEmpty()) return null
        return TABLE.firstOrNull { it.first.containsMatchIn(n) }?.second
    }
}
