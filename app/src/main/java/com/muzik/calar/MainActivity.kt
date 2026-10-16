package com.muzik.calar

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { MuzikTheme { PlayerApp() } }
    }
}

@Composable
fun MuzikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB388FF),
            background = Color(0xFF0E0E12),
            surface = Color(0xFF1A1A22),
            onBackground = Color(0xFFF2F2F7),
            onSurface = Color(0xFFF2F2F7)
        ),
        content = content
    )
}

fun formatDate(epochSeconds: Long): String =
    SimpleDateFormat("dd-MM", Locale.getDefault()).format(Date(epochSeconds * 1000L))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPerm = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPerm) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = ContextCompat.checkSelfPermission(context, audioPerm) ==
            PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) {
        val wanted = if (Build.VERSION.SDK_INT >= 33)
            listOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
        else listOf(audioPerm)
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            songs = withContext(Dispatchers.IO) { loadSongs(context) }
        }
    }

    var bgChoice by remember { mutableStateOf(ThemeStore.load(context)) }
    var showThemes by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var sortMode by remember { mutableStateOf(SongSort.DATE) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var playMode by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }

    var playlists by remember { mutableStateOf(MusicStore.loadLists(context)) }
    var counts by remember { mutableStateOf(MusicStore.loadCounts(context)) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var creatingList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var addTarget by remember { mutableStateOf<Song?>(null) }

    fun savePlaylists(newMap: Map<String, List<Long>>) {
        playlists = newMap
        MusicStore.saveLists(context, newMap)
    }

    val filtered = remember(songs, query, sortMode) {
        val base = if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
        }
        when (sortMode) {
            SongSort.NAME -> base.sortedBy { it.title.lowercase() }
            SongSort.DATE -> base.sortedByDescending { it.dateAdded }
        }
    }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var countedFor by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    BackHandler(enabled = showPlayer) { showPlayer = false }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future) }
    }

    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    currentId = item?.mediaId
                    countedFor = null
                }
            }
            c.addListener(listener)
            isPlaying = c.isPlaying
            currentId = c.currentMediaItem?.mediaId
            onDispose { c.removeListener(listener) }
        }
    }

    // Konum takibi ve dinlenme sayacı.
    // Şarkının yarısından fazlası çalındığında sayaç bir artar.
    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.coerceAtLeast(0L)
            }
            val id = currentId
            if (isPlaying && id != null && duration > 0 &&
                position > duration / 2 && countedFor != id
            ) {
                countedFor = id
                MusicStore.incrementCount(context, id.toLong())
                counts = MusicStore.loadCounts(context)
            }
            delay(500)
        }
    }

    fun playList(list: List<Song>, index: Int) {
        val c = controller ?: return
        applyMode(c, playMode)
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun cycleMode() {
        playMode = (playMode + 1) % 3
        controller?.let { applyMode(it, playMode) }
    }

    var menuFor by remember { mutableStateOf<Song?>(null) }
    var renameTarget by remember { mutableStateOf<Song?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Song?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        scope.launch { songs = withContext(Dispatchers.IO) { loadSongs(context) } }
    }

    fun performDelete(song: Song) {
        val sender = deleteSongIntentSender(context, song.uri)
        if (sender != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            scope.launch {
                withContext(Dispatchers.IO) { deleteSongDirect(context, song.uri) }
                songs = withContext(Dispatchers.IO) { loadSongs(context) }
            }
        }
    }

    val currentSong = songs.firstOrNull { it.id.toString() == currentId }
    val c = controller

    Box(Modifier.fillMaxSize().background(bgChoice.brush())) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentSong != null && c != null) {
                    NowPlayingBar(
                        song = currentSong,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        onOpen = { showPlayer = true },
                        onPrev = { c.seekToPreviousMediaItem() },
                        onToggle = { if (isPlaying) c.pause() else c.play() },
                        onNext = { c.seekToNextMediaItem() }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Tab(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            text = { Text("Şarkılar") }
                        )
                        Tab(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            text = { Text("Çalma Listeleri") }
                        )
                    }
                    IconButton(onClick = { showThemes = true }) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Tema")
                    }
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Ara")
                    }
                }

                if (searching && hasPermission && tab == 0) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Şarkı veya sanatçı ara") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                if (!hasPermission) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Müziklerine erişmek için izin gerekli")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(arrayOf(audioPerm)) }) {
                            Text("İzin ver")
                        }
                    }
                } else if (tab == 1) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        MusicPlaylistsTab(
                            songs = songs,
                            lists = playlists,
                            counts = counts,
                            openName = openPlaylist,
                            onOpen = { openPlaylist = it },
                            onCreate = { creatingList = true },
                            onDelete = { name ->
                                savePlaylists(playlists - name)
                                if (openPlaylist == name) openPlaylist = null
                            },
                            onRemove = { name, id ->
                                savePlaylists(
                                    playlists + (name to playlists[name].orEmpty().filter { it != id })
                                )
                            },
                            onPlay = { list, index -> playList(list, index) }
                        )
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${filtered.size} şarkı",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            sortMode = if (sortMode == SongSort.DATE) SongSort.NAME else SongSort.DATE
                        }) {
                            Icon(
                                Icons.Rounded.SwapVert,
                                contentDescription = if (sortMode == SongSort.DATE) {
                                    "Tarihe göre"
                                } else {
                                    "Ada göre"
                                }
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (filtered.isNotEmpty()) {
                                    playMode = 1
                                    playList(filtered, filtered.indices.random())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Karıştır")
                        }
                        Button(
                            onClick = {
                                if (filtered.isNotEmpty()) {
                                    playList(filtered, 0)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Oynat")
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = {
                            scope.launch {
                                refreshing = true
                                songs = withContext(Dispatchers.IO) { loadSongs(context) }
                                refreshing = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            itemsIndexed(filtered, key = { _, s -> s.id }) { index, song ->
                                SongListItem(
                                    song = song,
                                    active = song.id.toString() == currentId,
                                    menuOpen = menuFor == song,
                                    onClick = {
                                        playList(filtered, index)
                                        showPlayer = true
                                    },
                                    onMenuOpen = { menuFor = song },
                                    onMenuClose = { menuFor = null },
                                    onAddToList = { addTarget = song },
                                    onShare = { shareSong(context, song.uri) },
                                    onRename = {
                                        renameText = song.title
                                        renameTarget = song
                                    },
                                    onDelete = { deleteTarget = song }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && c != null && currentSong != null,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            if (c != null && currentSong != null) {
                FullPlayer(
                    song = currentSong,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    playMode = playMode,
                    onSeek = { c.seekTo(it) },
                    onPrev = { c.seekToPreviousMediaItem() },
                    onToggle = { if (isPlaying) c.pause() else c.play() },
                    onNext = { c.seekToNextMediaItem() },
                    onCycleMode = { cycleMode() },
                    onClose = { showPlayer = false }
                )
            }
        }

        if (showThemes) {
            AlertDialog(
                onDismissRequest = { showThemes = false },
                title = { Text("Arka plan") },
                text = {
                    Column {
                        AppBackground.values().forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        bgChoice = option
                                        ThemeStore.save(context, option)
                                        showThemes = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(option.brush())
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(option.label, modifier = Modifier.weight(1f))
                                if (option == bgChoice) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemes = false }) {
                        Text("Kapat")
                    }
                }
            )
        }

        if (creatingList) {
            AlertDialog(
                onDismissRequest = { creatingList = false },
                title = { Text("Yeni liste") },
                text = {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        singleLine = true,
                        label = { Text("Liste adı") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = newListName.trim()
                        if (name.isNotEmpty() && !playlists.containsKey(name)) {
                            savePlaylists(playlists + (name to emptyList<Long>()))
                        }
                        newListName = ""
                        creatingList = false
                    }) {
                        Text("Oluştur")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { creatingList = false }) {
                        Text("İptal")
                    }
                }
            )
        }

        addTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { addTarget = null },
                title = { Text("Çalma listesine ekle") },
                text = {
                    Column {
                        if (playlists.isEmpty()) {
                            Text("Önce Çalma Listeleri sekmesinden bir liste oluştur.")
                        }
                        for (name in playlists.keys) {
                            TextButton(onClick = {
                                val ids = playlists[name].orEmpty()
                                if (!ids.contains(target.id)) {
                                    savePlaylists(playlists + (name to (ids + target.id)))
                                }
                                addTarget = null
                            }) {
                                Text(name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { addTarget = null }) {
                        Text("Kapat")
                    }
                }
            )
        }

        renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Adını değiştir") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Yeni ad") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newBase = renameText.trim()
                        val uri = target.uri
                        renameTarget = null
                        if (newBase.isNotEmpty()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        renameSong(context, uri, newBase)
                                    } catch (e: Exception) {
                                    }
                                }
                                songs = withContext(Dispatchers.IO) { loadSongs(context) }
                            }
                        }
                    }) {
                        Text("Kaydet")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text("İptal")
                    }
                }
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Şarkıyı sil") },
                text = { Text("\"${target.title}\" cihazdan kalıcı olarak silinsin mi?") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        performDelete(target)
                    }) {
                        Text("Sil")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    active: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onMenuOpen: () -> Unit,
    onMenuClose: () -> Unit,
    onAddToList: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(song, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground
            )
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Text(
            formatDate(song.dateAdded),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Box {
            IconButton(onClick = onMenuOpen) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Seçenekler")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuClose) {
                DropdownMenuItem(
                    text = { Text("Çalma listesine ekle") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        onMenuClose()
                        onAddToList()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Paylaş") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = {
                        onMenuClose()
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Adını değiştir") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        onMenuClose()
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Cihazdan sil") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        onMenuClose()
                        onDelete()
                    }
                )
            }
        }
    }
}
