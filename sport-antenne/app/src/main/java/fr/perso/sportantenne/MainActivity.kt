package fr.perso.sportantenne

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.Guide
import fr.perso.sportantenne.core.Schedule
import fr.perso.sportantenne.ui.SportAntenneApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Repo.scheduleBackgroundRefresh(applicationContext)
        setContent { SportAntenneApp() }
    }
}

class AppState(app: Application) : AndroidViewModel(app) {
    var events by mutableStateOf<List<Event>>(emptyList())
        private set
    var channels by mutableStateOf<Map<String, Channel>>(emptyMap())
        private set
    var settings by mutableStateOf(Settings.load(app))
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastUpdate by mutableStateOf(0L)
        private set

    init {
        viewModelScope.launch {
            Repo.load(app)?.let { show(it) }
            if (events.isEmpty() || System.currentTimeMillis() - lastUpdate > Repo.STALE_MS) refresh()
        }
    }

    private suspend fun show(guide: Guide) {
        val built = withContext(Dispatchers.Default) { Schedule.build(guide.broadcasts) }
        channels = guide.channels.associateBy { it.id }
        events = built
        lastUpdate = Repo.lastUpdate(getApplication())
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                show(Repo.refresh(getApplication()))
            } catch (e: Exception) {
                error = "Mise à jour impossible : ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun update(change: (Settings) -> Settings) {
        val next = change(settings)
        settings = next
        viewModelScope.launch(Dispatchers.IO) {
            next.save(getApplication())
            UpcomingWidget.updateAll(getApplication())
        }
    }
}
