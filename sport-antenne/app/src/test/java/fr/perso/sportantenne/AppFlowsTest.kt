package fr.perso.sportantenne

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import fr.perso.sportantenne.core.Broadcast
import fr.perso.sportantenne.core.Catalogue
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Competitions
import fr.perso.sportantenne.core.Fixture
import fr.perso.sportantenne.core.Fixtures
import fr.perso.sportantenne.core.Guide
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.Kind
import fr.perso.sportantenne.core.Races
import fr.perso.sportantenne.ui.addToCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Parcours utilisateur complets sur un programme fictif calé sur l'heure courante :
 * favoris, filtres, alertes, calendrier, widget, persistance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // vraie mise en page du texte : indispensable pour que les clics tombent juste
@Config(sdk = [34], qualifiers = "w411dp-h3000dp-xhdpi") // écran très haut : toutes les lignes des listes sont composées
class AppFlowsTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val min = 60_000L
    private val now = System.currentTimeMillis()

    private fun b(home: String, away: String, inMin: Long, channel: String, competition: String = "Ligue 1", sport: String = "Football") =
        Broadcast(now + inMin * min, now + (inMin + 120) * min, channel, sport, competition, competition, home, away, "", "Description du match", Kind.EVENT, false)

    private val guide = Guide(
        listOf(Channel("c1", "Canal Test", ""), Channel("c2", "beIN Test", "")),
        listOf(
            b("Monaco", "Rennes", -20, "c1"),            // en cours, confirmé
            b("Nantes", "Brest", -150, "c1"),            // terminé depuis 30 minutes
            b("Reims", "Metz", -300, "c2"),              // terminé depuis 3 heures
            b("Lille", "Lyon", 30, "c1"),                // confirmé
            b("Paris-SG", "Marseille", 45, "c2"),        // confirmé (noms différents dans le calendrier)
            b("Nice", "Lens", 50, "c1"),                 // absent du calendrier : rediffusion masquée
            b("Arsenal", "Chelsea", 35, "c2", "Premier League"),
            b("Bruges", "Gand", 38, "c1", "Pro League"),
            b("Etoile", "Avenir", 42, "c1", "Ligue 3"),  // compétition hors calendrier
            b("Alcaraz", "Sinner", 40, "c2", "ATP Finals", "Tennis"), // sport sans calendrier
            b("Lille", "Lyon", 32, "c2"),                // même match sur une deuxième chaîne
            Broadcast(now + 55 * min, now + 130 * min, "c1", "Biathlon", "Coupe du monde", "Coupe du monde", "", "", "Sprint 7,5 km dames", "", Kind.EVENT, false),
            Broadcast(now + 55 * min, now + 130 * min, "c2", "Biathlon", "Coupe du monde", "Coupe du monde", "", "", "Sprint 7,5 km dames", "", Kind.EVENT, false),
        ),
    )
    private val fixtures = Fixtures(
        listOf(
            Fixture("football", now - 10 * min, "AS Monaco", "Stade Rennais", "Ligue 1", homeScore = "1", awayScore = "0", state = "in", clock = "25'"),
            Fixture("football", now - 140 * min, "Nantes", "Brest", "Ligue 1", homeScore = "2", awayScore = "1", state = "post", clock = "FT"),
            Fixture("football", now - 290 * min, "Reims", "Metz", "Ligue 1", homeScore = "0", awayScore = "0", state = "post", clock = "FT"),
            Fixture("football", now + 45 * min, "Lille", "Lyon", "Ligue 1"),
            Fixture("football", now + 60 * min, "Paris Saint-Germain", "Marseille", "Ligue 1"),
            Fixture("football", now + 50 * min, "Arsenal", "Chelsea", "Premier League"),
            Fixture("football", now + 50 * min, "Club Brugge", "Gent", "Belgian Pro League"),
        ),
        mapOf("football" to setOf(Fixtures.dayOf(now - 86_400_000), Fixtures.dayOf(now), Fixtures.dayOf(now + 86_400_000))),
    )
    private val ligue1 = Competitions.idOf("Football", "Ligue 1")
    private val premier = Competitions.idOf("Football", "Premier League")

    private val seed = object : ExternalResource() {
        override fun before() {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                app, Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).setExecutor(SynchronousExecutor()).build(),
            )
            Repo.liveUpdates = false // pas d'appel réseau pendant ces parcours
            GuideStore.save(app.filesDir, guide)
            fixtures.save(app.filesDir)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun ready() = waitFor("programme affiché") { compose.onAllNodesWithText("Lille – Lyon").fetchSemanticsNodes().isNotEmpty() }
    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun tab(label: String) { compose.onAllNodesWithText(label).onLast().performClick(); compose.waitForIdle() }
    private fun settings() = Settings.load(app)
    private fun waitFor(what: String, condition: () -> Boolean) = try {
        compose.waitUntil(8_000) { condition() }
    } catch (e: Throwable) {
        System.getenv("SHOTS")?.let { dir ->
            runCatching { compose.onAllNodes(isRoot()).onLast().captureRoboImage(File(dir, "ECHEC-" + what.replace(' ', '_') + ".png").path) }
        }
        throw AssertionError("jamais atteint : $what — réglages : ${settings()}", e)
    }
    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private fun events() = Repo.buildEvents(app, GuideStore.load(app.filesDir)!!)

    /** Dans Favoris, les cases des sports suivent l'ordre du catalogue : Football en premier, Rugby ensuite. */
    private fun sportCheckbox(index: Int) = compose.onAllNodes(isToggleable())[index]
    private fun openSport(name: String) { compose.onNodeWithText(name).performClick(); compose.waitForIdle() }

    @Test fun onlyLiveMatchesAreListedWithTheirChannel() {
        ready()
        assertTrue(shown("Monaco – Rennes")); assertTrue(shown("EN COURS"))
        assertTrue(shown("Lille – Lyon")); assertTrue(shown("Arsenal – Chelsea")); assertTrue(shown("Alcaraz – Sinner"))
        assertFalse("un match absent du calendrier ne doit pas apparaître", shown("Nice – Lens"))
        assertTrue(shown("Canal Test")); assertTrue(shown("beIN Test"))
        assertTrue(compose.onAllNodes(hasText("Direct confirmé", substring = true)).fetchSemanticsNodes().size >= 3)
        // Sans favori, tous les sports du programme ont leur onglet.
        assertTrue(shown("Football")); assertTrue(shown("Tennis"))
    }

    @Test fun liveScoreAndFinishedMatchesFollowTheKeepSetting() {
        ready()
        // Match en cours : score et minute de jeu.
        assertTrue(shown("Monaco – Rennes")); assertTrue(shown("1 – 0")); assertTrue(shown("EN COURS · 25'"))
        // Terminé depuis 30 minutes : encore affiché avec son résultat (réglage par défaut : 1 heure).
        assertTrue(shown("Nantes – Brest")); assertTrue(shown("2 – 1")); assertTrue(shown("TERMINÉ"))
        // Terminé depuis 3 heures : déjà retiré.
        assertFalse(shown("Reims – Metz"))
        tab("Réglages")
        compose.onNodeWithText("4 h").performClick()
        waitFor("conservation 4 h") { settings().keepFinishedMin == 240 }
        tab("À venir")
        assertTrue(shown("Reims – Metz")); assertTrue(shown("0 – 0")); assertTrue(shown("Nantes – Brest"))
        tab("Réglages")
        compose.onNodeWithText("Dès la fin").performClick()
        waitFor("retrait dès la fin") { settings().keepFinishedMin == 0 }
        tab("À venir")
        assertFalse(shown("Nantes – Brest")); assertFalse(shown("Reims – Metz"))
        assertTrue("le match en cours reste", shown("Monaco – Rennes"))
        tab("Réglages")
        compose.onNodeWithText("Tous").performClick()
        waitFor("tout conserver") { settings().keepFinishedMin == Settings.KEEP_ALL }
        tab("À venir")
        assertTrue(shown("Reims – Metz")); assertTrue(shown("Nantes – Brest"))
        // Un match terminé ne déclenche jamais d'alerte et n'apparaît pas comme « à venir » dans le widget.
        assertTrue(alarms().none { it.triggerAtMs < now })
        // La fiche du match terminé rappelle le score final.
        compose.onNodeWithText("Nantes – Brest").performClick()
        waitFor("fiche ouverte") { shown("Score final") }
    }

    @Test fun everyChannelOfABroadcastIsShown() {
        ready()
        val lille = events().first { it.home == "Lille" && !it.replay }
        assertEquals("les deux diffusions simultanées forment un seul match", listOf("c1", "c2"), lille.channels)
        assertEquals(1, compose.onAllNodesWithText("Lille – Lyon").fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodes(hasText("Diffusé sur 2 chaînes")).fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Lille – Lyon").performClick()
        waitFor("fiche ouverte") { shown("Ajouter à mon calendrier") }
        assertTrue(shown("Diffusé sur 2 chaînes :")); assertTrue(shown("Sur Canal Test")); assertTrue(shown("Sur beIN Test"))
    }

    @Test fun biathlonRaceShowsItsFormatAndGender() {
        ready()
        assertTrue(shown("♀ Femmes")); assertTrue(shown("Sprint")); assertTrue(shown("7,5 km"))
        val race = events().first { it.sport == "Biathlon" }
        assertEquals(Races.WOMEN, race.race.gender); assertEquals("Sprint", race.race.format)
        // Le calendrier ajouté au téléphone nomme les deux chaînes.
        compose.runOnUiThread { addToCalendar(compose.activity, race, guide.channels.associateBy { it.id }) }
        val intent = shadowOf(app).nextStartedActivity
        assertEquals("Canal Test, beIN Test", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertEquals("Biathlon · Coupe du monde · Sprint 7,5 km dames — Canal Test, beIN Test", intent.getStringExtra(CalendarContract.Events.TITLE))
    }

    @Test fun checkedSportsDecideWhichTabsAndMatchesAppear() {
        ready()
        tab("Favoris")
        sportCheckbox(0).performClick()
        waitFor("football coché") { settings().isFavSport("Football") }
        assertTrue(shown("Toutes les compétitions"))
        tab("À venir")
        assertTrue(shown("Lille – Lyon")); assertTrue(shown("Arsenal – Chelsea"))
        assertFalse("le tennis n'est pas coché", shown("Alcaraz – Sinner"))
        assertFalse("pas d'onglet pour un sport non coché", compose.onAllNodesWithText("Tennis").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Football").fetchSemanticsNodes().isNotEmpty())
        // Décocher : tout revient.
        tab("Favoris")
        sportCheckbox(0).performClick()
        waitFor("football décoché") { settings().favSports.isEmpty() }
        tab("À venir")
        assertTrue(shown("Alcaraz – Sinner")); assertTrue(compose.onAllNodesWithText("Tennis").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun competitionsAreListedByCountryAndFilterTheProgramme() {
        ready()
        tab("Favoris")
        openSport("Football")
        assertTrue(shown("🇫🇷 France")); assertTrue(shown("🇬🇧 Angleterre")); assertTrue(shown("Ligue 1")); assertTrue(shown("Premier League"))
        compose.onNodeWithText("Premier League").performClick()
        waitFor("Premier League cochée") { settings().isFavCompetition("Football", premier) }
        assertTrue("cocher une compétition coche son sport", settings().isFavSport("Football"))
        assertTrue(shown("1 compétition(s) cochée(s)"))
        tab("À venir")
        assertTrue(shown("Arsenal – Chelsea"))
        assertFalse("Ligue 1 non cochée", shown("Lille – Lyon")); assertFalse("championnat belge non coché", shown("Bruges – Gand"))
        assertFalse(shown("Alcaraz – Sinner"))
        waitFor("alarme pour le match de la compétition cochée") { alarms().size == 1 }
        // Ajouter la Ligue 1, puis retirer la Premier League.
        tab("Favoris"); openSport("Football")
        compose.onNodeWithText("Ligue 1").performClick()
        waitFor("Ligue 1 cochée") { settings().competitionCount("Football") == 2 }
        tab("À venir")
        assertTrue(shown("Lille – Lyon")); assertTrue(shown("Arsenal – Chelsea")); assertFalse(shown("Bruges – Gand"))
        tab("Favoris")
        assertTrue(shown("2 compétitions cochées"))
        openSport("Football")
        compose.onNodeWithText("Premier League").performClick()
        waitFor("Premier League retirée") { !settings().isFavCompetition("Football", premier) }
        tab("À venir")
        assertFalse(shown("Arsenal – Chelsea")); assertTrue(shown("Lille – Lyon"))
        // Tout décocher dans le sport : tout le football revient, les autres sports restent masqués.
        tab("Favoris"); openSport("Football")
        compose.onNodeWithText("Ligue 1").performClick()
        waitFor("plus de compétition") { settings().competitionCount("Football") == 0 }
        waitFor("alarmes retirées") { alarms().isEmpty() }
        tab("À venir")
        assertTrue(shown("Bruges – Gand")); assertTrue(shown("Arsenal – Chelsea")); assertFalse(shown("Alcaraz – Sinner"))
    }

    @Test fun searchingCompetitionsAndThoseOutsideTheCatalogue() {
        ready()
        tab("Favoris"); openSport("Football")
        compose.onNode(hasText("Rechercher une compétition ou un pays")).performTextInput("belg")
        compose.waitForIdle()
        assertTrue(shown("Pro League")); assertTrue(shown("🇧🇪 Belgique")); assertFalse(shown("Premier League"))
        compose.onNodeWithText("Pro League").performClick()
        waitFor("Pro League cochée") { settings().isFavCompetition("Football", Competitions.idOf("Football", "Pro League")) }
        tab("À venir")
        assertTrue(shown("Bruges – Gand")); assertFalse(shown("Lille – Lyon"))
    }

    @Test fun sportTabsOnTheMainPageFilterTheView() {
        ready()
        compose.onNodeWithText("Tennis").performClick()
        compose.waitForIdle()
        assertTrue(shown("Alcaraz – Sinner")); assertFalse(shown("Lille – Lyon"))
        compose.onNodeWithText("Tous").performClick()
        compose.waitForIdle()
        assertTrue(shown("Lille – Lyon")); assertTrue(shown("Alcaraz – Sinner"))
        assertTrue("l'onglet de sport ne modifie pas les favoris", settings().favSports.isEmpty())
    }

    @Test fun otherDaysAreReachableFromTheDayStrip() {
        ready()
        assertTrue(shown("Aujourd'hui")); assertTrue(shown("Demain"))
        compose.onNodeWithText("Demain").performClick()
        compose.waitForIdle()
        waitFor("page de demain") { shown("Aucun direct ce jour-là") }
        compose.onNodeWithText("Aujourd'hui").performClick()
        waitFor("retour à aujourd'hui") { shown("Lille – Lyon") }
    }

    @Test fun checkedChannelsFilterTheProgramme() {
        ready()
        tab("Chaînes")
        compose.onNodeWithText("beIN Test").performClick()
        waitFor("chaîne enregistrée") { settings().myChannels == setOf("c2") }
        tab("À venir")
        assertTrue(shown("Paris-SG – Marseille")); assertTrue(shown("Alcaraz – Sinner")); assertTrue(shown("Lille – Lyon"))
        assertFalse(shown("Bruges – Gand")); assertFalse(shown("Monaco – Rennes"))
        tab("Chaînes")
        compose.onNodeWithText("beIN Test").performClick()
        waitFor("chaîne décochée") { settings().myChannels.isEmpty() }
        tab("À venir")
        assertTrue(shown("Bruges – Gand"))
    }

    @Test fun settingsSwitchesChangeTheList() {
        ready()
        tab("Réglages")
        compose.onNodeWithText("Masquer les rediffusions").performClick()
        waitFor("rediffusions visibles") { !settings().hideReplays }
        tab("À venir")
        assertTrue(shown("Nice – Lens")); assertTrue(shown("Rediffusion"))
        tab("Réglages")
        compose.onNodeWithText("Masquer les rediffusions").performClick()
        compose.onNodeWithText("Directs confirmés uniquement").performClick()
        waitFor("mode strict") { settings().verifiedOnly && settings().hideReplays }
        tab("À venir")
        assertTrue(shown("Lille – Lyon")); assertFalse(shown("Alcaraz – Sinner")); assertFalse(shown("Nice – Lens")); assertFalse(shown("Etoile – Avenir"))
    }

    @Test fun eventSheetAlertSwitchAndCompetitionFavourite() {
        ready()
        compose.onNodeWithText("Paris-SG – Marseille").performClick()
        waitFor("fiche ouverte") { shown("Ajouter à mon calendrier") }
        assertTrue(shown("Sur beIN Test")); assertTrue(shown("Direct confirmé"))
        compose.onAllNodes(isToggleable()).onFirst().assertIsOff()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        waitFor("alerte posée") { alarms().size == 1 }
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
        val psg = events().first { it.home == "Paris-SG" }
        assertEquals("alarme 15 min avant le début", psg.start - 15 * min, alarms().single().triggerAtMs)
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        waitFor("alerte retirée") { alarms().isEmpty() }
        // Cocher la compétition depuis la fiche.
        compose.onAllNodes(isToggleable()).onLast().performClick()
        waitFor("Ligue 1 cochée depuis la fiche") { settings().isFavCompetition("Football", ligue1) }
    }

    @Test fun alertDelayAndGlobalSwitch() {
        ready()
        tab("Favoris"); openSport("Football")
        compose.onNodeWithText("Ligue 1").performClick()
        waitFor("alarmes des deux matchs de Ligue 1 à venir") { alarms().size == 2 }
        val starts = events().filter { it.competition == "Ligue 1" && !it.replay && it.start > now }.map { it.start }.toSet()
        assertEquals(starts.map { it - 15 * min }.toSet(), alarms().map { it.triggerAtMs }.toSet())
        tab("Réglages")
        compose.onNodeWithText("30 min").performClick()
        waitFor("délai 30 min") { alarms().map { it.triggerAtMs }.toSet() == starts.map { maxOf(it - 30 * min, 0) }.toSet() || alarms().size == 2 && settings().alertMinutes == 30 }
        compose.onNodeWithText("Me prévenir avant mes favoris").performClick()
        waitFor("alertes coupées") { alarms().isEmpty() }
        compose.onNodeWithText("Me prévenir avant mes favoris").performClick()
        waitFor("alertes rétablies") { alarms().size == 2 }
    }

    @Test fun alarmFiresANotificationNamingTheChannelOnlyOnce() {
        val channels = guide.channels.associateBy { it.id }
        val s = Settings().toggleCompetition("Football", premier)
        Alerts.reschedule(app, events(), channels, s)
        val alarm = alarms().single()
        AlertReceiver().onReceive(app, shadowOf(alarm.operation).savedIntent)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Arsenal – Chelsea", posted.extras.getString("android.title"))
        assertTrue(posted.extras.getString("android.text")!!.contains("beIN Test"))
        // Une mise à jour horaire ne doit pas reprogrammer une alerte déjà envoyée.
        Alerts.reschedule(app, events(), channels, s)
        assertTrue(alarms().isEmpty())
    }

    @Test fun alarmsDisappearWhenAMatchIsCancelled() {
        val channels = guide.channels.associateBy { it.id }
        val s = Settings().toggleCompetition("Football", premier)
        Alerts.reschedule(app, events(), channels, s)
        assertEquals(1, alarms().size)
        // Le calendrier ne contient plus Arsenal – Chelsea (reporté) : plus de direct, plus d'alerte.
        Fixtures(fixtures.list.filter { it.home != "Arsenal" }, fixtures.coveredDays).save(app.filesDir)
        val after = events()
        assertTrue(after.first { it.home == "Arsenal" }.replay)
        Alerts.reschedule(app, after, channels, s)
        assertTrue(alarms().isEmpty())
        Alerts.rescheduleFromDisk(app) // comme au redémarrage du téléphone
        assertTrue(alarms().isEmpty())
    }

    @Test fun calendarEntryCarriesTheChannelAndTheTimes() {
        ready()
        val e = events().first { it.home == "Paris-SG" }
        compose.runOnUiThread { addToCalendar(compose.activity, e, guide.channels.associateBy { it.id }) }
        val intent = shadowOf(app).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Paris-SG – Marseille — beIN Test", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("beIN Test", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertEquals(e.start, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(e.stop, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }

    @Test fun favouritesSurviveARestartAndOldFormatsAreDropped() {
        Settings().toggleCompetition("Football", ligue1).toggleSport("Rugby").toggleChannel("c2").copy(alertMinutes = 60, hideReplays = false).save(app)
        val back = Settings.load(app)
        assertTrue(back.isFavCompetition("Football", ligue1)); assertTrue(back.isFavSport("Football")); assertTrue(back.isFavSport("Rugby"))
        assertEquals(setOf("c2"), back.myChannels); assertEquals(60, back.alertMinutes); assertFalse(back.hideReplays)
        // Favoris d'une ancienne version (nom libre de compétition, équipes) : ignorés sans rien casser.
        app.getSharedPreferences("settings", 0).edit()
            .putStringSet("favCompetitions", setOf("Football|Ligue 1 McDonald's", "Football|$premier")).putStringSet("favTeams", setOf("Lille")).commit()
        val migrated = Settings.load(app)
        assertEquals(setOf("Football|$premier"), migrated.favCompetitions)
        // Une compétition vue une fois reste proposée même absente du guide suivant.
        Catalogue.EMPTY.merged(events()).save(app.filesDir)
        val later = Catalogue.load(app.filesDir).merged(emptyList())
        assertTrue(later.competitions.values.any { it.second == "ATP Finals" }); assertTrue(later.competitions.values.any { it.second == "Ligue 3" })
    }

    @Test fun corruptedCacheFilesDoNotCrashTheApp() {
        File(app.filesDir, "fixtures.tsv").writeText("F\tfootball\tpas-un-nombre\nn'importe quoi\n\n")
        File(app.filesDir, "catalogue.tsv").writeText("T\tseul\n\u0000\u0001\n")
        File(app.filesDir, "guide.tsv").appendText("B\ttronqué\nB\t1\t2\n")
        ready()
        assertTrue(shown("Lille – Lyon"))
    }
}
