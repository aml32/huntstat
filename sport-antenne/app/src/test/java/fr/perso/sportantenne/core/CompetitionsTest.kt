package fr.perso.sportantenne.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionsTest {
    private fun name(sport: String, label: String) = Competitions.byId(Competitions.idOf(sport, label))?.let { "${it.country.substringAfter(' ')} / ${it.name}" }

    @Test fun guideLabelsMapToTheCatalogue() {
        val foot = "Football"
        assertEquals("France / Ligue 1", name(foot, "Ligue 1 McDonald's"))
        assertEquals("France / Ligue 2", name(foot, "Ligue 2 BKT"))
        assertEquals("France / National (Ligue 3)", name(foot, "Ligue 3"))
        assertEquals("France / Première Ligue féminine", name(foot, "Arkema Première Ligue"))
        assertEquals("Angleterre / Premier League", name(foot, "Premier League"))
        assertEquals("Espagne / Liga", name(foot, "Liga"))
        assertEquals("Portugal / Championnat du Portugal", name(foot, "Championnat du Portugal"))
        assertEquals("Portugal / Championnat du Portugal", name(foot, "Liga Portugal"))
        assertEquals("Italie / Serie A", name(foot, "Serie A"))
        assertEquals("Amérique du Sud / Championnat du Brésil", name(foot, "Brazilian Serie A"))
        assertEquals("Allemagne / Bundesliga", name(foot, "Bundesliga"))
        assertEquals("Allemagne / Bundesliga 2", name(foot, "Bundesliga 2"))
        assertEquals("Turquie / Süper Lig", name(foot, "Süper Lig"))
        assertEquals("Europe / Ligue des champions", name(foot, "Ligue des champions"))
        assertEquals("Europe / Ligue des champions féminine", name(foot, "Ligue des champions féminine"))
        assertEquals("Afrique / Ligue des champions de la CAF", name(foot, "Ligue des champions féminine de la CAF"))
        assertEquals("Europe / Ligue des nations", name(foot, "Ligue des Nations UEFA"))
        assertEquals("Europe / Ligue Europa", name(foot, "Ligue Europa"))
        assertEquals("Afrique / Qualifications CAN", name(foot, "Qualifications de la CAN"))
        assertEquals("Afrique / Coupe d'Afrique des nations", name(foot, "Coupe d'Afrique des Nations Total"))
        assertEquals("International / Qualifications Coupe du monde", name(foot, "Qualifications à la Coupe du monde"))
        assertEquals("International / Coupe du monde", name(foot, "Coupe du monde"))
        assertEquals("Amérique du Sud / Copa Sudamericana", name(foot, "Copa Sudamericana"))
    }

    @Test fun otherSports() {
        assertEquals("France / Top 14", name("Rugby", "Top 14"))
        assertEquals("France / Pro D2", name("Rugby", "Pro D2"))
        assertEquals("International / Nations Championship", name("Rugby", "Championnat des nations"))
        assertEquals("Amérique du Nord / NBA", name("Basket-ball", "NBA Saison régulière"))
        assertEquals("Europe / Euroligue", name("Basket-ball", "Euroligue masculine"))
        assertEquals("France / Starligue", name("Handball", "Daikin StarLigue"))
        assertEquals("International / ATP Finals", name("Tennis", "ATP Finals"))
        assertEquals("International / Tournois ATP", name("Tennis", "ATP 250 Stockholm"))
        assertEquals("International / Grands Prix de Formule 1", name("Formule 1", "Grand Prix de Las Vegas"))
        assertEquals("Amérique du Nord / NFL", name("Football américain", "NFL"))
    }

    @Test fun sameLabelInTwoSportsStaysSeparate() {
        assertNotEquals(Competitions.idOf("Football", "Coupe de France"), Competitions.idOf("Basket-ball", "Coupe de France"))
        assertNotEquals(Competitions.idOf("Football", "Ligue des champions"), Competitions.idOf("Handball", "Ligue des champions"))
    }

    @Test fun unknownCompetitionsGetAStableDynamicId() {
        val a = Competitions.idOf("Football", "Trophée Machin")
        assertTrue(a.startsWith("dyn:"))
        assertEquals(a, Competitions.idOf("football", "TROPHÉE MACHIN"))
        assertNotEquals(a, Competitions.idOf("Football", "Trophée Truc"))
    }

    @Test fun catalogueIsConsistent() {
        assertTrue(Competitions.sports.first() == "Football")
        assertTrue(Competitions.of("Football").size > 50)
        val ids = Competitions.sports.flatMap { Competitions.of(it) }.map { it.id }
        assertEquals("identifiants uniques", ids.size, ids.toSet().size)
        // Chaque sport du catalogue porte un nom reconnu par l'analyse du guide.
        Competitions.sports.forEach { assertEquals(it, Sports.detect(it)) }
    }
}
