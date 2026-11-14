package fr.perso.sportantenne.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class XmltvParserTest {
    private fun prog(
        title: String, sub: String? = null, cats: List<String>, channel: String = "c1",
        start: String = "20261114210000 +0100", stop: String = "20261114230000 +0100", extra: String = "",
    ) = buildString {
        append("<programme start=\"$start\" stop=\"$stop\" channel=\"$channel\">\n")
        append("    <title lang=\"fr\">$title</title>\n")
        if (sub != null) append("    <sub-title lang=\"fr\">$sub</sub-title>\n")
        cats.forEach { append("    <category lang=\"fr\">$it</category>\n") }
        append(extra)
        append("  </programme>\n")
    }

    private fun parse(vararg progs: String): List<Broadcast> {
        val xml = "<tv>\n<channel id=\"c1\">\n<display-name>Chaîne &amp; Co</display-name>\n" +
            "<icon src=\"https://x/logo.png\"/>\n</channel>\n" + progs.joinToString("") + "</tv>\n"
        return XmltvParser.parse(xml.byteInputStream()).broadcasts
    }

    @Test fun titleWithCompetitionAndTeams() {
        val b = parse(prog("Football : Ligue 1 McDonald&apos;s | Paris-SG / Troyes", cats = listOf("Football"))).single()
        assertEquals("Football", b.sport)
        assertEquals("Ligue 1 McDonald's", b.competition)
        assertEquals("Paris-SG", b.home)
        assertEquals("Troyes", b.away)
        assertEquals(Kind.EVENT, b.kind)
    }

    @Test fun teamsInSubtitle() {
        val b = parse(prog("Rugby : Top 14", "9e journée : Pau / Perpignan", listOf("Rugby"))).single()
        assertEquals("Top 14", b.competition)
        assertEquals("Pau", b.home)
        assertEquals("Perpignan", b.away)
        assertEquals("9e journée", b.detail)
    }

    @Test fun teamsInTitleCompetitionInSubtitle() {
        val b = parse(prog("Villarreal / Paris-SG", "Ligue des champions - 4e journée", listOf("Sport", "Football"))).single()
        assertEquals("Football", b.sport)
        assertEquals("Ligue des champions", b.competition)
        assertEquals("Villarreal", b.home)
        assertEquals("4e journée", b.detail)
    }

    @Test fun sportAndCompetitionInSubtitle() {
        val b = parse(prog("Naples / AS Roma", "Football. Serie A 8e journée.", listOf("Football", "Emission sportive"))).single()
        assertEquals("Football", b.sport)
        assertEquals("Serie A", b.competition)
        assertEquals("AS Roma", b.away)
        val r = parse(prog("Irlande / Afrique du Sud", "Rugby. Nations Championship. 6e journée.", listOf("Sport"))).single()
        assertEquals("Rugby", r.sport)
        assertEquals("Nations Championship", r.competition)
        assertEquals("6e journée", r.detail)
    }

    @Test fun dashSeparatedOpponents() {
        val b = parse(prog("Diego Elias - Paul Coll", "Squash. US Open Finale messieurs.", listOf("Squash", "Emission sportive"))).single()
        assertEquals("Squash", b.sport)
        assertEquals("Diego Elias", b.home)
        assertEquals("Paul Coll", b.away)
    }

    @Test fun eventWithoutTeams() {
        val b = parse(prog("Formule 1 : Grand Prix du Brésil", "La course", listOf("Formule 1"))).single()
        assertEquals("Formule 1", b.sport)
        assertEquals("Grand Prix du Brésil", b.competition)
        assertEquals("La course", b.detail)
        assertTrue(b.home.isEmpty())
    }

    @Test fun magazinesAndFillersAreNotEvents() {
        val list = parse(
            prog("L'Equipe du soir", cats = listOf("Sport")),
            prog("Rugby : Championnat des nations, le mag", cats = listOf("Magazine sportif")),
            prog("Football", cats = listOf("Football")),
            prog("Programme terminé", cats = listOf("Sport")),
            prog("Le journal de 20h", cats = listOf("Journal")),
        )
        assertTrue(list.all { it.kind == Kind.MAGAZINE })
        assertEquals(listOf("L'Equipe du soir", "Rugby : Championnat des nations, le mag", "Football").sorted(), list.map { it.title }.sorted())
    }

    @Test fun pastProgrammesAreDropped() {
        val xml = "<tv>" + prog("Football : Ligue 2 | Pau / Nantes", cats = listOf("Football")) + "</tv>"
        val stop = XmltvParser.parseTime("20261114230000 +0100")!!
        assertTrue(XmltvParser.parse(xml.byteInputStream(), keepAfter = stop).broadcasts.isEmpty())
        assertEquals(1, XmltvParser.parse(xml.byteInputStream(), keepAfter = stop - 1).broadcasts.size)
    }

    @Test fun timeParsing() {
        assertEquals(XmltvParser.parseTime("20261114200000 +0000"), XmltvParser.parseTime("20261114210000 +0100"))
        assertNull(XmltvParser.parseTime("oops"))
    }

    @Test fun simultaneousBroadcastsMergeAndLaterOnesAreReplays() {
        val t = "Football : Ligue 1 | Lille / Lyon"
        val events = Schedule.build(
            parse(
                prog(t, cats = listOf("Football"), channel = "a"),
                prog(t, cats = listOf("Football"), channel = "b", start = "20261114210500 +0100"),
                prog(t, cats = listOf("Football"), channel = "a", start = "20261115030000 +0100", stop = "20261115050000 +0100"),
            ),
        )
        assertEquals(2, events.size)
        assertEquals(listOf("a", "b"), events[0].channels)
        assertFalse(events[0].replay)
        assertTrue(events[1].replay)
    }

    @Test fun storeRoundTrip() {
        val guide = XmltvParser.parse(
            ("<tv><channel id=\"c1\">\n<display-name>Une</display-name>\n</channel>\n" +
                prog("Rugby : Top 14", "9e journée : Pau / Perpignan", listOf("Rugby")) + "</tv>").byteInputStream(),
        )
        val dir = File(System.getProperty("java.io.tmpdir"), "sa-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            GuideStore.save(dir, guide)
            val back = GuideStore.load(dir)!!
            assertEquals(guide.broadcasts, back.broadcasts)
            assertEquals(guide.channels, back.channels)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Charge les calendriers téléchargés dans FIXTURES_DIR (soc_JJ.json, nba.json, nfl.json, nhl.json, rug_*.json, f1.json). */
    private fun realFixtures(): Fixtures {
        // Avec LIVE_SOURCES=1, les vrais calendriers en ligne, exactement comme l'appli les lit.
        if (System.getenv("LIVE_SOURCES") == "1") return fr.perso.sportantenne.Repo.downloadFixtures(System.currentTimeMillis())
        val dir = File(System.getenv("FIXTURES_DIR") ?: return Fixtures.EMPTY)
        val list = ArrayList<Fixture>()
        val days = HashMap<String, MutableSet<String>>()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val source = when {
                f.name.startsWith("soc_") -> "football"
                f.name.startsWith("rug_") -> "rugby"
                f.name in setOf("nba.json", "nfl.json", "nhl.json") -> f.name.removeSuffix(".json")
                f.name == "f1.json" -> "f1"
                else -> return@forEach
            }
            val parsed = try { if (source == "f1") FixtureParsers.formula1(f.readText()) else FixtureParsers.espn(f.readText(), source) } catch (e: Exception) { emptyList() }
            list += parsed
            val set = days.getOrPut(source) { HashSet() }
            if (f.name.startsWith("soc_")) set += "2026-11-" + f.name.substring(4, 6)
            else (1..30).forEach { set += "2026-11-%02d".format(it) }
        }
        return Fixtures(list, days)
    }

    /** Contrôle sur un vrai guide et de vrais calendriers. */
    @Test fun realGuideReport() {
        val path = System.getenv("GUIDE_GZ")
        assumeTrue(path != null && File(path).exists())
        val guide = GZIPInputStream(File(path!!).inputStream()).use { XmltvParser.parse(it) }
        val fixtures = realFixtures()
        System.getenv("FIXTURES_OUT")?.let { fixtures.save(File(it).apply { mkdirs() }) }
        val check = LiveCheck(fixtures)
        val events = Schedule.build(guide.broadcasts, check)
        val names = guide.channels.associate { it.id to it.name }
        val sb = StringBuilder()
        val ev = events.filter { it.kind == Kind.EVENT }
        sb.appendLine("fixtures=${fixtures.list.size} ${fixtures.list.groupingBy { it.source }.eachCount()}")
        sb.appendLine("EVENT=${ev.size} shown=${ev.count { !it.replay }} verified=${ev.count { it.verified }} withTeams=${ev.count { it.hasTeams }}")
        val firsts = guide.broadcasts.filter { it.kind == Kind.EVENT && it.home.isNotBlank() }
        val byVerdict = firsts.groupBy { (LiveCheck.sourceOf(it) ?: "-") + " " + check.verdict(it) }
        sb.appendLine("VERDICTS " + byVerdict.mapValues { it.value.size }.toSortedMap())
        fun line(e: Event) = "  ${java.text.SimpleDateFormat("EEE dd HH:mm").format(e.start)} | ${e.sport} | ${e.competition} | ${e.headline} | ${e.detail} | ${e.channels.map { c -> names[c] }}"
        sb.appendLine("PAR SPORT (affichés : confirmés / non confirmés) " + ev.filter { !it.replay }.groupBy { it.sport }
            .entries.sortedByDescending { it.value.size }.joinToString { "${it.key}=${it.value.count { e -> e.verified }}/${it.value.count { e -> !e.verified }}" })
        for (sport in listOf("Football", "Rugby", "Basket-ball", "Formule 1", "Football américain", "Hockey", "Tennis", "Golf", "MMA")) {
            sb.appendLine("== $sport VERIFIED"); ev.filter { it.sport == sport && it.verified }.forEach { sb.appendLine(line(it)) }
            sb.appendLine("== $sport UNVERIFIED BUT SHOWN"); ev.filter { it.sport == sport && !it.verified && !it.replay }.forEach { sb.appendLine(line(it)) }
            sb.appendLine("== $sport REPLAY (sample)"); ev.filter { it.sport == sport && it.replay }.shuffled(java.util.Random(3)).take(25).forEach { sb.appendLine(line(it)) }
        }
        sb.appendLine("== OTHER SPORTS SHOWN"); ev.filter { it.sport !in setOf("Football", "Rugby", "Basket-ball", "Formule 1", "Football américain", "Hockey") && !it.replay }.forEach { sb.appendLine(line(it)) }
        // Couverture du catalogue : compétitions du guide réel qui n'y sont pas rattachées.
        sb.appendLine("== HORS CATALOGUE")
        ev.groupBy { it.sport to Competitions.idOf(it.sport, it.competition) }.filter { !Competitions.isCatalogued(it.key.second) }
            .entries.sortedWith(compareBy({ it.key.first }, { -it.value.size }))
            .forEach { sb.appendLine("  ${it.key.first} | ${it.value[0].competition} | ${it.value.size}") }
        sb.appendLine("== RATTACHEMENTS")
        ev.groupBy { Triple(it.sport, it.competition, Competitions.byId(Competitions.idOf(it.sport, it.competition))?.let { c -> c.country.substringAfter(' ') + " / " + c.name }) }
            .filter { it.key.third != null }.entries.sortedWith(compareBy({ it.key.first }, { it.key.third }))
            .forEach { sb.appendLine("  ${it.key.first} | ${it.key.second} -> ${it.key.third} | ${it.value.size}") }
        File(System.getenv("GUIDE_REPORT") ?: "build/guide-report.txt").writeText(sb.toString())
        assertTrue(ev.size > 200)
    }
}
