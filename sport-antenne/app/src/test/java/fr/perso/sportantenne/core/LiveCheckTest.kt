package fr.perso.sportantenne.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveCheckTest {
    private val t0 = XmltvParser.parseTime("20261114210000 +0100")!! // samedi 21h, heure de Paris
    private val min = 60_000L
    private val day = Fixtures.dayOf(t0)

    private fun match(
        home: String, away: String, competition: String = "Ligue 1 McDonald's", sport: String = "Football",
        start: Long = t0, channel: String = "c1", shown: Boolean = false, detail: String = "",
    ) = Broadcast(start, start + 120 * min, channel, sport, competition, competition, home, away, detail, "", Kind.EVENT, shown)

    private fun check(vararg f: Fixture, covered: Map<String, Set<String>> = mapOf("football" to setOf(day))) =
        LiveCheck(Fixtures(f.toList(), covered))

    private fun foot(home: String, away: String, start: Long = t0 + 15 * min, league: String = "Ligue 1") =
        Fixture("football", start, home, away, league)

    @Test fun frenchNamesMatchTheCalendar() {
        val c = check(foot("Paris Saint-Germain", "Marseille"), foot("Germany", "Netherlands"), foot("Ivory Coast", "Congo DR"))
        assertEquals(Verdict.LIVE, c.verdict(match("Paris-SG", "Olympique de Marseille")))
        assertEquals(Verdict.LIVE, c.verdict(match("Allemagne", "Pays-Bas", "Ligue des Nations UEFA")))
        assertEquals(Verdict.LIVE, c.verdict(match("Côte d'Ivoire", "RD Congo", "Qualifications de la CAN")))
    }

    @Test fun homeAndAwayOrderDoesNotMatter() {
        assertEquals(Verdict.LIVE, check(foot("Lyon", "Lille")).verdict(match("Lille", "Lyon")))
    }

    @Test fun preShowBeforeKickoffIsStillLive() {
        val c = check(foot("Lille", "Lyon", start = t0 + 60 * min))
        assertEquals(Verdict.LIVE, c.verdict(match("Lille", "Lyon")))
    }

    @Test fun sameMatchAtAnotherTimeIsAReplay() {
        val c = check(foot("Lille", "Lyon", start = t0 - 26 * 60 * min))
        assertEquals(Verdict.REPLAY, c.verdict(match("Lille", "Lyon")))
    }

    @Test fun rescheduledMatchIsNoLongerLiveInItsOldSlot() {
        // Match décalé de 3 heures : le créneau annoncé par le guide ne correspond plus.
        val c = check(foot("Lille", "Lyon", start = t0 + 180 * min))
        assertEquals(Verdict.REPLAY, c.verdict(match("Lille", "Lyon")))
    }

    @Test fun coveredCompetitionWithoutFixtureIsAReplay() {
        val c = check(foot("Nice", "Lens"))
        assertEquals(Verdict.REPLAY, c.verdict(match("Toulouse", "Strasbourg")))
    }

    @Test fun nothingIsDecidedWhenTheDayWasNotDownloaded() {
        val c = check(foot("Nice", "Lens"), covered = emptyMap())
        assertEquals(Verdict.UNKNOWN, c.verdict(match("Toulouse", "Strasbourg")))
        assertEquals(Verdict.UNKNOWN, LiveCheck(Fixtures.EMPTY).verdict(match("Toulouse", "Strasbourg")))
    }

    @Test fun uncoveredCompetitionsStayUnknown() {
        val c = check(foot("Nice", "Lens"))
        assertEquals(Verdict.UNKNOWN, c.verdict(match("Rouen", "Bastia", "Ligue 3")))
        assertEquals(Verdict.UNKNOWN, c.verdict(match("OL", "Macon", "National 2")))
        assertEquals(Verdict.UNKNOWN, c.verdict(match("Chambéry", "Nîmes", "Daikin StarLigue", sport = "Handball")))
    }

    @Test fun menAndWomenAreNotConfused() {
        val c = check(foot("Marseille Women", "Paris Saint-Germain Women", league = "Première Ligue"))
        assertEquals(Verdict.REPLAY, c.verdict(match("Marseille", "Paris-SG")))
        assertEquals(Verdict.LIVE, c.verdict(match("Marseille", "Paris-SG", "Arkema Première Ligue")))
    }

    @Test fun parisFcIsNotParisSaintGermain() {
        val c = check(foot("Paris FC", "Lens"), foot("Paris Saint-Germain", "Nice", start = t0 + 24 * 60 * min))
        assertEquals(Verdict.REPLAY, c.verdict(match("Paris-SG", "Lens")))
        assertEquals(Verdict.LIVE, c.verdict(match("Paris FC", "Lens")))
    }

    @Test fun youthTeamsAreNotTheSeniorTeam() {
        val c = check(foot("France U21", "Italy U21", league = "EURO U-21 Qualifying"))
        assertEquals(Verdict.REPLAY, c.verdict(match("France", "Italie", "Ligue des Nations UEFA")))
    }

    @Test fun postponedAndCancelledFixturesAreIgnored() {
        fun event(status: String, home: String, away: String) =
            """{"date":"2026-11-14T20:15Z","competitions":[{"date":"2026-11-14T20:15Z","altGameNote":"Ligue 1",
               "status":{"type":{"name":"$status"}},"competitors":[
               {"homeAway":"home","team":{"displayName":"$home"}},{"homeAway":"away","team":{"displayName":"$away"}}]}]}"""
        val json = """{"events":[${event("STATUS_SCHEDULED", "Lille", "Lyon")},${event("STATUS_POSTPONED", "Nice", "Lens")},
            ${event("STATUS_CANCELED", "Brest", "Nantes")}]}"""
        val fixtures = FixtureParsers.espn(json, "football")
        assertEquals(listOf("Lille"), fixtures.map { it.home })
        assertEquals(t0 + 15 * min, fixtures.single().start)
        val c = LiveCheck(Fixtures(fixtures, mapOf("football" to setOf(day))))
        assertEquals(Verdict.LIVE, c.verdict(match("Lille", "Lyon")))
        assertEquals("un match reporté n'est plus un direct", Verdict.REPLAY, c.verdict(match("Nice", "Lens")))
        assertEquals("un match annulé n'est plus un direct", Verdict.REPLAY, c.verdict(match("Brest", "Nantes")))
    }

    @Test fun teamLogosFollowTheGuideOrder() {
        val json = """{"events":[{"competitions":[{"date":"2026-11-14T20:15Z","status":{"type":{"name":"STATUS_SCHEDULED"}},"competitors":[
            {"homeAway":"home","team":{"displayName":"Lyon","logo":"https://img/lyon.png"}},
            {"homeAway":"away","team":{"displayName":"Lille","logo":"https://img/lille.png"}}]}]}]}"""
        val c = LiveCheck(Fixtures(FixtureParsers.espn(json, "football"), mapOf("football" to setOf(day))))
        // Le guide annonce « Lille – Lyon » alors que le calendrier reçoit à Lyon : les logos suivent l'ordre du guide.
        val event = Schedule.build(listOf(match("Lille", "Lyon")), c).single()
        assertTrue(event.verified)
        assertEquals("https://img/lille.png", event.homeLogo)
        assertEquals("https://img/lyon.png", event.awayLogo)
        assertEquals("", Schedule.build(listOf(match("Toulouse", "Strasbourg")), c).single().homeLogo)
    }

    @Test fun scoresFollowTheGuideOrderAndTheMatchState() {
        fun event(home: String, hs: String, away: String, aw: String, state: String, detail: String) =
            """{"competitions":[{"date":"2026-11-14T20:15Z","status":{"type":{"name":"STATUS_X","state":"$state","shortDetail":"$detail"}},"competitors":[
               {"homeAway":"home","score":"$hs","team":{"displayName":"$home"}},{"homeAway":"away","score":"$aw","team":{"displayName":"$away"}}]}]}"""
        val json = """{"events":[${event("Lyon", "2", "Lille", "1", "in", "67'")},${event("Nice", "0", "Lens", "3", "post", "FT")},
            ${event("Brest", "0", "Nantes", "0", "pre", "Sat, November 14th")}]}"""
        val c = LiveCheck(Fixtures(FixtureParsers.espn(json, "football"), mapOf("football" to setOf(day))))
        // Le guide écrit « Lille – Lyon » : le score est retourné dans cet ordre.
        val lille = Schedule.build(listOf(match("Lille", "Lyon")), c).single()
        assertEquals(Score("1", "2", "in", "67'"), lille.score)
        assertEquals("1 – 2", lille.score!!.text)
        assertFalse(lille.score!!.finished)
        val nice = Schedule.build(listOf(match("Nice", "Lens")), c).single()
        assertEquals(Score("0", "3", "post", "FT"), nice.score)
        assertTrue(nice.score!!.finished)
        // Avant le coup d'envoi : pas de « 0 – 0 » trompeur.
        assertEquals(null, Schedule.build(listOf(match("Brest", "Nantes")), c).single().score)
        // Sans calendrier : pas de score.
        assertEquals(null, Schedule.build(listOf(match("Lille", "Lyon"))).single().score)
    }

    @Test fun scoresSurviveTheLocalCache() {
        val dir = File(System.getProperty("java.io.tmpdir"), "sa-sc-${System.nanoTime()}").apply { mkdirs() }
        try {
            val f = Fixture("football", t0, "Lille", "Lyon", "Ligue 1", "https://a/1.png", "https://a/2.png", "3", "2", "post", "FT")
            Fixtures(listOf(f), mapOf("football" to setOf(day))).save(dir)
            assertEquals(f, Fixtures.load(dir).list.single())
            // Un cache écrit par une version précédente (sans score) reste lisible.
            File(dir, "fixtures.tsv").writeText("F\tfootball\t$t0\tLille\tLyon\tLigue 1\n")
            assertEquals(Fixture("football", t0, "Lille", "Lyon", "Ligue 1"), Fixtures.load(dir).list.single())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun channelLogoCandidates() {
        val canal = Channel("CanalPlusSport.fr", "Canal+ Sport", "https://guide/canal.png")
        val list = ChannelLogos.candidates(canal, canal.id)
        assertTrue(list.first().endsWith("/france/canal-plus-sport-fr.png"))
        assertEquals("https://guide/canal.png", list.last())
        // Famille de chaînes sans fichier propre : le logo de la marque, puis l'adresse du guide.
        val bein = Channel("beINSPORTSMAX7.fr", "beIN SPORTS MAX 7", "https://guide/bein.png")
        assertEquals(listOf("bein-sports-fr.png", "bein.png"), ChannelLogos.candidates(bein, bein.id).map { it.substringAfterLast('/') })
        assertTrue(ChannelLogos.hasVerified(bein, bein.id))
        // Chaîne inconnue : seulement l'adresse du guide ; adresse non sécurisée ou absente : rien.
        assertEquals(listOf("https://guide/x.png"), ChannelLogos.candidates(Channel("x", "Télé Locale", "https://guide/x.png"), "x"))
        assertTrue(ChannelLogos.candidates(Channel("x", "Télé Locale", "http://guide/x.png"), "x").isEmpty())
        assertTrue(ChannelLogos.candidates(null, "inconnue").isEmpty())
    }

    @Test fun malformedCalendarsDoNotCrash() {
        assertTrue(FixtureParsers.espn("{}", "football").isEmpty())
        assertTrue(FixtureParsers.espn("""{"events":[{"competitions":[]},{"competitions":[{"competitors":[{}]}]}]}""", "football").isEmpty())
        assertTrue(FixtureParsers.formula1("{}").isEmpty())
    }

    @Test fun usLeaguesAreVerified() {
        val nba = Fixture("nba", t0 + 10 * min, "Los Angeles Lakers", "Phoenix Suns", "")
        val c = LiveCheck(Fixtures(listOf(nba), mapOf("nba" to setOf(day))))
        assertEquals(Verdict.LIVE, c.verdict(match("Phoenix Suns", "Los Angeles Lakers", "NBA Saison régulière", "Basket-ball")))
        assertEquals(Verdict.REPLAY, c.verdict(match("Boston Celtics", "Orlando Magic", "NBA", "Basket-ball")))
        assertEquals(Verdict.UNKNOWN, c.verdict(match("Paris Basketball", "Fenerbahce", "Euroligue masculine", "Basket-ball")))
    }

    @Test fun formulaOneSessions() {
        val json = """{"MRData":{"RaceTable":{"Races":[{"raceName":"Las Vegas Grand Prix","date":"2026-11-14","time":"20:10:00Z",
            "Qualifying":{"date":"2026-11-13","time":"20:00:00Z"}}]}}}"""
        val sessions = FixtureParsers.formula1(json)
        assertEquals(2, sessions.size)
        val c = LiveCheck(Fixtures(sessions, mapOf("f1" to setOf(day))))
        val race = Broadcast(t0, t0 + 150 * min, "c1", "Formule 1", "Grand Prix de Las Vegas", "Grand Prix de Las Vegas", "", "", "La course", "", Kind.EVENT, false)
        assertEquals(Verdict.LIVE, c.verdict(race))
        assertEquals(Verdict.REPLAY, c.verdict(race.copy(start = t0 + 12 * 60 * min, stop = t0 + 14 * 60 * min)))
    }

    @Test fun scheduleUsesTheVerdicts() {
        val c = check(foot("Lille", "Lyon"), foot("Marseille", "Paris Saint-Germain"))
        val events = Schedule.build(
            listOf(
                match("Lille", "Lyon", shown = true), // drapeau « déjà diffusé » du guide contredit par le calendrier
                match("Toulouse", "Strasbourg"),
                match("Olympique de Marseille", "Paris-SG", channel = "c1"),
                match("Marseille", "Paris-SG", channel = "c2"),
                match("Lille", "Lyon", start = t0 + 6 * 60 * min),
            ),
            c,
        )
        val lille = events.first { it.home == "Lille" && it.start == t0 }
        assertTrue(lille.verified)
        assertFalse(lille.replay)
        assertTrue(events.first { it.home == "Toulouse" }.replay)
        val om = events.filter { it.away == "Paris-SG" }
        assertEquals("les variantes de nom sont regroupées", 1, om.size)
        assertEquals(listOf("c1", "c2"), om.single().channels)
        assertTrue(events.first { it.home == "Lille" && it.start > t0 }.replay)
    }

    @Test fun heuristicsWithoutCalendar() {
        fun single(b: Broadcast) = Schedule.build(listOf(b)).single()
        val tennis = Broadcast(t0, t0 + 120 * min, "c1", "Tennis", "ATP Finals", "ATP Finals", "", "", "Demi-finale", "", Kind.EVENT, false)
        assertFalse(single(tennis).replay)
        assertTrue(single(tennis.copy(previouslyShown = true)).replay)
        assertTrue(single(tennis.copy(detail = "Les temps forts")).replay)
        assertTrue(single(tennis.copy(competition = "Roland-Garros 2019", title = "Roland-Garros 2019")).replay)
        // Sport collectif sans affiche : pas un match, sauf multiplex.
        val foot = tennis.copy(sport = "Football", competition = "Premier League", title = "Premier League", detail = "")
        assertTrue(single(foot).replay)
        assertFalse(single(foot.copy(detail = "Multiplex")).replay)
        // Match européen diffusé à 4 h du matin.
        val night = match("Chambéry", "Nîmes", "Daikin StarLigue", "Handball", start = XmltvParser.parseTime("20261115040000 +0100")!!)
        assertTrue(single(night).replay)
        assertFalse(single(night.copy(start = t0, stop = t0 + 100 * min)).replay)
        // Deuxième passage de la même épreuve : rediffusion.
        val twice = Schedule.build(listOf(tennis, tennis.copy(start = t0 + 5 * 60 * min, stop = t0 + 7 * 60 * min)))
        assertEquals(listOf(false, true), twice.map { it.replay })
    }

    @Test fun fixturesSurviveSaveLoadAndMerge() {
        val dir = File(System.getProperty("java.io.tmpdir"), "sa-fx-${System.nanoTime()}").apply { mkdirs() }
        try {
            val old = Fixtures(listOf(foot("Lille", "Lyon"), foot("Nice", "Lens", start = t0 - 5 * 24 * 60 * min)), mapOf("football" to setOf(day)))
            old.save(dir)
            val loaded = Fixtures.load(dir)
            assertEquals(old.list.toSet(), loaded.list.toSet())
            assertEquals(old.coveredDays, loaded.coveredDays)
            // Nouveau téléchargement du même jour : Lille – Lyon a disparu (annulé), un autre match est apparu.
            val fresh = Fixtures(listOf(foot("Brest", "Nantes")), mapOf("football" to setOf(day)))
            val merged = loaded.mergedWith(fresh, t0 - 2 * 24 * 60 * min)
            assertEquals(listOf("Brest"), merged.list.map { it.home })
            assertTrue(Fixtures.load(File(dir, "absent")).list.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
