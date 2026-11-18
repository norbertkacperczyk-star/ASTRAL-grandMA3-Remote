package com.astral.grandma3remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    var listenPort by remember { mutableStateOf(prefs.getInt("listen_port", 8001).toString()) }
    var feedbackLog by remember { mutableStateOf("Oczekiwanie na feedback OSC…") }
    var webPort by remember { mutableStateOf(prefs.getInt("web_port", 8080).toString()) }
    var oscPath by remember { mutableStateOf(prefs.getString("osc_path", "/cmd") ?: "/cmd") }
    var page by remember { mutableIntStateOf(1) }
    var tab by remember { mutableStateOf("PILOT") }
    var status by remember { mutableStateOf("NIEPOŁĄCZONO") }
    var commandLog by remember { mutableStateOf("Gotowy do wysyłania poleceń OSC") }
    var executorNames by remember {
        mutableStateOf((1..150).associateWith { prefs.getString("executor_name_$it", "") ?: "" })
    }

    DisposableEffect(listenPort) {
        val port = listenPort.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 8001
        val running = AtomicBoolean(true)
        val socket = try { DatagramSocket(port).apply { soTimeout = 1000 } } catch (e: Exception) {
            mainHandler.post { feedbackLog = "Nie można uruchomić nasłuchu UDP na porcie $port: ${e.message}" }
            null
        }
        val thread = if (socket != null) Thread {
            val buffer = ByteArray(4096)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val decoded = decodeOscPacket(packet.data, packet.length)
                    mainHandler.post { feedbackLog = decoded; status = "OSC ODEBRANY" }
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    if (running.get()) mainHandler.post { feedbackLog = "Błąd nasłuchu OSC: ${e.message}" }
                }
            }
        }.apply { name = "AstralOscReceiver"; isDaemon = true; start() } else null
        if (socket != null) feedbackLog = "Nasłuch OSC aktywny • UDP $port"
        onDispose { running.set(false); socket?.close(); thread?.interrupt() }
    }

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
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("PILOT", "VALUE CTRL", "WEB REMOTE", "USTAWIENIA").forEach { item ->
                    val selected = tab == item
                    Button(onClick = { tab = item }, modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Blue else Panel),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text(item, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
            when (tab) {
                "PILOT" -> PilotScreen(page, { page = it }, executorNames, { executor, name ->
                    executorNames = executorNames + (executor to name)
                    prefs.edit().putString("executor_name_$executor", name).apply()
                }, { command ->
                    sendOsc(host, oscPort.toIntOrNull() ?: 8000, oscPath, command) { ok, message ->
                        status = if (ok) "OSC WYSŁANY" else "BŁĄD OSC"
                        commandLog = message
                    }
                }, commandLog)
                "VALUE CTRL" -> ValueControlScreen(feedbackLog, commandLog) { command ->
                    sendOsc(host, oscPort.toIntOrNull() ?: 8000, oscPath, command) { ok, message ->
                        status = if (ok) "OSC WYSŁANY" else "BŁĄD OSC"
                        commandLog = message
                    }
                }
                "WEB REMOTE" -> WebRemoteScreen(host, webPort.toIntOrNull() ?: 8080)
                else -> SettingsScreen(host, { host = it }, oscPort, { oscPort = it }, listenPort, { listenPort = it }, webPort, { webPort = it },
                    oscPath, { oscPath = it }, {
                        prefs.edit().putString("host", host).putInt("osc_port", oscPort.toIntOrNull() ?: 8000)
                            .putInt("listen_port", listenPort.toIntOrNull() ?: 8001)
                            .putInt("web_port", webPort.toIntOrNull() ?: 8080).putString("osc_path", oscPath).apply()
                        commandLog = "Ustawienia zapisane na tym urządzeniu"
                    }, {
                        sendOsc(host, oscPort.toIntOrNull() ?: 8000, oscPath, "List") { ok, msg ->
                            status = if (ok) "OSC WYSŁANY" else "BŁĄD OSC"
                            commandLog = msg
                        }
                    }, commandLog, feedbackLog)
            }
        }
    }
}

@Composable
private fun PilotScreen(page: Int, onPage: (Int) -> Unit, executorNames: Map<Int, String>, onRename: (Int, String) -> Unit, onSend: (String) -> Unit, commandLog: String) {
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
            items((1..15).toList()) { index -> val id = (page - 1) * 15 + index; ExecutorTile(id, executorNames[id].orEmpty().ifBlank { "EXEC $id" }, onRename, onSend) }
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
private fun ExecutorTile(executor: Int, label: String, onRename: (Int, String) -> Unit, onSend: (String) -> Unit) {
    var level by remember { mutableFloatStateOf(0f) }
    var editingName by remember { mutableStateOf(false) }
    var draftName by remember(label) { mutableStateOf(label) }
    Card(Modifier.fillMaxWidth().height(136.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.clickable { draftName = label; editingName = true })
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
    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Nazwa executora $executor") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(24) },
                    singleLine = true,
                    label = { Text("Nazwa widoczna na przycisku") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(executor, draftName.trim())
                    editingName = false
                }) { Text("ZAPISZ") }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) { Text("ANULUJ") }
            }
        )
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
private fun ValueControlScreen(feedbackLog: String, commandLog: String, onSend: (String) -> Unit) {
    val groups = listOf("DIMMER", "POSITION", "COLOR", "GOBO", "BEAM", "ALL")
    val attributes = mapOf(
        "DIMMER" to listOf("Dimmer", "Shutter", "Strobe", "Strobe Speed"),
        "POSITION" to listOf("Pan", "Tilt", "Pan Fine", "Tilt Fine"),
        "COLOR" to listOf("Red", "Green", "Blue", "Color Temperature", "Hue", "Saturation"),
        "GOBO" to listOf("Gobo1", "Gobo2", "Gobo Rotation", "Gobo Index"),
        "BEAM" to listOf("Zoom", "Focus", "Iris", "Frost", "Prism", "Prism Rotation", "Blade 1", "Blade 2"),
        "ALL" to listOf("Dimmer", "Pan", "Tilt", "Zoom", "Focus", "Iris", "Speed", "Size", "Phase", "Width", "Red", "Green", "Blue")
    )
    var group by remember { mutableStateOf("DIMMER") }
    var command by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("PROGRAMMER / VALUE CONTROL", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Atrybuty • encodery • pozycja • kolor", color = Muted, fontSize = 10.sp)
            }
            Text("OSC", color = Blue, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            groups.forEach { item ->
                Button(onClick = { group = item }, modifier = Modifier.weight(1f).height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (group == item) Blue else Panel),
                    contentPadding = PaddingValues(horizontal = 1.dp)) {
                    Text(item, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (group == "POSITION") {
            Text("POSITION CONTROL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueEncoderCard("Pan", onSend, Modifier.weight(1f), -270f..270f)
                ValueEncoderCard("Tilt", onSend, Modifier.weight(1f), -135f..135f)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Pan", "Tilt").forEach { axis ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(axis.uppercase() + " NUDGE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(-10, -1, 1, 10).forEach { step ->
                                OutlinedButton(onClick = { onSend("Attribute \"$axis\" At Relative $step") },
                                    modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text(if (step > 0) "+$step" else step.toString(), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 4.dp)) {
                items(attributes[group].orEmpty()) { attribute -> ValueEncoderCard(attribute, onSend) }
            }
        }
        Text("COMMAND LINE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = command, onValueChange = { command = it }, modifier = Modifier.weight(1f),
                singleLine = true, placeholder = { Text("Komenda grandMA3") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Blue, unfocusedBorderColor = Muted))
            Button(onClick = { if (command.isNotBlank()) { onSend(command.trim()); command = "" } },
                modifier = Modifier.height(54.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("SEND") }
        }
        Text("TX: $commandLog", color = Muted, fontSize = 9.sp, maxLines = 1)
        Text("RX: $feedbackLog", color = TextMain, fontSize = 9.sp, maxLines = 2)
        Text("RX pokazuje surowy odebrany komunikat OSC. Pełna synchronizacja executorów wymaga mapowania adresów feedback skonfigurowanych w grandMA3.", color = Muted, fontSize = 8.sp)
    }
}

@Composable
private fun ValueEncoderCard(
    attribute: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..100f
) {
    var value by remember(attribute) { mutableFloatStateOf(if (attribute == "Dimmer") 0f else if (range.start < 0f) 0f else 50f) }
    Card(modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(attribute.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (range.start < 0f) "%.1f".format(value) else value.toInt().toString(),
                color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Slider(value = value, onValueChange = { value = it }, valueRange = range,
                onValueChangeFinished = { onSend("Attribute \"$attribute\" At ${"%.1f".format(value)}") },
                modifier = Modifier.height(30.dp), colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { value = (value - 5f).coerceAtLeast(range.start); onSend("Attribute \"$attribute\" At ${"%.1f".format(value)}") },
                    contentPadding = PaddingValues(horizontal = 3.dp)) { Text("−5", color = TextMain, fontSize = 10.sp) }
                TextButton(onClick = { value = 0f.coerceIn(range.start, range.endInclusive); onSend("Attribute \"$attribute\" At ${"%.1f".format(value)}") },
                    contentPadding = PaddingValues(horizontal = 3.dp)) { Text("ZERO", color = Muted, fontSize = 9.sp) }
                TextButton(onClick = { value = (value + 5f).coerceAtMost(range.endInclusive); onSend("Attribute \"$attribute\" At ${"%.1f".format(value)}") },
                    contentPadding = PaddingValues(horizontal = 3.dp)) { Text("+5", color = TextMain, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(host: String, setHost: (String) -> Unit, oscPort: String, setOscPort: (String) -> Unit,
    listenPort: String, setListenPort: (String) -> Unit, webPort: String, setWebPort: (String) -> Unit, oscPath: String, setOscPath: (String) -> Unit,
    onSave: () -> Unit, onTest: () -> Unit, commandLog: String, feedbackLog: String) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("POŁĄCZENIE Z KONSOLĄ", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Telefon i grandMA3 muszą być w tej samej sieci lokalnej Wi-Fi/LAN.", color = Muted, fontSize = 12.sp)
        ConfigField("ADRES IP GRANDMA3", host, setHost)
        ConfigField("PORT OSC WYJŚCIOWY (UDP)", oscPort, setOscPort)
        ConfigField("PORT NASŁUCHU FEEDBACK (UDP)", listenPort, setListenPort)
        ConfigField("PORT WEB REMOTE", webPort, setWebPort)
        ConfigField("ADRES OSC", oscPath, setOscPath)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("ZAPISZ USTAWIENIA") }
        OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("WYŚLIJ TEST OSC") }
        Text(commandLog, color = Muted, fontSize = 11.sp)
        Text("OSTATNI FEEDBACK Z GRANDMA3", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(feedbackLog, color = TextMain, fontSize = 11.sp)
        Text("W grandMA3 ustaw OSC Output: Enable Output, Send = Yes, Destination IP = adres telefonu, Port = port nasłuchu powyżej. Odbieranie danych nie oznacza jeszcze synchronizacji nazw executorów.", color = Muted, fontSize = 11.sp)
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
                val initialUrl = "http://$host:$port"
                tag = initialUrl
                loadUrl(initialUrl)
            }
        }, update = { view ->
            val target = "http://$host:$port"
            if (view.tag != target) {
                view.tag = target
                view.loadUrl(target)
            }
        })
        if (error) Text("Nie udało się otworzyć Web Remote. Sprawdź IP, port i sieć.", color = Color(0xFFFF8B8B), fontSize = 11.sp)
    }
}

private val sender = Executors.newSingleThreadExecutor()
private val mainHandler = Handler(Looper.getMainLooper())

private fun sendOsc(host: String, port: Int, path: String, command: String, callback: (Boolean, String) -> Unit) {
    sender.execute {
        try {
            require(path.startsWith("/")) { "Adres OSC musi zaczynać się od /" }
            val packetBytes = oscMessage(path, command)
            DatagramSocket().use { socket ->
                val address = InetAddress.getByName(host)
                socket.send(DatagramPacket(packetBytes, packetBytes.size, address, port))
            }
            mainHandler.post { callback(true, "Wysłano OSC: $command") }
        } catch (e: Exception) {
            mainHandler.post { callback(false, "Błąd wysyłania: ${e.message ?: "sprawdź IP i port"}") }
        }
    }
}

private fun decodeOscPacket(data: ByteArray, length: Int): String {
    fun readOscString(start: Int): Pair<String, Int> {
        if (start >= length) return "" to length
        var end = start
        while (end < length && data[end].toInt() != 0) end++
        val value = String(data, start, end - start, Charsets.UTF_8)
        return value to (((end + 4) / 4) * 4).coerceAtMost(length)
    }
    return try {
        val (address, afterAddress) = readOscString(0)
        val (tags, afterTags) = readOscString(afterAddress)
        val values = mutableListOf<String>()
        var offset = afterTags
        val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
        for (tag in tags.drop(1)) when (tag) {
            'i' -> if (offset + 4 <= length) { values += buffer.getInt(offset).toString(); offset += 4 }
            'f' -> if (offset + 4 <= length) { values += "%.1f".format(buffer.getFloat(offset)); offset += 4 }
            's' -> { val (v, next) = readOscString(offset); values += v; offset = next }
            'T' -> values += "true"
            'F' -> values += "false"
        }
        if (values.isEmpty()) "$address $tags" else "$address  ${values.joinToString(", ")}"
    } catch (_: Exception) { "Odebrano pakiet OSC (${length} B)" }
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
