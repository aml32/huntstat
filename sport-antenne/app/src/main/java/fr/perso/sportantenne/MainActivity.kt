package fr.perso.sportantenne

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.perso.sportantenne.core.Catalogue
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.ui.SportAntenneApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Fabrique explicite : l'état est toujours construit avec l'application courante.
    private val state: AppState by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppState(application) as T
        }
    }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.scheduleBackgroundRefresh(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SportAntenneApp(state) }
    }

    override fun onResume() {
        super.onResume()
        // Retour dans l'appli : on revérifie si la dernière mise à jour date de plus d'une heure.
        state.foreground = true
        state.refreshIfStale()
    }

    override fun onPause() {
        super.onPause()
        state.foreground = false
    }
}

class AppState(app: Application) : AndroidViewModel(app) {
    var events by mutableStateOf<List<Event>>(emptyList())
        private set
    var channels by mutableStateOf<Map<String, Channel>>(emptyMap())
        private set
    var catalogue by mutableStateOf(Catalogue.EMPTY)
        private set
    var settings by mutableStateOf(Settings.load(app))
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastUpdate by mutableLongStateOf(0L)
        private set
    var calendars by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** L'appli est à l'écran : les scores ne sont rafraîchis que dans ce cas. */
    @Volatile var foreground = false

    init {
        // Pendant un match confirmé, les scores sont relus toutes les minutes.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                if (!foreground || loading || !Repo.liveUpdates) continue
                val now = System.currentTimeMillis()
                val sources = events.filter { it.verified && it.score?.finished != true && now >= it.start - 15 * 60_000 && now <= it.stop + 45 * 60_000 }
                    .mapNotNull { e -> fr.perso.sportantenne.core.LiveCheck.sourceOf(e.sport, e.competition) }.toSet()
                try { Repo.refreshLive(app, sources)?.let { show(it) } } catch (e: Exception) { /* prochain essai dans une minute */ }
            }
        }
        viewModelScope.launch {
            Repo.load(app)?.let { show(it) }
            if (events.isEmpty() || System.currentTimeMillis() - lastUpdate > Repo.STALE_MS) refresh()
        }
    }

    private fun show(data: Data) {
        channels = data.channels
        catalogue = data.catalogue
        events = data.events
        lastUpdate = data.updated
        calendars = data.calendars
    }

    fun refreshIfStale() {
        if (lastUpdate != 0L && System.currentTimeMillis() - lastUpdate > Repo.STALE_MS) refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                show(Repo.refresh(getApplication()))
                // Deuxième source, lente : lue ensuite en arrière-plan, au plus toutes les six heures.
                if (Repo.liveUpdates && Repo.extraIsStale(getApplication())) {
                    viewModelScope.launch { runCatching { Repo.refreshExtra(getApplication())?.let { show(it) } } }
                }
            } catch (e: Exception) {
                error = if (events.isEmpty()) "Programme indisponible : vérifiez la connexion puis réessayez."
                else "Mise à jour impossible pour l'instant (${e.message ?: "réseau"}). Programme précédent affiché."
            } finally {
                loading = false
            }
        }
    }

    fun update(change: (Settings) -> Settings) {
        val next = change(settings)
        settings = next
        val snapshot = events
        val names = channels
        viewModelScope.launch(Dispatchers.IO) {
            next.save(getApplication())
            UpcomingWidget.render(getApplication(), snapshot, names, next)
            Alerts.reschedule(getApplication(), snapshot, names, next)
        }
    }
}
