package com.streamlivex.android.tv.sports

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackPreferences
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.content.TvLogoImage
import com.streamlivex.android.tv.data.*
import com.streamlivex.android.tv.i18n.TvLocale
import com.streamlivex.android.tv.player.TvNativePlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvSportsScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    menuFocusRequester: FocusRequester,
    onContentFocused: () -> Unit,
    initialEventId: String? = null,
    onInitialEventConsumed: () -> Unit = {},
    onDetailStateChanged: (Boolean) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { SportsRepository(context) }
    val store = remember { TvContentStore(context) }
    val firstFilter = remember { FocusRequester() }
    var events by remember { mutableStateOf<List<SportsEvent>>(emptyList()) }
    var channels by remember { mutableStateOf<List<NativeLiveChannel>>(TvLiveLibraryCache.library?.channels.orEmpty()) }
    var selectedCompetition by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<SportsEvent?>(null) }
    var lastFocusedEventId by remember { mutableStateOf<String?>(null) }
    var pendingFocusRestore by remember { mutableStateOf<String?>(null) }
    val eventFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    var selectedChannelGroup by remember { mutableStateOf<SportsChannelGroup?>(null) }
    var playing by remember { mutableStateOf<NativeLiveChannel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var initialFilterFocusApplied by remember { mutableStateOf(false) }
    val channelIndex = remember(channels) { SportsChannelIndex.forChannels(channels) }

    LaunchedEffect(channelIndex, events) {
        if (channels.isNotEmpty()) {
            val exactCount = events.count { SportsBroadcastResolver.options(it).firstOrNull()?.evidence == BroadcastEvidence.EXACT }
            val fallbackCount = events.count { SportsBroadcastResolver.options(it).firstOrNull()?.evidence == BroadcastEvidence.TURKEY_NETWORK_FALLBACK }
            Log.i("SportsBroadcast", "events=${events.size} exact=$exactCount fallback=$fallbackCount canonical=${channelIndex.canonicalCount} sources=${channelIndex.totalSourceCount}")
            SportsCanonicalChannels.names.forEach { Log.i("SportsBroadcast", "channel=$it sources=${channelIndex.sourceCount(it)}") }
        }
    }

    LaunchedEffect(selectedEvent?.id) { onDetailStateChanged(selectedEvent != null) }
    DisposableEffect(Unit) { onDispose { onDetailStateChanged(false) } }
    LaunchedEffect(selectedEvent, pendingFocusRestore) {
        if (selectedEvent == null) pendingFocusRestore?.let { id ->
            onContentFocused()
            val visible = events.filter { selectedCompetition == null || it.league == selectedCompetition }.sortedBy { it.startMs }
            visible.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { gridState.scrollToItem(it) }
        }
    }

    LaunchedEffect(provider.server, provider.username) {
        Thread {
            val library = TvLiveLibraryCache.library ?: XtreamClient().loadLiveLibrary(provider).getOrNull()
            SportsChannelIndex.forChannels(library?.channels.orEmpty())
            val loadedEvents = repository.today()
            Handler(Looper.getMainLooper()).post {
                channels = library?.channels.orEmpty()
                events = loadedEvents
                Log.i("SportsPipeline", "sports screen fixture count=${events.size}")
                Log.i("SportsPipeline", "sports screen rendered fixtures=${events.joinToString(" | ") { "${it.home}-${it.away}" }}")
                initialEventId?.let { requested ->
                    loadedEvents.firstOrNull { it.id == requested }?.let { selectedEvent = it }
                    onInitialEventConsumed()
                }
                loading = false
            }
            val enriched = repository.enrichTeamBadges(loadedEvents, maxNetworkLookups = 40)
            Handler(Looper.getMainLooper()).post { events = enriched }
        }.start()
    }

    playing?.let { channel ->
        val saved = TvSavedItem("sports-live-${channel.id}", "live", channel.name, channel.streamUrl, channel.logo)
        TvNativePlayer(
            saved = saved,
            request = PlaybackRequest(
                sessionId = "tv-sports-${channel.id}",
                item = PlaybackItem(channel.name, channel.streamUrl, "live"),
                preferences = PlaybackPreferences(showInfo = false),
            ),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            store = store,
            locale = locale,
            onFullscreenStateChanged = onFullscreenStateChanged,
            onClose = { playing = null },
        )
        return
    }

    selectedEvent?.let { event ->
        SportsEventDetail(
            event = event,
            channelIndex = channelIndex,
            repository = repository,
            onEnrich = { selectedEvent = it },
            selectedChannelGroup = selectedChannelGroup,
            onChannelGroupSelected = { selectedChannelGroup = it },
            onBack = {
                pendingFocusRestore = lastFocusedEventId ?: event.id
                selectedChannelGroup = null
                selectedEvent = null
            },
            onPlay = {
                TvLiveProfileStore(context).recordChannelView(it.id)
                playing = it
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF070D13)).padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Bugünün Sporları", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text(SimpleDateFormat("dd MMMM", Locale("tr", "TR")).format(Date()), color = Color(0xFF9FB0BC), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
        }
        val competitions = remember(events) { events.map { it.league }.distinct() }
        if (competitions.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp)) {
            item {
                CompetitionChip("Tüm Ligler", selectedCompetition == null, Modifier.focusRequester(firstFilter).focusProperties { left = menuFocusRequester }) { selectedCompetition = null; onContentFocused() }
            }
            items(competitions) { league ->
                CompetitionChip(league, selectedCompetition == league) { selectedCompetition = league; onContentFocused() }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF38C9F2)) }
            events.isEmpty() -> EmptySports("Bugün desteklenen sporlarda etkinlik bulunamadı.")
            else -> {
                // Kotlin's stable sort keeps the provider order for fixtures with the same kickoff.
                val visible = events.filter { selectedCompetition == null || it.league == selectedCompetition }
                    .sortedBy { it.startMs }
                if (visible.isEmpty()) EmptySports("Bugün bu spor için veri bulunamadı.")
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2), state = gridState, modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(visible, key = { it.id }) { event ->
                        val requester = eventFocusRequesters.getOrPut(event.id) { FocusRequester() }
                        SportsEventRow(
                            event,
                            requester,
                            restoreFocus = pendingFocusRestore == event.id,
                            onFocusRestored = { pendingFocusRestore = null },
                            onFocused = { lastFocusedEventId = event.id },
                        ) {
                            lastFocusedEventId = event.id
                            selectedChannelGroup = null
                            selectedEvent = event
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(loading, initialFilterFocusApplied) {
        if (!loading && !initialFilterFocusApplied) {
            initialFilterFocusApplied = true
            kotlinx.coroutines.delay(100)
            runCatching { firstFilter.requestFocus() }
        }
    }
}

@Composable
private fun CompetitionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember(label) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "competition-chip")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (selected) Color(0xFF123B4B) else Color(0xFF121C25), RoundedCornerShape(16.dp))
            .border(if (focused) 2.dp else 1.dp, when { focused -> Color(0xFF67E8F9); selected -> Color(0xFF238EAD); else -> Color(0xFF30404D) }, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 8.dp),
    ) { Text(label, color = when { focused -> Color.White; selected -> Color(0xFF67E8F9); else -> Color(0xFFB5C2CC) }, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun EmptySports(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color(0xFF94A3B8)) }

@Composable
private fun SportsEventRow(
    event: SportsEvent,
    focusRequester: FocusRequester,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val scale by animateFloatAsState(if (focused) 1.012f else 1f, label = "sports-card")
    LaunchedEffect(restoreFocus) {
        if (restoreFocus) {
            kotlinx.coroutines.delay(160)
            runCatching { focusRequester.requestFocus() }
            kotlinx.coroutines.delay(100)
            runCatching { focusRequester.requestFocus() }
            onFocusRestored()
        }
    }
    Column(
        Modifier.fillMaxWidth().height(126.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (focused) Color(0xFF102C39) else Color(0xFF101820), RoundedCornerShape(11.dp))
            .border(1.dp, if (focused) Color(0xFF38C9F2) else Color(0xFF263540), RoundedCornerShape(10.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${sportsFlag(event.country)} ${event.league}", color = Color(0xFF8DD7F0), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(if (event.status?.contains("live", true) == true) "● CANLI" else formatter.format(Date(event.startMs)), color = if (event.status?.contains("live", true) == true) Color(0xFFF87171) else Color(0xFF9FB0BC), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            TeamMatchSide(event.home, event.homeBadge, isHome = true, Modifier.weight(1f))
            Text(
                scoreText(event),
                color = if (focused) Color(0xFF67E8F9) else Color(0xFFAAB8C2),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.widthIn(min = 30.dp, max = 48.dp).padding(horizontal = 4.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            TeamMatchSide(event.away, event.awayBadge, isHome = false, Modifier.weight(1f))
        }
        Text(event.broadcasts.firstOrNull()?.channelName ?: "Kanal bilgisi yok", color = Color(0xFF738491), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun TeamMatchSide(name: String, badge: String?, isHome: Boolean, modifier: Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isHome) Arrangement.End else Arrangement.Start,
    ) {
        val teamName: @Composable (Modifier) -> Unit = { textModifier -> Text(
            name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isHome) TextAlign.End else TextAlign.Start,
            modifier = textModifier,
        ) }
        if (isHome) {
            teamName(Modifier.weight(1f))
            Spacer(Modifier.width(5.dp))
            TvLogoImage(badge, Modifier.size(28.dp))
        } else {
            TvLogoImage(badge, Modifier.size(28.dp))
            Spacer(Modifier.width(5.dp))
            teamName(Modifier.weight(1f))
        }
    }
}

private fun sportsFlag(country: String?): String = when (country?.lowercase(Locale.ROOT)) {
    "turkey", "türkiye" -> "🇹🇷"; "england" -> "🇬🇧"; "spain" -> "🇪🇸"; "germany" -> "🇩🇪"
    "italy" -> "🇮🇹"; "france" -> "🇫🇷"; "portugal" -> "🇵🇹"; "netherlands" -> "🇳🇱"; "brazil" -> "🇧🇷"; else -> "🌍"
}

@Composable
private fun SportsEventDetail(
    event: SportsEvent,
    channelIndex: SportsChannelIndex,
    repository: SportsRepository,
    onEnrich: (SportsEvent) -> Unit,
    selectedChannelGroup: SportsChannelGroup?,
    onChannelGroupSelected: (SportsChannelGroup?) -> Unit,
    onBack: () -> Unit,
    onPlay: (NativeLiveChannel) -> Unit,
) {
    var loadingBroadcasts by remember(event.id) { mutableStateOf(event.broadcasts.isEmpty()) }
    val options = remember(event.id, event.broadcasts) { SportsBroadcastResolver.options(event) }
    val channelGroups = remember(options, channelIndex) { channelIndex.resolve(options) }
    val evidence = options.firstOrNull()?.evidence
    val firstChannel = remember(event.id) { FocusRequester() }
    BackHandler { if (selectedChannelGroup != null) onChannelGroupSelected(null) else onBack() }
    LaunchedEffect(event.id) {
        if (event.broadcasts.isEmpty()) Thread {
            val enriched = repository.enrichBroadcasts(event)
            Handler(Looper.getMainLooper()).post { loadingBroadcasts = false; onEnrich(enriched) }
        }.start()
    }
    if (selectedChannelGroup != null) {
        SportsSourcePicker(
            group = selectedChannelGroup,
            onBack = { onChannelGroupSelected(null) },
            onPlay = onPlay,
        )
        return
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF070D13)).padding(horizontal = 42.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailBackControl(onBack)
        Text("${event.league}  •  ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.startMs))}", color = Color(0xFF8DD7F0), fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TeamBlock(event.home, event.homeBadge)
            Text(scoreText(event), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 34.dp))
            TeamBlock(event.away, event.awayBadge)
        }
        event.venue?.let { Text(it, modifier = Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF94A3B8)) }
        Text(
            if (evidence == BroadcastEvidence.EXACT) "Yayın Kanalı" else "Türkiye Yayın Seçenekleri",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (evidence == BroadcastEvidence.TURKEY_NETWORK_FALLBACK) {
            Text("Yayın ağına göre alternatifler", color = Color(0xFF7F919E), style = MaterialTheme.typography.labelMedium)
        }
        when {
            loadingBroadcasts -> CircularProgressIndicator(color = Color(0xFF38C9F2), modifier = Modifier.size(28.dp))
            options.isEmpty() -> Text("Bu karşılaşma için doğrulanmış yayın seçeneği bulunamadı.", color = Color(0xFF94A3B8))
            channelGroups.isEmpty() -> Text("Uygun kanal oynatma listenizde bulunamadı.", color = Color(0xFF94A3B8))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(channelGroups, key = { it.canonicalName }) { group ->
                    CanonicalChannelRow(group, if (group == channelGroups.first()) Modifier.focusRequester(firstChannel) else Modifier) { onChannelGroupSelected(group) }
                }
            }
        }
    }
    LaunchedEffect(loadingBroadcasts, channelGroups) {
        if (!loadingBroadcasts && channelGroups.isNotEmpty()) {
            kotlinx.coroutines.delay(80)
            runCatching { firstChannel.requestFocus() }
        }
    }
}

@Composable
private fun DetailBackControl(onBack: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.background(if (focused) Color(0xFF123B4B) else Color(0xFF101820), RoundedCornerShape(8.dp))
            .border(1.dp, if (focused) Color(0xFF38C9F2) else Color(0xFF30404D), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onBack).padding(horizontal = 13.dp, vertical = 8.dp),
    ) { Text("←  Geri", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun TeamBlock(name: String, badge: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(190.dp)) {
        TvLogoImage(badge, Modifier.size(76.dp))
        Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CanonicalChannelRow(group: SportsChannelGroup, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember(group.canonicalName) { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(52.dp).background(if (focused) Color(0xFF123647) else Color(0xFF111A22), RoundedCornerShape(8.dp))
            .border(1.dp, if (focused) Color(0xFF38C9F2) else Color(0xFF263540), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvLogoImage(group.sources.firstOrNull()?.logo ?: group.exactBroadcast?.channelLogo, Modifier.size(36.dp))
        Text(group.canonicalName, Modifier.padding(start = 12.dp).weight(1f), color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${group.sources.size} kaynak", color = Color(0xFF8DD7F0), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SportsSourcePicker(group: SportsChannelGroup, onBack: () -> Unit, onPlay: (NativeLiveChannel) -> Unit) {
    val firstSource = remember(group.canonicalName) { FocusRequester() }
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(Color(0xFF070D13)).padding(horizontal = 42.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DetailBackControl(onBack)
        Text(group.canonicalName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text("${group.sources.size} gerçek oynatma listesi kaynağı", color = Color(0xFF8DD7F0), style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(group.sources, key = { it.id }) { source ->
                SportsSourceRow(
                    source,
                    group.sources.indexOf(source) + 1,
                    if (source == group.sources.first()) Modifier.focusRequester(firstSource) else Modifier,
                ) { onPlay(source) }
            }
        }
    }
    LaunchedEffect(group.canonicalName) {
        kotlinx.coroutines.delay(80)
        runCatching { firstSource.requestFocus() }
    }
}

@Composable
private fun SportsSourceRow(source: NativeLiveChannel, number: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember(source.id) { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(52.dp)
            .background(if (focused) Color(0xFF123647) else Color(0xFF111A22), RoundedCornerShape(8.dp))
            .border(1.dp, if (focused) Color(0xFF38C9F2) else Color(0xFF263540), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Kaynak $number", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(sportsSourceQuality(source.name), color = Color(0xFF8DD7F0), style = MaterialTheme.typography.labelMedium)
    }
}

private fun scoreText(event: SportsEvent): String = if (event.homeScore != null || event.awayScore != null) "${event.homeScore ?: "–"} : ${event.awayScore ?: "–"}" else "VS"
