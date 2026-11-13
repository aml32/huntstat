package fr.perso.sportantenne.core

/** Une compétition du catalogue : rangée par sport puis par pays, reconnue par un motif sur son libellé. */
class Competition(val id: String, val sport: String, val country: String, val name: String, pattern: String) {
    internal val regex = Regex("\\b(?:$pattern)\\b")
}

/**
 * Catalogue des compétitions proposées en favoris. Les libellés du guide TV varient
 * (sponsor, langue, casse) : chaque entrée porte un motif appliqué au libellé normalisé.
 * L'ordre compte : dans un même sport, la première entrée qui correspond l'emporte.
 */
object Competitions {
    const val FR = "🇫🇷 France"
    const val EN = "🇬🇧 Angleterre"
    const val ES = "🇪🇸 Espagne"
    const val IT = "🇮🇹 Italie"
    const val DE = "🇩🇪 Allemagne"
    const val PT = "🇵🇹 Portugal"
    const val NL = "🇳🇱 Pays-Bas"
    const val BE = "🇧🇪 Belgique"
    const val TR = "🇹🇷 Turquie"
    const val SCO = "🏴 Écosse"
    const val CH = "🇨🇭 Suisse"
    const val EU = "🇪🇺 Europe"
    const val WORLD = "🌍 International"
    const val AFRICA = "🌍 Afrique"
    const val US = "🇺🇸 Amérique du Nord"
    const val SOUTH = "🌎 Amérique du Sud"
    const val ASIA = "🌏 Asie et Océanie"
    const val SA = "🇸🇦 Arabie saoudite"
    const val OTHER = "Autres compétitions"

    private val list = ArrayList<Competition>()
    private fun add(sport: String, country: String, vararg entries: Pair<String, String>) {
        for ((name, pattern) in entries) {
            list += Competition(norm("$sport $country $name").replace(' ', '-'), sport, country, name, pattern)
        }
    }

    init {
        val foot = "Football"
        add(foot, FR,
            "Ligue 1" to "ligue 1", "Ligue 2" to "ligue 2", "National (Ligue 3)" to "ligue 3|national(?! 2| 3| league)",
            "National 2 et 3" to "national 2|national 3|n2|n3", "Coupe de France" to "coupe de france(?! feminine)",
            "Trophée des champions" to "trophee des champions", "Première Ligue féminine" to "arkema|premiere ligue|d1 feminine|d1 arkema",
            "Coupe de France féminine" to "coupe de france feminine", "Championnats de jeunes" to "u ?1[5-9]|moins de 1\\d|gambardella",
        )
        add(foot, EN,
            "Premier League" to "premier league(?! feminine)", "Championship" to "championship|efl championship",
            "FA Cup" to "fa cup|coupe d angleterre|cup anglaise", "League Cup" to "league cup|carabao|coupe de la ligue anglaise|efl cup",
            "Community Shield" to "community shield", "Women's Super League" to "wsl|women s super league|super league feminine",
        )
        add(foot, ES,
            "Liga" to "liga(?! portugal| mx| f\\b| 2| nos)|laliga(?! 2)|championnat d espagne", "Liga 2" to "liga 2|laliga 2|segunda",
            "Coupe du Roi" to "coupe du roi|copa del rey", "Supercoupe d'Espagne" to "supercoupe d espagne", "Liga F" to "liga f",
        )
        add(foot, IT,
            "Serie A" to "(?<!brazilian )serie a(?! bresil)|championnat d italie", "Serie B" to "serie b", "Coupe d'Italie" to "coupe d italie|coppa italia",
            "Supercoupe d'Italie" to "supercoupe d italie",
        )
        add(foot, DE,
            "Bundesliga 2" to "bundesliga 2|2 bundesliga", "Bundesliga" to "bundesliga|championnat d allemagne",
            "Coupe d'Allemagne" to "coupe d allemagne|dfb pokal", "Supercoupe d'Allemagne" to "supercoupe d allemagne",
        )
        add(foot, PT, "Championnat du Portugal" to "championnat du portugal|liga portugal|liga nos|primeira liga", "Coupe du Portugal" to "coupe du portugal|taca")
        add(foot, NL, "Eredivisie" to "eredivisie|championnat des pays bas")
        add(foot, BE, "Pro League" to "pro league|championnat de belgique|jupiler", "Coupe de Belgique" to "coupe de belgique")
        add(foot, TR, "Süper Lig" to "super lig|championnat de turquie")
        add(foot, SCO, "Premiership écossaise" to "championnat d ecosse|premiership ecossaise|scottish")
        add(foot, CH, "Super League suisse" to "super league suisse|championnat de suisse")
        add(foot, EU,
            "Ligue des champions féminine" to "ligue des champions feminine(?! de la caf)|women s champions league",
            "Ligue des champions" to "ligue des champions(?! feminine| de la caf| d afrique| asiatique| de l afc)|champions league(?! de la caf)",
            "Ligue Europa" to "ligue europa|europa league", "Ligue Conférence" to "ligue conference|conference league",
            "Supercoupe d'Europe" to "supercoupe d europe|supercoupe de l uefa", "Youth League" to "youth league",
            "Ligue des nations" to "ligue des nations(?! feminine)|nations league", "Euro" to "euro(?! ?basket| u| espoirs| feminin)|championnat d europe(?! u| espoirs)",
            "Euro féminin" to "euro feminin", "Euro Espoirs" to "euro espoirs|euro u ?21",
        )
        add(foot, WORLD,
            "Qualifications Coupe du monde" to "qualifications? (a la |de la |pour la )?coupe du monde|eliminatoires (de la )?coupe du monde|qualif\\w* mondial",
            "Coupe du monde" to "coupe du monde(?! des clubs| feminine| u)", "Coupe du monde féminine" to "coupe du monde feminine",
            "Coupe du monde des clubs" to "coupe du monde des clubs|mondial des clubs|coupe intercontinentale",
            "Matchs amicaux" to "amical|amicaux", "Jeux olympiques" to "jeux olympiques|olympi",
        )
        add(foot, AFRICA,
            "Qualifications CAN" to "qualifications de la can|qualifications? a la can|eliminatoires de la can",
            "Coupe d'Afrique des nations" to "coupe d afrique|can(?! am)",
            "Ligue des champions de la CAF" to "caf",
        )
        add(foot, US, "MLS" to "mls|major league soccer", "Liga MX" to "liga mx|championnat du mexique", "Gold Cup et Concacaf" to "gold cup|concacaf")
        add(foot, SOUTH,
            "Championnat du Brésil" to "championnat du bresil|brasileirao|serie a bresil|brazilian", "Championnat d'Argentine" to "championnat d argentine|liga profesional",
            "Copa Libertadores" to "libertadores", "Copa Sudamericana" to "sudamericana", "Copa América" to "copa america",
        )
        add(foot, SA, "Saudi Pro League" to "saudi|arabie saoudite|roshn")
        add(foot, ASIA, "Ligue des champions d'Asie" to "ligue des champions (asiatique|de l afc|d asie)|afc", "Championnat du Japon" to "j league|championnat du japon")

        val rugby = "Rugby"
        add(rugby, FR, "Top 14" to "top 14", "Pro D2" to "pro d2", "Nationale" to "nationale", "Élite 1 féminine" to "elite 1|elite feminine", "In Extenso Supersevens" to "supersevens")
        add(rugby, EU,
            "Champions Cup" to "champions cup|coupe d europe", "Challenge Cup" to "challenge cup|challenge europeen",
            "Tournoi des Six Nations" to "six nations(?! feminin| u)|6 nations(?! feminin| u)", "Six Nations féminin" to "six nations feminin|6 nations feminin",
            "Six Nations U20" to "six nations u|6 nations u|moins de 20", "Premiership anglaise" to "premiership", "United Rugby Championship" to "united rugby|urc",
        )
        add(rugby, WORLD,
            "Coupe du monde" to "coupe du monde", "Nations Championship" to "championnat des nations|nations championship",
            "Tests-matchs" to "test match|tests? matchs?|tournee", "Rugby Championship" to "rugby championship", "Bledisloe Cup" to "bledisloe",
            "Rugby à 7 (SVNS)" to "sevens|svns|rugby a 7|a vii",
        )
        add(rugby, ASIA, "Super Rugby" to "super rugby", "Championnat des provinces néo-zélandaises" to "provinces neo zelandaises|npc")

        val basket = "Basket-ball"
        add(basket, US, "NBA" to "nba(?! g)", "WNBA" to "wnba", "NCAA" to "ncaa")
        add(basket, FR, "Betclic Élite" to "betclic elite|elite(?! 2)|pro a|jeep elite", "Pro B / Élite 2" to "pro b|elite 2", "Coupe de France" to "coupe de france", "Ligue féminine (Wonderligue)" to "wonderligue|ligue feminine|lfb", "Leaders Cup" to "leaders cup")
        add(basket, EU, "Euroligue" to "euroligue(?! feminine)|euroleague(?! women)", "Euroligue féminine" to "euroligue feminine", "EuroCoupe" to "eurocoupe|eurocup", "Ligue des champions (BCL)" to "ligue des champions|bcl", "Europe Cup" to "europe cup")
        add(basket, WORLD, "EuroBasket" to "eurobasket|euro basket|championnat d europe", "Coupe du monde" to "coupe du monde|mondial", "Basket 3x3" to "3x3|world tour")

        val hand = "Handball"
        add(hand, FR, "Starligue" to "starligue|lidl starligue|liqui moly starligue", "Proligue" to "proligue", "Ligue féminine (Butagaz)" to "butagaz|ligue feminine|lfh", "Coupe de France" to "coupe de france", "Trophée des champions" to "trophee des champions")
        add(hand, EU, "Ligue des champions féminine" to "ligue des champions feminine", "Ligue des champions" to "ligue des champions", "Ligue européenne" to "ligue europeenne|european league", "Euro" to "euro|championnat d europe")
        add(hand, WORLD, "Championnat du monde" to "championnat du monde|mondial|coupe du monde", "Matchs internationaux" to "amical|golden league|qualifications")

        val volley = "Volley-ball"
        add(volley, FR, "Marmara SpikeLigue (Ligue A)" to "spikeligue|ligue a(?! feminine)|volstar|ligue am", "Ligue A féminine" to "ligue a feminine|saforelle|laf", "Coupe et Supercoupe de France" to "coupe de france|supercoupe")
        add(volley, EU, "Ligue des champions" to "ligue des champions", "Euro" to "euro|championnat d europe")
        add(volley, WORLD, "Ligue des nations" to "ligue des nations|vnl", "Championnat du monde" to "championnat du monde|mondial|coupe du monde")

        val tennis = "Tennis"
        add(tennis, WORLD,
            "Open d'Australie" to "open d australie|australian open", "Roland-Garros" to "roland garros", "Wimbledon" to "wimbledon", "US Open" to "us open",
            "ATP Finals" to "atp finals|masters de fin", "WTA Finals" to "wta finals", "Masters 1000" to "masters 1000|rolex|indian wells|miami|monte carlo|madrid|rome|cincinnati|shanghai|paris bercy|montreal|toronto",
            "Coupe Davis" to "coupe davis|davis cup", "Billie Jean King Cup" to "billie jean king", "Laver Cup et United Cup" to "laver cup|united cup",
            "Tournois ATP" to "atp", "Tournois WTA" to "wta",
        )

        add("Formule 1", WORLD, "Grands Prix de Formule 1" to ".*")
        add("Moto", WORLD, "MotoGP, Moto2, Moto3" to "moto ?gp|moto ?2|moto ?3|grand prix", "Superbike" to "superbike|wsbk", "Motocross et Supercross" to "motocross|supercross|mxgp")
        add("Rallye", WORLD, "WRC" to "wrc|championnat du monde", "Dakar" to "dakar", "ERC et rallyes nationaux" to "erc|championnat de france|championnat d europe")
        add("Sport auto", WORLD, "24 Heures du Mans et WEC" to "24 heures du mans|wec|endurance|6 heures|8 heures|4 heures", "Formule E" to "formule e|formula e", "IndyCar et NASCAR" to "indycar|nascar|indy")

        val velo = "Cyclisme"
        add(velo, FR, "Tour de France" to "tour de france(?! singapore| saitama)", "Paris-Nice" to "paris nice", "Critérium du Dauphiné" to "dauphine", "Paris-Roubaix" to "paris roubaix", "Coupe de France" to "coupe de france")
        add(velo, WORLD, "Tour d'Italie" to "tour d italie|giro", "Tour d'Espagne" to "tour d espagne|vuelta", "Classiques" to "milan san remo|tour des flandres|liege bastogne|amstel|fleche wallonne|tour de lombardie|strade bianche|gand wevelgem",
            "Championnats du monde" to "championnats? du monde", "Cyclo-cross" to "cyclo cross|superprestige|x2o|coupe du monde de cyclo", "VTT" to "vtt|cross country|descente", "Piste" to "piste")

        add("Golf", WORLD, "Majeurs" to "masters|us open|british open|the open|pga championship|uspga", "Ryder Cup et Solheim Cup" to "ryder cup|solheim", "PGA Tour" to "pga tour|circuit americain|open de|championnat d", "DP World Tour" to "dp world|circuit europeen", "LIV Golf" to "liv")
        add("Hockey", US, "NHL" to "nhl")
        add("Hockey", FR, "Ligue Magnus" to "ligue magnus|magnus", "Coupe de France" to "coupe de france")
        add("Hockey", WORLD, "Championnat du monde" to "championnat du monde|mondial")
        add("Football américain", US, "NFL" to "nfl|super bowl", "NCAA" to "ncaa")
        add("Baseball", US, "MLB" to "mlb|world series")
        add("Boxe", WORLD, "Championnats du monde" to "championnat du monde|wbc|wba|ibf|wbo", "Galas et réunions" to ".*")
        add("MMA", WORLD, "UFC" to "ufc", "PFL" to "pfl", "KSW, Hexagone et autres" to ".*")
        add("Ski", WORLD, "Coupe du monde de ski alpin" to "coupe du monde|ski alpin|slalom|descente|geant", "Saut à ski" to "saut|tremplin", "Championnats du monde" to "championnats? du monde")
        add("Biathlon", WORLD, "Coupe du monde" to "coupe du monde", "Championnats du monde" to "championnats? du monde")
        add("Athlétisme", WORLD, "Diamond League" to "diamond league", "Championnats du monde" to "championnats? du monde", "Championnats d'Europe" to "championnats? d europe", "Marathons" to "marathon")
        add("Natation", WORLD, "Championnats du monde" to "championnats? du monde", "Championnats d'Europe" to "championnats? d europe")
        add("Judo", WORLD, "Grand Slam et Grand Prix" to "grand slam|grand chelem|grand prix", "Championnats du monde" to "championnats? du monde")
        add("Snooker", WORLD, "Championnat du monde" to "championnat du monde", "Masters et Opens" to ".*")
    }

    private val bySport: Map<String, List<Competition>> = list.groupBy { norm(it.sport) }
    private val byId: Map<String, Competition> = list.associateBy { "c:" + it.id }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Sports qui ont un catalogue, dans l'ordre d'affichage. */
    val sports: List<String> = list.map { it.sport }.distinct()

    fun of(sport: String): List<Competition> = bySport[norm(sport)] ?: emptyList()

    fun byId(id: String): Competition? = byId[id]

    /**
     * Identifiant stable de la compétition d'une diffusion : « c:… » pour une entrée du catalogue,
     * « dyn:… » pour une compétition vue dans le guide mais absente du catalogue.
     */
    fun idOf(sport: String, competition: String): String {
        val key = competitionKey(sport, competition)
        return cache.getOrPut(key) {
            val text = norm(competition)
            val found = of(sport).firstOrNull { it.regex.containsMatchIn(text) }
            if (found != null) "c:" + found.id else "dyn:$key"
        }
    }

    fun isCatalogued(id: String) = id.startsWith("c:")
}
