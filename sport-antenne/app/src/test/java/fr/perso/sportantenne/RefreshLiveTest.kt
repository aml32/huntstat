package fr.perso.sportantenne

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import fr.perso.sportantenne.core.Fixtures
import fr.perso.sportantenne.core.GuideStore
import fr.perso.sportantenne.core.Kind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Mise à jour complète contre les vrais serveurs, comme sur un téléphone :
 * LIVE_SOURCES=1 ./gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshLiveTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun fullRefreshThenHourlyRecheck() = runBlocking {
        assumeTrue(System.getenv("LIVE_SOURCES") == "1")
        val t0 = System.currentTimeMillis()
        val first = Repo.refresh(app)
        val firstMs = System.currentTimeMillis() - t0
        val live = first.events.filter { it.kind == Kind.EVENT && !it.replay }
        println("REFRESH 1: ${firstMs} ms, ${first.events.size} diffusions, ${live.size} directs, ${live.count { it.verified }} confirmés, ${first.channels.size} chaînes")
        assertTrue(first.events.size > 500)
        assertTrue(first.channels.size > 100)
        assertTrue("des directs confirmés par le calendrier", live.count { it.verified } > 5)
        assertTrue(Fixtures.load(app.filesDir).list.size > 300)
        assertTrue(first.catalogue.teams.size > 100)
        val etag = app.getSharedPreferences("refresh", Context.MODE_PRIVATE).getString("etag", null)
        assertNotNull("empreinte du guide mémorisée", etag)
        assertTrue(Repo.lastCheck(app) >= t0)

        // Vérification horaire : le guide n'a pas changé, il ne doit pas être retéléchargé.
        val guideFile = File(app.filesDir, "guide.tsv")
        val written = guideFile.lastModified()
        val t1 = System.currentTimeMillis()
        val second = Repo.refresh(app)
        println("REFRESH 2: ${System.currentTimeMillis() - t1} ms")
        assertEquals("guide inchangé : pas de réécriture", written, guideFile.lastModified())
        assertEquals(first.events.size, second.events.size)
        assertTrue(Repo.lastCheck(app) >= t1)

        // La tâche de fond horaire fait la même chose et réussit.
        val worker = TestListenableWorkerBuilder<RefreshWorker>(app).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        // Sans cache local, l'empreinte ne doit pas empêcher un nouveau téléchargement complet.
        guideFile.delete()
        val third = Repo.refresh(app)
        assertTrue(third.events.size > 500)
        assertNotNull(GuideStore.load(app.filesDir))
    }
}
