package com.muzik.calar

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri)

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
        )
        .build()

fun loadSongs(context: Context): List<Song> {
    val result = mutableListOf<Song>()
    val collection = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
        "${MediaStore.Audio.Media.DURATION} > 30000"
    val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    context.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            val artist = c.getString(artistCol)
            result.add(
                Song(
                    id = id,
                    title = c.getString(titleCol) ?: "Bilinmeyen",
                    artist = if (artist == null || artist == "<unknown>") "Bilinmeyen sanatçı" else artist,
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                )
            )
        }
    }
    return result
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// 0 = Sırayla, 1 = Karışık, 2 = Tek şarkıyı tekrarla
fun modeIcon(mode: Int) = when (mode) {
    1 -> Icons.Rounded.Shuffle
    2 -> Icons.Rounded.RepeatOne
    else -> Icons.Rounded.List
}

fun modeLabel(mode: Int) = when (mode) {
    1 -> "Karışık"
    2 -> "Tek şarkıyı tekrarla"
    else -> "Sırayla"
}

fun applyMode(c: MediaController, mode: Int) {
    c.shuffleModeEnabled = mode == 1
    c.repeatMode = when (mode) {
        2 -> Player.REPEAT_MODE_ONE
        1 -> Player.REPEAT_MODE_ALL
        else -> Player.REPEAT_MODE_OFF
    }
}

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

    var query by remember { mutableStateOf("") }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playMode by remember { mutableStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }

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
                }
            }
            c.addListener(listener)
            isPlaying = c.isPlaying
            currentId = c.currentMediaItem?.mediaId
            onDispose { c.removeListener(listener) }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.coerceAtLeast(0L)
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

    val currentSong = songs.firstOrNull { it.id.toString() == currentId }
    val c = controller

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (currentSong != null && c != null) {
                    NowPlayingBar(
                        song = currentSong,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        onOpen = { showPlayer = true },
                        onSeek = { c.seekTo(it) },
                        onPrev = { c.seekToPreviousMediaItem() },
                        onToggle = { if (isPlaying) c.pause() else c.play() },
                        onNext = { c.seekToNextMediaItem() }
                    )
                }
            }
        ) { padding ->
            if (!hasPermission) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Müziklerine erişmek için izin gerekli")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(arrayOf(audioPerm)) }) {
                            Text("İzin ver")
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        "Müzik",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp)
                    )
                    Text(
                        "${songs.size} şarkı",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Şarkı veya sanatçı ara") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        FilledIconButton(
                            onClick = {
                                if (filtered.isNotEmpty()) {
                                    playMode = 1
                                    playList(filtered, filtered.indices.random())
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Karıştır")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                                SongRow(
                                    song = song,
                                    active = song.id.toString() == currentId,
                                    onClick = {
                                        playList(filtered, index)
                                        showPlayer = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && currentSong != null && c != null,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            if (currentSong != null && c != null) {
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
    }
}

@Composable
fun SongRow(song: Song, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
    }
}

@Composable
fun Cover(song: Song, size: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, song.id) {
        value = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    context.contentResolver.loadThumbnail(song.uri, Size(512, 512), null)
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }
    val bmp = bitmap
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF4A3478), Color(0xFF1C1C28)))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun NowPlayingBar(
    song: Song,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onOpen: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val max = duration.coerceAtLeast(1L).toFloat()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(song, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Önceki")
                }
                FilledIconButton(onClick = onToggle) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Oynat / Duraklat"
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Sonraki")
                }
            }
            Slider(
                value = dragValue ?: position.toFloat().coerceAtMost(max),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { onSeek(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..max
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatTime(dragValue?.toLong() ?: position),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    formatTime(duration),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun FullPlayer(
    song: Song,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    playMode: Int,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onCycleMode: () -> Unit,
    onClose: () -> Unit
) {
    var dragY by remember { mutableStateOf(0f) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val max = duration.coerceAtLeast(1L).toFloat()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val coverSize = (screenWidth - 64).coerceAtMost(340).dp

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.coerceAtLeast(0f).roundToInt()) }
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2B2358), Color(0xFF0E0E12)))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragEnd = {
                        if (dragY > 200f) onClose() else dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragY += amount
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Kapat", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Şimdi çalıyor",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.weight(1f))

        Cover(song, coverSize)

        Spacer(Modifier.height(28.dp))

        Text(
            song.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White
        )
        Text(
            song.artist,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(Modifier.weight(1f))

        Slider(
            value = dragValue ?: position.toFloat().coerceAtMost(max),
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..max,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime(dragValue?.toLong() ?: position),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                formatTime(duration),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCycleMode) {
                Icon(
                    modeIcon(playMode),
                    contentDescription = modeLabel(playMode),
                    tint = if (playMode == 0) Color.White.copy(alpha = 0.6f) else Color.White
                )
            }
            IconButton(onClick = onPrev) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Önceki",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Oynat / Duraklat",
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Sonraki",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            modeLabel(playMode),
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
    }
}
