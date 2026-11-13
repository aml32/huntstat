package fr.perso.sportantenne.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacesTest {
    private fun bia(detail: String, competition: String = "Coupe du monde", desc: String = "") = Races.parse("Biathlon", competition, competition, detail, desc)

    @Test fun everyBiathlonFormatIsRecognised() {
        assertEquals(RaceInfo(Races.WOMEN, "Sprint", "7,5 km"), bia("Sprint 7,5 km dames"))
        assertEquals(RaceInfo(Races.MEN, "Sprint", "10 km"), bia("Sprint 10 km messieurs"))
        assertEquals(RaceInfo(Races.MEN, "Poursuite", "12,5 km"), bia("Poursuite 12,5 km messieurs"))
        assertEquals(RaceInfo(Races.WOMEN, "Poursuite", "10 km"), bia("Poursuite 10 km dames"))
        assertEquals(RaceInfo(Races.MEN, "Individuel", "20 km"), bia("Individuel 20 km messieurs"))
        assertEquals(RaceInfo(Races.WOMEN, "Individuel", "15 km"), bia("Individuelle 15 km dames"))
        assertEquals(RaceInfo(Races.WOMEN, "Individuel court", "12,5 km"), bia("Individuel court 12,5 km dames"))
        assertEquals(RaceInfo(Races.MEN, "Mass start", "15 km"), bia("Mass Start 15 km messieurs"))
        assertEquals(RaceInfo(Races.WOMEN, "Mass start", "12,5 km"), bia("Mass-start 12,5 km dames"))
        assertEquals(RaceInfo(Races.MEN, "Mass start"), bia("Départ groupé hommes"))
        assertEquals(RaceInfo(Races.MEN, "Relais", "4 × 7,5 km"), bia("Relais 4 x 7,5 km messieurs"))
        assertEquals(RaceInfo(Races.WOMEN, "Relais", "4 × 6 km"), bia("Relais 4x6 km dames"))
        assertEquals(RaceInfo(Races.MIXED, "Relais mixte"), bia("Relais mixte"))
        assertEquals(RaceInfo(Races.MIXED, "Relais mixte simple"), bia("Relais mixte simple"))
        assertEquals(RaceInfo(Races.MIXED, "Relais mixte simple"), bia("Single mixte"))
        assertEquals(RaceInfo(Races.MEN, "Super sprint"), bia("Super sprint messieurs"))
    }

    @Test fun genderWordingVariants() {
        for (w in listOf("dames", "femmes", "féminin", "féminine", "Women")) assertEquals(w, Races.WOMEN, bia("Sprint $w").gender)
        for (w in listOf("messieurs", "hommes", "masculin", "Men")) assertEquals(w, Races.MEN, bia("Sprint $w").gender)
        assertEquals("", bia("Sprint").gender)
    }

    @Test fun placeAndDescriptionFallback() {
        val r = Races.parse("Biathlon", "Coupe du monde à Östersund", "Coupe du monde à Östersund", "Epreuve d'Östersund · Poursuite 12,5 km messieurs", "")
        assertEquals(RaceInfo(Races.MEN, "Poursuite", "12,5 km", "Östersund"), r)
        assertEquals("Le Grand-Bornand", bia("Mass start dames", "Coupe du monde au Grand-Bornand", "Etape du Grand-Bornand").place.ifEmpty { bia("Mass start dames", "Coupe du monde - Le Grand-Bornand").place })
        // Le guide ne détaille parfois l'épreuve que dans la description.
        val fromDesc = bia("", desc = "Sprint 7,5 km dames. Etape de Hochfilzen, en Autriche.")
        assertEquals(RaceInfo(Races.WOMEN, "Sprint", "7,5 km", "Hochfilzen"), fromDesc)
        assertTrue(Races.parse("Biathlon", "Coupe du monde", "Coupe du monde", "", "").isEmpty)
    }

    @Test fun otherSports() {
        assertEquals(RaceInfo(Races.MEN, "Slalom géant"), Races.parse("Ski", "Coupe du monde à Sölden", "", "Slalom géant messieurs : 2e manche"))
        assertEquals(RaceInfo(Races.WOMEN, "Descente"), Races.parse("Ski", "Coupe du monde", "", "Descente dames"))
        assertEquals("Super-G", Races.parse("Ski", "Coupe du monde", "", "Super-G messieurs").format)
        assertEquals("Étape 12", Races.parse("Cyclisme", "Tour de France", "", "12e étape : Pau / Luchon").format)
        assertEquals("Contre-la-montre", Races.parse("Cyclisme", "Tour de France", "", "Contre-la-montre individuel").format)
        assertEquals("Essais libres 2", Races.parse("Formule 1", "Grand Prix de Las Vegas", "", "Essais Libres 2").format)
        assertEquals("Qualifications", Races.parse("Formule 1", "Grand Prix du Brésil", "", "Les qualifications").format)
        assertEquals("Course", Races.parse("Formule 1", "Grand Prix du Brésil", "", "La course").format)
        assertEquals("Demi-finale", Races.parse("Tennis", "ATP Finals", "", "Demi-finale").format)
        assertEquals("Quart de finale", Races.parse("Tennis", "ATP 250 Stockholm", "", "Quarts de finale").format)
        assertEquals("", Races.parse("Tennis", "ATP Finals", "ATP Finals", "").format)
        assertEquals(Races.WOMEN, Races.parse("Football", "Arkema Première Ligue féminine", "", "9e journée").gender)
        assertEquals("", Races.parse("Football", "Ligue 1", "", "11e journée").gender)
    }

    /** Du libellé brut du guide jusqu'à l'épreuve : les trois façons d'écrire rencontrées. */
    @Test fun fromRawGuideEntries() {
        fun prog(title: String, sub: String?, cat: String, desc: String? = null) =
            "<programme start=\"20261128144500 +0100\" stop=\"20261128160000 +0100\" channel=\"c1\">\n<title lang=\"fr\">$title</title>\n" +
                (sub?.let { "<sub-title lang=\"fr\">$it</sub-title>\n" } ?: "") + (desc?.let { "<desc lang=\"fr\">$it</desc>\n" } ?: "") +
                "<category lang=\"fr\">$cat</category>\n</programme>\n"
        fun event(xml: String) = Schedule.build(XmltvParser.parse("<tv>$xml</tv>".byteInputStream()).broadcasts).single()

        val a = event(prog("Biathlon : Coupe du monde | Sprint 7,5 km dames", null, "Biathlon"))
        assertEquals("Biathlon", a.sport); assertEquals("Coupe du monde", a.competition)
        assertEquals(RaceInfo(Races.WOMEN, "Sprint", "7,5 km"), a.race)

        val b = event(prog("Biathlon : Coupe du monde à Östersund", "Epreuve d'Östersund. Poursuite 12,5 km messieurs. Coupe du monde.", "Sport"))
        assertEquals(RaceInfo(Races.MEN, "Poursuite", "12,5 km", "Östersund"), b.race)
        assertEquals("International / Coupe du monde", Competitions.byId(Competitions.idOf(b.sport, b.competition))!!.let { it.country.substringAfter(' ') + " / " + it.name })

        val c = event(prog("Biathlon : Championnats du monde | Relais mixte simple", null, "Biathlon"))
        assertEquals(RaceInfo(Races.MIXED, "Relais mixte simple"), c.race)
        assertEquals("Championnats du monde", Competitions.byId(Competitions.idOf(c.sport, c.competition))!!.name)

        val d = event(prog("Biathlon : Courses de pré-saison | Mass Start messieurs", null, "Biathlon"))
        assertEquals(RaceInfo(Races.MEN, "Mass start"), d.race)
        assertEquals("Biathlon d'été et courses de préparation", Competitions.byId(Competitions.idOf(d.sport, d.competition))!!.name)

        // Deux épreuves différentes le même jour ne sont pas confondues, ni prises pour une rediffusion l'une de l'autre.
        val two = Schedule.build(
            XmltvParser.parse(
                ("<tv>" + prog("Biathlon : Coupe du monde | Sprint 10 km messieurs", null, "Biathlon") +
                    prog("Biathlon : Coupe du monde | Sprint 7,5 km dames", null, "Biathlon").replace("1445", "1700").replace("1600", "1815") + "</tv>").byteInputStream(),
            ).broadcasts,
        )
        assertEquals(listOf(Races.MEN, Races.WOMEN), two.map { it.race.gender })
        assertEquals(listOf(false, false), two.map { it.replay })
    }
}
