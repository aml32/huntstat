package fr.perso.sportantenne

import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val now = 1_800_000_000_000L
    private fun event(
        home: String = "Lille", away: String = "Lyon", sport: String = "Football", competition: String = "Ligue 1 McDonald's",
        start: Long = now + 3_600_000, channels: List<String> = listOf("c1"), replay: Boolean = false,
        kind: Kind = Kind.EVENT, verified: Boolean = false,
    ) = Event("k|$home|$away", start, start + 7_200_000, sport, competition, competition, home, away, "", "", kind, replay, channels, verified)

    @Test fun addingAndRemovingATeamIgnoresCaseAndAccents() {
        var s = Settings().toggleTeam("Saint-Étienne")
        assertTrue(s.isFavTeam("saint etienne"))
        assertTrue(s.isFavorite(event(home = "SAINT-ETIENNE")))
        assertTrue(s.isFavorite(event(home = "Lyon", away = "Saint-Etienne")))
        assertFalse(s.isFavorite(event()))
        s = s.toggleTeam("saint-etienne")
        assertTrue(s.favTeams.isEmpty())
        assertFalse(s.hasFavorites)
    }

    @Test fun addingTheSameFavouriteTwiceRemovesIt() {
        val s = Settings().toggleTeam("Lille").toggleTeam("Lyon").toggleTeam("Lille")
        assertEquals(setOf("Lyon"), s.favTeams)
    }

    @Test fun competitionFavouritesIgnoreTheSponsorAndTheSport() {
        var s = Settings().toggleCompetition("Football", "Ligue 1 McDonald's")
        assertTrue(s.isFavCompetition("Football", "Ligue 1"))
        assertTrue(s.isFavorite(event(competition = "LIGUE 1")))
        assertFalse("une autre discipline du même nom n'est pas concernée", s.isFavorite(event(sport = "Handball", competition = "Ligue 1")))
        s = s.toggleCompetition("Football", "Ligue 1")
        assertTrue(s.favCompetitions.isEmpty())
    }

    @Test fun pastEventsAreNeverShown() {
        val s = Settings()
        assertTrue(s.visible(listOf(event(start = now - 3 * 3_600_000)), now).isEmpty())
        assertEquals("un match en cours reste affiché", 1, s.visible(listOf(event(start = now - 600_000)), now).size)
    }

    @Test fun replaysAndMagazinesFollowTheSwitches() {
        val list = listOf(event(), event(home = "Nice", replay = true), event(home = "Mag", kind = Kind.MAGAZINE))
        assertEquals(listOf("Lille"), Settings().visible(list, now).map { it.home })
        assertEquals(2, Settings(hideReplays = false).visible(list, now).size)
        assertEquals(2, Settings(showMagazines = true).visible(list, now).size)
    }

    @Test fun checkedChannelsFilterTheProgramme() {
        val list = listOf(event(channels = listOf("c1")), event(home = "Nice", channels = listOf("c2", "c3")))
        assertEquals(2, Settings().visible(list, now).size)
        assertEquals(listOf("Nice"), Settings().toggleChannel("c3").visible(list, now).map { it.home })
        assertEquals(2, Settings().toggleChannel("c3").toggleChannel("c3").visible(list, now).size)
    }

    @Test fun checkedSportsFilterTheProgrammeButKeepFavouriteTeams() {
        val list = listOf(event(), event(home = "Toulouse", away = "Pau", sport = "Rugby", competition = "Top 14"), event(home = "Alcaraz", away = "Sinner", sport = "Tennis", competition = "ATP Finals"))
        assertEquals(3, Settings().visible(list, now).size)
        val rugby = Settings().toggleSport("rugby")
        assertEquals(listOf("Rugby"), rugby.visible(list, now).map { it.sport })
        assertEquals(listOf("Football", "Rugby"), rugby.toggleTeam("Lyon").visible(list, now).map { it.sport })
        assertEquals(3, rugby.toggleSport("Rugby").visible(list, now).size)
    }

    @Test fun verifiedOnlyKeepsConfirmedLives() {
        val list = listOf(event(verified = true), event(home = "Nice"))
        assertEquals(listOf("Lille"), Settings(verifiedOnly = true).visible(list, now).map { it.home })
    }

    @Test fun alertsFollowFavouritesAndManualChoices() {
        val lille = event()
        val nice = event(home = "Nice", away = "Lens")
        var s = Settings().toggleTeam("Lille")
        assertTrue(s.hasAlert(lille))
        assertFalse(s.hasAlert(nice))
        assertFalse("pas d'alerte pour une rediffusion", s.hasAlert(lille.copy(replay = true)))
        s = s.toggleAlert(lille) // coupée à la main
        assertFalse(s.hasAlert(lille))
        s = s.toggleAlert(lille) // réactivée
        assertTrue(s.hasAlert(lille))
        s = s.toggleAlert(nice) // alerte sur un match non favori
        assertTrue(s.hasAlert(nice))
        assertFalse(s.copy(alertsEnabled = false).hasAlert(event(home = "Lille", away = "Brest")))
        assertTrue("une alerte posée à la main survit à l'interrupteur général", s.copy(alertsEnabled = false).hasAlert(nice))
    }
}
