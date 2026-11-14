package fr.perso.sportantenne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import fr.perso.sportantenne.core.Races
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import fr.perso.sportantenne.AppState
import fr.perso.sportantenne.core.Channel
import fr.perso.sportantenne.core.Competitions
import fr.perso.sportantenne.core.Event
import fr.perso.sportantenne.core.Kind
import fr.perso.sportantenne.core.norm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ───────────────────────────── Couleurs ─────────────────────────────

private val Orange = Color(0xFFFF6D00)
private val LiveRed = Color(0xFFD50000)
private val HeaderBrush = Brush.horizontalGradient(listOf(Color(0xFF1A237E), Color(0xFF6A1B9A), Color(0xFFC2185B)))

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4527A0), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7F6), onPrimaryContainer = Color(0xFF1A0E4F),
    secondary = Orange, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF3E2000),
    background = Color(0xFFF6F4FB), onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFE9E5F4), onSurfaceVariant = Color(0xFF2B2B2B),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFF0ECF8),
    outline = Color(0xFF5E5873), outlineVariant = Color(0xFFD5CFE6), error = Color(0xFFB71C1C),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB39DDB), onPrimary = Color.Black,
    primaryContainer = Color(0xFF311B92), onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFAB40), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5D3A00), onSecondaryContainer = Color.White,
    background = Color(0xFF0F0D17), onBackground = Color.White,
    surface = Color(0xFF171422), onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2638), onSurfaceVariant = Color(0xFFE0E0E0),
    surfaceContainer = Color(0xFF1B1826), surfaceContainerLow = Color(0xFF1B1826), surfaceContainerHigh = Color(0xFF262236),
    outline = Color(0xFFB0A8C8), outlineVariant = Color(0xFF3F3A52), error = Color(0xFFFF8A80),
)

/** Une couleur et un pictogramme par sport, pour repérer chaque ligne d'un coup d'œil. */
private class SportStyle(val emoji: String, val color: Color)

private val SPORT_STYLES = mapOf(
    "football" to SportStyle("⚽", Color(0xFF2E7D32)), "rugby" to SportStyle("🏉", Color(0xFFBF360C)),
    "rugby a xiii" to SportStyle("🏉", Color(0xFF8D6E63)), "basket ball" to SportStyle("🏀", Color(0xFFEF6C00)),
    "tennis" to SportStyle("🎾", Color(0xFF9E9D24)), "handball" to SportStyle("🤾", Color(0xFF1565C0)),
    "volley ball" to SportStyle("🏐", Color(0xFF6A1B9A)), "formule 1" to SportStyle("🏎️", Color(0xFFD50000)),
    "moto" to SportStyle("🏍️", Color(0xFF455A64)), "rallye" to SportStyle("🚗", Color(0xFF5D4037)),
    "sport auto" to SportStyle("🏁", Color(0xFF37474F)), "cyclisme" to SportStyle("🚴", Color(0xFFF9A825)),
    "golf" to SportStyle("⛳", Color(0xFF00796B)), "hockey" to SportStyle("🏒", Color(0xFF00838F)),
    "football americain" to SportStyle("🏈", Color(0xFF4E342E)), "baseball" to SportStyle("⚾", Color(0xFF283593)),
    "boxe" to SportStyle("🥊", Color(0xFFC62828)), "mma" to SportStyle("🥋", Color(0xFFAD1457)),
    "kickboxing" to SportStyle("🥊", Color(0xFFAD1457)), "judo" to SportStyle("🥋", Color(0xFF283593)),
    "ski" to SportStyle("⛷️", Color(0xFF0277BD)), "biathlon" to SportStyle("🎿", Color(0xFF0288D1)),
    "patinage" to SportStyle("⛸️", Color(0xFF00ACC1)), "athletisme" to SportStyle("🏃", Color(0xFFE65100)),
    "natation" to SportStyle("🏊", Color(0xFF0277BD)), "snooker" to SportStyle("🎱", Color(0xFF1B5E20)),
    "equitation" to SportStyle("🏇", Color(0xFF6D4C41)), "sports nautiques" to SportStyle("⛵", Color(0xFF01579B)),
    "padel" to SportStyle("🎾", Color(0xFF00897B)), "flechettes" to SportStyle("🎯", Color(0xFFC62828)),
    "petanque" to SportStyle("🎯", Color(0xFF546E7A)), "tir sportif" to SportStyle("🎯", Color(0xFF37474F)),
    "tir a l arc" to SportStyle("🏹", Color(0xFF558B2F)), "e sport" to SportStyle("🎮", Color(0xFF7B1FA2)),
)
private val DEFAULT_STYLE = SportStyle("🏅", Color(0xFF5C6BC0))
private fun styleOf(sport: String) = SPORT_STYLES[norm(sport)] ?: DEFAULT_STYLE

private val LOGO_BACKGROUND = Brush.verticalGradient(listOf(Color(0xFF78909C), Color(0xFF546E7A)))
private val CHANNEL_COLORS = listOf(0xFF1565C0, 0xFFC62828, 0xFF2E7D32, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F, 0xFFAD1457, 0xFF4E342E).map { Color(it) }

/**
 * Miniature du sport : le pictogramme lui-même, en grand et sans cadre (ballon de football, balle de tennis,
 * ballon de basket…), avec une ombre portée. Il rebondit quand il devient actif et s'estompe sinon.
 */
@Composable
private fun SportBadge(sport: String, size: Dp, active: Boolean = true) {
    val st = styleOf(sport)
    val scale by animateFloatAsState(
        if (active) 1f else 0.84f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "badge",
    )
    Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (active) 1f else 0.4f }, contentAlignment = Alignment.Center) {
        Text(
            st.emoji, fontSize = (size.value * 0.78f).sp, maxLines = 1,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 3f), 6f)),
        )
    }
}

/** Étiquettes de l'épreuve : qui court, quel format, quelle distance, où. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaceTags(e: Event, sportColor: Color) {
    val r = e.race
    if (r.isEmpty) return
    FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (r.gender) {
            Races.MEN -> Tag("♂ Hommes", Color(0xFF1565C0))
            Races.WOMEN -> Tag("♀ Femmes", Color(0xFFC2185B))
            Races.MIXED -> Tag("⚥ Mixte", Color(0xFF6A1B9A))
        }
        if (r.format.isNotEmpty()) Tag(r.format, sportColor)
        if (r.distance.isNotEmpty()) Tag(r.distance, Color(0xFF455A64))
        if (r.place.isNotEmpty()) Tag("📍 ${r.place}", Color(0xFF00695C))
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text, Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 7.dp, vertical = 2.dp),
        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

/** Badge « EN COURS » avec un point qui clignote. */
@Composable
private fun LiveBadge(clock: String = "") {
    val alpha by rememberInfiniteTransition(label = "live").animateFloat(
        1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink",
    )
    Row(
        Modifier.padding(top = 2.dp).clip(RoundedCornerShape(4.dp)).background(LiveRed).padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text(if (clock.isEmpty()) "EN COURS" else "EN COURS · $clock", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private enum class Screen(val label: String, val icon: ImageVector) {
    PROGRAMME("À venir", Icons.Filled.Home),
    FAVORITES("Favoris", Icons.Filled.Star),
    CHANNELS("Chaînes", Icons.Filled.PlayArrow),
    SETTINGS("Réglages", Icons.Filled.Settings),
}

@Composable
fun SportAntenneApp(state: AppState) {
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
        var sportView by rememberSaveable { mutableStateOf<String?>(null) }
        val settings = state.settings
        val visible = remember(state.events, settings, now) { settings.visible(state.events, now) }

        Scaffold(
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(HeaderBrush).statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Screen.entries[screen].label, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.padding(13.dp).size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = state::refresh) { Icon(Icons.Filled.Refresh, "Mettre à jour le programme", tint = Color.White) }
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEachIndexed { i, s ->
                        val bounce by animateFloatAsState(if (screen == i) 1.18f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "nav")
                        NavigationBarItem(
                            screen == i, { screen = i }, { Icon(s.icon, null, Modifier.graphicsLayer { scaleX = bounce; scaleY = bounce }) }, label = { Text(s.label, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Orange, selectedIconColor = Color.White, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
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
                if (state.events.isNotEmpty() && state.calendars.isNotEmpty() && (state.calendars["football"] ?: 0) == 0 && !state.loading) {
                    Text(
                        "Calendrier du football non chargé : les matchs de football ne peuvent pas être confirmés. Touchez ↻, puis voyez Réglages > Envoyer le diagnostic.",
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onError, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (state.events.isNotEmpty() && state.calendars.isEmpty() && !state.loading) {
                    Text(
                        "Calendriers sportifs indisponibles : aucun direct ne peut être confirmé pour l'instant. Touchez ↻ pour réessayer.",
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onError, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
                when (Screen.entries[screen]) {
                    Screen.PROGRAMME -> ProgrammeScreen(visible, state, now, sportView, { sportView = it }) { selected = it }
                    Screen.FAVORITES -> FavoritesScreen(state)
                    Screen.CHANNELS -> ChannelsScreen(state)
                    Screen.SETTINGS -> SettingsScreen(state)
                }
            }
        }
        selected?.let { EventSheet(it, state, now) { selected = null } }
    }
}

// ───────────────────────────── À venir : un jour par page ─────────────────────────────

@Composable
private fun ProgrammeScreen(
    events: List<Event>, state: AppState, now: Long,
    sportView: String?, onSportView: (String?) -> Unit, onOpen: (Event) -> Unit,
) {
    val settings = state.settings
    // Onglets de sport : uniquement les sports cochés en favoris ; sans favori, tous ceux du programme.
    val sports = remember(events, settings.favSports) {
        if (settings.favSports.isNotEmpty()) {
            (Competitions.sports + settings.favSports).distinctBy { norm(it) }.filter { settings.isFavSport(it) }
        } else events.groupingBy { it.sport }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    val current = sportView?.takeIf { v -> sports.any { norm(it) == norm(v) } }
    val scoped = if (current == null) events else events.filter { norm(it.sport) == norm(current) }

    val today = startOfDay(Calendar.getInstance().apply { timeInMillis = now })
    val days = remember(today) {
        (0..6).map { Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, it) }.timeInMillis }
    }
    val pager = rememberPagerState { days.size }
    val scope = rememberCoroutineScope()
    val dayStrip = rememberLazyListState()
    LaunchedEffect(pager.currentPage) { dayStrip.animateScrollToItem(maxOf(0, pager.currentPage - 1)) }

    Column {
        // Les jours côte à côte : le jour affiché est en surbrillance.
        LazyRow(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), state = dayStrip,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(days) { i, d ->
                val on = pager.currentPage == i
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (on) Orange else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }
                        .padding(horizontal = if (on) 18.dp else 12.dp, vertical = if (on) 10.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val color = if (on) Color.White else MaterialTheme.colorScheme.onSurface
                    Text(
                        when (i) { 0 -> "Aujourd'hui"; 1 -> "Demain"; else -> SimpleDateFormat("EEEE", Locale.FRENCH).format(Date(d)).replaceFirstChar { it.uppercase() } },
                        color = color, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = if (on) 17.sp else 15.sp,
                    )
                    Text(SimpleDateFormat("d MMM", Locale.FRENCH).format(Date(d)), color = color, fontSize = 13.sp)
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SportChip("Tous", "🏟️", MaterialTheme.colorScheme.primary, current == null) { onSportView(null) } }
            items(sports) { s ->
                val st = styleOf(s)
                SportChip(s, st.emoji, st.color, current != null && norm(current) == norm(s)) { onSportView(if (current != null && norm(current) == norm(s)) null else s) }
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            val list = remember(scoped, page, days) {
                scoped.filter { e -> if (page == 0) e.start < days[1] else e.start >= days[page] && e.start < (days.getOrNull(page + 1) ?: (days[page] + 90_000_000L)) }
            }
            // Ce que les filtres masquent ce jour-là, pour ne jamais laisser une page vide sans explication.
            val hidden = remember(state.events, settings, page, days, current, now) {
                val sameDay = state.events.filter { e ->
                    (current == null || norm(e.sport) == norm(current)) &&
                        (if (page == 0) e.start < days[1] && e.stop > days[0] else e.start >= days[page] && e.start < (days.getOrNull(page + 1) ?: (days[page] + 90_000_000L)))
                }
                settings.hiddenCounts(sameDay, now)
            }
            if (list.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Text(
                        when {
                            state.loading -> "Chargement du programme…"
                            hidden.isEmpty() -> "Aucune diffusion sportive ce jour-là" + (if (current != null) " en $current." else ".")
                            else -> "Rien à afficher ce jour-là" + (if (current != null) " en $current" else "") + " avec vos réglages."
                        },
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                    HiddenSummary(hidden)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Les événements sont regroupés sous leur heure de début.
                    val rows = ArrayList<Any>()
                    var last = ""
                    for (e in list) {
                        val h = hour(e.start).replace(':', 'h')
                        if (h != last) { rows += h; last = h }
                        rows += e
                    }
                    items(rows, key = { if (it is Event) it.id else "h$it" }) { row ->
                        if (row is Event) EventRow(row, state, now, onOpen) else TimeHeader(row as String)
                    }
                    if (hidden.isNotEmpty()) item(key = "masquees") { Column(Modifier.padding(8.dp)) { HiddenSummary(hidden) } }
                }
            }
        }
    }
}

/** Détail des diffusions masquées par les filtres, raison par raison. */
@Composable
private fun HiddenSummary(hidden: Map<String, Int>) {
    if (hidden.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text("${hidden.values.sum()} diffusion(s) masquée(s) ce jour-là :", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    hidden.forEach { (reason, n) -> Text("• $n $reason", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SportChip(label: String, emoji: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "chip")
    Row(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(50))
            .background(if (selected) color else color.copy(alpha = 0.13f))
            .border(1.5.dp, color, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Bandeau d'heure au-dessus des événements qui commencent à ce moment-là. */
@Composable
private fun TimeHeader(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text, Modifier.shadow(3.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(HeaderBrush).padding(horizontal = 34.dp, vertical = 5.dp),
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
        )
    }
}

@Composable
private fun EventRow(e: Event, state: AppState, now: Long, onOpen: (Event) -> Unit) {
    val st = styleOf(e.sport)
    val finished = e.score?.finished == true || e.stop <= now
    val live = !finished && e.start <= now
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).shadow(2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).clickable { onOpen(e) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(st.color))
        Spacer(Modifier.width(8.dp))
        SportBadge(e.sport, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(e.homeLogo)
                Text(e.headline, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TeamLogo(e.awayLogo, start = true)
            }
            Text(subtitle(e), color = st.color.forText(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RaceTags(e, st.color)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (live) LiveBadge(e.score?.clock ?: "") else if (finished) Tag("TERMINÉ", Color(0xFF616161))
                // Trois états, toujours affichés : confirmé par un calendrier, non confirmé, ou rediffusion.
                Text(
                    when {
                        e.verified && e.precise -> "✓ Direct confirmé"
                        e.verified -> "✓ Tournoi en cours confirmé"
                        e.replay -> "Rediffusion"
                        else -> "Horaire non confirmé"
                    },
                    color = if (e.verified) confirmedGreen() else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, fontWeight = if (e.verified) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                )
                if (state.settings.hasAlert(e) && e.start > now) Icon(Icons.Filled.Notifications, "Alerte activée", Modifier.size(16.dp), tint = Orange)
            }
            if (e.kickoff > 0 && e.score == null) {
                Text("Coup d'envoi ${hour(e.kickoff).replace(':', 'h')}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (e.channels.size > 1) {
                Text("Diffusé sur ${e.channels.size} chaînes", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        e.score?.let { ScoreBox(it, live) }
        // Toutes les chaînes qui diffusent l'événement : logo et nom, l'une sous l'autre.
        Column(Modifier.padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            e.channels.forEach { ChannelBadge(state.channels[it], it, compact = e.channels.size > 1) }
        }
    }
}

/** Score du match : rouge pendant le jeu, neutre une fois terminé. */
@Composable
private fun ScoreBox(score: fr.perso.sportantenne.core.Score, live: Boolean) {
    Text(
        score.text,
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (live) LiveRed else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (live) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1,
    )
}

/** Écusson d'une équipe ; rien n'est affiché si le calendrier n'en fournit pas ou s'il ne se charge pas. */
@Composable
private fun TeamLogo(url: String, start: Boolean = false) {
    if (url.isEmpty()) return
    // Aucune place n'est réservée tant que l'image n'est pas arrivée : pas de trou si elle ne se charge pas.
    var loaded by remember(url) { mutableStateOf(false) }
    AsyncImage(
        url, null,
        if (loaded) Modifier.padding(start = if (start) 6.dp else 0.dp, end = if (start) 0.dp else 6.dp).size(24.dp) else Modifier.size(0.dp),
        onSuccess = { loaded = true },
    )
}

/** Logo de la chaîne, son nom en petit dessous. */
@Composable
private fun ChannelBadge(channel: Channel?, id: String, compact: Boolean = false) {
    Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelLogo(channel, id, if (compact) 62 else 74, if (compact) 34 else 44)
        Text(
            channel?.name ?: id, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Vrai logo de la chaîne : les adresses connues sont essayées l'une après l'autre.
 * Si aucune ne répond, le nom de la chaîne est écrit dans le cadre.
 */
@Composable
private fun ChannelLogo(channel: Channel?, id: String, width: Int, height: Int = width) {
    val name = channel?.name ?: id
    val shape = RoundedCornerShape(8.dp)
    val urls = remember(id, channel?.logo) { fr.perso.sportantenne.core.ChannelLogos.candidates(channel, id) }
    var attempt by remember(urls) { mutableIntStateOf(0) }
    Box(
        // Fond gris-bleu : il laisse lisibles aussi bien les logos blancs (Eurosport, Canal+) que les logos sombres.
        Modifier.size(width.dp, height.dp).clip(shape).background(LOGO_BACKGROUND).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (attempt < urls.size) {
            AsyncImage(urls[attempt], name, Modifier.fillMaxSize().padding(3.dp), onError = { attempt++ })
        } else {
            val color = CHANNEL_COLORS[(name.hashCode() and 0x7fffffff) % CHANNEL_COLORS.size]
            Box(Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
                Text(name, Modifier.padding(2.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp)
            }
        }
    }
}

private fun initials(name: String): String {
    val words = name.split(' ', '+', '-').filter { it.isNotBlank() }
    return (if (words.size >= 2) "${words[0].first()}${words[1].first()}" else name.take(2)).uppercase()
}

// ───────────────────────────── Favoris : sports puis compétitions par pays ─────────────────────────────

@Composable
private fun FavoritesScreen(state: AppState) {
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    val settings = state.settings
    if (sport != null) {
        BackHandler { sport = null }
        CompetitionPicker(sport!!, state) { sport = null }
        return
    }
    val sports = remember(state.events, state.catalogue) {
        (Competitions.sports + state.catalogue.competitions.values.map { it.first } + state.events.filter { it.kind == Kind.EVENT }.map { it.sport })
            .filter { it.isNotBlank() }.distinctBy { norm(it) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                if (settings.favSports.isEmpty()) "Cochez vos sports : seuls ceux-là apparaîtront dans « À venir ». Touchez un sport pour choisir ses compétitions, pays par pays."
                else "${settings.favSports.size} sport(s) coché(s). Touchez un sport pour choisir ses compétitions ; sans compétition cochée, tout le sport est affiché.",
                Modifier.padding(16.dp), fontSize = 14.sp,
            )
        }
        items(sports, key = { norm(it) }) { s ->
            val st = styleOf(s)
            val checked = settings.isFavSport(s)
            val count = settings.competitionCount(s)
            Row(
                Modifier.fillMaxWidth().clickable { sport = s }.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, { state.update { it.toggleSport(s) } })
                SportBadge(s, 44.dp, active = checked)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(s, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        when {
                            !checked -> "Non suivi"
                            count == 0 -> "Toutes les compétitions"
                            count == 1 -> "1 compétition cochée"
                            else -> "$count compétitions cochées"
                        },
                        fontSize = 13.sp, color = if (checked) st.color.forText() else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(Icons.Filled.KeyboardArrowRight, "Choisir les compétitions")
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))
        }
    }
}

private class CompetitionChoice(val id: String, val name: String, val country: String)

@Composable
private fun CompetitionPicker(sport: String, state: AppState, onBack: () -> Unit) {
    val settings = state.settings
    val st = styleOf(sport)
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(sport, state.catalogue) {
        val catalogued = Competitions.choices(sport).map { CompetitionChoice("c:" + it.id, it.name, it.country) }
        // Compétitions vues dans le programme mais absentes du catalogue.
        val seen = state.catalogue.competitions.values.filter { norm(it.first) == norm(sport) }
            .map { Competitions.idOf(it.first, it.second) to it.second }
            .filter { !Competitions.isCatalogued(it.first) }.distinctBy { it.first }.sortedBy { norm(it.second) }
            .map { CompetitionChoice(it.first, it.second, Competitions.OTHER) }
        catalogued + seen
    }
    val q = norm(query)
    val rows = remember(all, q) {
        val out = ArrayList<Any>()
        // Un seul bloc par pays, dans l'ordre du catalogue ; les compétitions hors catalogue en dernier.
        val groups = all.filter { q.isEmpty() || norm(it.name).contains(q) || norm(it.country).contains(q) }.groupBy { it.country }
        // France d'abord, puis les coupes d'Europe et les compétitions internationales, puis les autres pays.
        fun rank(country: String) = when (country) {
            Competitions.FR -> 0; Competitions.EU -> 1; Competitions.WORLD -> 2; Competitions.OTHER -> 9; else -> 5
        }
        for ((country, list) in groups.entries.sortedBy { rank(it.key) }) {
            out.add(country)
            out.addAll(list.distinctBy { it.id })
        }
        out
    }
    Column {
        Row(
            Modifier.fillMaxWidth().background(st.color).clickable(onClick = onBack).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ArrowBack, "Retour", Modifier.padding(10.dp), tint = Color.White)
            Text("${st.emoji}  $sport", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
        Text(
            when (val n = settings.competitionCount(sport)) {
                0 -> "Aucune compétition cochée : tout le $sport est affiché. Cochez celles que vous voulez suivre pour masquer les autres. Les coupes d'Europe sont dans la rubrique Europe, les sélections dans International."
                else -> "$n compétition(s) cochée(s) : seules celles-ci sont affichées."
            },
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp,
        )
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text("Rechercher une compétition ou un pays") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Clear, "Effacer") } },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(rows) { row ->
                when (row) {
                    is String -> Text(
                        row, Modifier.fillMaxWidth().padding(top = 12.dp).background(st.color.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                    )
                    is CompetitionChoice -> CheckRow(row.name, "", settings.isFavCompetition(sport, row.id), null) {
                        state.update { it.toggleCompetition(sport, row.id) }
                    }
                }
            }
            if (rows.isEmpty()) item { Text("Aucun résultat.", Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun CheckRow(label: String, note: String, checked: Boolean, logo: (@Composable () -> Unit)?, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onToggle() })
        if (logo != null) {
            logo()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, fontSize = 16.sp, fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note.isNotEmpty()) Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val shown = (if (q.isEmpty()) all else all.filter { norm(it.name).contains(q) }).sortedBy { it.id !in settings.myChannels }
    Column {
        Text(
            if (settings.myChannels.isEmpty()) "Aucune chaîne cochée : tout le programme est affiché. Cochez les chaînes que vous recevez pour masquer les autres."
            else "${settings.myChannels.size} chaîne(s) cochée(s) : seul leur programme est affiché.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp,
        )
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("Rechercher une chaîne") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { c ->
                CheckRow(c.name, "", c.id in settings.myChannels, { ChannelLogo(c, c.id, 66, 42) }) { state.update { it.toggleChannel(c.id) } }
            }
        }
    }
}

// ───────────────────────────── Réglages ─────────────────────────────

private val CALENDAR_LABELS = listOf(
    "football" to "Football (matchs)", "rugby" to "Rugby (matchs)", "nba" to "NBA", "nfl" to "NFL", "nhl" to "NHL", "ncaab" to "Basket universitaire",
    "ncaaf" to "Football universitaire", "f1" to "Formule 1 (séances)", "tennis" to "Tennis (tournois)", "golf" to "Golf (tournois)", "ufc" to "UFC (soirées)",
)
private val KEEP_CHOICES = listOf(0 to "Dès la fin", 60 to "1 h", 240 to "4 h", 720 to "12 h", fr.perso.sportantenne.Settings.KEEP_ALL to "Tous")
private val DELAYS = listOf(5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h")

@Composable
private fun SettingsScreen(state: AppState) {
    val settings = state.settings
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("🔔  Alertes")
        SwitchRow("Me prévenir avant mes favoris", "Notification avant chaque direct d'une compétition cochée, avec la chaîne.", settings.alertsEnabled) { v ->
            state.update { it.copy(alertsEnabled = v) }
        }
        Text("Délai avant le début", Modifier.padding(start = 16.dp, top = 4.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DELAYS.forEach { (minutes, label) ->
                FilterChip(settings.alertMinutes == minutes, { state.update { it.copy(alertMinutes = minutes) } }, { Text(label) })
            }
        }
        SectionTitle("🏁  Matchs terminés")
        Text(
            "Combien de temps un match reste affiché après sa fin, avec son résultat quand il est connu.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KEEP_CHOICES.forEach { (minutes, label) ->
                FilterChip(settings.keepFinishedMin == minutes, { state.update { it.copy(keepFinishedMin = minutes) } }, { Text(label) })
            }
        }
        SectionTitle("📺  Programme")
        SwitchRow("Masquer les rediffusions", "Recommandé. Chaque match est comparé aux calendriers réels : s'il ne se joue pas à cette heure-là, c'est une rediffusion.", settings.hideReplays) { v ->
            state.update { it.copy(hideReplays = v) }
        }
        SwitchRow("Directs confirmés uniquement", "N'afficher que les matchs dont l'horaire est confirmé par un calendrier sportif (football, NBA, NFL, NHL, rugby, Formule 1). Les autres sports disparaissent.", settings.verifiedOnly) { v ->
            state.update { it.copy(verifiedOnly = v) }
        }
        SwitchRow("Afficher les magazines", "Émissions, documentaires et journaux sportifs.", settings.showMagazines) { v ->
            state.update { it.copy(showMagazines = v) }
        }
        SectionTitle("🔄  Mise à jour")
        Text(
            (if (state.lastUpdate == 0L) "Programme jamais chargé."
            else "Dernière vérification : " + SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRENCH).format(Date(state.lastUpdate)) + ".") +
                "\nLe programme se revérifie tout seul toutes les heures : reports, annulations et changements d'horaire ou de chaîne.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
        )
        // Diagnostic : ce que chaque calendrier a réellement fourni au dernier chargement.
        Text("Calendriers de vérification", Modifier.padding(start = 16.dp, top = 12.dp), fontWeight = FontWeight.Bold)
        CALENDAR_LABELS.forEach { (key, label) ->
            val n = state.calendars[key] ?: 0
            val failure = fr.perso.sportantenne.Repo.failures[key]
            Text(
                if (failure != null) "✗ $label : échec ($failure)" else if (n > 0) "✓ $label : $n" else "✗ $label : non chargé",
                Modifier.padding(horizontal = 16.dp), fontSize = 14.sp,
                color = if (n > 0 && failure == null) confirmedGreen() else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "Sans calendrier (rugby international, handball, volley, cyclisme, biathlon, ski…), une diffusion porte la mention « Horaire non confirmé ».",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Rapport complet à envoyer en cas de problème.
        val ctx = LocalContext.current
        Button(
            onClick = {
                val report = fr.perso.sportantenne.Repo.diagnostic(ctx, state.events, settings)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, report)
                runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Envoyer le diagnostic")) }
                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostic Sport Antenne", report))
            },
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        ) { Text("Envoyer le diagnostic") }
        Text(
            "Version ${fr.perso.sportantenne.BuildConfig.VERSION_NAME}. Le diagnostic est aussi copié dans le presse-papiers : il suffit de le coller.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

// ───────────────────────────── Fiche d'un événement ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventSheet(e: Event, state: AppState, now: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val settings = state.settings
    val st = styleOf(e.sport)
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("${st.emoji}  ${subtitle(e)}", color = st.color.forText(), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(e.homeLogo)
                    Text(e.headline, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    TeamLogo(e.awayLogo, start = true)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (e.stop <= now) "Terminé · " else if (e.start <= now) "En cours · " else "") + dayLabel(e.start, now) + " de " + hour(e.start) + " à " + hour(e.stop) +
                        if (e.replay) " · Rediffusion" else if (e.verified && e.precise) " · Direct confirmé" else if (e.verified) " · Tournoi en cours confirmé" else " · Horaire non confirmé",
                    fontWeight = FontWeight.SemiBold, color = if (e.start <= now) LiveRed else MaterialTheme.colorScheme.onSurface,
                )
                RaceTags(e, st.color)
                e.score?.let { sc ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScoreBox(sc, !sc.finished)
                        Text(if (sc.finished) "Score final" else "En cours" + (if (sc.clock.isNotEmpty()) " · ${sc.clock}" else ""), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (e.channels.size > 1) Text("Diffusé sur ${e.channels.size} chaînes :", fontWeight = FontWeight.Bold)
                e.channels.forEach { id ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(state.channels[id], id, 78, 48)
                        Spacer(Modifier.width(12.dp))
                        Text("Sur ${state.channels[id]?.name ?: id}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("En bref", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                summary(e, state.channels, now).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (e.desc.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Présentation de la chaîne", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(e.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { addToCalendar(ctx, e, state.channels) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter à mon calendrier", fontWeight = FontWeight.Bold)
                }
            }
            if (e.start > now) {
                SwitchRow("M'alerter avant le début", "Notification ${DELAYS.firstOrNull { it.first == settings.alertMinutes }?.second ?: "${settings.alertMinutes} min"} avant, avec la chaîne.", settings.hasAlert(e)) {
                    state.update { it.toggleAlert(e) }
                }
            }
            if (e.kind == Kind.EVENT && e.competition.isNotBlank()) {
                SectionTitle("⭐  Mes favoris")
                val id = Competitions.idOf(e.sport, e.competition)
                val known = Competitions.byId(id)
                CheckRow(known?.name ?: e.competition, "Compétition" + (known?.let { " · ${it.country}" } ?: ""), settings.isFavCompetition(e.sport, id), null) {
                    state.update { it.toggleCompetition(e.sport, id) }
                }
            }
        }
    }
}

// ───────────────────────────── Utilitaires ─────────────────────────────

/** Couleur de sport assombrie ou éclaircie pour rester lisible en texte. */
@Composable
private fun Color.forText(): Color = if (isSystemInDarkTheme()) lerp(this, Color.White, 0.55f) else lerp(this, Color.Black, 0.25f)

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

@Composable
private fun confirmedGreen(): Color = if (isSystemInDarkTheme()) Color(0xFF69F0AE) else Color(0xFF1B7F3B)

private fun subtitle(e: Event): String = buildList {
    val known = Competitions.byId(Competitions.idOf(e.sport, e.competition))
    // Le drapeau du pays précède la compétition quand elle est au catalogue.
    val flag = known?.country?.substringBefore(' ')?.takeIf { it.isNotEmpty() && !it.first().isLetter() }
    if (e.competition.isNotBlank() && norm(e.competition) != norm(e.sport) && e.competition != e.headline) {
        add((if (flag != null) "$flag " else "") + e.competition)
    } else add(e.sport)
    if (e.detail.isNotBlank()) add(e.detail)
}.joinToString(" · ")

/** Résumé en quelques lignes : quoi, qui, quand, sur quelle chaîne, et ce qui est vérifié. */
internal fun summary(e: Event, channels: Map<String, Channel>, now: Long): List<String> {
    val lines = ArrayList<String>()
    fun h(t: Long) = hour(t).replace(':', 'h')
    val known = Competitions.byId(Competitions.idOf(e.sport, e.competition))
    val place = known?.country?.substringAfter(' ')?.takeIf { known.country != Competitions.OTHER }
    val competition = e.competition.takeIf { it.isNotBlank() && norm(it) != norm(e.sport) }
    lines += buildString {
        append(e.sport)
        if (competition != null) append(" — ").append(competition)
        if (place != null) append(" ($place)")
        if (e.detail.isNotBlank() && e.race.isEmpty) append(", ").append(e.detail)
        append('.')
    }
    if (e.hasTeams) lines += "${e.home} affronte ${e.away}."
    if (!e.race.isEmpty) {
        val r = e.race
        lines += listOf(r.format, r.distance, r.gender.lowercase().takeIf { it.isNotEmpty() }?.let { "($it)" } ?: "", r.place.takeIf { it.isNotEmpty() }?.let { "à $it" } ?: "")
            .filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercase() } + "."
    }
    lines += buildString {
        append(dayLabel(e.start, now)).append(" : prise d'antenne à ").append(h(e.start))
        if (e.kickoff > 0) append(", coup d'envoi à ").append(h(e.kickoff))
        append(", fin prévue vers ").append(h(e.stop)).append('.')
    }
    e.score?.let { lines += (if (it.finished) "Score final : " else "Score en cours : ") + "${e.home} ${it.home} – ${it.away} ${e.away}" + (if (!it.finished && it.clock.isNotEmpty()) " (${it.clock})." else ".") }
    lines += when {
        e.replay -> "Rediffusion : ce n'est pas un direct."
        e.verified && e.precise -> "Direct confirmé : l'horaire correspond au calendrier officiel de la compétition."
        e.verified -> "Tournoi en cours confirmé : il se joue bien ce jour-là, l'heure exacte des matchs n'est pas vérifiée."
        else -> "Horaire non confirmé : aucun calendrier indépendant ne couvre cette épreuve, seule la chaîne l'annonce."
    }
    val names = e.channels.map { channels[it]?.name ?: it }
    lines += if (names.size == 1) "Diffusé sur ${names[0]}." else "Diffusé sur ${names.size} chaînes : ${names.joinToString(", ")}."
    return lines
}

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
