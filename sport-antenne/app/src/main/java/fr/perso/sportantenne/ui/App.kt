@file:OptIn(ExperimentalMaterial3Api::class)

package fr.perso.sportantenne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import fr.perso.sportantenne.AppState
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.Kind
import fr.perso.sportantenne.core.competitionKey
import fr.perso.sportantenne.core.norm
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val LiveRed = Color(0xFFC62828)
private val VerifiedGreen = Color(0xFF1B7F3B)

// Thème volontairement très contrasté : texte noir sur blanc (ou blanc sur noir), bandeau bleu foncé.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0D47A1), onPrimary = Color.White,
    primaryContainer = Color(0xFF0D47A1), onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF0D47A1), onSecondaryContainer = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFE3E8EF), onSurfaceVariant = Color(0xFF2B2B2B),
    surfaceContainer = Color(0xFFEEF1F5), surfaceContainerLow = Color(0xFFF5F7FA), surfaceContainerHigh = Color(0xFFE3E8EF),
    outline = Color(0xFF5A6472), outlineVariant = Color(0xFFB8C0CC), error = Color(0xFFB71C1C),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF82B1FF), onPrimary = Color.Black,
    primaryContainer = Color(0xFF82B1FF), onPrimaryContainer = Color.Black,
    secondaryContainer = Color(0xFF82B1FF), onSecondaryContainer = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2F36), onSurfaceVariant = Color(0xFFE0E0E0),
    surfaceContainer = Color(0xFF16191D), surfaceContainerLow = Color(0xFF101214), surfaceContainerHigh = Color(0xFF22262B),
    outline = Color(0xFFAAB2BD), outlineVariant = Color(0xFF4A515B), error = Color(0xFFFF8A80),
)

private enum class Screen(val label: String, val icon: ImageVector) {
    PROGRAMME("À venir", Icons.Filled.Home),
    FAVORITES("Favoris", Icons.Filled.Star),
    CHANNELS("Chaînes", Icons.Filled.PlayArrow),
    SETTINGS("Réglages", Icons.Filled.Settings),
}

@Composable
fun SportAntenneApp(state: AppState = viewModel()) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme) {
        var screen by rememberSaveable { mutableIntStateOf(0) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                now = System.currentTimeMillis()
            }
        }
        var selected by remember { mutableStateOf<Event?>(null) }
        val settings = state.settings
        val visible = remember(state.events, settings, now) { settings.visible(state.events, now) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(Screen.entries[screen].label, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D2A4D), titleContentColor = Color.White, actionIconContentColor = Color.White,
                    ),
                    actions = {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = state::refresh) { Icon(Icons.Filled.Refresh, "Mettre à jour le programme") }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEachIndexed { i, s ->
                        NavigationBarItem(screen == i, { screen = i }, { Icon(s.icon, null) }, label = { Text(s.label) })
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let {
                    Text(
                        it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (Screen.entries[screen]) {
                    Screen.PROGRAMME -> ProgrammeScreen(visible, state, now) { selected = it }
                    Screen.FAVORITES -> FavoritesScreen(state)
                    Screen.CHANNELS -> ChannelsScreen(state)
                    Screen.SETTINGS -> SettingsScreen(state)
                }
            }
        }
        // La fiche suit l'état courant (favoris, alerte) de l'événement ouvert.
        selected?.let { EventSheet(it, state, now) { selected = null } }
    }
}

// ───────────────────────────── À venir : un jour par page ─────────────────────────────

@Composable
private fun ProgrammeScreen(events: List<Event>, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val scoped = remember(events, mineOnly, settings) { if (mineOnly) events.filter { settings.isFavorite(it) } else events }
    // Sports proposés : tous ceux du programme, y compris ceux actuellement masqués par le filtre.
    val sports = remember(state.events, settings.myChannels, settings.hideReplays, settings.verifiedOnly, now) {
        settings.copy(favSports = emptySet()).visible(state.events, now)
            .groupingBy { it.sport }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    val today = startOfDay(Calendar.getInstance().apply { timeInMillis = now })
    val days = remember(today) {
        (0..6).map { Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, it) }.timeInMillis }
    }
    val pager = rememberPagerState { days.size }
    val scope = rememberCoroutineScope()

    Column {
        // Les jours côte à côte ; on glisse à gauche ou à droite pour changer de jour.
        ScrollableTabRow(pager.currentPage, edgePadding = 4.dp) {
            days.forEachIndexed { i, d ->
                Tab(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } }) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (i) { 0 -> "Auj."; 1 -> "Demain"; else -> SimpleDateFormat("EEE", Locale.FRENCH).format(Date(d)).replaceFirstChar { it.uppercase() } },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(SimpleDateFormat("d MMM", Locale.FRENCH).format(Date(d)), fontSize = 13.sp)
                    }
                }
            }
        }
        // Sports à cocher : le choix est mémorisé et filtre tout le programme.
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(mineOnly, { mineOnly = !mineOnly }, { Text("★ Mes favoris", fontWeight = FontWeight.Bold) }) }
            item { FilterChip(settings.favSports.isEmpty(), { state.update { it.copy(favSports = emptySet()) } }, { Text("Tous les sports") }) }
            items(sports) { s -> FilterChip(settings.isFavSport(s), { state.update { it.toggleSport(s) } }, { Text(s) }) }
        }
        HorizontalDivider()
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            val from = days[page]
            val to = from + 86_400_000L + 3_600_000L // marge pour les changements d'heure
            val list = remember(scoped, page, from) {
                scoped.filter { e ->
                    if (page == 0) e.start < days[1] else e.start >= from && e.start < (days.getOrNull(page + 1) ?: to)
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            state.loading -> "Chargement du programme…"
                            mineOnly && !settings.hasFavorites -> "Cochez vos équipes et compétitions dans l'onglet Favoris."
                            mineOnly -> "Aucun de vos favoris en direct ce jour-là."
                            else -> "Aucun direct ce jour-là."
                        },
                        fontSize = 16.sp,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { e ->
                        EventRow(e, state, now, onOpen)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: Event, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    val live = e.start <= now
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp)) {
            Text(hour(e.start), fontWeight = FontWeight.Bold, fontSize = 19.sp)
            if (live) Text("EN COURS", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(e.headline, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle(e), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.channels.joinToString(" · ") { state.channels[it]?.name ?: it },
                    Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (e.verified) {
                    Text("  ✓ Direct confirmé", color = VerifiedGreenFor(), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                } else if (e.replay) {
                    Text("  Rediffusion", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        if (state.settings.hasAlert(e) && !live) {
            Icon(Icons.Filled.Notifications, "Alerte activée", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun VerifiedGreenFor(): Color = if (isSystemInDarkTheme()) Color(0xFF69F0AE) else VerifiedGreen

// ───────────────────────────── Favoris à cocher ─────────────────────────────

private class Choice(val label: String, val note: String, val checked: Boolean, val toggle: () -> Unit)

@Composable
private fun FavoritesScreen(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val settings = state.settings
    val catalogue = state.catalogue
    Column {
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Équipes (${settings.favTeams.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Compétitions (${settings.favCompetitions.size})") })
            Tab(tab == 2, { tab = 2 }, text = { Text("Sports (${settings.favSports.size})") })
        }
        when (tab) {
            0 -> {
                val known = catalogue.teams
                val choices = remember(known, settings.favTeams) {
                    val extra = settings.favTeams.filter { norm(it) !in known }.map { it to "" }
                    (known.values + extra).map { (name, sport) ->
                        Choice(name, sport, settings.isFavTeam(name)) { state.update { it.toggleTeam(name) } }
                    }
                }
                Checklist(choices, "Rechercher une équipe ou un joueur") { typed -> state.update { it.toggleTeam(typed) } }
            }
            1 -> {
                val known = catalogue.competitions
                val choices = remember(known, settings.favCompetitions) {
                    val extra = settings.favCompetitions
                        .filter { competitionKey(it.substringBefore('|'), it.substringAfter('|')) !in known }
                        .map { it.substringBefore('|') to it.substringAfter('|') }
                    (known.values + extra).map { (sport, name) ->
                        Choice(name, sport, settings.isFavCompetition(sport, name)) { state.update { it.toggleCompetition(sport, name) } }
                    }
                }
                Checklist(choices, "Rechercher une compétition", null)
            }
            else -> {
                val choices = remember(state.events, known(catalogue), settings.favSports) {
                    val names = (catalogue.competitions.values.map { it.first } + catalogue.teams.values.map { it.second } +
                        state.events.filter { it.kind == Kind.EVENT }.map { it.sport } + settings.favSports)
                        .filter { it.isNotBlank() }.distinctBy { norm(it) }
                    names.map { s -> Choice(s, "", settings.isFavSport(s)) { state.update { it.toggleSport(s) } } }
                }
                Text(
                    if (settings.favSports.isEmpty()) "Aucun sport coché : tous les sports sont affichés. Cochez ceux qui vous intéressent pour masquer les autres."
                    else "${settings.favSports.size} sports cochés : seul leur programme est affiché (plus vos équipes et compétitions).",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Checklist(choices, "Rechercher un sport", null)
            }
        }
    }
}

private fun known(c: fr.perso.sportantenne.core.Catalogue) = c.teams.size + c.competitions.size

/** Liste à cocher avec recherche : les éléments cochés en premier, puis l'ordre alphabétique. */
@Composable
private fun Checklist(choices: List<Choice>, hint: String, onAdd: ((String) -> Unit)?) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = norm(query)
    val shown = remember(choices, q) {
        choices.filter { q.isEmpty() || norm(it.label).contains(q) || norm(it.note).contains(q) }
            .sortedWith(compareBy<Choice> { !it.checked }.thenBy { norm(it.label) })
    }
    Column {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text(hint) }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Clear, "Effacer") } },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.label + "|" + it.note }) { c -> CheckRow(c.label, c.note, c.checked, null, c.toggle) }
            val typed = query.trim()
            if (onAdd != null && typed.length >= 2 && choices.none { norm(it.label) == q }) {
                item {
                    Text(
                        "Ajouter « $typed » à mes favoris",
                        Modifier.fillMaxWidth().clickable { onAdd(typed); query = "" }.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (shown.isEmpty() && (onAdd == null || query.trim().length < 2)) {
                item {
                    Text(
                        if (choices.isEmpty()) "La liste se remplit au premier chargement du programme." else "Aucun résultat.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, note: String, checked: Boolean, logo: String?, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onToggle() })
        if (!logo.isNullOrEmpty()) {
            AsyncImage(logo, null, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────────────────────────── Chaînes ─────────────────────────────

@Composable
private fun ChannelsScreen(state: AppState) {
    val settings = state.settings
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(state.events, state.channels) {
        state.events.filter { it.kind == Kind.EVENT }.flatMap { it.channels }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { state.channels[it.key] ?: Channel(it.key, it.key, "") }
    }
    val q = norm(query)
    val shown = if (q.isEmpty()) all else all.filter { norm(it.name).contains(q) }
    Column {
        Text(
            if (settings.myChannels.isEmpty()) "Aucune chaîne cochée : tout le programme est affiché. Cochez les chaînes que vous recevez pour masquer les autres."
            else "${settings.myChannels.size} chaînes cochées : seul leur programme est affiché.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Rechercher une chaîne") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { c ->
                CheckRow(c.name, "", c.id in settings.myChannels, c.logo) { state.update { it.toggleChannel(c.id) } }
            }
        }
    }
}

// ───────────────────────────── Réglages ─────────────────────────────

private val DELAYS = listOf(5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h")

@Composable
private fun SettingsScreen(state: AppState) {
    val settings = state.settings
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("Alertes")
        SwitchRow("Me prévenir avant mes favoris", "Notification avant chaque diffusion d'une équipe ou d'une compétition cochée.", settings.alertsEnabled) { v ->
            state.update { it.copy(alertsEnabled = v) }
        }
        Text("Délai avant le début", Modifier.padding(start = 16.dp, top = 4.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DELAYS.forEach { (minutes, label) ->
                FilterChip(settings.alertMinutes == minutes, { state.update { it.copy(alertMinutes = minutes) } }, { Text(label) })
            }
        }
        SectionTitle("Programme")
        SwitchRow("Masquer les rediffusions", "Recommandé. Chaque match est comparé aux calendriers réels : s'il ne se joue pas à cette heure-là, c'est une rediffusion.", settings.hideReplays) { v ->
            state.update { it.copy(hideReplays = v) }
        }
        SwitchRow("Directs confirmés uniquement", "N'afficher que les matchs dont l'horaire est confirmé par un calendrier sportif (football, NBA, NFL, NHL, rugby, Formule 1). Les autres sports disparaissent.", settings.verifiedOnly) { v ->
            state.update { it.copy(verifiedOnly = v) }
        }
        SwitchRow("Afficher les magazines", "Émissions, documentaires et journaux sportifs.", settings.showMagazines) { v ->
            state.update { it.copy(showMagazines = v) }
        }
        SectionTitle("Mise à jour")
        Text(
            (if (state.lastUpdate == 0L) "Programme jamais chargé."
            else "Dernière mise à jour : " + SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRENCH).format(Date(state.lastUpdate)) + ".") +
                "\nLe programme se met à jour tout seul, à l'ouverture et environ toutes les 4 heures.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = state::refresh, enabled = !state.loading, modifier = Modifier.padding(16.dp)) {
            Text(if (state.loading) "Mise à jour…" else "Mettre à jour maintenant")
        }
        Text(
            "Chaînes : guide TV xmltvfr.fr. Horaires réels des matchs : calendriers ESPN et Jolpica (F1). Sans publicité, sans compte.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

// ───────────────────────────── Fiche d'un événement ─────────────────────────────

@Composable
private fun EventSheet(e: Event, state: AppState, now: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val settings = state.settings
    val names = e.channels.joinToString(", ") { state.channels[it]?.name ?: it }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(e.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle(e), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (e.start <= now) "En cours · " else "") + dayLabel(e.start, now) + " de " + hour(e.start) + " à " + hour(e.stop) +
                        if (e.replay) " · Rediffusion" else if (e.verified) " · Direct confirmé" else "",
                    fontWeight = FontWeight.SemiBold, color = if (e.start <= now) LiveRed else MaterialTheme.colorScheme.onSurface,
                )
                Text("Sur $names", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (e.desc.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(e.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { addToCalendar(ctx, e, state.channels) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter à mon calendrier")
                }
            }
            if (e.start > now) {
                SwitchRow("M'alerter avant le début", "Notification ${DELAYS.firstOrNull { it.first == settings.alertMinutes }?.second ?: "${settings.alertMinutes} min"} avant, avec la chaîne.", settings.hasAlert(e)) {
                    state.update { it.toggleAlert(e) }
                }
            }
            SectionTitle("Mes favoris")
            if (e.hasTeams) {
                CheckRow(e.home, "Équipe / joueur", settings.isFavTeam(e.home), null) { state.update { it.toggleTeam(e.home) } }
                CheckRow(e.away, "Équipe / joueur", settings.isFavTeam(e.away), null) { state.update { it.toggleTeam(e.away) } }
            }
            if (e.kind == Kind.EVENT && e.competition.isNotBlank() && norm(e.competition) != norm(e.sport)) {
                CheckRow(e.competition, "Compétition", settings.isFavCompetition(e.sport, e.competition), null) {
                    state.update { it.toggleCompetition(e.sport, e.competition) }
                }
            }
        }
    }
}

// ───────────────────────────── Utilitaires ─────────────────────────────

private fun subtitle(e: Event): String = buildList {
    add(e.sport)
    if (e.competition.isNotBlank() && norm(e.competition) != norm(e.sport) && e.competition != e.headline) add(e.competition)
    if (e.detail.isNotBlank()) add(e.detail)
}.joinToString(" · ")

private fun hour(t: Long): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))

private fun dayLabel(t: Long, now: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = now }
    val b = Calendar.getInstance().apply { timeInMillis = t }
    return when (Math.round((startOfDay(b) - startOfDay(a)) / 86_400_000.0).toInt()) {
        0 -> "Aujourd'hui"
        1 -> "Demain"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(Date(t)).replaceFirstChar { it.uppercase() }
    }
}

private fun startOfDay(c: Calendar): Long {
    val d = c.clone() as Calendar
    d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0); d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0)
    return d.timeInMillis
}
