@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.perso.sportantenne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.clip
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
import fr.perso.sportantenne.Settings
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

private val Gold = Color(0xFFF2C14E)
private val Live = Color(0xFFFF6B5E)

private val Scheme = darkColorScheme(
    primary = Color(0xFF7CE0A3),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF1D4A33),
    onPrimaryContainer = Color(0xFFB9F5CF),
    secondary = Gold,
    secondaryContainer = Color(0xFF23352B),
    onSecondaryContainer = Color(0xFFDDEBE2),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE6EFE9),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFE6EFE9),
    surfaceVariant = Color(0xFF1A2620),
    onSurfaceVariant = Color(0xFF9DB0A5),
    surfaceContainer = Color(0xFF131C17),
    surfaceContainerLow = Color(0xFF16201B),
    surfaceContainerHigh = Color(0xFF1A2620),
    outline = Color(0xFF3A4A41),
    outlineVariant = Color(0xFF25332B),
)

private enum class Tab(val label: String, val icon: ImageVector) {
    UPCOMING("À venir", Icons.Filled.Home),
    FAVORITES("Favoris", Icons.Filled.Star),
    EXPLORE("Explorer", Icons.Filled.Search),
    SETTINGS("Réglages", Icons.Filled.Settings),
}

@Composable
fun SportAntenneApp(state: AppState = viewModel()) {
    MaterialTheme(colorScheme = Scheme) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
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
                    title = {
                        Column {
                            Text("Sport Antenne", fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    state.loading -> "Mise à jour du guide…"
                                    state.lastUpdate == 0L -> "Guide non chargé"
                                    else -> "Guide du " + SimpleDateFormat("d MMM à HH:mm", Locale.FRENCH).format(Date(state.lastUpdate))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = state::refresh) { Icon(Icons.Filled.Refresh, "Mettre à jour") }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let {
                    Text(
                        it,
                        Modifier.fillMaxWidth().background(Color(0xFF4A1F1B)).padding(12.dp),
                        color = Color(0xFFFFDAD5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (Tab.entries[tab]) {
                    Tab.UPCOMING -> UpcomingScreen(visible, state, now) { selected = it }
                    Tab.FAVORITES -> FavoritesScreen(visible, state, now) { selected = it }
                    Tab.EXPLORE -> ExploreScreen(visible, state, now) { selected = it }
                    Tab.SETTINGS -> SettingsScreen(state)
                }
            }
        }
        selected?.let { EventSheet(it, state, now) { selected = null } }
    }
}

// ───────────────────────────── Écrans ─────────────────────────────

@Composable
private fun UpcomingScreen(events: List<Event>, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    val sports = remember(events) { events.groupingBy { it.sport }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    if (sport != null && sport !in sports) sport = null
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(sport == null, { sport = null }, { Text("Tous") }) }
            items(sports) { s -> FilterChip(sport == s, { sport = if (sport == s) null else s }, { Text(s) }) }
        }
        EventList(
            if (sport == null) events else events.filter { it.sport == sport },
            state, now, onOpen,
            empty = if (state.loading) "Chargement du guide TV…" else "Aucun événement à venir. Touchez ↻ pour mettre à jour.",
        )
    }
}

@Composable
private fun FavoritesScreen(events: List<Event>, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    val settings = state.settings
    val mine = remember(events, settings) { events.filter { settings.isFavorite(it) } }
    EventList(
        mine, state, now, onOpen,
        empty = if (settings.hasFavorites) "Aucun de vos favoris n'est programmé dans les prochains jours."
        else "Pas encore de favoris.\n\nTouchez un match puis ★ sur une équipe, une compétition ou un sport — " +
            "ou parcourez l'onglet Explorer.",
    )
}

@Composable
private fun EventList(events: List<Event>, state: AppState, now: Long, onOpen: (Event) -> Unit, empty: String) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val rows = remember(events) {
        val out = ArrayList<Any>()
        var last = ""
        for (e in events) {
            val d = dayLabel(e.start, now)
            if (d != last && e.start > now) { out += d; last = d }
            else if (e.start <= now && last.isEmpty()) { out += "En ce moment"; last = "En ce moment" }
            out += e
        }
        out
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows) { row ->
            when (row) {
                is String -> Text(
                    row.uppercase(),
                    Modifier.padding(top = 10.dp, start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                is Event -> EventCard(row, state, now, onOpen)
            }
        }
    }
}

@Composable
private fun EventCard(e: Event, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    val live = e.start <= now
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(e) },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hour(e.start), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (live) Badge("EN COURS", Live) else if (e.replay) Badge("REDIFF", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.headline, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle(e),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    e.channels.take(4).forEach { ChannelPill(state.channels[it], it) }
                    if (e.channels.size > 4) Text("+${e.channels.size - 4}", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (state.settings.isFavorite(e)) Icon(Icons.Filled.Star, "Favori", Modifier.size(18.dp), tint = Gold)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun ChannelPill(channel: Channel?, id: String) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!channel?.logo.isNullOrEmpty()) {
            AsyncImage(channel?.logo, null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(5.dp))
        }
        Text(channel?.name ?: id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

// ───────────────────────────── Explorer ─────────────────────────────

@Composable
private fun ExploreScreen(events: List<Event>, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    var competition by rememberSaveable { mutableStateOf<String?>(null) }
    val settings = state.settings
    BackHandler(enabled = sport != null || query.isNotEmpty()) {
        when {
            query.isNotEmpty() -> query = ""
            competition != null -> competition = null
            else -> sport = null
        }
    }
    Column {
        OutlinedTextField(
            query, { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("Équipe, joueur, compétition…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
        )
        val q = norm(query)
        when {
            q.length >= 2 -> SearchResults(events, q, state, now, onOpen)
            sport == null -> {
                val sports = remember(events) { events.groupingBy { it.sport }.eachCount().entries.sortedByDescending { it.value } }
                LazyColumn {
                    item { SectionTitle("Sports") }
                    items(sports) { (name, count) ->
                        StarRow(name, "$count à venir", settings.isFavSport(name), { state.update { it.toggleSport(name) } }) { sport = name }
                    }
                }
            }
            competition == null -> {
                val s = sport!!
                val comps = remember(events, s) {
                    events.filter { it.sport == s }.groupBy { competitionKey(it.sport, it.competition) }
                        .values.sortedByDescending { it.size }
                }
                LazyColumn {
                    item { BackTitle(s) { sport = null } }
                    items(comps) { list ->
                        val name = list.first().competition
                        StarRow(
                            name, "${list.size} à venir", settings.isFavCompetition(s, name),
                            { state.update { it.toggleCompetition(s, name) } },
                        ) { competition = name }
                    }
                }
            }
            else -> {
                val key = competitionKey(sport!!, competition!!)
                Column {
                    BackTitle("$sport · $competition") { competition = null }
                    EventList(events.filter { competitionKey(it.sport, it.competition) == key }, state, now, onOpen, "Rien à venir.")
                }
            }
        }
    }
}

@Composable
private fun SearchResults(events: List<Event>, q: String, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    val settings = state.settings
    val teams = remember(events, q) {
        events.filter { it.hasTeams }.flatMap { listOf(it.home, it.away) }
            .filter { norm(it).contains(q) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(12)
    }
    val comps = remember(events, q) {
        events.filter { norm(it.competition).contains(q) }.groupBy { competitionKey(it.sport, it.competition) }
            .values.sortedByDescending { it.size }.take(8)
    }
    val matches = remember(events, q) {
        events.filter { norm(it.headline).contains(q) || norm(it.competition).contains(q) || norm(it.detail).contains(q) }.take(60)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        if (teams.isNotEmpty()) item { SectionTitle("Équipes et joueurs") }
        items(teams) { (name, count) ->
            StarRow(name, "$count à venir", settings.isFavTeam(name), { state.update { it.toggleTeam(name) } }, null)
        }
        if (comps.isNotEmpty()) item { SectionTitle("Compétitions") }
        items(comps) { list ->
            val e = list.first()
            StarRow(
                e.competition, "${e.sport} · ${list.size} à venir", settings.isFavCompetition(e.sport, e.competition),
                { state.update { it.toggleCompetition(e.sport, e.competition) } }, null,
            )
        }
        item { SectionTitle(if (matches.isEmpty()) "Aucune diffusion trouvée" else "Diffusions") }
        items(matches) { e ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { EventCard(e, state, now, onOpen) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun BackTitle(text: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.ArrowBack, "Retour", Modifier.padding(8.dp))
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StarRow(title: String, subtitle: String, starred: Boolean, onStar: () -> Unit, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onStar) {
            Icon(
                Icons.Filled.Star, if (starred) "Retirer des favoris" else "Ajouter aux favoris",
                tint = if (starred) Gold else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ───────────────────────────── Réglages ─────────────────────────────

@Composable
private fun SettingsScreen(state: AppState) {
    var pickingChannels by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    if (pickingChannels) {
        BackHandler { pickingChannels = false }
        ChannelPicker(state) { pickingChannels = false }
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("Affichage")
        SwitchRow("Masquer les rediffusions", "Détection automatique : un match déjà diffusé plus tôt est considéré comme une rediffusion.", settings.hideReplays) { v ->
            state.update { it.copy(hideReplays = v) }
        }
        SwitchRow("Afficher les magazines", "Émissions, documentaires et journaux sportifs en plus des compétitions.", settings.showMagazines) { v ->
            state.update { it.copy(showMagazines = v) }
        }
        SectionTitle("Mes chaînes")
        Row(Modifier.fillMaxWidth().clickable { pickingChannels = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (settings.myChannels.isEmpty()) "Toutes les chaînes" else "${settings.myChannels.size} chaînes sélectionnées")
                Text(
                    "Choisissez les chaînes que vous recevez pour masquer le reste.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Choisir", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        SectionTitle("Mes favoris")
        if (!settings.hasFavorites) {
            Text("Aucun favori pour l'instant.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        settings.favTeams.sorted().forEach { t -> StarRow(t, "Équipe / joueur", true, { state.update { it.toggleTeam(t) } }, null) }
        settings.favCompetitions.sorted().forEach { c ->
            val sport = c.substringBefore('|')
            val name = c.substringAfter('|')
            StarRow(name, "Compétition · $sport", true, { state.update { it.toggleCompetition(sport, name) } }, null)
        }
        settings.favSports.sorted().forEach { s -> StarRow(s, "Sport", true, { state.update { it.toggleSport(s) } }, null) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(
            "Programmes : guide XMLTV public xmltvfr.fr, mis à jour automatiquement toutes les 6 heures. " +
                "Aucune publicité, aucun compte, aucun achat.",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ChannelPicker(state: AppState, onBack: () -> Unit) {
    val settings = state.settings
    val list = remember(state.events, state.channels) {
        state.events.filter { it.kind == Kind.EVENT }.flatMap { it.channels }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { state.channels[it.key] ?: Channel(it.key, it.key, "") }
    }
    Column {
        BackTitle("Mes chaînes (${if (settings.myChannels.isEmpty()) "toutes" else settings.myChannels.size.toString()})", onBack)
        LazyColumn {
            items(list, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { state.update { it.toggleChannel(c.id) } }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(c.id in settings.myChannels, { state.update { it.toggleChannel(c.id) } })
                    if (c.logo.isNotEmpty()) {
                        AsyncImage(c.logo, null, Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(c.name)
                }
            }
        }
    }
}

// ───────────────────────────── Fiche événement ─────────────────────────────

@Composable
private fun EventSheet(e: Event, state: AppState, now: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val settings = state.settings
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(subtitle(e), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(e.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                (if (e.start <= now) "En cours · " else "") + dayLabel(e.start, now) + " · " + hour(e.start) + " – " + hour(e.stop) +
                    if (e.replay) " · Rediffusion" else "",
                color = if (e.start <= now) Live else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                e.channels.forEach { ChannelPill(state.channels[it], it) }
            }
            if (e.desc.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(e.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("Suivre")
            if (e.hasTeams) {
                StarRow(e.home, "Équipe / joueur", settings.isFavTeam(e.home), { state.update { it.toggleTeam(e.home) } }, null)
                StarRow(e.away, "Équipe / joueur", settings.isFavTeam(e.away), { state.update { it.toggleTeam(e.away) } }, null)
            }
            if (e.kind == Kind.EVENT && norm(e.competition) != norm(e.sport)) {
                StarRow(e.competition, "Compétition", settings.isFavCompetition(e.sport, e.competition), { state.update { it.toggleCompetition(e.sport, e.competition) } }, null)
            }
            StarRow(e.sport, "Sport", settings.isFavSport(e.sport), { state.update { it.toggleSport(e.sport) } }, null)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { addToCalendar(ctx, e, state.channels) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DateRange, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter à mon agenda")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
    )
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
    val days = ((startOfDay(b) - startOfDay(a)) / 86_400_000.0).let { Math.round(it).toInt() }
    return when (days) {
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
