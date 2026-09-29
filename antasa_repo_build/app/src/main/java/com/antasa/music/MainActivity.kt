package com.antasa.music

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var current by mutableIntStateOf(-1)
    private var playing by mutableStateOf(false)

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) loadSongs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        })
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) loadSongs()
        else permission.launch(p)
        setContent { AntasaApp(songs, current, playing, ::playSong, ::toggle, ::next) }
    }

    private fun loadSongs() {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.IS_MUSIC)
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += Song(id, c.getString(titleCol) ?: "Unknown",
                    c.getString(artistCol) ?: "Unknown artist", ContentUris.withAppendedId(collection, id))
            }
        }
        songs = result
    }

    private fun playSong(index: Int) {
        if (songs.isEmpty()) return
        current = index.coerceIn(0, songs.lastIndex)
        player.setMediaItem(MediaItem.fromUri(songs[current].uri))
        player.prepare()
        player.play()
    }
    private fun toggle() {
        if (current < 0 && songs.isNotEmpty()) playSong(0)
        else if (player.isPlaying) player.pause() else player.play()
    }
    private fun next() { if (songs.isNotEmpty()) playSong((current + 1).mod(songs.size)) }

    override fun onDestroy() { player.release(); super.onDestroy() }
}

@Composable
fun AntasaApp(songs: List<Song>, current: Int, playing: Boolean,
              onPlay: (Int) -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val bg = Color(0xFF101010)
    val orange = Color(0xFFFF8A00)
    var tab by remember { mutableStateOf("Home") }
    MaterialTheme(colorScheme = darkColorScheme(primary = orange, background = bg, surface = Color(0xFF202020))) {
        Column(Modifier.fillMaxSize().background(bg).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = orange, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text("Antasa Music", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "Home", onClick = { tab = "Home" }, label = { Text("Home") })
                FilterChip(selected = tab == "Library", onClick = { tab = "Library" }, label = { Text("Library") })
                FilterChip(selected = tab == "Streaming", onClick = { tab = "Streaming" }, label = { Text("Streaming") })
            }
            Spacer(Modifier.height(20.dp))
            if (tab == "Streaming") {
                Text("Streaming", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Bagian streaming akan dihubungkan ke sumber audio legal pada versi berikutnya.", color = Color.LightGray)
                Text("Belum ada katalog online yang terhubung.", color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
            } else {
                Text(if (tab == "Home") "Musikmu, tanpa gangguan." else "Musik di perangkat", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${songs.size} lagu ditemukan", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                if (songs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Belum ada lagu lokal.
Tambahkan file musik ke HP kamu.", color = Color.LightGray)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(songs) { i, song ->
                            Row(Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).background(Color(0xFF303030), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, null, tint = orange)
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(song.title, color = if (i == current) orange else Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(song.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                }
                                if (i == current && playing) Icon(Icons.Default.VolumeUp, null, tint = orange)
                                else Icon(Icons.Default.PlayArrow, null, tint = Color.LightGray)
                            }
                            HorizontalDivider(color = Color(0xFF292929))
                        }
                    }
                }
            }
            if (current >= 0 && current < songs.size) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF252525), RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Album, null, tint = orange, modifier = Modifier.size(38.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(songs[current].title, color = Color.White, maxLines = 1, fontWeight = FontWeight.Medium)
                        Text(songs[current].artist, color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton(onClick = onToggle) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                    }
                    IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                Text("⌂  Home", color = if (tab == "Home") orange else Color.Gray, modifier = Modifier.clickable { tab = "Home" })
                Text("♫  Library", color = if (tab == "Library") orange else Color.Gray, modifier = Modifier.clickable { tab = "Library" })
                Text("◎  Streaming", color = if (tab == "Streaming") orange else Color.Gray, modifier = Modifier.clickable { tab = "Streaming" })
            }
        }
    }
}
