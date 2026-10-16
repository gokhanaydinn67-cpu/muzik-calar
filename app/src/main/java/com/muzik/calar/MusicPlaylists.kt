package com.muzik.calar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MusicPlaylistsTab(
    songs: List<Song>,
    lists: Map<String, List<Long>>,
    counts: Map<Long, Int>,
    openName: String?,
    onOpen: (String?) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onRemove: (String, Long) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    if (openName == null) {
        MusicPlaylistList(
            songs = songs,
            lists = lists,
            counts = counts,
            onOpen = onOpen,
            onCreate = onCreate,
            onDelete = onDelete,
            onPlay = onPlay
        )
    } else {
        MusicPlaylistDetail(
            name = openName,
            songs = songs,
            lists = lists,
            onBack = { onOpen(null) },
            onRemove = onRemove,
            onPlay = onPlay
        )
    }
}

@Composable
private fun MusicPlaylistList(
    songs: List<Song>,
    lists: Map<String, List<Long>>,
    counts: Map<Long, Int>,
    onOpen: (String?) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val top = topSongs(songs, counts, 20)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Çalma Listeleri",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp)
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onCreate,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Yeni liste")
            }
            Spacer(Modifier.height(8.dp))
        }

        items(lists.keys.toList()) { name ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(name) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${lists[name].orEmpty().size} şarkı",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { onDelete(name) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Listeyi sil")
                }
            }
        }

        if (top.isNotEmpty()) {
            item {
                Text(
                    "Sık kullanılanlar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
                )
            }
            itemsIndexed(top) { index, song ->
                MiniSongRow(
                    song = song,
                    onClick = { onPlay(top, index) }
                ) {
                    Text(
                        "${counts[song.id] ?: 0} kez",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MusicPlaylistDetail(
    name: String,
    songs: List<Song>,
    lists: Map<String, List<Long>>,
    onBack: () -> Unit,
    onRemove: (String, Long) -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val list = lists[name].orEmpty().mapNotNull { id ->
        songs.firstOrNull { it.id == id }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Geri")
            }
            Text(
                name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (list.isEmpty()) {
            Text(
                "Bu listede şarkı yok. Şarkılar sekmesinde bir şarkının üç noktasından ekleyebilirsin.",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(list) { index, song ->
                MiniSongRow(
                    song = song,
                    onClick = { onPlay(list, index) }
                ) {
                    IconButton(onClick = { onRemove(name, song.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Listeden çıkar")
                    }
                }
            }
        }
    }
}

@Composable
fun MiniSongRow(
    song: Song,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(song, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        trailing()
    }
}
