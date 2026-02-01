package com.example.hostextractor

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.net.URLEncoder

// --- Settings Manager ---
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var sni: String get() = prefs.getString("sni", "") ?: ""; set(value) = prefs.edit().putString("sni", value).apply()
    var path: String get() = prefs.getString("path", "") ?: ""; set(value) = prefs.edit().putString("path", value).apply()
    var uuid: String get() = prefs.getString("uuid", "") ?: ""; set(value) = prefs.edit().putString("uuid", value).apply()
}

data class DisplayResult(
    val host: String,
    val latency: Long,
    val jitter: Long,
    val successCount: Int,
    val status: String,
    val workingPorts: List<Int>,
    val isSuccess: Boolean,
    var speed: MutableState<String> = mutableStateOf(""),
    var testProgress: MutableState<Float> = mutableStateOf(0f)
)

data class V2Config(val address: String, val port: Int, val path: String, val sni: String)

object ConnectionState {
    var connectedHost = mutableStateOf<String?>(null)
}

class ScanService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = intent?.getIntExtra("CURRENT", 0) ?: 0
        val total = intent?.getIntExtra("TOTAL", 0) ?: 0
        val channelId = "scanner_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Advanced Scanner", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Scanner Running...")
            .setContentText("Progress: $current / $total")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(total, current, false).setOngoing(true).build()
        startForeground(101, notification)
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

val CF_IPV4_RANGES = listOf("104.16.0.0/20", "172.64.0.0/20", "108.162.192.0/20", "162.158.0.0/20", "173.245.48.0/20", "188.114.96.0/20", "190.93.240.0/20", "197.234.240.0/22", "198.41.128.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22")

class MainActivity : ComponentActivity() {
    val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.fillMaxWidth().statusBarsPadding().background(Color.Black))
                        Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                            MainTabScreen(activityScope)
                        }
                    }
                }
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); activityScope.cancel() }
}

@Composable
fun MainTabScreen(activityScope: CoroutineScope) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var configInput by rememberSaveable { mutableStateOf("") }
    var configResults by remember { mutableStateOf<List<DisplayResult>>(emptyList()) }
    var ipInput by rememberSaveable { mutableStateOf("") }
    var ipResults by remember { mutableStateOf<List<DisplayResult>>(emptyList()) }

    Column {
        TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF1E1E1E), contentColor = Color.White) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("INPUT", fontSize = 10.sp) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("CONFIG", fontSize = 10.sp) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("IP SCAN", fontSize = 10.sp) })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("HELP", fontSize = 10.sp) })
        }
        when (selectedTab) {
            0 -> InputTab(settings)
            1 -> ConfigScannerTab(settings, configInput, configResults, { configInput = it }, { configResults = it }, activityScope)
            2 -> IpScannerTab(settings, ipInput, ipResults, { ipInput = it }, { ipResults = it }, activityScope)
            3 -> HelpTab()
        }
    }
}

@Composable
fun HelpTab() {
    var isFarsi by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isFarsi) "راهنمای کارکرد" else "Operation Guide", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Button(onClick = { isFarsi = !isFarsi }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))) {
                Text(if (isFarsi) "English" else "فارسی", fontSize = 12.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            Column(Modifier.padding(16.dp)) {
                if (isFarsi) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text("• تنظیمات: مشخصات سرور را در تب Input وارد نمایید.\n• تحلیل: تست‌ها تا لایه ۷ و با هندشیک کامل انجام می‌شوند.", color = Color.White, lineHeight = 24.sp, fontSize = 14.sp)
                    }
                } else {
                    Text("• Settings: Set server details in Input.\n• Analysis: L7 Handshake based testing.", color = Color.White, lineHeight = 22.sp, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if (isFarsi) "جدول تحلیل کیفیت" else "Quality Analysis Table", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.border(1.dp, Color.Gray).padding(8.dp)) {
            val rows = listOf(
                Triple("GAMING", "Latency < 180ms & Jitter < 60", Color(0xFF00E676)),
                Triple("STREAM", "Latency < 300ms & Stable", Color(0xFF00B0FF)),
                Triple("AVERAGE", "Loss 5% to 30% or Unstable", Color(0xFFFFD600)),
                Triple("LOSS", "Loss > 30% or Timed Out", Color(0xFFFF5252))
            )
            rows.forEach { (label, desc, color) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(85.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(desc, color = Color.LightGray, fontSize = 12.sp)
                }
                Divider(color = Color.Gray.copy(alpha = 0.2f))
            }
        }
        Spacer(Modifier.height(16.dp))
        if(isFarsi) {
            Text("کاربرد:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• GAMING: بازی آنلاین و مکالمه تصویری\n• STREAM: یوتیوب، اینستاگرام و وب‌گردی\n• AVERAGE: استفاده معمولی با احتمال لگ\n• LOSS: قطعی کامل و غیرقابل استفاده", color = Color.Gray, fontSize = 12.sp, lineHeight = 20.sp)
        } else {
            Text("Use Case:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• GAMING: Online Gaming & Video Calls\n• STREAM: YouTube, Instagram & Browsing\n• AVERAGE: Standard use with possible lag\n• LOSS: High connection drop (Unusable)", color = Color.Gray, fontSize = 12.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun InputTab(settings: SettingsManager) {
    var sni by remember { mutableStateOf(settings.sni) }; var path by remember { mutableStateOf(settings.path) }; var uuid by remember { mutableStateOf(settings.uuid) }
    val context = LocalContext.current
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Scanner Configuration", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = sni, onValueChange = { sni = it }, label = { Text("SNI") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Path") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); OutlinedTextField(value = uuid, onValueChange = { uuid = it }, label = { Text("UUID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = { settings.sni = sni.trim(); settings.path = path.trim(); settings.uuid = uuid.trim(); Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("SAVE SETTINGS") }
    }
}

@Composable
fun ConfigScannerTab(settings: SettingsManager, input: String, results: List<DisplayResult>, onInputChange: (String) -> Unit, onResultsChange: (List<DisplayResult>) -> Unit, activityScope: CoroutineScope) {
    val context = LocalContext.current; val clipboard = LocalClipboardManager.current
    var isTesting by remember { mutableStateOf(false) }; var isDeepTesting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }; var deepProgress by remember { mutableFloatStateOf(0f) }
    var summary by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }; var deepJob by remember { mutableStateOf<Job?>(null) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Box {
            OutlinedTextField(value = input, onValueChange = onInputChange, label = { Text("Configs...") }, modifier = Modifier.fillMaxWidth().height(140.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { clipboard.getText()?.let { onInputChange(it.text) } }) { Icon(Icons.Default.ContentPaste, null, tint = Color.Cyan) }
                IconButton(onClick = { onInputChange("") }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }
        }
        if (isTesting) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color.Cyan)
        if (summary.isNotEmpty()) Text(summary, color = Color(0xFFFFC107), fontSize = 12.sp)

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isTesting) {
                Button(onClick = {
                    val addresses = extractAddressesOnly(input); if (addresses.isEmpty() || settings.sni.isBlank()) return@Button
                    isTesting = true; onResultsChange(emptyList())
                    scanJob = activityScope.launch(Dispatchers.Default) { runUnifiedParallelScan(context, addresses, settings, { temp, prog -> onResultsChange(temp); progress = prog }, { final -> isTesting = false; summary = generateRangeReport(final) }) }
                }, modifier = Modifier.weight(1f)) { Text("START") }
            } else {
                Button(onClick = { scanJob?.cancel(); isTesting = false; context.stopService(Intent(context, ScanService::class.java)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("STOP") }
            }
            Button(onClick = { val greens = results.filter { it.isSuccess }.joinToString("\n") { buildVlessConfig(it, settings) }; clipboard.setText(AnnotatedString(greens)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("COPY ALL") }
        }

        if (!isTesting && results.any { it.isSuccess }) {
            Column {
                if (!isDeepTesting) {
                    Button(onClick = {
                        isDeepTesting = true; deepProgress = 0.01f
                        deepJob = activityScope.launch(Dispatchers.Default) {
                            val successes = results.filter { it.isSuccess }
                            val semaphore = Semaphore(20)
                            var completed = 0
                            successes.forEach { res -> launch { semaphore.withPermit { runQualityTest(res, settings) }; completed++; deepProgress = completed.toFloat() / successes.size; if (completed == successes.size) isDeepTesting = false } }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) {
                        Icon(Icons.Default.Speed, null); Spacer(Modifier.width(8.dp)); Text("SPEED TEST ALL SUCCESS")
                    }
                } else {
                    Button(onClick = { deepJob?.cancel(); isDeepTesting = false; deepProgress = 0f; results.forEach { it.testProgress.value = 0f; if(it.speed.value.contains("Analyzing")) it.speed.value = "" } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("STOP SPEED TEST")
                    }
                    LinearProgressIndicator(progress = { deepProgress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color.Yellow)
                }
            }
        }
        results.forEach { ResultRow(it, settings, activityScope) }
    }
}

@Composable
fun IpScannerTab(settings: SettingsManager, input: String, results: List<DisplayResult>, onInputChange: (String) -> Unit, onResultsChange: (List<DisplayResult>) -> Unit, activityScope: CoroutineScope) {
    val context = LocalContext.current; val clipboard = LocalClipboardManager.current
    var isTesting by remember { mutableStateOf(false) }; var isDeepTesting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }; var deepProgress by remember { mutableFloatStateOf(0f) }
    var summary by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }; var deepJob by remember { mutableStateOf<Job?>(null) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Button(onClick = { onInputChange(CF_IPV4_RANGES.joinToString("\n")) }, modifier = Modifier.fillMaxWidth()) { Text("Load Default CF Ranges") }
        Box {
            OutlinedTextField(value = input, onValueChange = onInputChange, label = { Text("IPs...") }, modifier = Modifier.fillMaxWidth().height(140.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { clipboard.getText()?.let { onInputChange(it.text) } }) { Icon(Icons.Default.ContentPaste, null, tint = Color.Cyan) }
                IconButton(onClick = { onInputChange("") }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }
        }
        if (isTesting) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color.Cyan)

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isTesting) {
                Button(onClick = {
                    if (settings.sni.isBlank()) return@Button
                    isTesting = true; onResultsChange(emptyList())
                    scanJob = activityScope.launch(Dispatchers.Default) {
                        val ips = input.lines().filter { it.isNotBlank() }.flatMap { if (it.contains("/")) generateFullIpsFromRange(it.trim()) else listOf(it.trim()) }.distinct()
                        runUnifiedParallelScan(context, ips, settings, { temp, prog -> onResultsChange(temp); progress = prog }, { final -> isTesting = false; summary = generateRangeReport(final) })
                    }
                }, modifier = Modifier.weight(1f)) { Text("SCAN IPs") }
            } else {
                Button(onClick = { scanJob?.cancel(); isTesting = false; context.stopService(Intent(context, ScanService::class.java)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("STOP") }
            }
            Button(onClick = { val greens = results.filter { it.isSuccess }.joinToString("\n") { buildVlessConfig(it, settings) }; clipboard.setText(AnnotatedString(greens)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("COPY ALL") }
        }

        if (!isTesting && results.any { it.isSuccess }) {
            Column {
                if (!isDeepTesting) {
                    Button(onClick = {
                        isDeepTesting = true; deepProgress = 0.01f
                        deepJob = activityScope.launch(Dispatchers.Default) {
                            val successes = results.filter { it.isSuccess }
                            val semaphore = Semaphore(20)
                            var completed = 0
                            successes.forEach { res -> launch { semaphore.withPermit { runQualityTest(res, settings) }; completed++; deepProgress = completed.toFloat() / successes.size; if (completed == successes.size) isDeepTesting = false } }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) {
                        Icon(Icons.Default.Speed, null); Spacer(Modifier.width(8.dp)); Text("SPEED TEST ALL SUCCESS")
                    }
                } else {
                    Button(onClick = { deepJob?.cancel(); isDeepTesting = false; deepProgress = 0f; results.forEach { it.testProgress.value = 0f; if(it.speed.value.contains("Analyzing")) it.speed.value = "" } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("STOP SPEED TEST")
                    }
                    LinearProgressIndicator(progress = { deepProgress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color.Yellow)
                }
            }
        }
        results.forEach { ResultRow(it, settings, activityScope) }
    }
}

suspend fun CoroutineScope.runUnifiedParallelScan(context: Context, targets: List<String>, settings: SettingsManager, onUpdate: (List<DisplayResult>, Float) -> Unit, onComplete: (List<DisplayResult>) -> Unit) {
    val totalCount = targets.size; if (totalCount == 0) return
    val serviceIntent = Intent(context, ScanService::class.java).apply { putExtra("TOTAL", totalCount) }
    context.startForegroundService(serviceIntent)
    val temp = mutableListOf<DisplayResult>(); val semaphore = Semaphore(100); var completedCount = 0; var lastUiUpdateTime = 0L
    targets.forEach { ip ->
        if (!isActive) return@runUnifiedParallelScan
        launch {
            try {
                semaphore.withPermit {
                    val workingPorts = mutableListOf<Int>(); val portLatencies = mutableMapOf<Int, Long>()
                    for (p in listOf(443, 2053, 8443, 2096)) {
                        val lat = withContext(Dispatchers.IO) { performFullHandshake(V2Config(ip, p, settings.path, settings.sni)) }
                        if (lat > 0) { workingPorts.add(p); portLatencies[p] = lat }
                    }
                    if (workingPorts.isNotEmpty()) {
                        val bestPort = portLatencies.minByOrNull { it.value }?.key ?: workingPorts[0]
                        val lats = mutableListOf<Long>().apply { add(portLatencies[bestPort]!!) }
                        for (i in 1..4) { delay(50); val l = withContext(Dispatchers.IO) { performFullHandshake(V2Config(ip, bestPort, settings.path, settings.sni)) }; if (l > 0) lats.add(l) }
                        val finalLatency = lats.average().toLong()
                        val finalJitter = (lats.maxOrNull() ?: 0L) - (lats.minOrNull() ?: 0L)
                        val status = when { lats.size >= 5 && finalJitter < 150 -> "STABLE"; lats.size >= 3 && finalJitter < 400 -> "MODERATE"; else -> "UNSTABLE" }
                        synchronized(temp) { temp.add(DisplayResult(ip, finalLatency, finalJitter, lats.size, status, workingPorts, true)) }
                    } else synchronized(temp) { temp.add(DisplayResult(ip, 0, 0, 0, "FAILED", emptyList(), false)) }
                }
            } catch (e: Exception) {}
            completedCount++
            val now = System.currentTimeMillis()
            if (now - lastUiUpdateTime > 350 || completedCount == totalCount) {
                lastUiUpdateTime = now
                val sorted = synchronized(temp) { temp.toList().sortedWith(compareByDescending<DisplayResult> { it.status == "STABLE" }.thenByDescending { it.status == "MODERATE" }.thenBy { if (it.isSuccess) it.latency else Long.MAX_VALUE }) }
                onUpdate(sorted, completedCount.toFloat() / totalCount)
            }
            if (completedCount % 10 == 0) context.startForegroundService(Intent(context, ScanService::class.java).apply { putExtra("CURRENT", completedCount); putExtra("TOTAL", totalCount) })
            if (completedCount == totalCount) {
                val top10 = synchronized(temp) { temp.filter { it.isSuccess }.sortedBy { it.latency }.take(10) }
                top10.forEach { res -> launch { runQualityTest(res, settings, isAuto = true) } }
                onComplete(temp); context.stopService(serviceIntent)
            }
        }
    }
}

private fun performFullHandshake(config: V2Config): Long {
    val socket = Socket()
    return try {
        val start = System.currentTimeMillis()
        socket.connect(InetSocketAddress(config.address, config.port), 8000)
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, config.address, config.port, true) as SSLSocket
        ssl.sslParameters = SSLParameters().apply { serverNames = listOf(SNIHostName(config.sni)); applicationProtocols = arrayOf("http/1.1") }
        ssl.soTimeout = 8000; ssl.startHandshake()
        val req = "GET ${config.path} HTTP/1.1\r\nHost: ${config.sni}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
        ssl.outputStream.write(req.toByteArray())
        val isOk = ssl.inputStream.bufferedReader().readLine()?.contains("101") == true
        ssl.close()
        if (isOk) System.currentTimeMillis() - start else -1L
    } catch (e: Exception) { -1L } finally { try { socket.close() } catch (e: Exception) {} }
}

suspend fun runQualityTest(res: DisplayResult, settings: SettingsManager, isAuto: Boolean = false) {
    res.speed.value = "Analyzing Quality..."; res.testProgress.value = 0.01f
    val port = res.workingPorts.firstOrNull() ?: 443; val allLatencies = mutableListOf<Long>(); val totalTests = 100; val batchSize = 10
    withContext(Dispatchers.Default) {
        for (b in 0 until (totalTests / batchSize)) {
            if (!isActive) break
            val batchResults = (1..batchSize).map { async(Dispatchers.IO) { performFullHandshake(V2Config(res.host, port, settings.path, settings.sni)) } }.awaitAll()
            allLatencies.addAll(batchResults.filter { it > 0 }); res.testProgress.value = ((b + 1) * batchSize).toFloat() / totalTests; delay(100)
        }
    }
    if (allLatencies.isEmpty()) res.speed.value = "LOSS > 30% (100% Packet Loss)" else {
        val avg = allLatencies.average().toInt(); val loss = ((totalTests - allLatencies.size) * 100) / totalTests; val jitter = (allLatencies.maxOrNull() ?: 0L) - (allLatencies.minOrNull() ?: 0L)
        res.speed.value = when {
            loss > 30 -> "LOSS > 30% ($loss% Loss)"
            loss > 5 -> "AVERAGE (Unstable / $loss% Loss)"
            avg < 180 && jitter < 60 -> "GAMING < 180ms (${avg}ms / J:${jitter})"
            else -> "STREAM < 300ms (${avg}ms)"
        }
    }
    delay(1000); res.testProgress.value = 0f
}

@Composable
fun ResultRow(res: DisplayResult, settings: SettingsManager, activityScope: CoroutineScope) {
    val context = LocalContext.current; val clipboard = LocalClipboardManager.current; val isThisActive = ConnectionState.connectedHost.value == res.host
    val speedColor = when {
        res.speed.value.contains("GAMING") -> Color(0xFF00E676)
        res.speed.value.contains("STREAM") -> Color(0xFF00B0FF)
        res.speed.value.contains("AVERAGE") -> Color(0xFFFFD600)
        res.speed.value.contains("LOSS") -> Color(0xFFFF5252)
        else -> Color.Yellow
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (isThisActive) Color(0xFF004D40) else if (res.isSuccess) Color(0xFF1E1E1E) else Color(0xFFB71C1C).copy(alpha = 0.1f))) {
        Column {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(res.host, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (res.isSuccess) "${res.status} | ${res.latency}ms" else "FAILED", color = if(res.isSuccess) Color.Cyan else Color.Gray, fontSize = 11.sp)
                    if (res.speed.value.isNotEmpty()) Text(res.speed.value, color = speedColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (res.isSuccess) {
                    IconButton(onClick = { activityScope.launch { runQualityTest(res, settings) } }) { Icon(Icons.Default.Speed, null, tint = Color.Yellow, modifier = Modifier.size(20.dp)) }
                    Button(onClick = { if (isThisActive) ConnectionState.connectedHost.value = null else { ConnectionState.connectedHost.value = res.host; copyAndOpenNetMod(context, buildVlessConfig(res, settings)) } }, colors = ButtonDefaults.buttonColors(containerColor = if (isThisActive) Color.Red else Color(0xFF2E7D32)), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if(isThisActive) "STOP" else "CONN", fontSize = 10.sp) }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(buildVlessConfig(res, settings))); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            if (res.testProgress.value > 0f) LinearProgressIndicator(progress = { res.testProgress.value }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Color.Yellow, trackColor = Color.Transparent)
        }
    }
}

private fun buildVlessConfig(res: DisplayResult, settings: SettingsManager): String { val p = res.workingPorts.firstOrNull() ?: 443; return "vless://${settings.uuid}@${res.host}:$p?encryption=none&flow=none&type=ws&host=${settings.sni}&headerType=none&path=${URLEncoder.encode(settings.path, "UTF-8")}&security=tls&fp=chrome&sni=${settings.sni}#${res.status}-${res.host}" }
private fun copyAndOpenNetMod(context: Context, config: String) { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("V2", config)); try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config)).apply { setPackage("com.netmod.syna"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (e: Exception) { context.packageManager.getLaunchIntentForPackage("com.netmod.syna")?.let { context.startActivity(it) } } }
private fun generateFullIpsFromRange(cidr: String): List<String> = try { val pts = cidr.split("/"); val base = pts[0]; val pref = pts[1].toInt().coerceIn(20, 32); val num = (1L shl (32 - pref)).coerceAtMost(2000); val ipL = base.split(".").map { it.toLong() }.let { (it[0] shl 24) or (it[1] shl 16) or (it[2] shl 8) or it[3] }; (0 until num).map { i -> val c = ipL + i; "${(c shr 24) and 0xFF}.${(c shr 16) and 0xFF}.${(c shr 8) and 0xFF}.${c and 0xFF}" } } catch (e: Exception) { listOf(cidr) }
private fun extractAddressesOnly(input: String) = Regex("""(?:vless|trojan)://[^@]*@([^:/?#\s]+)""").findAll(input).map { it.groupValues[1] }.toList().distinct()
private fun generateRangeReport(results: List<DisplayResult>): String { val b = results.filter { it.isSuccess }.groupBy { it.host.substringBeforeLast(".") + ".x" }.maxByOrNull { it.value.size }; return if (b != null) "Best Range: ${b.key} (${b.value.size} IPs)" else "" }