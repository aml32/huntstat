package fr.perso.sportantenne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Interroge les vrais calendriers en ligne : LIVE_SOURCES=1 ./gradlew testDebugUnitTest */
class LiveSourcesTest {
    @Test fun everySourceAnswers() {
        assumeTrue(System.getenv("LIVE_SOURCES") == "1")
        val fixtures = Repo.downloadFixtures(System.currentTimeMillis())
        val counts = fixtures.list.groupingBy { it.source }.eachCount()
        println("FIXTURES $counts covered=${fixtures.coveredDays.mapValues { it.value.size }}")
        for (source in listOf("tennis", "golf", "ufc", "ncaab")) assertTrue("$source: ${counts[source]}", (counts[source] ?: 0) > 0)
        for (source in listOf("football", "nba", "nfl", "nhl", "f1")) {
            assertTrue("$source: ${counts[source]}", (counts[source] ?: 0) > 0)
            assertTrue("$source couvert", (fixtures.coveredDays[source]?.size ?: 0) >= 9)
        }
        assertTrue("rugby couvert", fixtures.coveredDays.containsKey("rugby"))
        // Des matchs déjà joués portent un score.
        val played = fixtures.list.filter { it.state == "post" }
        println("SCORES ${played.size} terminés, ex. ${played.take(3).map { "${it.home} ${it.homeScore}-${it.awayScore} ${it.away} (${it.clock})" }}")
        assertTrue(played.size > 20 && played.all { it.homeScore.isNotEmpty() && it.awayScore.isNotEmpty() })
        assertTrue(fixtures.list.filter { it.state == "pre" }.all { it.homeScore.isEmpty() })

        // Deuxième source, sur deux compétitions seulement pour rester rapide : Pro D2 et Coupe du monde de biathlon.
        val second = Repo.downloadSportsDb(System.currentTimeMillis(), listOf("rugby" to "5172", "biathlon" to "5342"))
        println("SECOND ${second.list.groupingBy { it.source }.eachCount()} covered=${second.coveredDays.keys} ex. ${second.list.filter { it.source == "biathlon" }.take(4).map { it.home }}")
        assertEquals(setOf("tsdb:5172", "tsdb:5342"), second.coveredDays.keys)
        assertTrue(second.list.count { it.source == "rugby" } >= 8)
        assertTrue(second.list.filter { it.source == "biathlon" }.all { it.away.isEmpty() && it.home.isNotEmpty() })

        // Rafraîchissement léger pendant les matchs : seulement les sources demandées, veille et jour même.
        val live = Repo.downloadFixtures(System.currentTimeMillis(), setOf("football", "nba"))
        println("LIVE ${live.list.groupingBy { it.source }.eachCount()} covered=${live.coveredDays.mapValues { it.value.size }}")
        assertEquals(setOf("football", "nba"), live.coveredDays.keys)
        assertTrue(live.coveredDays.values.all { it.size == 2 })
        assertTrue(live.list.isNotEmpty())
    }
}
