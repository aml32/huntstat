package fr.perso.sportantenne

import android.app.AlarmManager
import android.app.Application
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.captureRoboImage
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.XmltvParser
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
import java.util.zip.GZIPInputStream

/**
 * Lance la vraie activité sur un vrai guide mis en cache et parcourt les écrans.
 * GUIDE_GZ=/chemin/xmltv_fr.xml.gz SHOTS=/dossier ./gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val shots = File(System.getenv("SHOTS") ?: "build/shots")

    private val seed = object : ExternalResource() {
        override fun before() {
            val path = System.getenv("GUIDE_GZ")
            assumeTrue(path != null && File(path).exists())
            WorkManagerTestInitHelper.initializeTestWorkManager(
                app, Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).setExecutor(SynchronousExecutor()).build(),
            )
            val guide = GZIPInputStream(File(path!!).inputStream()).use { XmltvParser.parse(it) }
            GuideStore.save(app.filesDir, guide)
            System.getenv("FIXTURES_TSV")?.let { File(it).copyTo(File(app.filesDir, "fixtures.tsv"), overwrite = true) }
            Settings(favTeams = setOf("Paris-SG"), favCompetitions = setOf("Rugby|Top 14")).save(app)
            shots.mkdirs()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage(File(shots, "$name.png").path)
    }

    @Test fun walkThroughEveryScreen() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Tous les sports").fetchSemanticsNodes().isNotEmpty() }
        shot("1-a-venir")
        compose.onNodeWithText("Demain").performClick()
        shot("2-demain")
        compose.onNodeWithText("Auj.").performClick()
        compose.onNodeWithText("Football").performClick()
        compose.waitUntil(5_000) { Settings.load(app).isFavSport("Football") }
        shot("3-football-seulement")
        compose.onNodeWithText("Tous les sports").performClick()
        compose.waitUntil(5_000) { Settings.load(app).favSports.isEmpty() }

        compose.onNodeWithText("Favoris").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Paris-SG").fetchSemanticsNodes().isNotEmpty() }
        shot("4-favoris-equipes")
        compose.onNode(hasText("Rechercher une équipe ou un joueur")).performTextInput("lyon")
        shot("5-favoris-recherche")
        compose.onAllNodesWithText("Lyon").onFirst().performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { Settings.load(app).isFavTeam("Lyon") }
        compose.onNode(hasText("Compétitions", substring = true)).performClick()
        shot("6-favoris-competitions")
        compose.onNode(hasText("Sports (", substring = true)).performClick()
        shot("7-favoris-sports")

        compose.onNodeWithText("Chaînes").performClick()
        shot("8-chaines")
        compose.onNodeWithText("Réglages").performClick()
        shot("9-reglages")

        compose.onAllNodesWithText("À venir").onLast().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("✓ Direct confirmé", substring = true)).onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ajouter à mon calendrier").fetchSemanticsNodes().isNotEmpty() }
        shot("10-fiche")

        // Sports cochés = filtre du programme.
        val now = System.currentTimeMillis()
        val all = Repo.buildEvents(app, GuideStore.load(app.filesDir)!!)
        assertTrue("directs confirmés", all.count { it.verified } > 20)
        val rugbyOnly = Settings(favSports = setOf("Rugby")).visible(all, now)
        assertTrue(rugbyOnly.isNotEmpty() && rugbyOnly.all { it.sport == "Rugby" })

        // Les favoris cochés donnent bien lieu à des alarmes programmées.
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue("alarmes programmées: ${alarms.size}", alarms.isNotEmpty())
    }
}
