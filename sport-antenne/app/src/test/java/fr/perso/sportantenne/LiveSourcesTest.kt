package fr.perso.sportantenne

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
        for (source in listOf("football", "nba", "nfl", "nhl", "f1")) {
            assertTrue("$source: ${counts[source]}", (counts[source] ?: 0) > 0)
            assertTrue("$source couvert", (fixtures.coveredDays[source]?.size ?: 0) >= 9)
        }
        assertTrue("rugby couvert", fixtures.coveredDays.containsKey("rugby"))
    }
}
