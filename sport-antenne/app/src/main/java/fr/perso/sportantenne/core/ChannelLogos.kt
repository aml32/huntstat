package fr.perso.sportantenne.core

/**
 * Logos des chaînes. Le guide TV fournit une adresse par chaîne, mais sans garantie de chargement ;
 * pour les chaînes sportives, une table de logos dont l'existence a été vérifiée un par un
 * (dépôt public tv-logo/tv-logos) passe en premier. L'écran essaie les adresses dans l'ordre.
 */
object ChannelLogos {
    private const val BASE = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/"

    /** Identifiant de chaîne du guide → fichier vérifié. */
    private val BY_ID = mapOf(
        "ABMoteurs.fr" to "france/automoto-la-chaine-fr",
        "ABXploreFR.fr" to "france/abx-fr",
        "BFMGrandLille.fr" to "france/bfm-grand-lille-fr",
        "BFMGrandLittoral.fr" to "france/bfm-grand-littoral-fr",
        "CanalPlus.fr" to "france/canal-plus-fr",
        "CanalPlusDocs.fr" to "france/canal-plus-docs-fr",
        "CanalPlusFoot.fr" to "france/canal-plus-foot-fr",
        "CanalPlusPremierLeague.fr" to "france/canal-plus-premier-league-fr",
        "CanalPlusSeries.fr" to "france/canal-plus-series-fr",
        "CanalPlusSport.fr" to "france/canal-plus-sport-fr",
        "CanalPlusSport360.fr" to "france/canal-plus-sport-360-fr",
        "ES1.fr" to "france/mgg-tv-fr",
        "Equidia.fr" to "france/equidia-fr",
        "Eurosport1.fr" to "france/eurosport-1-fr",
        "Eurosport2.fr" to "france/eurosport-2-fr",
        "France24.fr" to "france/france-24-fr",
        "France3.fr" to "france/france-3-fr",
        "France4.fr" to "france/france-4-fr",
        "GolfPlus.fr" to "france/golf-plus-fr",
        "Gulli.fr" to "france/gulli-fr",
        "LEquipe21.fr" to "france/lequipe-fr",
        "Ligue1Plus.fr" to "france/ligue-1plus-fr",
        "Ligue1Plus10.fr" to "france/ligue-1plus-10-fr",
        "Ligue1Plus2.fr" to "france/ligue-1plus-2-fr",
        "Ligue1Plus3.fr" to "france/ligue-1plus-3-fr",
        "Ligue1Plus4.fr" to "france/ligue-1plus-4-fr",
        "Ligue1Plus5.fr" to "france/ligue-1plus-5-fr",
        "Ligue1Plus6.fr" to "france/ligue-1plus-6-fr",
        "Ligue1Plus7.fr" to "france/ligue-1plus-7-fr",
        "Ligue1Plus8.fr" to "france/ligue-1plus-8-fr",
        "Ligue1Plus9.fr" to "france/ligue-1plus-9-fr",
        "MotorVisionTV.fr" to "france/motorvision-tv-fr",
        "NT1.fr" to "france/tfx-fr",
        "PlaneteAction.fr" to "france/planete-plus-aventure-fr",
        "RMCSport1.fr" to "france/rmc-sport-1-fr",
        "RMCSport2.fr" to "france/rmc-sport-2-fr",
        "RMCSport3.fr" to "france/rmc-sport-3-fr",
        "SportEnFrance.fr" to "france/sport-en-france-fr",
        "TF1.fr" to "france/tf1-fr",
        "TV5Monde.fr" to "france/tv5-monde-fr",
        "TeleLyonMetropole.fr" to "france/bfm-lyon-fr",
        "Toonami.fr" to "france/warner-tv-next-fr",
        "Trek.fr" to "france/trek-fr",
    )

    /** Familles de chaînes sans fichier propre : le logo de la marque. */
    private val BY_NAME: List<Pair<Regex, String>> = listOf(
        "bein" to "france/bein-sports-fr",
        "equipe" to "france/lequipe-fr",
        "dazn" to "international/dazn-int",
        "infosport" to "france/infosport-plus-fr",
        "eurosport 2" to "france/eurosport-2-fr",
        "eurosport" to "france/eurosport-1-fr",
        "rmc sport 2" to "france/rmc-sport-2-fr",
        "rmc sport" to "france/rmc-sport-1-fr",
        "canal sport 360" to "france/canal-plus-sport-360-fr",
        "canal foot" to "france/canal-plus-foot-fr",
        "canal sport" to "france/canal-plus-sport-fr",
        "canal" to "france/canal-plus-fr",
        "ligue 1" to "france/ligue-1plus-fr",
        "france 3" to "france/france-3-fr",
        "golf" to "france/golf-plus-fr",
        "equidia" to "france/equidia-fr",
        "automoto" to "france/automoto-la-chaine-fr",
        "sport en france" to "france/sport-en-france-fr",
    ).map { Regex("\\b${it.first}\\b") to it.second }

    /** Adresses à essayer, de la plus sûre à la moins sûre. */
    fun candidates(channel: Channel?, id: String): List<String> {
        val out = ArrayList<String>(3)
        BY_ID[id]?.let { out += "$BASE$it.png" }
        if (out.isEmpty()) {
            val name = norm(channel?.name ?: id)
            BY_NAME.firstOrNull { it.first.containsMatchIn(name) }?.let { out += "$BASE${it.second}.png" }
        }
        channel?.logo?.takeIf { it.startsWith("https://") }?.let { out += it }
        return out
    }

    /** Vrai si un logo vérifié existe pour cette chaîne (test et diagnostic). */
    fun hasVerified(channel: Channel?, id: String) = candidates(channel, id).firstOrNull()?.startsWith(BASE) == true
}
