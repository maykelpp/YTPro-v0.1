package com.dcpi.ytpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF09090D)
private val Panel = Color(0xFF17171F)
private val Pink = Color(0xFFFF168A)
private val Red = Color(0xFFFF3048)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YTProApp() }
    }
}

private data class Gate(val allowed: Boolean, val state: String, val message: String, val updateUrl: String = "")

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

@Composable
private fun YTProApp() {
    var gate by remember { mutableStateOf<Gate?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(retry) { gate = checkGate() }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Pink, secondary = Red
    )) {
        val current = gate
        if (current == null) {
            Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pink)
            }
        } else if (!current.allowed) {
            GateScreen(current) { retry++ }
        } else {
            HomeScreen()
        }
    }
}

@Composable
private fun GateScreen(gate: Gate, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF210D20), Ink)))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Pink, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text("YTPro", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(14.dp))
        Text(
            when (gate.state) {
                "maintenance" -> "MANTENIMIENTO"
                "update_required" -> "ACTUALIZACIÓN OBLIGATORIA"
                "disabled" -> "APLICACIÓN DESACTIVADA"
                else -> "VERIFICACIÓN NECESARIA"
            },
            color = Pink, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(gate.message, color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
        Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
            Text("Volver a comprobar")
        }
    }
}

@Composable
private fun HomeScreen() {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("Inicio") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("YT", color = Red, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text("Pro", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Buscar canciones, artistas o álbumes") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Pink, unfocusedBorderColor = Panel,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )
        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Para ti", "Música", "Podcasts").forEach { label ->
                FilterChip(selected = selected == label, onClick = { selected = label }, label = { Text(label) })
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            item {
                Text("Tu música, sin esperas", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("YTPro v0.1 · búsqueda y vista previa", color = Color.Gray)
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().height(174.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF6B102E), Color(0xFF21102D), Panel)), RoundedCornerShape(22.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text("ESCUCHA ANTES DE DESCARGAR", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Vista previa rápida por streaming", color = Color.LightGray, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Tu biblioteca", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Aquí aparecerán tus canciones descargadas.", color = Color.Gray)
                Spacer(Modifier.height(90.dp))
            }
        }
        NavigationBar(containerColor = Panel) {
            listOf("Inicio", "Explorar", "Biblioteca").forEachIndexed { index, label ->
                NavigationBarItem(
                    selected = selected == label,
                    onClick = { selected = label },
                    icon = { Icon(listOf(Icons.Default.Home, Icons.Default.Explore, Icons.Default.LibraryMusic)[index], null) },
                    label = { Text(label) }
                )
            }
        }
    }
}
