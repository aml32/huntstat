package fr.perso.sportantenne

import fr.perso.sportantenne.core.Competitions
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

    private val ligue1 = Competitions.idOf("Football", "Ligue 1")
    private val premier = Competitions.idOf("Football", "Premier League")
    private val list = listOf(
        event(),
        event(home = "Arsenal", away = "Chelsea", competition = "Premier League"),
        event(home = "Bruges", away = "Gand", competition = "Pro League"),
        event(home = "Toulouse", away = "Pau", sport = "Rugby", competition = "Top 14"),
        event(home = "Alcaraz", away = "Sinner", sport = "Tennis", competition = "ATP Finals"),
    )
    private fun homes(s: Settings) = s.visible(list, now).map { it.home }

    @Test fun withoutFavouritesEverythingIsShown() {
        assertEquals(5, Settings().visible(list, now).size)
    }

    @Test fun checkedSportsHideTheOthers() {
        val s = Settings().toggleSport("Football").toggleSport("rugby")
        assertEquals(listOf("Lille", "Arsenal", "Bruges", "Toulouse"), homes(s))
        assertEquals(listOf("Lille", "Arsenal", "Bruges"), homes(s.toggleSport("Rugby")))
        assertEquals(5, homes(s.toggleSport("Rugby").toggleSport("Football")).size)
    }

    @Test fun checkedCompetitionsHideTheOthersOfThatSport() {
        var s = Settings().toggleSport("Football").toggleSport("Rugby").toggleCompetition("Football", ligue1).toggleCompetition("Football", premier)
        // Le championnat belge n'est pas coché : il disparaît ; le rugby, sans compétition cochée, reste entier.
        assertEquals(listOf("Lille", "Arsenal", "Toulouse"), homes(s))
        assertEquals(2, s.competitionCount("football"))
        s = s.toggleCompetition("Football", premier)
        assertEquals(listOf("Lille", "Toulouse"), homes(s))
        s = s.toggleCompetition("Football", ligue1)
        assertEquals(listOf("Lille", "Arsenal", "Bruges", "Toulouse"), homes(s))
    }

    @Test fun checkingACompetitionAlsoChecksItsSport() {
        val s = Settings().toggleCompetition("Football", ligue1)
        assertTrue(s.isFavSport("Football"))
        assertEquals(listOf("Lille"), homes(s))
        assertTrue(s.isFavorite(event(competition = "LIGUE 1 McDonald's")))
        assertFalse(s.isFavorite(event(competition = "Premier League")))
    }

    @Test fun uncheckingASportDropsItsCompetitions() {
        val s = Settings().toggleCompetition("Football", ligue1).toggleCompetition("Rugby", Competitions.idOf("Rugby", "Top 14")).toggleSport("Football")
        assertFalse(s.isFavSport("Football"))
        assertEquals(0, s.competitionCount("Football"))
        assertEquals(1, s.competitionCount("Rugby"))
        assertEquals(listOf("Toulouse"), homes(s))
    }

    @Test fun sameCompetitionNameInAnotherSportIsNotAffected() {
        val cup = Competitions.idOf("Football", "Coupe de France")
        val s = Settings().toggleCompetition("Football", cup)
        assertFalse(s.isFavorite(event(sport = "Basket-ball", competition = "Coupe de France")))
        assertTrue(s.isFavorite(event(competition = "Coupe de France")))
    }

    @Test fun competitionsOutsideTheCatalogueCanBeChecked() {
        val odd = event(home = "A", away = "B", competition = "Trophée Machin")
        val id = Competitions.idOf("Football", "Trophée Machin")
        val s = Settings().toggleCompetition("Football", id)
        assertEquals(listOf("A"), s.visible(list + odd, now).map { it.home })
    }

    @Test fun checkedChannelsFilterTheProgramme() {
        val two = listOf(event(channels = listOf("c1")), event(home = "Nice", channels = listOf("c2", "c3")))
        assertEquals(2, Settings().visible(two, now).size)
        assertEquals(listOf("Nice"), Settings().toggleChannel("c3").visible(two, now).map { it.home })
        assertEquals(2, Settings().toggleChannel("c3").toggleChannel("c3").visible(two, now).size)
        // Chaînes et compétitions se cumulent.
        val s = Settings().toggleChannel("c1").toggleCompetition("Football", premier)
        assertEquals(listOf("Arsenal"), homes(s))
    }

    @Test fun pastEventsAreNeverShown() {
        assertTrue(Settings().visible(listOf(event(start = now - 3 * 3_600_000)), now).isEmpty())
        assertEquals("un match en cours reste affiché", 1, Settings().visible(listOf(event(start = now - 600_000)), now).size)
    }

    @Test fun replaysMagazinesAndVerifiedOnlyFollowTheSwitches() {
        val mixed = listOf(event(verified = true), event(home = "Nice", replay = true), event(home = "Mag", kind = Kind.MAGAZINE), event(home = "Brest"))
        assertEquals(listOf("Lille", "Brest"), Settings().visible(mixed, now).map { it.home })
        assertEquals(3, Settings(hideReplays = false).visible(mixed, now).size)
        assertEquals(3, Settings(showMagazines = true).visible(mixed, now).size)
        assertEquals(listOf("Lille"), Settings(verifiedOnly = true).visible(mixed, now).map { it.home })
    }

    @Test fun alertsFollowCheckedCompetitionsAndManualChoices() {
        val lille = event()
        val arsenal = event(home = "Arsenal", away = "Chelsea", competition = "Premier League")
        var s = Settings().toggleCompetition("Football", ligue1)
        assertTrue(s.hasAlert(lille))
        assertFalse(s.hasAlert(arsenal))
        assertFalse("pas d'alerte pour une rediffusion", s.hasAlert(lille.copy(replay = true)))
        s = s.toggleAlert(lille)
        assertFalse(s.hasAlert(lille))
        s = s.toggleAlert(lille)
        assertTrue(s.hasAlert(lille))
        s = s.toggleAlert(arsenal)
        assertTrue(s.hasAlert(arsenal))
        assertFalse(s.copy(alertsEnabled = false).hasAlert(event(home = "Brest")))
        assertTrue("une alerte posée à la main survit à l'interrupteur général", s.copy(alertsEnabled = false).hasAlert(arsenal))
        assertFalse("un sport coché sans compétition ne déclenche pas d'alerte", Settings().toggleSport("Football").hasAlert(lille))
    }
}
