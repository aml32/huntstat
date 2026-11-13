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
import fr.perso.sportantenne.core.Fixture
import fr.perso.sportantenne.core.Fixtures
import fr.perso.sportantenne.core.Guide
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.Kind
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
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
            b("Lille", "Lyon", 30, "c1"),                // confirmé
            b("Paris-SG", "Marseille", 45, "c2"),        // confirmé (noms différents dans le calendrier)
            b("Nice", "Lens", 50, "c1"),                 // absent du calendrier : rediffusion masquée
            b("Alcaraz", "Sinner", 40, "c2", "ATP Finals", "Tennis"), // sport sans calendrier
        ),
    )
    private val fixtures = Fixtures(
        listOf(
            Fixture("football", now - 10 * min, "AS Monaco", "Stade Rennais", "Ligue 1"),
            Fixture("football", now + 45 * min, "Lille", "Lyon", "Ligue 1"),
            Fixture("football", now + 60 * min, "Paris Saint-Germain", "Marseille", "Ligue 1"),
        ),
        mapOf("football" to setOf(Fixtures.dayOf(now - 86_400_000), Fixtures.dayOf(now), Fixtures.dayOf(now + 86_400_000))),
    )

    private val seed = object : ExternalResource() {
        override fun before() {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                app, Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).setExecutor(SynchronousExecutor()).build(),
            )
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

    @Test fun onlyLiveMatchesAreListedWithTheirChannel() {
        ready()
        assertTrue(shown("Monaco – Rennes")); assertTrue(shown("EN COURS"))
        assertTrue(shown("Lille – Lyon")); assertTrue(shown("Paris-SG – Marseille")); assertTrue(shown("Alcaraz – Sinner"))
        assertFalse("un match absent du calendrier ne doit pas apparaître", shown("Nice – Lens"))
        assertTrue(shown("Canal Test")); assertTrue(shown("beIN Test"))
        assertEquals(3, compose.onAllNodes(hasText("Direct confirmé", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun checkingAndUncheckingATeamFavourite() {
        ready()
        tab("Favoris")
        compose.onNodeWithText("Lille").performClick()
        waitFor("Lille enregistré") { settings().isFavTeam("Lille") }
        assertTrue(shown("Équipes (1)"))
        tab("À venir")
        compose.onNodeWithText("★ Mes favoris").performClick()
        compose.waitForIdle()
        assertTrue(shown("Lille – Lyon")); assertFalse(shown("Paris-SG – Marseille")); assertFalse(shown("Alcaraz – Sinner"))
        waitFor("alarme pour Lille") { alarms().size == 1 }
        // Décocher : le favori, la liste filtrée et l'alarme disparaissent.
        tab("Favoris")
        compose.onNodeWithText("Lille").performClick()
        waitFor("Lille retiré") { !settings().isFavTeam("Lille") }
        assertTrue(shown("Équipes (0)"))
        waitFor("alarme annulée") { alarms().isEmpty() }
        tab("À venir")
        assertTrue(shown("Cochez vos équipes"))
    }

    @Test fun searchingAndAddingATeamThatIsNotInTheList() {
        ready()
        tab("Favoris")
        compose.onNode(hasText("Rechercher une équipe ou un joueur")).performTextInput("mars")
        compose.waitForIdle()
        assertTrue(shown("Marseille")); assertFalse(shown("Lille"))
        compose.onNodeWithText("Marseille").performClick()
        waitFor("Marseille enregistré") { settings().isFavTeam("Marseille") }
        tab("Chaînes"); tab("Favoris")
        compose.onNode(hasText("Rechercher une équipe ou un joueur")).performTextInput("FC Inconnu")
        compose.waitForIdle()
        compose.onNode(hasText("Ajouter « FC Inconnu »", substring = true)).performClick()
        waitFor("équipe libre enregistrée") { settings().isFavTeam("fc inconnu") }
        assertTrue("l'équipe ajoutée à la main reste dans la liste", shown("FC Inconnu"))
        assertTrue(shown("Équipes (2)"))
    }

    @Test fun competitionFavouriteAddAndRemove() {
        ready()
        tab("Favoris")
        compose.onNode(hasText("Compétitions (", substring = true)).performClick()
        compose.onNodeWithText("ATP Finals").performClick()
        waitFor("compétition enregistrée") { settings().isFavCompetition("Tennis", "ATP Finals") }
        tab("À venir")
        compose.onNodeWithText("★ Mes favoris").performClick()
        compose.waitForIdle()
        assertTrue(shown("Alcaraz – Sinner")); assertFalse(shown("Lille – Lyon"))
        tab("Favoris")
        compose.onNode(hasText("Compétitions (", substring = true)).performClick()
        compose.onNodeWithText("ATP Finals").performClick()
        waitFor("compétition retirée") { settings().favCompetitions.isEmpty() }
    }

    @Test fun sportChipsFilterAndAreRemembered() {
        ready()
        compose.onNodeWithText("Tennis").performClick()
        waitFor("sport enregistré") { settings().isFavSport("Tennis") }
        compose.waitForIdle()
        assertTrue(shown("Alcaraz – Sinner")); assertFalse(shown("Lille – Lyon"))
        compose.onNodeWithText("Football").performClick()
        waitFor("deux sports") { settings().favSports.size == 2 }
        compose.waitForIdle()
        assertTrue(shown("Lille – Lyon")); assertTrue(shown("Alcaraz – Sinner"))
        compose.onNodeWithText("Tennis").performClick()
        waitFor("tennis retiré") { !settings().isFavSport("Tennis") }
        compose.waitForIdle()
        assertFalse(shown("Alcaraz – Sinner"))
        compose.onNodeWithText("Tous les sports").performClick()
        waitFor("filtre vidé") { settings().favSports.isEmpty() }
        compose.waitForIdle()
        assertTrue(shown("Alcaraz – Sinner"))
        // Même réglage depuis l'onglet Favoris > Sports.
        tab("Favoris")
        compose.onNode(hasText("Sports (", substring = true)).performClick()
        compose.onAllNodesWithText("Tennis").onLast().performClick()
        waitFor("sport coché dans Favoris") { settings().isFavSport("Tennis") }
        assertTrue(shown("Sports (1)"))
    }

    @Test fun checkedChannelsFilterTheProgramme() {
        ready()
        tab("Chaînes")
        compose.onNodeWithText("beIN Test").performClick()
        waitFor("chaîne enregistrée") { settings().myChannels == setOf("c2") }
        tab("À venir")
        assertTrue(shown("Paris-SG – Marseille")); assertTrue(shown("Alcaraz – Sinner"))
        assertFalse(shown("Lille – Lyon")); assertFalse(shown("Monaco – Rennes"))
        tab("Chaînes")
        compose.onNodeWithText("beIN Test").performClick()
        waitFor("chaîne décochée") { settings().myChannels.isEmpty() }
        tab("À venir")
        assertTrue(shown("Lille – Lyon"))
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
        assertTrue(shown("Lille – Lyon")); assertFalse(shown("Alcaraz – Sinner")); assertFalse(shown("Nice – Lens"))
    }

    @Test fun eventSheetAlertSwitchAndFavourites() {
        ready()
        compose.onNodeWithText("Paris-SG – Marseille").performClick()
        waitFor("fiche ouverte") { shown("Ajouter à mon calendrier") }
        assertTrue(shown("Sur beIN Test")); assertTrue(shown("Direct confirmé"))
        val switch = compose.onAllNodes(isToggleable()).onFirst()
        switch.assertIsOff()
        switch.performClick()
        waitFor("alerte posée") { alarms().size == 1 }
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
        val psg = events().first { it.home == "Paris-SG" }
        val alarm = alarms().single()
        assertEquals("alarme 15 min avant le début", psg.start - 15 * min, alarm.triggerAtMs)
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        waitFor("alerte retirée") { alarms().isEmpty() }
    }

    @Test fun alertDelayAndGlobalSwitch() {
        ready()
        compose.runOnUiThread { }
        tab("Favoris")
        compose.onNodeWithText("Paris-SG").performClick()
        waitFor("alarme") { alarms().size == 1 }
        val psg = events().first { it.home == "Paris-SG" }
        tab("Réglages")
        compose.onNodeWithText("30 min").performClick()
        waitFor("délai 30 min") { alarms().singleOrNull()?.triggerAtMs == psg.start - 30 * min }
        compose.onNodeWithText("Me prévenir avant mes favoris").performClick()
        waitFor("alertes coupées") { alarms().isEmpty() }
        compose.onNodeWithText("Me prévenir avant mes favoris").performClick()
        waitFor("alertes rétablies") { alarms().size == 1 }
    }

    @Test fun alarmFiresANotificationNamingTheChannelOnlyOnce() {
        val channels = guide.channels.associateBy { it.id }
        val s = Settings().toggleTeam("Lille")
        Alerts.reschedule(app, events(), channels, s)
        val alarm = alarms().single()
        val intent = shadowOf(alarm.operation).savedIntent
        AlertReceiver().onReceive(app, intent)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Lille – Lyon", posted.extras.getString("android.title"))
        assertTrue(posted.extras.getString("android.text")!!.contains("Canal Test"))
        // Une mise à jour horaire ne doit pas reprogrammer une alerte déjà envoyée.
        Alerts.reschedule(app, events(), channels, s)
        assertTrue(alarms().isEmpty())
    }

    @Test fun alarmsDisappearWhenAMatchIsCancelled() {
        val channels = guide.channels.associateBy { it.id }
        val s = Settings().toggleTeam("Lille")
        Alerts.reschedule(app, events(), channels, s)
        assertEquals(1, alarms().size)
        // Le calendrier ne contient plus Lille – Lyon (reporté) : plus de direct, plus d'alerte.
        Fixtures(fixtures.list.filter { it.home != "Lille" }, fixtures.coveredDays).save(app.filesDir)
        val after = events()
        assertTrue(after.first { it.home == "Lille" }.replay)
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

    @Test fun favouritesSurviveARestartAndStaySelectableWhenTheTeamLeavesTheGuide() {
        Settings().toggleTeam("Lille").toggleCompetition("Tennis", "ATP Finals").toggleSport("Rugby").toggleChannel("c2")
            .copy(alertMinutes = 60, hideReplays = false).save(app)
        val back = Settings.load(app)
        assertTrue(back.isFavTeam("Lille")); assertTrue(back.isFavCompetition("Tennis", "ATP Finals")); assertTrue(back.isFavSport("Rugby"))
        assertEquals(setOf("c2"), back.myChannels); assertEquals(60, back.alertMinutes); assertFalse(back.hideReplays)
        // Catalogue : une équipe vue une fois reste proposée même absente du guide suivant.
        Catalogue.EMPTY.merged(events()).save(app.filesDir)
        val later = Catalogue.load(app.filesDir).merged(emptyList())
        assertTrue(later.teams.values.any { it.first == "Lille" }); assertTrue(later.teams.values.any { it.first == "Sinner" })
        assertTrue(later.competitions.values.any { it.second == "ATP Finals" })
    }

    @Test fun corruptedCacheFilesDoNotCrashTheApp() {
        File(app.filesDir, "fixtures.tsv").writeText("F\tfootball\tpas-un-nombre\nn'importe quoi\n\n")
        File(app.filesDir, "catalogue.tsv").writeText("T\tseul\n\u0000\u0001\n")
        File(app.filesDir, "guide.tsv").appendText("B\ttronqué\nB\t1\t2\n")
        ready()
        assertTrue(shown("Lille – Lyon"))
    }
}
