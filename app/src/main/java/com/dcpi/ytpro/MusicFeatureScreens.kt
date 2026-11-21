package com.dcpi.ytpro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dcpi.ytpro.library.LocalLibraryRepository
import com.dcpi.ytpro.library.LocalTrack
import com.dcpi.ytpro.library.PlaylistStore
import com.dcpi.ytpro.library.UserPlaylist
import com.dcpi.ytpro.playback.AudioEffectsController
import java.util.UUID

@Composable
fun LocalLibraryScreen(onPlay: (LocalTrack) -> Unit) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var playlists by remember { mutableStateOf(PlaylistStore(context).all()) }
    var status by remember { mutableStateOf("Permite el acceso al audio para explorar tu música local.") }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            tracks = runCatching { LocalLibraryRepository(context).scanAudio() }.getOrDefault(emptyList())
            status = "${tracks.size} canciones encontradas"
        } else status = "Permiso denegado. No se puede leer la biblioteca."
    }
    val hasPermission = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca local", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                if (hasPermission) {
                    tracks = runCatching { LocalLibraryRepository(context).scanAudio() }.getOrDefault(emptyList())
                    status = "${tracks.size} canciones encontradas"
                } else launcher.launch(permission)
            }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (hasPermission) {
                    tracks = runCatching { LocalLibraryRepository(context).scanAudio() }.getOrDefault(emptyList())
                    status = "${tracks.size} canciones encontradas"
                } else launcher.launch(permission)
            }) { Text("Escanear música") }
            OutlinedButton(onClick = {
                if (tracks.isNotEmpty()) {
                    PlaylistStore(context).save(UserPlaylist(UUID.randomUUID().toString(), "Mi música", tracks.map { it.uri }))
                    playlists = PlaylistStore(context).all()
                }
            }, enabled = tracks.isNotEmpty()) { Text("Crear lista") }
        }
        if (playlists.isNotEmpty()) {
            Text("Mis listas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            playlists.forEach { Text("♫ ${it.name} · ${it.trackUris.size} pistas", style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tracks, key = { it.uri }) { track ->
                ListItem(
                    headlineContent = { Text(track.title, maxLines = 1) },
                    supportingContent = { Text("${track.artist} · ${track.album}", maxLines = 1) },
                    leadingContent = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                    modifier = Modifier.clickable { onPlay(track) }
                )
            }
        }
    }
}

@Composable
fun AudioEffectsScreen(audioSessionId: Int) {
    if (audioSessionId <= 0) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Reproduce una canción primero para activar los efectos de audio.")
        }
        return
    }
    val effects = remember(audioSessionId) { runCatching { AudioEffectsController(audioSessionId) }.getOrNull() }
    DisposableEffect(effects) { onDispose { effects?.close() } }
    var enabled by remember(audioSessionId) { mutableStateOf(true) }
    var bass by remember(audioSessionId) { mutableFloatStateOf(0f) }
    var loudness by remember(audioSessionId) { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Audio y ecualizador", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Efectos activos")
            Spacer(Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; effects?.enable(it) })
        }
        Text("Refuerzo de graves")
        Slider(value = bass, onValueChange = { bass = it; effects?.setBass((it * 1000).toInt()) }, valueRange = 0f..1f, enabled = enabled && effects != null)
        Text("Normalización / ganancia")
        Slider(value = loudness, onValueChange = { loudness = it; effects?.setLoudness((it * 1200).toInt()) }, valueRange = 0f..1f, enabled = enabled && effects != null)
        Text("Bandas del ecualizador: ${effects?.bandCount ?: 0}")
        if (effects == null) Text("Este dispositivo no expuso los efectos de audio solicitados.")
        else {
            val bandRange = effects.bandRange()
            repeat(effects.bandCount) { band ->
                var level by remember(audioSessionId, band) { mutableFloatStateOf(0f) }
                Text("Banda ${band + 1}: ${level.toInt()} mB")
                Slider(
                    value = level.coerceIn(bandRange.first.toFloat(), bandRange.last.toFloat()),
                    onValueChange = { level = it; effects.setBand(band, it.toInt()) },
                    valueRange = bandRange.first.toFloat()..bandRange.last.toFloat(),
                    enabled = enabled
                )
            }
        }
        Text("Los efectos dependen del hardware y del motor de audio de Android.", style = MaterialTheme.typography.bodySmall)
    }
}
