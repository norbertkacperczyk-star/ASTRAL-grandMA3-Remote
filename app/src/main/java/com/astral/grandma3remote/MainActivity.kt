package com.astral.grandma3remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF10243D)
private val Blue = Color(0xFF168BFF)
private val TextMain = Color(0xFFEAF3FF)
private val Muted = Color(0xFF91A8C4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstralApp() }
    }
}

@Composable
private fun AstralApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("astral_remote", Context.MODE_PRIVATE) }
    var host by remember { mutableStateOf(prefs.getString("host", "192.168.0.100") ?: "192.168.0.100") }
    var oscPort by remember { mutableStateOf(prefs.getInt("osc_port", 8000).toString()) }
    var webPort by remember { mutableStateOf(prefs.getInt("web_port", 8080).toString()) }
    var oscPath by remember { mutableStateOf(prefs.getString("osc_path", "/cmd") ?: "/cmd") }
    var page by remember { mutableIntStateOf(1) }
    var tab by remember { mutableStateOf("PILOT") }
    var status by remember { mutableStateOf("NIEPOŁĄCZONO") }
    var commandLog by remember { mutableStateOf("Gotowy do wysyłania poleceń OSC") }

    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Panel, onPrimary = Color.White, onBackground = TextMain, onSurface = TextMain)) {
        Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ASTRAL", color = Blue, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("grandMA3 REMOTE  •  v1.0", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(status, color = if (status == "OSC WYSŁANY") Color(0xFF52D69A) else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("MA3 2.4", color = TextMain, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PILOT", "WEB REMOTE", "USTAWIENIA").forEach { item ->
                    val selected = tab == item
                    Button(onClick = { tab = item }, modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Blue else Panel),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text(item, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
            when (tab) {
                "PILOT" -> PilotScreen(page, { page = it }, { command ->
                    sendOsc(host, oscPort.toIntOrNull() ?: 8000, oscPath, command) { ok, message ->
                        status = if (ok) "OSC WYSŁANY" else "BŁĄD OSC"
                        commandLog = message
                    }
                }, commandLog)
                "WEB REMOTE" -> WebRemoteScreen(host, webPort.toIntOrNull() ?: 8080)
                else -> SettingsScreen(host, { host = it }, oscPort, { oscPort = it }, webPort, { webPort = it },
                    oscPath, { oscPath = it }, {
                        prefs.edit().putString("host", host).putInt("osc_port", oscPort.toIntOrNull() ?: 8000)
                            .putInt("web_port", webPort.toIntOrNull() ?: 8080).putString("osc_path", oscPath).apply()
                        commandLog = "Ustawienia zapisane na tym urządzeniu"
                    }, {
                        sendOsc(host, oscPort.toIntOrNull() ?: 8000, oscPath, "List") { ok, msg ->
                            status = if (ok) "OSC WYSŁANY" else "BŁĄD OSC"
                            commandLog = msg
                        }
                    }, commandLog)
            }
        }
    }
}

@Composable
private fun PilotScreen(page: Int, onPage: (Int) -> Unit, onSend: (String) -> Unit, commandLog: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("EXECUTORS", color = TextMain, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BANK", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onPage((page - 1).coerceAtLeast(1)) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Text("‹") }
                Text("  $page  ", color = TextMain, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { onPage(page + 1) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Text("›") }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 8.dp)) {
            items((1..15).toList()) { index -> ExecutorTile((page - 1) * 15 + index, onSend) }
        }
        Text("SZYBKIE STEROWANIE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("GO", "PAUSE", "OFF", "FLASH").forEach { action ->
                Button(onClick = { onSend(action) }, modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = when (action) {
                        "GO" -> Color(0xFF087E55)
                        "OFF" -> Color(0xFF9B3444)
                        "FLASH" -> Color(0xFFB47719)
                        else -> Blue
                    }), contentPadding = PaddingValues(2.dp)) { Text(action, fontSize = 11.sp, fontWeight = FontWeight.Black) }
            }
        }
        Text(commandLog, color = Muted, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun ExecutorTile(executor: Int, onSend: (String) -> Unit) {
    var level by remember { mutableFloatStateOf(0f) }
    Card(Modifier.fillMaxWidth().height(136.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("EXEC $executor", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallAction("GO", Modifier.weight(1f)) { onSend("Go Executor $executor") }
                SmallAction("OFF", Modifier.weight(1f)) { onSend("Off Executor $executor") }
            }
            Spacer(Modifier.height(4.dp))
            Slider(value = level, onValueChange = { level = it },
                onValueChangeFinished = { onSend("Executor $executor At ${(level * 100).toInt()}") },
                modifier = Modifier.height(30.dp), colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
            Text("${(level * 100).toInt()}%", color = TextMain, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SmallAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(30.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (label == "OFF") Color(0xFF713443) else Color(0xFF174C7B))) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(host: String, setHost: (String) -> Unit, oscPort: String, setOscPort: (String) -> Unit,
    webPort: String, setWebPort: (String) -> Unit, oscPath: String, setOscPath: (String) -> Unit,
    onSave: () -> Unit, onTest: () -> Unit, commandLog: String) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("POŁĄCZENIE Z KONSOLĄ", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Telefon i grandMA3 muszą być w tej samej sieci lokalnej Wi-Fi/LAN.", color = Muted, fontSize = 12.sp)
        ConfigField("ADRES IP GRANDMA3", host, setHost)
        ConfigField("PORT OSC (UDP)", oscPort, setOscPort)
        ConfigField("PORT WEB REMOTE", webPort, setWebPort)
        ConfigField("ADRES OSC", oscPath, setOscPath)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("ZAPISZ USTAWIENIA") }
        OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("WYŚLIJ TEST OSC") }
        Text(commandLog, color = Muted, fontSize = 11.sp)
        Text("Porty i adres OSC są konfigurowalne. Sprawdź port wejściowy OSC oraz mapowanie poleceń w ustawieniach grandMA3.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ConfigField(label: String, value: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                focusedBorderColor = Blue, unfocusedBorderColor = Muted))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebRemoteScreen(host: String, port: Int) {
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("grandMA3 Web Remote  •  http://$host:$port", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, e: android.webkit.WebResourceError?) {
                        if (request?.isForMainFrame == true) error = true
                    }
                }
                loadUrl("http://$host:$port")
            }
        }, update = { view ->
            val target = "http://$host:$port"
            if (view.url != target && view.url == null) view.loadUrl(target)
        })
        if (error) Text("Nie udało się otworzyć Web Remote. Sprawdź IP, port i sieć.", color = Color(0xFFFF8B8B), fontSize = 11.sp)
    }
}

private val sender = Executors.newSingleThreadExecutor()

private fun sendOsc(host: String, port: Int, path: String, command: String, callback: (Boolean, String) -> Unit) {
    sender.execute {
        try {
            require(path.startsWith("/")) { "Adres OSC musi zaczynać się od /" }
            val packetBytes = oscMessage(path, command)
            DatagramSocket().use { socket ->
                val address = InetAddress.getByName(host)
                socket.send(DatagramPacket(packetBytes, packetBytes.size, address, port))
            }
            callback(true, "Wysłano OSC: $command")
        } catch (e: Exception) {
            callback(false, "Błąd wysyłania: ${e.message ?: "sprawdź IP i port"}")
        }
    }
}

private fun oscMessage(address: String, value: String): ByteArray {
    val out = ByteArrayOutputStream()
    val data = DataOutputStream(out)
    fun writeOscString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        data.write(bytes)
        data.writeByte(0)
        repeat((4 - ((bytes.size + 1) % 4)) % 4) { data.writeByte(0) }
    }
    writeOscString(address)
    writeOscString(",s")
    writeOscString(value)
    return out.toByteArray()
}
