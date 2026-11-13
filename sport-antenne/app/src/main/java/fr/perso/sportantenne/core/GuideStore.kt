package fr.perso.sportantenne.core

import java.io.File

/** Cache local du guide filtré, au format texte tabulé (une diffusion par ligne). */
object GuideStore {
    private fun esc(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun save(dir: File, guide: Guide) {
        val tmp = File(dir, "guide.tsv.tmp")
        tmp.bufferedWriter().use { w ->
            for (c in guide.channels) {
                w.write("C\t${esc(c.id)}\t${esc(c.name)}\t${esc(c.logo)}\n")
            }
            for (b in guide.broadcasts) {
                w.write(
                    listOf(
                        "B", b.start, b.stop, esc(b.channelId), esc(b.sport), esc(b.competition), esc(b.title),
                        esc(b.home), esc(b.away), esc(b.detail), esc(b.desc), b.kind.name,
                        if (b.previouslyShown) "1" else "0",
                    ).joinToString("\t"),
                )
                w.write("\n")
            }
        }
        val target = File(dir, "guide.tsv")
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun load(dir: File): Guide? {
        val f = File(dir, "guide.tsv")
        if (!f.exists()) return null
        val channels = ArrayList<Channel>()
        val broadcasts = ArrayList<Broadcast>()
        f.forEachLine { line ->
            val p = line.split('\t')
            if (p[0] == "C" && p.size >= 4) {
                channels += Channel(p[1], p[2], p[3])
            } else if (p[0] == "B" && p.size >= 13) {
                val start = p[1].toLongOrNull()
                val stop = p[2].toLongOrNull()
                if (start != null && stop != null) {
                    broadcasts += Broadcast(
                        start, stop, p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10],
                        if (p[11] == Kind.MAGAZINE.name) Kind.MAGAZINE else Kind.EVENT, p[12] == "1",
                    )
                }
            }
        }
        return Guide(channels, broadcasts)
    }

    fun lastUpdate(dir: File): Long = File(dir, "guide.tsv").let { if (it.exists()) it.lastModified() else 0L }
}
