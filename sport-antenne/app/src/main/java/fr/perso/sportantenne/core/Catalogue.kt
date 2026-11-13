package fr.perso.sportantenne.core

import java.io.File

/**
 * Mémoire des équipes, joueurs et compétitions déjà vus dans le guide.
 * Le guide ne couvre que quelques jours : le catalogue s'enrichit à chaque mise à jour
 * pour que la liste des favoris à cocher reste complète.
 */
class Catalogue(
    /** clé normalisée → (nom affiché, sport) */
    val teams: Map<String, Pair<String, String>>,
    /** clé de compétition → (sport, nom affiché) */
    val competitions: Map<String, Pair<String, String>>,
) {
    fun merged(events: List<Event>): Catalogue {
        val t = HashMap(teams)
        val c = HashMap(competitions)
        for (e in events) {
            if (e.kind != Kind.EVENT) continue
            if (e.hasTeams) {
                t.putIfAbsent(norm(e.home), e.home to e.sport)
                t.putIfAbsent(norm(e.away), e.away to e.sport)
            }
            if (e.competition.isNotBlank() && norm(e.competition) != norm(e.sport)) {
                c.putIfAbsent(competitionKey(e.sport, e.competition), e.sport to e.competition)
            }
        }
        return Catalogue(t, c)
    }

    fun save(dir: File) {
        val tmp = File(dir, "catalogue.tsv.tmp")
        tmp.bufferedWriter().use { w ->
            teams.values.forEach { w.write("T\t${it.first}\t${it.second}\n") }
            competitions.values.forEach { w.write("K\t${it.first}\t${it.second}\n") }
        }
        val target = File(dir, "catalogue.tsv")
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
    }

    companion object {
        val EMPTY = Catalogue(emptyMap(), emptyMap())

        fun load(dir: File): Catalogue {
            val f = File(dir, "catalogue.tsv")
            if (!f.exists()) return EMPTY
            val t = HashMap<String, Pair<String, String>>()
            val c = HashMap<String, Pair<String, String>>()
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 3) {
                    if (p[0] == "T") t[norm(p[1])] = p[1] to p[2]
                    else if (p[0] == "K") c[competitionKey(p[1], p[2])] = p[1] to p[2]
                }
            }
            return Catalogue(t, c)
        }
    }
}
