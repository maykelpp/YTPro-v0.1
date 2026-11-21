package com.dcpi.ytpro

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import com.dcpi.ytpro.library.LocalTrack
import com.dcpi.ytpro.downloads.DownloadQueueManager
import com.dcpi.ytpro.downloads.DownloadStatus
import com.dcpi.ytpro.playback.LrcParser
import com.dcpi.ytpro.playback.LyricLine
import com.dcpi.ytpro.playback.TransitionController
import kotlinx.coroutines.delay

private val Ink = Color(0xFF09090D)
private val Panel = Color(0xFF17171F)
private val Pink = Color(0xFFFF168A)
private val Red = Color(0xFFFF3048)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            YoutubeDL.getInstance().init(application)
            FFmpeg.getInstance().init(application)
            lifecycleScope.launch(Dispatchers.IO) {
                try { YoutubeDL.getInstance().updateYoutubeDL(this@MainActivity, UpdateChannel._NIGHTLY) }
                catch (e: Exception) { Log.w("YTPro", "No se pudo actualizar yt-dlp", e) }
            }
        } catch (e: Exception) {
            Log.e("YTPro", "No se pudo iniciar yt-dlp/FFmpeg", e)
        }
        setContent { YTProApp() }
    }
}

private data class Gate(val allowed: Boolean, val state: String, val message: String, val updateUrl: String = "")
private data class Track(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val duration: Long,
    val url: String
)

private suspend fun checkGate(): Gate = withContext(Dispatchers.IO) {
    try {
        val connection = URL("${BuildConfig.CONTROL_API}/v1/status?versionCode=${BuildConfig.VERSION_CODE}")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 7000
        connection.readTimeout = 7000
        connection.requestMethod = "GET"
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val json = JSONObject(body)
        Gate(json.optBoolean("allowed"), json.optString("state"), json.optString("message"), json.optString("updateUrl"))
    } catch (_: Exception) {
        Gate(false, "offline", "No se pudo verificar el estado de YTPro. Comprueba tu conexión e inténtalo de nuevo.")
    }
}

private suspend fun searchTracks(query: String, start: Int = 1, pageSize: Int = 20): List<Track> = withContext(Dispatchers.IO) {
    val request = YoutubeDLRequest("ytsearch" + (start + pageSize - 1) + ":" + query)
    request.addOption("--dump-single-json")
    request.addOption("--flat-playlist")
    request.addOption("--skip-download")
    request.addOption("--no-warnings")
    request.addOption("--playlist-start", start.toString())
    request.addOption("--playlist-end", (start + pageSize - 1).toString())
    request.addOption("--no-update")
    val output = YoutubeDL.getInstance().execute(request).out
    val root = JSONObject(output)
    val entries = root.optJSONArray("entries") ?: return@withContext emptyList()
    buildList {
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank() || id == "null") continue
            val thumb = item.optString("thumbnail").ifBlank {
                "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            }
            add(
                Track(
                    id = id,
                    title = item.optString("title", "Sin título"),
                    channel = item.optString("channel").ifBlank { item.optString("uploader", "YouTube") },
                    thumbnail = thumb,
                    duration = item.optLong("duration", 0L),
                    url = item.optString("url").takeIf { it.startsWith("http") }
                        ?: "https://www.youtube.com/watch?v=$id"
                )
            )
        }
    }
}

private suspend fun getAudioStream(track: Track): String = withContext(Dispatchers.IO) {
    val request = YoutubeDLRequest(track.url)
    request.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
    request.addOption("--no-warnings")
    request.addOption("--no-update")
    val info = YoutubeDL.getInstance().getInfo(request)
    info.url?.takeIf { it.startsWith("https://") }
        ?: error("YouTube no entregó un enlace de audio válido. Prueba otro resultado.")
}

private suspend fun downloadAudio(context: android.content.Context, track: Track): Uri = withContext(Dispatchers.IO) {
    val base = File(File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "YTPro"), track.id)
    if (!base.exists() && !base.mkdirs()) error("No se pudo preparar el almacenamiento de YTPro.")
    val request = YoutubeDLRequest(track.url)
    request.addOption("-x")
    request.addOption("--audio-format", "mp3")
    request.addOption("--audio-quality", "0")
    request.addOption("--no-playlist")
    request.addOption("--no-update")
    request.addOption("-o", File(base, "%(title).180B.%(ext)s").absolutePath)
    YoutubeDL.getInstance().execute(request)
    val output = base.listFiles()
        ?.filter { it.isFile && it.extension.equals("mp3", true) }
        ?.maxByOrNull { it.lastModified() }
        ?: error("yt-dlp terminó, pero no se encontró el MP3 generado.")

    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, output.name)
        put(MediaStore.Audio.Media.TITLE, output.nameWithoutExtension)
        put(MediaStore.Audio.Media.ARTIST, track.channel)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YTPro")
        put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Android no permitió guardar el audio en Música/YTPro.")
    try {
        resolver.openOutputStream(uri)?.use { destination ->
            output.inputStream().use { source -> source.copyTo(destination) }
        } ?: error("No se pudo escribir el archivo en la biblioteca.")
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        output.delete()
        uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
}

@Composable
private fun YTProApp() {
    var gate by remember { mutableStateOf<Gate?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) { gate = checkGate() }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Pink, secondary = Red)) {
        val current = gate
        when {
            current == null -> Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pink)
            }
            !current.allowed -> GateScreen(current) { retry++ }
            else -> HomeScreen()
        }
    }
}

@Composable
private fun GateScreen(gate: Gate, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF210D20), Ink))).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Pink, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text("YTPro", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(14.dp))
        Text(when (gate.state) {
            "maintenance" -> "MANTENIMIENTO"
            "update_required" -> "ACTUALIZACIÓN OBLIGATORIA"
            "disabled" -> "APLICACIÓN DESACTIVADA"
            else -> "VERIFICACIÓN NECESARIA"
        }, color = Pink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(gate.message, color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
        if (gate.state == "update_required" && gate.updateUrl.isNotBlank()) {
            val context = LocalContext.current
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gate.updateUrl))) }, colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text("Actualizar YTPro") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = retry) { Text("Volver a comprobar") }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }
    val downloadQueue = remember(context) { DownloadQueueManager { task -> downloadAudio(context, Track(task.contentId, task.title, "YTPro", "", 0L, task.source)) } }
    val downloadTasks by downloadQueue.tasks.collectAsState()
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var previewStreams by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var current by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var audioSessionId by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Busca música o pega un enlace de contenido que tengas permiso de guardar.") }
    var selected by remember { mutableStateOf("Inicio") }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricIndex by remember { mutableIntStateOf(-1) }
    val lyricPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val source = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
            lyrics = LrcParser.parse(source)
            lyricIndex = -1
            message = if (lyrics.isEmpty()) "El archivo no contiene letras LRC válidas." else "Letras cargadas: " + lyrics.size + " líneas."
        }
    }
    LaunchedEffect(player, current, isPlaying, selected, lyrics) {
        while (isPlaying && selected == "Letras") {
            lyricIndex = LrcParser.activeIndex(lyrics, player.currentPosition)
            delay(150L)
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YT", color = Red, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text("Pro", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Buscar canciones, artistas o pegar enlace") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Pink, unfocusedBorderColor = Panel,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (query.isBlank() || busy) return@Button
                    busy = true
                    message = "Buscando con yt-dlp…"
                    scope.launch {
                        try {
                            val found = searchTracks(query.trim())
                            tracks = found
                            previewStreams = emptyMap()
                            message = if (found.isEmpty()) "No se encontraron resultados." else "${found.size} resultados · preparando vistas previas"
                            // Preparar enlaces en segundo plano para acelerar el toque en Escuchar.
                            scope.launch {
                                found.take(8).forEach { candidate ->
                                    if (previewStreams[candidate.id] == null) {
                                        val resolved = runCatching { getAudioStream(candidate) }.getOrNull()
                                        if (resolved != null) previewStreams = previewStreams + (candidate.id to resolved)
                                    }
                                }
                            }
                        }
                        catch (e: Exception) { message = "Error de búsqueda: ${e.message ?: "inténtalo de nuevo"}" }
                        finally { busy = false }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                enabled = !busy
            ) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Buscar") }
            OutlinedButton(onClick = { tracks = emptyList(); message = "Lista limpia." }, enabled = !busy) { Text("Limpiar") }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Pink, strokeWidth = 2.dp)
        }
        Text(message, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        when (selected) {
            "Biblioteca" -> LocalLibraryScreen { localTrack ->
                val item = Track(
                    id = localTrack.uri,
                    title = localTrack.title,
                    channel = localTrack.artist,
                    thumbnail = "",
                    duration = localTrack.durationMs / 1000L,
                    url = localTrack.uri
                )
                current = item
                scope.launch {
                    runCatching { TransitionController(player).playWithFade(localTrack.uri) }
                        .onSuccess {
                            audioSessionId = player.audioSessionId
                            isPlaying = true
                            message = "Reproduciendo desde la biblioteca local"
                        }
                        .onFailure { message = "No se pudo reproducir el archivo: ${it.message ?: "error de audio"}" }
                }
            }
            "Audio" -> AudioEffectsScreen(audioSessionId)
            "Letras" -> Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                Button(onClick = { lyricPicker.launch(arrayOf("text/plain", "application/octet-stream")) }) { Text("Abrir archivo .LRC") }
                if (lyrics.isEmpty()) {
                    Text("Carga un archivo LRC para ver las letras sincronizadas con la reproducción.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
                        items(lyrics.size) { index ->
                            Text(lyrics[index].text, color = if (index == lyricIndex) Pink else Color.LightGray, fontSize = if (index == lyricIndex) 22.sp else 17.sp, fontWeight = if (index == lyricIndex) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                        }
                    }
                }
            }
            "Descargas" -> LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                item { Text("Cola de descargas", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(12.dp)) }
                items(downloadTasks, key = { it.id }) { task ->
                    ListItem(headlineContent = { Text(task.title, color = Color.White, maxLines = 1) }, supportingContent = { Text(task.message.ifBlank { task.status.name }, color = Color.Gray) }, leadingContent = { Icon(Icons.Default.Download, contentDescription = null, tint = Pink) }, trailingContent = { Text(task.status.name, color = if (task.status == DownloadStatus.FAILED) Red else Pink, fontSize = 10.sp) })
                }
                item { TextButton(onClick = { downloadQueue.removeFinished() }) { Text("Limpiar terminadas") } }
            }
            else -> LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onPreview = {
                            if (busy) return@TrackRow
                            busy = true
                            message = "Preparando vista previa…"
                            scope.launch {
                                try {
                                    val stream = previewStreams[track.id] ?: withTimeout(2000L) { getAudioStream(track) }
                                    TransitionController(player).playWithFade(stream)
                                    audioSessionId = player.audioSessionId
                                    current = track
                                    isPlaying = true
                                    message = "Reproduciendo vista previa"
                                } catch (e: Exception) { message = "No se pudo reproducir: ${e.message ?: "flujo no disponible"}" }
                                finally { busy = false }
                            }
                        },
                        onDownload = {
                            downloadQueue.enqueue(track.title, track.url, track.id)
                            message = "Añadido a la cola de descargas."
                            selected = "Descargas"
                        }
                    )
                }
            }
        }
        current?.let { track ->
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(track.thumbnail, null, modifier = Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.channel, color = Color.Gray, fontSize = 12.sp)
                }
                IconButton(onClick = {
                    if (player.isPlaying) { player.pause(); isPlaying = false }
                    else { player.play(); isPlaying = true }
                }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Pink) }
            }
        }
        NavigationBar(containerColor = Panel) {
            listOf("Inicio", "Biblioteca", "Audio", "Letras", "Descargas").forEachIndexed { index, label ->
                NavigationBarItem(
                    selected = selected == label, onClick = { selected = label },
                    icon = { Icon(listOf(Icons.Default.Home, Icons.Default.LibraryMusic, Icons.Default.GraphicEq, Icons.Default.Lyrics, Icons.Default.Download)[index], null) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onPreview: () -> Unit, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).background(Panel, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(track.thumbnail, null, modifier = Modifier.size(64.dp).background(Color.DarkGray, RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(track.channel, formatDuration(track.duration)).filter { it.isNotBlank() }.joinToString(" · "),
                color = Color.Gray, fontSize = 11.sp, maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPreview) { Icon(Icons.Default.PlayArrow, null); Text("Escuchar") }
                TextButton(onClick = onDownload) { Icon(Icons.Default.Download, null); Text("Descargar") }
            }
        }
    }
}
