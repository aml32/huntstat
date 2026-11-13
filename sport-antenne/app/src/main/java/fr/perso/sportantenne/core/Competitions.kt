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
            "Coupe de France féminine" to "coupe de france feminine", "Championnats de jeunes" to "championnats? de jeunes|championnat (de france |national )?(des )?(u ?1[5-9]|moins de 1\\d)|gambardella",
        )
        add(foot, EN,
            "Premier League" to "premier league(?! feminine)", "Championship" to "championship|efl championship",
            "FA Cup" to "fa cup|coupe d angleterre|cup anglaise", "League Cup" to "league cup|carabao|coupe de la ligue anglaise|efl cup",
            "Community Shield" to "community shield", "Women's Super League" to "wsl|women s super league|super league feminine",
        )
        add(foot, ES,
            "Liga" to "liga(?! portugal| mx| f\\b| 2| nos)|laliga(?! 2)|championnat d espagne", "Liga 2" to "liga 2|laliga 2|segunda",
            "Coupe du Roi" to "coupe du roi(?! d arabie)|copa del rey", "Supercoupe d'Espagne" to "supercoupe d espagne", "Liga F" to "liga f",
        )
        add(foot, IT,
            "Serie A" to "(?<!brazilian )serie a(?! bresil)|championnat d italie", "Serie B" to "serie b", "Coupe d'Italie" to "coupe d italie|coppa italia",
            "Supercoupe d'Italie" to "supercoupe d italie",
        )
        add(foot, DE,
            "Bundesliga 2" to "bundesliga 2|2 bundesliga", "Bundesliga" to "bundesliga(?! autrichienne| feminine)|championnat d allemagne", "Bundesliga féminine" to "bundesliga feminine|frauen bundesliga",
            "Coupe d'Allemagne" to "coupe d allemagne|dfb pokal", "Supercoupe d'Allemagne" to "supercoupe d allemagne",
        )
        add(foot, PT, "Championnat du Portugal" to "championnat du portugal|liga portugal|liga nos|primeira liga", "Coupe du Portugal" to "coupe du portugal|taca")
        add(foot, NL, "Eredivisie" to "eredivisie|championnat des pays bas")
        add(foot, BE, "Pro League" to "(?<!saudi )(?<!uae )pro league|championnat de belgique|jupiler", "Coupe de Belgique" to "coupe de belgique")
        add(foot, TR, "Süper Lig" to "super lig|championnat de turquie")
        add(foot, SCO, "Premiership écossaise" to "championnat d ecosse|premiership ecossaise|scottish premiership|premiership", "Coupe d'Écosse" to "coupe d ecosse|scottish cup")
        add(foot, CH, "Super League suisse" to "super league suisse|championnat de suisse", "Coupe de Suisse" to "coupe de suisse")
        add(foot, "🇦🇹 Autriche", "Bundesliga autrichienne" to "bundesliga autrichienne|championnat d autriche")
        add(foot, "🇬🇷 Grèce", "Super League grecque" to "championnat de grece|super league grecque")
        add(foot, "🇩🇰 Danemark", "Superliga danoise" to "superliga(?! serbe| roumaine)|championnat du danemark")
        add(foot, "🇸🇪 Suède", "Allsvenskan" to "allsvenskan|championnat de suede")
        add(foot, "🇳🇴 Norvège", "Eliteserien" to "eliteserien|championnat de norvege")
        add(foot, "🇵🇱 Pologne", "Ekstraklasa" to "ekstraklasa|championnat de pologne")
        add(foot, "🇨🇿 Tchéquie", "Championnat de Tchéquie" to "championnat de tchequie|championnat tcheque|fortuna liga")
        add(foot, "🇭🇷 Croatie", "Championnat de Croatie" to "championnat de croatie|hnl")
        add(foot, "🇷🇸 Serbie", "Championnat de Serbie" to "championnat de serbie|superliga serbe")
        add(foot, "🇷🇴 Roumanie", "Championnat de Roumanie" to "championnat de roumanie|superliga roumaine")
        add(foot, "🇺🇦 Ukraine", "Championnat d'Ukraine" to "championnat d ukraine")
        add(foot, "🇷🇺 Russie", "Championnat de Russie" to "championnat de russie|premier liga russe")
        add(foot, "🇮🇪 Irlande", "Championnat d'Irlande" to "championnat d irlande|league of ireland")
        add(foot, EU,
            "Ligue des champions féminine" to "ligue des champions feminine(?! de la caf)|women s champions league",
            "Ligue des champions" to "ligue des champions(?! feminine| de la caf| d afrique| asiatique| de l afc| d asie)|champions league(?! de la caf)",
            "Ligue Europa" to "ligue europa|europa league", "Ligue Conférence" to "ligue conference|conference league",
            "Supercoupe d'Europe" to "supercoupe d europe|supercoupe de l uefa", "Youth League" to "youth league",
            "Ligue des nations féminine" to "ligue des nations feminine|women s nations league",
            "Ligue des nations" to "ligue des nations(?! feminine)|nations league",
            "Qualifications Euro" to "qualifications? (a l |de l |pour l )?euro(?! ?basket)|eliminatoires (de l )?euro",
            "Euro" to "euro(?! ?basket| u| espoirs| feminin)|championnat d europe(?! u| espoirs)",
            "Euro féminin" to "euro feminin", "Euro Espoirs" to "euro espoirs|euro u ?21",
            "Euro U17 et U19" to "euro u ?1[79]|championnat d europe u ?1[79]",
        )
        add(foot, WORLD,
            "Qualifications Coupe du monde" to "qualifications? (a la |de la |pour la )?coupe du monde|eliminatoires (de la )?coupe du monde|qualif\\w* mondial",
            "Coupe du monde" to "coupe du monde(?! des clubs| feminine| u| des moins)", "Coupe du monde féminine" to "coupe du monde feminine",
            "Coupes du monde U17 et U20" to "coupes? du monde (u ?\\d+|des moins de \\d+)|mondial u ?\\d+",
            "Coupe du monde des clubs" to "coupe du monde des clubs|mondial des clubs|coupe intercontinentale",
            "Matchs amicaux" to "amical|amicaux|tournoi de france|shebelieves", "Jeux olympiques" to "jeux olympiques|olympi\\w*",
        )
        add(foot, AFRICA,
            "Qualifications CAN" to "qualifications? (de la |a la )?can|eliminatoires (de la )?can",
            "Coupe d'Afrique des nations" to "coupe d afrique|can(?! am)",
            "Ligue des champions de la CAF" to "caf",
        )
        add(foot, US, "MLS" to "mls|major league soccer", "Liga MX" to "liga mx|championnat du mexique", "Gold Cup et Concacaf" to "gold cup|concacaf")
        add(foot, SOUTH,
            "Championnat du Brésil" to "championnat du bresil|brasileirao|serie a bresil|brazilian", "Championnat d'Argentine" to "championnat d argentine|liga profesional",
            "Copa Libertadores" to "libertadores", "Copa Sudamericana" to "sudamericana", "Copa América" to "copa america",
        )
        add(foot, SOUTH, "Championnat de Colombie" to "championnat de colombie", "Championnat du Chili" to "championnat du chili", "Championnat d'Uruguay" to "championnat d uruguay")
        add(foot, US, "Leagues Cup et US Open Cup" to "leagues cup|us open cup", "NWSL (féminin)" to "nwsl")
        add(foot, AFRICA, "Championnat du Maroc (Botola)" to "botola|championnat du maroc", "Championnat d'Algérie" to "championnat d algerie", "Championnat de Tunisie" to "championnat de tunisie",
            "Championnat d'Égypte" to "championnat d egypte", "CHAN et Coupe arabe" to "chan|coupe arabe")
        add(foot, SA, "Saudi Pro League" to "saudi|arabie saoudite|roshn", "Coupe du Roi d'Arabie" to "king s cup|coupe du roi d arabie")
        add(foot, "🇶🇦 Golfe", "Championnat du Qatar" to "qatar|stars league", "Championnat des Émirats" to "emirats|uae pro league")
        add(foot, ASIA, "Ligue des champions d'Asie" to "ligue des champions (asiatique|de l afc|d asie)|afc", "Championnat du Japon" to "j league|championnat du japon",
            "Championnat de Corée" to "k league|championnat de coree", "Championnat de Chine" to "super league chinoise|championnat de chine", "A-League (Australie)" to "a league|championnat d australie",
            "Coupe d'Asie des nations" to "coupe d asie")
        add(foot, WORLD, "Futsal et beach soccer" to "futsal|beach soccer", "Kings League et foot à 7" to "kings league|foot a 7|queens league")

        val rugby = "Rugby"
        add(rugby, FR, "Top 14" to "top 14", "Pro D2" to "pro d2", "Nationale" to "nationale", "Élite 1 féminine" to "elite 1|elite feminine", "In Extenso Supersevens" to "supersevens")
        add(rugby, EU,
            "Champions Cup" to "champions cup|coupe d europe(?! feminine)", "Challenge Cup" to "challenge cup|challenge europeen",
            "Tournoi des Six Nations" to "six nations(?! feminin| u)|6 nations(?! feminin| u)", "Six Nations féminin" to "six nations feminin|6 nations feminin",
            "Six Nations U20" to "(six|6) nations (u ?\\d+|des moins de \\d+)", "Rugby Europe Championship" to "rugby europe", "Premiership anglaise" to "premiership", "United Rugby Championship" to "united rugby|urc",
        )
        add(rugby, WORLD,
            "Coupe du monde féminine" to "coupe du monde feminine", "Coupe du monde U20" to "(championnat|coupe) du monde (u ?20|des moins de 20)|mondial u ?20",
            "Coupe du monde" to "coupe du monde", "WXV (féminin)" to "wxv",
            "Pacific Nations Cup" to "pacific nations", "Nations Championship" to "championnat des nations|nations championship|coupe des nations|nations cup",
            "Tests-matchs et tournées" to "test match|tests? matchs?|tournee|autumn nations|serie d automne", "Rugby Championship" to "rugby championship", "Bledisloe Cup" to "bledisloe",
            "Rugby à 7 (SVNS)" to "sevens|svns|rugby a 7|a vii", "Jeux olympiques" to "jeux olympiques|olympi\\w*",
        )
        add(rugby, ASIA, "Super Rugby" to "super rugby", "Championnat des provinces néo-zélandaises" to "provinces neo zelandaises|npc")

        val basket = "Basket-ball"
        add(basket, US, "NBA" to "nba(?! g)", "WNBA" to "wnba", "NCAA" to "ncaa")
        add(basket, FR, "Betclic Élite" to "betclic elite|elite(?! 2)|pro a|jeep elite", "Pro B / Élite 2" to "pro b|elite 2", "Coupe de France" to "coupe de france", "Ligue féminine (Wonderligue)" to "wonderligue|ligue feminine|lfb", "Leaders Cup" to "leaders cup")
        add(basket, EU, "Euroligue" to "euroligue(?! feminine)|euroleague(?! women)", "Euroligue féminine" to "euroligue feminine", "EuroCoupe" to "eurocoupe|eurocup", "Ligue des champions (BCL)" to "ligue des champions|bcl", "Europe Cup" to "europe cup")
        add(basket, WORLD, "EuroBasket féminin" to "eurobasket feminin|euro basket feminin|euro feminin", "Coupe du monde féminine" to "coupe du monde feminine",
            "Jeux olympiques" to "jeux olympiques|olympi\\w*", "EuroBasket" to "eurobasket|euro basket|championnat d europe", "Coupe du monde" to "coupe du monde|mondial", "Basket 3x3" to "3x3|world tour")

        val hand = "Handball"
        add(hand, FR, "Starligue" to "starligue|lidl starligue|liqui moly starligue", "Proligue" to "proligue", "Ligue féminine (Butagaz)" to "butagaz|ligue feminine|lfh", "Coupe de France" to "coupe de france", "Trophée des champions" to "trophee des champions")
        add(hand, EU, "Ligue des champions féminine" to "ligue des champions feminine", "Ligue des champions" to "ligue des champions", "Ligue européenne" to "ligue europeenne|european league", "Euro féminin" to "euro feminin|championnat d europe feminin", "Euro" to "euro|championnat d europe")
        add(hand, WORLD, "Championnat du monde féminin" to "(championnat du monde|mondial) feminin", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Championnat du monde" to "championnat du monde|mondial|coupe du monde", "Matchs internationaux" to "matchs? internationaux|amical|golden league|qualifications")

        val volley = "Volley-ball"
        add(volley, FR, "Ligue A féminine (Saforelle)" to "ligue a feminine|saforelle|laf", "Marmara SpikeLigue (Ligue A)" to "spikeligue|ligue a|alterna|volstar|ligue am", "Coupe et Supercoupe de France" to "coupe de france|supercoupe")
        add(volley, EU, "Ligue des champions" to "ligue des champions", "Euro" to "euro|championnat d europe")
        add(volley, WORLD, "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Ligue des nations" to "ligue des nations|vnl", "Championnat du monde" to "championnat du monde|mondial|coupe du monde")

        val tennis = "Tennis"
        add(tennis, WORLD,
            "Open d'Australie" to "open d australie|australian open", "Roland-Garros" to "roland garros", "Wimbledon" to "wimbledon", "US Open" to "us open",
            "ATP Finals" to "atp finals|masters de fin", "WTA Finals" to "wta finals", "Masters 1000" to "masters 1000|rolex|indian wells|miami|monte carlo|madrid|rome|cincinnati|shanghai|paris bercy|montreal|toronto",
            "Coupe Davis" to "coupe davis|davis cup", "Billie Jean King Cup" to "billie jean king", "Laver Cup et United Cup" to "laver cup|united cup", "Jeux olympiques" to "jeux olympiques|olympi\\w*",
            "Tournois ATP" to "atp", "Tournois WTA" to "wta",
        )

        add("Formule 1", WORLD, "Grands Prix de Formule 1" to ".*")
        add("Moto", WORLD, "MotoGP, Moto2, Moto3" to "moto ?gp|moto ?2|moto ?3|grand prix", "Superbike" to "superbike|wsbk", "Motocross et Supercross" to "motocross|supercross|mxgp")
        add("Rallye", WORLD, "WRC" to "wrc|championnat du monde", "Dakar" to "dakar", "ERC et rallyes nationaux" to "erc|championnat de france|championnat d europe")
        add("Sport auto", WORLD, "24 Heures du Mans et WEC" to "24 heures du mans|wec|endurance|6 heures|8 heures|4 heures", "Formule E" to "formule e|formula e", "IndyCar et NASCAR" to "indycar|nascar|indy")

        val velo = "Cyclisme"
        add(velo, FR, "Tour de France" to "tour de france(?! singapore| saitama)", "Paris-Nice" to "paris nice", "Critérium du Dauphiné" to "dauphine", "Paris-Roubaix" to "paris roubaix", "Coupe de France" to "coupe de france")
        add(velo, WORLD, "Tour d'Italie" to "tour d italie|giro", "Tour d'Espagne" to "tour d espagne|vuelta", "Classiques" to "classiques?|milan san remo|tour des flandres|liege bastogne|amstel|fleche wallonne|tour de lombardie|strade bianche|gand wevelgem",
            "Championnats du monde" to "championnats? du monde", "Cyclo-cross" to "cyclo cross|superprestige|x2o|coupe du monde de cyclo", "VTT" to "vtt|cross country|descente", "Piste" to "piste")

        add("Golf", WORLD, "Majeurs" to "majeurs?|masters|us open|british open|the open|pga championship|uspga", "Ryder Cup et Solheim Cup" to "ryder cup|solheim", "PGA Tour" to "pga tour|circuit americain|open de|championnat d", "DP World Tour" to "dp world|circuit europeen", "LIV Golf" to "liv")
        add("Hockey", US, "NHL" to "nhl")
        add("Hockey", FR, "Ligue Magnus" to "ligue magnus|magnus", "Coupe de France" to "coupe de france")
        add("Hockey", WORLD, "Championnat du monde" to "championnat du monde|mondial")
        add("Football américain", US, "NFL" to "nfl|super bowl", "NCAA" to "ncaa")
        add("Baseball", US, "MLB" to "mlb|world series")
        add("Boxe", WORLD, "Championnats du monde" to "championnats? du monde|wbc|wba|ibf|wbo", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Galas et réunions" to ".*")
        add("MMA", WORLD, "UFC" to "ufc", "PFL" to "pfl", "KSW, Hexagone et autres" to ".*")
        add("Ski", WORLD, "Tournée des quatre tremplins" to "4 tremplins|quatre tremplins", "Saut à ski" to "saut|tremplins?|vol a ski",
            "Championnats du monde" to "championnats? du monde", "Coupe du monde de ski alpin" to "coupe du monde|ski alpin|slalom|descente|geant|super g")
        add("Biathlon", WORLD,
            "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Championnats du monde juniors et jeunes" to "(championnats?|mondiaux) (du monde )?(juniors?|jeunes)|juniors?|jeunes",
            "Championnats du monde" to "championnats? du monde|mondiaux", "Championnats d'Europe" to "championnats? d europe|europeens",
            "IBU Cup" to "ibu cup|coupe ibu", "Coupe du monde" to "coupe du monde|world cup",
            "Championnats de France et Samse Tour" to "championnats? de france|samse|national tour",
            "Biathlon d'été et courses de préparation" to "ete|pre saison|preparation|city biathlon|nordic festival|world team challenge|sur route|ski roues|loop one|blink",
            "Autres courses" to ".*",
        )
        add("Athlétisme", WORLD, "Diamond League" to "diamond league", "Championnats du monde" to "championnats? du monde", "Championnats d'Europe" to "championnats? d europe", "Marathons" to "marathons?")
        add("Natation", WORLD, "Championnats du monde" to "championnats? du monde", "Championnats d'Europe" to "championnats? d europe")
        add("Judo", WORLD, "Grand Slam et Grand Prix" to "grand slam|grand chelem|grand prix", "Championnats du monde" to "championnats? du monde")
        add("Snooker", WORLD, "Championnat du monde" to "championnat du monde", "Masters et Opens" to ".*")

        add("Rugby à XIII", WORLD, "Coupe du monde" to "coupe du monde", "Super League" to "super league", "NRL" to "nrl", "Élite 1 (France)" to "elite 1|championnat de france", "Challenge Cup" to "challenge cup")
        add("Monoplace & circuits", WORLD, "Formule 2 et Formule 3" to "formule 2|formule 3|f2|f3", "Formule E" to "formule e|formula e", "IndyCar" to "indycar|indy", "NASCAR" to "nascar")
        add("Moto", FR, "Championnat de France et Coupes de France" to "championnat de france|coupe de france", "Bol d'Or et 24 Heures Motos" to "bol d or|24 heures motos|endurance")
        add("Rallye", WORLD, "Rallycross et rallye-raid" to "rallycross|rallye raid|w2rc")
        add("Sport auto", WORLD, "GT, DTM et tourisme" to "gt world|dtm|tourisme|gt4|porsche|ferrari challenge", "European Le Mans Series" to "le mans series|elms", "Karting" to "karting")
        add("Cyclisme", WORLD, "Championnats d'Europe" to "championnats? d europe", "Courses par étapes (Suisse, Romandie, Pays basque, Catalogne, UAE…)" to "tour de suisse|tour de romandie|pays basque|catalogne|uae tour|tirreno|tour down under|tour de pologne|benelux|tour de grande bretagne",
            "Critériums" to "criteriums?", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "BMX" to "bmx")
        add("Golf", WORLD, "TGL et WTGL" to "w?tgl", "LPGA et circuit féminin" to "lpga|ladies|evian", "Jeux olympiques" to "jeux olympiques|olympi\\w*")
        add("Hockey", EU, "Ligue des champions (CHL)" to "ligue des champions|chl", "KHL" to "khl", "Jeux olympiques" to "jeux olympiques|olympi\\w*")
        add("Hockey", WORLD, "Hockey sur gazon" to "gazon|pro league|fih")
        add("Baseball", WORLD, "World Baseball Classic" to "classic|coupe du monde")
        add("Football américain", WORLD, "Ligue européenne (ELF) et France" to "elf|european league|championnat de france|casque de diamant")
        add("Kickboxing", WORLD, "ONE Championship" to "one", "Glory" to "glory", "K-1 et autres galas" to ".*")
        add("Judo", WORLD, "Ligue des champions" to "ligue des champions", "Championnats d'Europe" to "championnats? d europe", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Championnat de France" to "championnats? de france")
        add("Sports de combat", WORLD, "Karaté" to "karate", "Taekwondo" to "taekwondo", "Lutte" to "lutte", "Autres galas" to ".*")
        add("Catch", US, "WWE" to "wwe|raw|smackdown|nxt|wrestlemania", "AEW" to "aew|dynamite|collision", "Autres shows" to ".*")
        add("Ski", WORLD, "Ski de fond" to "ski de fond|tour de ski", "Ski freestyle et snowboard" to "freestyle|snowboard|ski cross|bosses",
            "Combiné nordique" to "combine nordique", "Jeux olympiques d'hiver" to "jeux olympiques|olympi\\w*", "Autres épreuves (Levi, Kitzbühel, Sölden…)" to ".*")
        add("Patinage", WORLD, "Grand Prix ISU" to "grand prix|isu|skate america|skate canada|nhk|internationaux de france", "Championnats du monde" to "championnats? du monde",
            "Championnats d'Europe" to "championnats? d europe", "Patinage de vitesse et short-track" to "vitesse|short track|coupe du monde", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Galas et autres épreuves" to ".*")
        add("Athlétisme", WORLD, "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Meetings et cross" to "meeting|cross|indoor|salle", "Championnats de France" to "championnats? de france")
        add("Natation", WORLD, "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Coupe du monde et World Series" to "coupe du monde|world series", "Water-polo et plongeon" to "water polo|plongeon",
            "Championnats de France" to "championnats? de france")
        add("Équitation", WORLD, "Coupe du monde de saut d'obstacles" to "coupe du monde.*saut|coupe du monde fei|csi ?5 ?w", "Coupe du monde de dressage" to "dressage", "Coupe des nations" to "coupe des nations",
            "Global Champions Tour et League" to "global champions", "Grands Prix Rolex et Majeurs" to "rolex|spruce meadows|aix la chapelle|chi de geneve", "Saut Hermès" to "hermes",
            "Jumpings et CSI" to "jumping|csi|derby|equita|grand prix|cross indoor|caiw|grand national|printemps des sports", "Courses hippiques" to "quinte|prix d amerique|arc de triomphe|trot|galop|courses?",
            "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Autres concours" to ".*")
        add("Sports nautiques", WORLD, "Coupe de l'America" to "coupe de l america|america s cup", "SailGP" to "sailgp|sail gp", "Vendée Globe et courses au large" to "vendee globe|route du rhum|transat|ocean race|solitaire|jules verne",
            "F1 motonautique et XCAT" to "formule 1|f1h2o|xcat|motonautisme|powerboat|grand prix", "Aviron et canoë-kayak" to "aviron|canoe|kayak|coupe du monde de sprint", "Surf et glisse" to "surf|kite|windsurf|wsl", "Autres régates" to ".*")
        add("Padel", WORLD, "Premier Padel (Majors, P1, P2)" to "premier padel|major|p1|p2|finals", "Autres tournois" to ".*")
        add("Squash", WORLD, "PSA World Tour (US Open, British Open…)" to ".*")
        add("Badminton", WORLD, "BWF World Tour" to "bwf|open|masters", "Championnats du monde et d'Europe" to "championnats?")
        add("Tennis de table", WORLD, "WTT" to "wtt|smash|champions|contender", "Championnats du monde et d'Europe" to "championnats?", "Pro A (France)" to "pro a|championnat de france")
        add("Trail", WORLD, "UTMB World Series" to "utmb", "Golden Trail World Series" to "golden trail", "Autres courses" to ".*")
        add("Triathlon", WORLD, "T100" to "t100", "WTCS et championnats du monde" to "wtcs|world triathlon|championnats? du monde", "Ironman" to "ironman", "Autres épreuves" to ".*")
        add("Fléchettes", WORLD, "Championnat du monde PDC" to "championnat du monde|world championship", "Premier League Darts" to "premier league", "Autres tournois" to ".*")
        add("Pétanque", FR, "Masters et Passion Pétanque Française" to "masters|ppf|passion petanque", "Championnats" to "championnats?|trophee|mondial", "Autres concours" to ".*")
        add("E-sport", WORLD, "Sim-racing" to "sim racing|gran turismo|iracing", "League of Legends" to "league of legends|lol|lec|lfl|worlds", "Counter-Strike, Valorant et autres" to ".*")
        add("Multisports", WORLD, "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Jeux méditerranéens, européens et mondiaux" to "jeux mediterraneens|jeux europeens|jeux mondiaux|universiade", "Autres événements" to ".*")
        add("Handisport", WORLD, "Jeux paralympiques" to "paralympi\\w*", "Championnats du monde et d'Europe" to "championnats?", "Autres épreuves" to ".*")
        add("Gymnastique", WORLD, "Championnats du monde et d'Europe" to "championnats?", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Coupe du monde et Internationaux de France" to ".*")
        add("Escrime", WORLD, "Championnats du monde et d'Europe" to "championnats?", "Grand Prix et Coupe du monde" to ".*")
        add("Force", WORLD, "Force athlétique et haltérophilie" to ".*")
        add("Sports extrêmes", WORLD, "X Games, skate et freeride" to ".*")
        add("Escalade", WORLD, "Coupe du monde et championnats" to ".*")
        add("Poker", WORLD, "WSOP, EPT et tournois" to ".*")
        add("Ski de fond", WORLD, "Tour de Ski" to "tour de ski", "Coupe du monde" to "coupe du monde", "Championnats du monde" to "championnats? du monde|mondiaux",
            "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Combiné nordique" to "combine nordique", "Courses longue distance" to "courses? longue distance|transjurassienne|vasaloppet|marcialonga|ski classics", "Autres courses" to ".*")
        add("Curling", WORLD, "Championnats du monde et d'Europe" to "championnats?", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Autres tournois" to ".*")
        add("Bobsleigh, luge et skeleton", WORLD, "Coupe du monde" to "coupe du monde", "Championnats du monde" to "championnats? du monde", "Jeux olympiques" to "jeux olympiques|olympi\\w*", "Autres épreuves" to ".*")
        add("Hockey sur gazon", WORLD, "Pro League et championnats" to ".*")
        add("Cricket", WORLD, "Coupe du monde et tests" to ".*")
        add("Tir et tir à l'arc", WORLD, "Coupe du monde et championnats" to ".*")
        add("Pelote basque", FR, "Championnats et masters" to ".*")
        add("Pentathlon moderne", WORLD, "Coupe du monde et championnats" to ".*")
        add("Échecs", WORLD, "Championnats et tournois" to ".*")
        add("Futsal", WORLD, "Championnat de France et Coupes d'Europe" to ".*")
        add("Beach soccer", WORLD, "Championnats et Coupe du monde" to ".*")
        add("Beach-volley", WORLD, "Pro Tour et championnats" to ".*")
        add("Tennis", FR, "Tournois français (Challengers, Open)" to "tournois? francais|open d|orleans|challenger|play offs?|interclubs")
        add("Basket-ball", EU, "Liga ACB (Espagne)" to "acb|liga endesa", "Championnats étrangers" to "championnats? etrangers|championnat d (italie|allemagne|espagne)|lega|bbl")
        add("Handball", EU, "Bundesliga (Allemagne)" to "bundesliga", "Liga Asobal (Espagne)" to "asobal")
        add("Volley-ball", EU, "SuperLega (Italie)" to "superlega|championnat d italie", "PlusLiga (Pologne)" to "plusliga", "CEV Cup et Challenge Cup" to "cev|challenge cup")
        add("Rugby", EU, "Autres compétitions féminines" to "feminine?s?")
    }

    private val bySport: Map<String, List<Competition>> = list.groupBy { norm(it.sport) }
    private val byId: Map<String, Competition> = list.associateBy { "c:" + it.id }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Sports qui ont un catalogue, dans l'ordre d'affichage. */
    val sports: List<String> = list.map { it.sport }.distinct()

    fun of(sport: String): List<Competition> = bySport[norm(sport)] ?: emptyList()

    fun byId(id: String): Competition? = byId[id]
        ?: id.removePrefix("c:").takeIf { it.endsWith("-non-precisee") }?.let { Competition(it, "", OTHER, "Compétition non précisée par le guide", "$^") }

    /**
     * Identifiant stable de la compétition d'une diffusion : « c:… » pour une entrée du catalogue,
     * « dyn:… » pour une compétition vue dans le guide mais absente du catalogue.
     */
    fun idOf(sport: String, competition: String): String {
        val key = competitionKey(sport, competition)
        return cache.getOrPut(key) {
            val text = norm(competition)
            // Le guide n'indique parfois que le sport : entrée dédiée, cochable comme les autres.
            if (text.isEmpty() || text == norm(sport)) return@getOrPut "c:" + unspecified(sport)
            val found = of(sport).firstOrNull { it.regex.containsMatchIn(text) }
            if (found != null) "c:" + found.id else "dyn:$key"
        }
    }

    private fun unspecified(sport: String) = norm(sport).replace(' ', '-') + "-non-precisee"

    /** Entrées complètes d'un sport, y compris « compétition non précisée ». */
    fun choices(sport: String): List<Competition> =
        of(sport) + Competition(unspecified(sport), sport, OTHER, "Compétition non précisée par le guide", "$^")

    fun isCatalogued(id: String) = id.startsWith("c:")
}
