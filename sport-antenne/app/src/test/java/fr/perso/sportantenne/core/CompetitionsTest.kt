package fr.perso.sportantenne.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionsTest {
    private companion object { const val ALLOWED_AMBIGUOUS = 0 }

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
        assertEquals("France / Ligue A féminine (Saforelle)", name("Volley-ball", "Saforelle VOLSTAR"))
        assertEquals("France / Marmara SpikeLigue (Ligue A)", name("Volley-ball", "Alterna VOLSTAR"))
        assertEquals("France / Ligue féminine (Butagaz)", name("Handball", "Ligue Butagaz Energie"))
        assertEquals("International / ATP Finals", name("Tennis", "ATP Finals"))
        assertEquals("International / Tournois ATP", name("Tennis", "ATP 250 Stockholm"))
        assertEquals("International / Grands Prix de Formule 1", name("Formule 1", "Grand Prix de Las Vegas"))
        assertEquals("Amérique du Nord / NFL", name("Football américain", "NFL"))
    }

    @Test fun nationalTeamCompetitions() {
        val foot = "Football"
        assertEquals("International / Coupe du monde", name(foot, "Coupe du monde 2026"))
        assertEquals("International / Coupe du monde féminine", name(foot, "Coupe du monde féminine"))
        assertEquals("International / Coupes du monde U17 et U20", name(foot, "Coupe du monde U17"))
        assertEquals("International / Coupes du monde U17 et U20", name(foot, "Coupe du monde des moins de 20 ans"))
        assertEquals("International / Matchs amicaux", name(foot, "Match amical international"))
        assertEquals("Europe / Euro", name(foot, "Euro 2028"))
        assertEquals("Europe / Euro féminin", name(foot, "Euro féminin"))
        assertEquals("Europe / Qualifications Euro", name(foot, "Qualifications à l'Euro"))
        assertEquals("Europe / Ligue des nations", name(foot, "Ligue des Nations UEFA"))
        assertEquals("Europe / Ligue des nations féminine", name(foot, "Ligue des nations féminine"))
        assertEquals("Amérique du Sud / Copa América", name(foot, "Copa America"))
        assertEquals("International / Jeux olympiques", name(foot, "Jeux olympiques"))
        assertEquals("France / Championnats de jeunes", name(foot, "Championnat de France des moins de 17 ans"))
        assertEquals("France / Championnats de jeunes", name(foot, "Championnat de France U19"))
        val rugby = "Rugby"
        assertEquals("International / Coupe du monde", name(rugby, "Coupe du monde"))
        assertEquals("International / Coupe du monde féminine", name(rugby, "Coupe du monde féminine"))
        assertEquals("Europe / Tournoi des Six Nations", name(rugby, "Tournoi des Six Nations"))
        assertEquals("Europe / Six Nations féminin", name(rugby, "Tournoi des Six Nations féminin"))
        assertEquals("Europe / Six Nations U20", name(rugby, "Six Nations U20"))
        assertEquals("International / Nations Championship", name(rugby, "Nations Championship"))
        assertEquals("International / Nations Championship", name(rugby, "Coupe des Nations"))
        assertEquals("International / Pacific Nations Cup", name(rugby, "Pacific Nations Cup"))
        assertEquals("International / Tests-matchs et tournées", name(rugby, "Test-match"))
        assertEquals("International / Rugby Championship", name(rugby, "Rugby Championship"))
        assertEquals("International / WXV (féminin)", name(rugby, "WXV 1"))
        assertEquals("International / Coupe Davis", name("Tennis", "Coupe Davis"))
        assertEquals("International / Billie Jean King Cup", name("Tennis", "Billie Jean King Cup"))
        assertEquals("International / EuroBasket féminin", name("Basket-ball", "Qualifications à l'EuroBasket féminin"))
        assertEquals("International / EuroBasket", name("Basket-ball", "EuroBasket"))
        assertEquals("International / Championnat du monde féminin", name("Handball", "Championnat du monde féminin"))
        assertEquals("International / Championnat du monde", name("Handball", "Championnat du monde"))
        assertEquals("Europe / Euro féminin", name("Handball", "Euro féminin"))
        assertEquals("International / Ligue des nations", name("Volley-ball", "Ligue des nations"))
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

    @Test fun minorSportsAndGenericLabels() {
        assertEquals("International / Tournée des quatre tremplins", name("Ski", "Tournée des quatre tremplins"))
        assertEquals("International / Tournée des quatre tremplins", name("Ski", "Tournée des 4 tremplins à Innsbruck"))
        assertEquals("International / Saut à ski", name("Ski", "Coupe du monde de saut à ski"))
        assertEquals("International / Coupe du monde de ski alpin", name("Ski", "Coupe du monde à Sölden"))
        assertEquals("International / Autres épreuves (Levi, Kitzbühel, Sölden…)", name("Ski", "Levi"))
        assertEquals("Arabie saoudite / Saudi Pro League", name("Football", "Saudi Pro League"))
        assertEquals("Belgique / Pro League", name("Football", "Pro League"))
        assertEquals("Asie et Océanie / Ligue des champions d'Asie", name("Football", "Ligue des champions d'Asie"))
        assertEquals("International / Jeux paralympiques", name("Handisport", "Jeux paralympiques"))
        assertEquals("International / Grand Prix ISU", name("Patinage", "Grand Prix ISU"))
        assertEquals("International / ONE Championship", name("Kickboxing", "ONE Friday Fights 179"))
        assertEquals("International / Coupe de l'America", name("Sports nautiques", "Coupe de l'America"))
        assertEquals("International / Global Champions Tour et League", name("Équitation", "Global Champions League"))
        assertEquals("International / Jumpings et CSI", name("Équitation", "Jumping International de Bordeaux"))
        assertEquals("International / T100", name("Triathlon", "Dubaï T100"))
        assertEquals("International / UTMB World Series", name("Trail", "UTMB Index en Cappadoce"))
        assertEquals("International / Coupe du monde", name("Rugby à XIII", "Coupe du monde"))
        assertEquals("International / TGL et WTGL", name("Golf", "WTGL"))
        assertEquals("International / Ligue des champions", name("Judo", "Ligue des champions par équipes"))
        assertEquals("International / Jeux olympiques", name("Multisports", "Home of the Olympics"))
        assertEquals("Écosse / Premiership écossaise", name("Football", "Scottish Premiership"))
        assertEquals("Danemark / Superliga danoise", name("Football", "Superliga"))
        assertEquals("Tir sportif", Sports.detect("Tir sportif")); assertEquals("Tir sportif", Sports.detect("Tir")); assertEquals("Tir à l'arc", Sports.detect("Tir à l'arc"))
        assertEquals("International / Coupe du monde ISSF", name("Tir sportif", "Coupe du monde ISSF"))
        assertEquals("France / Championnats de France", name("Tir sportif", "Championnats de France"))
        // Le guide ne donne parfois que le sport : une entrée dédiée, cochable.
        val generic = Competitions.idOf("Football", "Football")
        assertTrue(Competitions.isCatalogued(generic))
        assertEquals("Compétition non précisée par le guide", Competitions.byId(generic)!!.name)
        assertTrue(Competitions.choices("Football").any { "c:" + it.id == generic })
        assertNotEquals(generic, Competitions.idOf("Rugby", "Rugby"))
    }

    /** Contrôle systématique : chaque entrée du catalogue doit être reconnue sous son propre nom. */
    @Test fun everyEntryIsReachableUnderItsOwnName() {
        val wrong = ArrayList<String>()
        for (sport in Competitions.sports) for (c in Competitions.of(sport)) {
            val got = Competitions.byId(Competitions.idOf(sport, c.name))
            if (got?.id != c.id) wrong += "$sport / ${c.country} / ${c.name} -> ${got?.let { it.country + " / " + it.name } ?: "hors catalogue"}"
        }
        System.getenv("GUIDE_REPORT")?.let { java.io.File(it + ".self").writeText(wrong.joinToString("\n")) }
        assertTrue("entrées inatteignables :\n" + wrong.joinToString("\n"), wrong.size <= ALLOWED_AMBIGUOUS)
    }

    @Test fun catalogueIsConsistent() {
        assertTrue(Competitions.sports.first() == "Football")
        assertTrue(Competitions.of("Football").size > 90)
        assertTrue(Competitions.sports.size >= 45)
        val ids = Competitions.sports.flatMap { Competitions.of(it) }.map { it.id }
        assertEquals("identifiants uniques", ids.size, ids.toSet().size)
        // Chaque sport du catalogue porte un nom reconnu par l'analyse du guide.
        Competitions.sports.forEach { assertEquals(it, Sports.detect(it)) }
    }
}
