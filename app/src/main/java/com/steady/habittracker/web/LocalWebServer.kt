package com.steady.habittracker.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.util.Log
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureItem
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.LocalWebPrefs
import com.steady.habittracker.data.MotivationalQuotes
import com.steady.habittracker.data.PathAlignmentCheck
import com.steady.habittracker.data.inboxCaptures
import com.steady.habittracker.data.journalCaptures
import com.steady.habittracker.data.withAddedCapture
import com.steady.habittracker.data.withAddedHabit
import com.steady.habittracker.data.withAddedPathCheck
import com.steady.habittracker.data.withArchivedHabit
import com.steady.habittracker.data.withPomodoroPrefs
import com.steady.habittracker.data.withRemovedEntry
import com.steady.habittracker.data.withUpdatedCapture
import com.steady.habittracker.data.withUpdatedEntry
import com.steady.habittracker.data.withUpdatedGoal
import com.steady.habittracker.data.withoutCapture
import com.steady.habittracker.extensions.ExtensionManager
import com.steady.habittracker.widget.WidgetRenderer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import kotlin.concurrent.thread

/**
 * LAN web server for desktop access.
 * - HTTP on [LocalWebPrefs.port] (default 8787), bound to 0.0.0.0
 * - Optional HTTPS on port+1 with bundled self-signed cert
 * - SPA from assets/web/index.html + JSON APIs covering app features
 */
object LocalWebServer {
    private const val TAG = "SteadyWeb"
    private const val KEYSTORE_ASSET = "steady_local.p12"
    private const val KEYSTORE_PASS = "steady-local"
    private const val WEB_ASSET_DIR = "web"

    private val lock = Any()
    private val running = AtomicBoolean(false)
    private val httpSocket = AtomicReference<ServerSocket?>(null)
    private val httpsSocket = AtomicReference<ServerSocket?>(null)
    private val prefsRef = AtomicReference(LocalWebPrefs())
    private var appContext: Context? = null

    @Volatile
    var statusMessage: String = "Stopped"
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var boundHttpPort: Int = 0
        private set

    @Volatile
    var boundHttpsPort: Int = 0
        private set

    @Volatile
    var autoOffDeadlineMs: Long = 0L
        private set

    @Volatile
    var currentSsid: String? = null

    fun isRunning(): Boolean = running.get()
    fun currentPrefs(): LocalWebPrefs? = prefsRef.get()
    fun rememberPrefs(prefs: LocalWebPrefs) {
        prefsRef.set(prefs)
    }

    fun setAutoOffDeadline(epochMs: Long) {
        autoOffDeadlineMs = epochMs
    }

    fun autoOffRemainingLabel(): String? {
        val d = autoOffDeadlineMs
        if (d <= 0L) return null
        val left = d - System.currentTimeMillis()
        if (left <= 0L) return "now"
        val mins = (left / 60_000L).toInt()
        val secs = ((left % 60_000L) / 1000L).toInt()
        return if (mins >= 60) {
            val h = mins / 60
            val m = mins % 60
            "${h}h ${m}m"
        } else if (mins > 0) {
            "${mins}m ${secs}s"
        } else {
            "${secs}s"
        }
    }

    fun markFailed(message: String) {
        lastError = message
        statusMessage = "Failed: $message"
        running.set(false)
    }

    fun setEnabled(context: Context, data: AppData) {
        appContext = context.applicationContext
        val prefs = data.localWebPrefs
        prefsRef.set(prefs)
        if (prefs.enabled) {
            val withDeadline = if (prefs.autoOffAtEpochMs <= 0L) {
                val mins = prefs.effectiveAutoOffMinutes(WifiWebMonitor.isOnTrustedWifi(context, prefs))
                if (mins > 0) {
                    prefs.copy(autoOffAtEpochMs = System.currentTimeMillis() + mins * 60_000L)
                } else {
                    prefs
                }
            } else {
                prefs
            }
            prefsRef.set(withDeadline)
            LocalWebService.start(context.applicationContext, withDeadline)
        } else {
            LocalWebService.stop(context.applicationContext)
            stop()
        }
    }

    fun start(context: Context, prefs: LocalWebPrefs = prefsRef.get()): Boolean {
        synchronized(lock) {
            appContext = context.applicationContext
            prefsRef.set(prefs)
            if (running.get() &&
                boundHttpPort == prefs.port.coerceIn(1024, 65534) &&
                httpSocket.get()?.isClosed == false
            ) {
                statusMessage = buildStatus(boundHttpsPort > 0)
                Log.i(TAG, "Already running · $statusMessage")
                return true
            }
            stopInternal()

            val httpPort = prefs.port.coerceIn(1024, 65534)
            val httpsPort = (httpPort + 1).coerceAtMost(65535)
            lastError = null

            return try {
                val http = ServerSocket()
                http.reuseAddress = true
                http.bind(InetSocketAddress("0.0.0.0", httpPort))
                httpSocket.set(http)
                boundHttpPort = http.localPort

                var httpsOk = false
                if (prefs.httpsEnabled) {
                    try {
                        val factory = sslServerSocketFactory(context)
                        if (factory != null) {
                            val https = factory.createServerSocket() as SSLServerSocket
                            https.reuseAddress = true
                            https.bind(InetSocketAddress("0.0.0.0", httpsPort))
                            https.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
                            httpsSocket.set(https)
                            boundHttpsPort = https.localPort
                            httpsOk = true
                        } else {
                            Log.w(TAG, "HTTPS keystore missing; HTTP only")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "HTTPS bind failed (HTTP still up)", e)
                        boundHttpsPort = 0
                    }
                } else {
                    boundHttpsPort = 0
                }

                running.set(true)
                statusMessage = buildStatus(httpsOk)

                thread(name = "steady-web-http", isDaemon = true) {
                    acceptLoop(http, "http")
                }
                if (httpsOk) {
                    val hs = httpsSocket.get()
                    if (hs != null) {
                        thread(name = "steady-web-https", isDaemon = true) {
                            acceptLoop(hs, "https")
                        }
                    }
                }

                Log.i(TAG, statusMessage)
                true
            } catch (e: Exception) {
                Log.e(TAG, "HTTP bind failed on $httpPort", e)
                lastError = e.message ?: e.javaClass.simpleName
                statusMessage = "Failed: $lastError"
                running.set(false)
                stopInternal()
                false
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            stopInternal()
            statusMessage = "Stopped"
            boundHttpPort = 0
            boundHttpsPort = 0
            autoOffDeadlineMs = 0L
        }
    }

    private fun stopInternal() {
        running.set(false)
        try {
            httpSocket.getAndSet(null)?.close()
        } catch (_: Exception) {
        }
        try {
            httpsSocket.getAndSet(null)?.close()
        } catch (_: Exception) {
        }
        try {
            Thread.sleep(80)
        } catch (_: InterruptedException) {
        }
    }

    private fun buildStatus(httpsOk: Boolean): String {
        val ips = lanIpv4Addresses()
        val ipHint = ips.firstOrNull() ?: "127.0.0.1"
        return buildString {
            append("Listening · HTTP http://$ipHint:$boundHttpPort")
            if (httpsOk) append(" · HTTPS https://$ipHint:$boundHttpsPort (self-signed)")
        }
    }

    private fun acceptLoop(server: ServerSocket, label: String) {
        while (running.get() && !server.isClosed) {
            try {
                val client = server.accept()
                thread(name = "steady-web-client-$label", isDaemon = true) {
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.w(TAG, "accept ($label) error: ${e.message}")
                    if (label == "http") {
                        markFailed("Accept loop died: ${e.message}")
                        try {
                            server.close()
                        } catch (_: Exception) {
                        }
                    }
                }
                break
            }
        }
    }

    private fun sslServerSocketFactory(context: Context): SSLServerSocketFactory? {
        return try {
            val ks = KeyStore.getInstance("PKCS12")
            context.assets.open(KEYSTORE_ASSET).use { stream ->
                ks.load(stream, KEYSTORE_PASS.toCharArray())
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, KEYSTORE_PASS.toCharArray())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(kmf.keyManagers, null, null)
            ctx.serverSocketFactory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TLS keystore", e)
            null
        }
    }

    fun lanIpv4Addresses(): List<String> {
        val found = linkedSetOf<String>()
        try {
            val ctx = appContext
            if (ctx != null) {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val network = cm.activeNetwork
                    val caps = network?.let { cm.getNetworkCapabilities(it) }
                    val lp: LinkProperties? = network?.let { cm.getLinkProperties(it) }
                    val isWifiOrEth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                    if (isWifiOrEth && lp != null) {
                        lp.linkAddresses.forEach { la ->
                            val a = la.address
                            if (a is Inet4Address && !a.isLoopbackAddress) {
                                found.add(a.hostAddress ?: return@forEach)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        try {
            val ifaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (ni in ifaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in Collections.list(ni.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        found.add(addr.hostAddress ?: continue)
                    }
                }
            }
        } catch (_: Exception) {
        }

        try {
            val ctx = appContext
            if (ctx != null) {
                @Suppress("DEPRECATION")
                val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                val ipInt = wm?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    @Suppress("DEPRECATION")
                    val ip = android.text.format.Formatter.formatIpAddress(ipInt)
                    if (ip != "0.0.0.0") found.add(ip)
                }
            }
        } catch (_: Exception) {
        }

        return found.toList()
    }

    fun httpUrls(): List<String> {
        val port = if (boundHttpPort > 0) boundHttpPort else prefsRef.get().port
        val ips = lanIpv4Addresses().ifEmpty { listOf("127.0.0.1") }
        return ips.map { "http://$it:$port" } + "http://127.0.0.1:$port"
    }

    fun httpsUrls(): List<String> {
        if (boundHttpsPort <= 0) return emptyList()
        val ips = lanIpv4Addresses().ifEmpty { listOf("127.0.0.1") }
        return ips.map { "https://$it:$boundHttpsPort" } + "https://127.0.0.1:$boundHttpsPort"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 20_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val rawPath = parts.getOrNull(1) ?: "/"
            val path = rawPath.substringBefore("?")
            val query = rawPath.substringAfter("?", "")
            var contentLength = 0
            var pinHeader = ""
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val l = line!!
                if (l.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = l.substringAfter(":").trim().toIntOrNull() ?: 0
                } else if (l.startsWith("X-Steady-Pin:", ignoreCase = true)) {
                    pinHeader = l.substringAfter(":").trim()
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength.coerceAtMost(2_000_000))
                var read = 0
                while (read < contentLength && read < buf.size) {
                    val n = reader.read(buf, read, (contentLength - read).coerceAtMost(buf.size - read))
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else {
                ""
            }

            val out = socket.getOutputStream()
            val prefs = prefsRef.get()
            val pinRequired = prefs.pin.isNotBlank()

            // Public endpoints
            when {
                path == "/api/status" && method == "GET" -> {
                    writeResponse(
                        out, 200, "application/json",
                        JSONObject()
                            .put("running", running.get())
                            .put("httpPort", boundHttpPort)
                            .put("httpsPort", boundHttpsPort)
                            .put("status", statusMessage)
                            .put("pinRequired", pinRequired)
                            .put("autoOff", autoOffRemainingLabel() ?: JSONObject.NULL)
                            .put("ssid", currentSsid ?: JSONObject.NULL)
                            .toString()
                    )
                    return
                }
                path == "/api/auth" && method == "POST" -> {
                    writeResponse(out, 200, "application/json", apiAuth(body))
                    return
                }
                (path == "/" || path == "/index.html") && method == "GET" -> {
                    serveAsset(out, "index.html")
                    return
                }
                path.startsWith("/assets/") && method == "GET" -> {
                    serveAsset(out, path.removePrefix("/assets/"))
                    return
                }
            }

            if (pinRequired && path.startsWith("/api")) {
                val pinOk = pinMatches(prefs.pin, pinHeader, body, query)
                if (!pinOk) {
                    writeResponse(out, 401, "application/json", """{"error":"pin required"}""")
                    return
                }
            }

            when {
                path == "/api/summary" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiSummary())
                path == "/api/today" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiToday())
                path == "/api/habits" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiHabits())
                path == "/api/groups" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiGroups())
                path == "/api/history" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiHistory())
                path == "/api/captures" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiCaptures())
                path == "/api/path" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiPath())
                path == "/api/more" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiMore())
                path == "/api/pomodoro" && method == "GET" ->
                    writeResponse(out, 200, "application/json", apiPomodoro())
                path == "/api/quote" && method == "GET" -> {
                    val q = MotivationalQuotes.forToday()
                    writeResponse(
                        out, 200, "application/json",
                        JSONObject().put("text", q.text).put("attribution", q.attribution).toString()
                    )
                }
                path == "/api/log" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiLog(body))
                path == "/api/unlog" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiUnlog(body))
                path == "/api/skip" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiSkip(body))
                path == "/api/habit" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiCreateHabit(body))
                path == "/api/archive" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiArchiveHabit(body))
                path == "/api/capture" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiCapture(body))
                path == "/api/capture/done" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiCaptureDone(body))
                path == "/api/capture/delete" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiCaptureDelete(body))
                path == "/api/path/check" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiPathCheck(body))
                path == "/api/path/goal" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiPathGoal(body))
                path == "/api/pomodoro" && method == "POST" ->
                    writeResponse(out, 200, "application/json", apiPomodoroPost(body))
                else -> writeResponse(out, 404, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "client error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun pinMatches(expected: String, header: String, body: String, query: String): Boolean {
        if (expected.isBlank()) return true
        if (header == expected) return true
        if (query.contains("pin=$expected")) return true
        return try {
            JSONObject(body.ifBlank { "{}" }).optString("pin", "") == expected
        } catch (_: Exception) {
            body.contains("\"pin\":\"$expected\"")
        }
    }

    private fun serveAsset(out: OutputStream, name: String) {
        val ctx = appContext
        if (ctx == null) {
            writeResponse(out, 500, "text/plain", "No context")
            return
        }
        val safe = name.trimStart('/').replace("..", "")
        val assetPath = if (safe.startsWith(WEB_ASSET_DIR)) safe else "$WEB_ASSET_DIR/$safe"
        try {
            ctx.assets.open(assetPath).use { input ->
                val bytes = input.readBytes()
                val type = when {
                    safe.endsWith(".html") -> "text/html; charset=utf-8"
                    safe.endsWith(".js") -> "application/javascript; charset=utf-8"
                    safe.endsWith(".css") -> "text/css; charset=utf-8"
                    safe.endsWith(".json") -> "application/json"
                    safe.endsWith(".svg") -> "image/svg+xml"
                    safe.endsWith(".png") -> "image/png"
                    else -> "application/octet-stream"
                }
                writeBytes(out, 200, type, bytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "asset miss $assetPath: ${e.message}")
            writeResponse(out, 404, "text/plain", "Not found")
        }
    }

    private fun apiAuth(body: String): String {
        val prefs = prefsRef.get()
        return try {
            val pin = JSONObject(body.ifBlank { "{}" }).optString("pin", "")
            if (prefs.pin.isBlank() || pin == prefs.pin) {
                JSONObject().put("ok", true).toString()
            } else {
                JSONObject().put("ok", false).put("error", "Wrong PIN").toString()
            }
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "auth failed").toString()
        }
    }

    private fun loadData(): AppData? {
        val ctx = appContext ?: return null
        return try {
            runBlocking { AndroidHabitRepository(ctx).appDataFlow.first() }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveData(data: AppData) {
        val ctx = appContext ?: return
        runBlocking {
            val repo = AndroidHabitRepository(ctx)
            repo.saveData(data)
            WidgetRenderer.updateAll(ctx, data)
        }
    }

    private fun formatWhen(epoch: Long): String {
        if (epoch <= 0L) return ""
        return try {
            val z = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
            z.format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))
        } catch (_: Exception) {
            ""
        }
    }

    private fun summaryJson(data: AppData): JSONObject {
        val today = HabitDomain.getToday()
        val pts = HabitDomain.computeDayPoints(data, today)
        val life = HabitDomain.effectiveLifetimePoints(data, today)
        val level = HabitDomain.computeLevel(life)
        val due = HabitDomain.habitsDueOn(data, LocalDate.now())
        val entries = data.entries[today] ?: emptyMap()
        val done = due.count { h ->
            val e = entries[h.id]
            e != null && !e.skipped && e.value >= 0.5
        }
        val completion = if (due.isEmpty()) 1f else done.toFloat() / due.size
        return JSONObject()
            .put("date", today)
            .put("momentum", pts)
            .put("lifetimePoints", life)
            .put("level", level)
            .put("levelTitle", HabitDomain.levelTitle(level))
            .put("streak", HabitDomain.computeStreak(data))
            .put("pendingCaptures", data.inboxCaptures().size)
            .put("completion", completion)
            .put("completionLabel", "$done/${due.size} due")
    }

    private fun apiSummary(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        return summaryJson(data).toString()
    }

    /** Full-day timeline (all due habits, not only pending) for web undo support. */
    private fun apiToday(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        return try {
            val today = HabitDomain.getToday()
            val date = LocalDate.now()
            val entries = data.entries[today] ?: emptyMap()
            val due = HabitDomain.habitsDueOn(data, date)
            val byGroup = mutableMapOf<String, MutableList<Habit>>()
            due.forEach { h ->
                val gids = listOf(h.groupId) + h.additionalGroupIds
                gids.distinct().forEach { gid ->
                    byGroup.getOrPut(gid) { mutableListOf() }.add(h)
                }
            }
            val ordered = data.groups.filter { !it.archived }.sortedBy { it.order }
            val currentId = HabitDomain.resolveCurrentGroup(data)?.id
            val sections = JSONArray()
            ordered.forEach { g ->
                val habits = byGroup[g.id] ?: return@forEach
                if (habits.isEmpty()) return@forEach
                val unique = habits.distinctBy { it.id }
                val arr = JSONArray()
                var doneCount = 0
                unique.sortedBy { it.order }.forEach { h ->
                    val entry = entries[h.id]
                    val skipped = entry?.skipped == true
                    val done = !skipped && (entry?.value ?: 0.0) >= 0.5
                    if (done || skipped) doneCount++
                    val valueLabel = when {
                        entry == null -> ""
                        skipped -> "skipped"
                        h.type == HabitType.SCALE_1_5 -> entry.value.toInt().toString()
                        h.type == HabitType.COUNTER || h.type == HabitType.DURATION_MIN ->
                            "${entry.value.toInt()}${if (h.unit.isNotBlank()) " ${h.unit}" else ""}"
                        h.type == HabitType.NOTE -> entry.note.take(40)
                        else -> if (done) "done" else ""
                    }
                    arr.put(
                        JSONObject()
                            .put("id", h.id)
                            .put("name", h.name)
                            .put("description", h.description.ifBlank { h.why })
                            .put("icon", h.icon)
                            .put("type", h.type.name)
                            .put("extension", h.extensionType.name)
                            .put("canSkip", h.canSkip)
                            .put("target", h.target ?: JSONObject.NULL)
                            .put("unit", h.unit)
                            .put("done", done)
                            .put("skipped", skipped)
                            .put("value", entry?.value ?: 0.0)
                            .put("note", entry?.note ?: "")
                            .put("valueLabel", valueLabel)
                    )
                }
                sections.put(
                    JSONObject()
                        .put("group", g.name)
                        .put("groupIcon", g.icon)
                        .put("groupId", g.id)
                        .put("hint", g.timeHint)
                        .put("isNow", g.id == currentId)
                        .put("habits", arr)
                        .put("doneCount", doneCount)
                        .put("total", unique.size)
                )
            }
            val sum = summaryJson(data)
            sum.put("sections", sections)
            sum.put("captureTags", JSONArray(data.capturePrefs.visibleTags()))
            sum.put("defaultTags", JSONArray(data.capturePrefs.defaultTags))
            sum.put("capturePlaceholder", data.capturePrefs.placeholderTitle)
            sum.toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "load failed").toString()
        }
    }

    private fun apiPomodoro(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        val p = data.pomodoroPrefs
        return JSONObject()
            .put("workMin", p.workMin)
            .put("breakMin", p.breakMin)
            .put("sessionStartedAt", p.sessionStartedAt)
            .put("sessionIsBreak", p.sessionIsBreak)
            .toString()
    }

    private fun apiHabits(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        val today = HabitDomain.getToday()
        val arr = JSONArray()
        data.habits.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { h ->
            val entry = data.entries[today]?.get(h.id)
            val groupName = data.groups.find { it.id == h.groupId }?.name ?: h.groupId
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("name", h.name)
                    .put("description", h.description.ifBlank { h.why })
                    .put("groupId", h.groupId)
                    .put("groupName", groupName)
                    .put("type", h.type.name)
                    .put("extension", h.extensionType.name)
                    .put("icon", h.icon)
                    .put("doneToday", (entry?.value ?: 0.0) >= 0.5 && entry?.skipped != true)
            )
        }
        return JSONObject().put("habits", arr).toString()
    }

    private fun apiGroups(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        val arr = JSONArray()
        data.groups.filter { !it.archived }.sortedBy { it.order }.forEach { g ->
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("timeHint", g.timeHint)
                    .put("order", g.order)
                    .put("icon", g.icon)
            )
        }
        return JSONObject().put("groups", arr).toString()
    }

    private fun apiHistory(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        val today = LocalDate.now()
        val todayStr = today.toString()
        val days = JSONArray()
        for (i in 0 until 14) {
            val d = today.minusDays(i.toLong())
            val ds = d.toString()
            val pts = HabitDomain.computeDayPoints(data, ds)
            val due = HabitDomain.habitsDueOn(data, d)
            val entries = data.entries[ds] ?: emptyMap()
            val done = due.count { h ->
                val e = entries[h.id]
                e != null && !e.skipped && e.value >= 0.5
            }
            days.put(
                JSONObject()
                    .put("date", ds)
                    .put("points", pts)
                    .put("done", done)
                    .put("due", due.size)
            )
        }
        val heat = JSONArray()
        val heatDays = 16 * 7
        for (i in (heatDays - 1) downTo 0) {
            val d = today.minusDays(i.toLong())
            val ds = d.toString()
            val due = HabitDomain.habitsDueOn(data, d)
            val entries = data.entries[ds] ?: emptyMap()
            val v = if (due.isEmpty()) {
                null
            } else {
                due.count { h ->
                    val e = entries[h.id]
                    e != null && !e.skipped && e.value >= 0.5
                }.toFloat() / due.size
            }
            heat.put(JSONObject().put("date", ds).put("v", v ?: JSONObject.NULL))
        }
        val tags = JSONArray()
        data.tags.filter { !it.archived }.forEach { t ->
            tags.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("avg", HabitDomain.computeTag7DayAvg(data, t.id))
            )
        }
        val nights = JSONArray()
        data.sleepNights.sortedByDescending { it.startedAt }.take(7).forEach { n ->
            nights.put(
                JSONObject()
                    .put("wakeDate", n.wakeDate)
                    .put("quietScore", n.quietScore)
                    .put("eventCount", n.eventCount)
                    .put("snoreLikeCount", n.snoreLikeCount)
                    .put("loudMinutes", n.loudMinutes.toDouble())
            )
        }
        val life = HabitDomain.effectiveLifetimePoints(data, todayStr)
        val level = HabitDomain.computeLevel(life)
        return JSONObject()
            .put("streak", HabitDomain.computeStreak(data))
            .put("lifetimePoints", life)
            .put("todayPoints", HabitDomain.computeDayPoints(data, todayStr))
            .put("level", level)
            .put("levelTitle", HabitDomain.levelTitle(level))
            .put("days", days)
            .put("heatmap", heat)
            .put("heatmapDays", heatDays)
            .put("tags", tags)
            .put("sleepNights", nights)
            .toString()
    }

    private fun apiCaptures(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        fun capArr(list: List<CaptureItem>): JSONArray {
            val arr = JSONArray()
            list.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("title", c.title)
                        .put("note", c.note)
                        .put("tags", JSONArray(c.tags))
                        .put("processed", c.processed)
                        .put("when", formatWhen(c.createdAt))
                )
            }
            return arr
        }
        return JSONObject()
            .put("inbox", capArr(data.inboxCaptures()))
            .put("journal", capArr(data.journalCaptures()))
            .toString()
    }

    private fun apiPath(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        val goals = JSONArray()
        data.goals.filter { !it.archived }.forEach { g ->
            goals.put(
                JSONObject()
                    .put("id", g.id)
                    .put("title", g.title)
                    .put("description", g.description)
                    .put("category", g.category.name)
                    .put("horizon", g.horizon.name)
                    .put("progress", g.progress.toDouble())
                    .put("confidence", g.confidence.toDouble())
                    .put("firstStepNow", g.firstStepNow)
            )
        }
        val last = data.pathChecks.maxByOrNull { it.loggedAt }
        val lastJson = if (last != null) {
            JSONObject()
                .put("date", last.date)
                .put("vision", last.visionAlignment)
                .put("energy", last.energyTowardDreams)
                .put("identity", last.identityCongruence)
                .put("note", last.note)
        } else {
            JSONObject.NULL
        }
        return JSONObject()
            .put("goals", goals)
            .put("lastCheck", lastJson)
            .toString()
    }

    private fun apiMore(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        val workouts = JSONArray()
        data.workoutSessions.sortedByDescending { it.startedAt }.take(10).forEach { w ->
            val name = data.routines.find { it.id == w.routineId }?.name
            workouts.put(
                JSONObject()
                    .put("id", w.id)
                    .put("routineId", w.routineId)
                    .put("routineName", name ?: w.routineId)
                    .put("date", w.date)
                    .put("totalDurationMin", w.totalDurationMin ?: JSONObject.NULL)
                    .put("completed", w.completed)
            )
        }
        val routines = JSONArray()
        data.routines.filter { !it.archived }.forEach { r ->
            routines.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("exercises", r.exercises.size)
                    .put("estimatedDurationMin", r.estimatedDurationMin)
            )
        }
        val reminders = JSONArray()
        data.reminders.forEach { r ->
            val label = r.groupId?.let { gid -> data.groups.find { it.id == gid }?.name } ?: "Daily review"
            reminders.put(
                JSONObject()
                    .put("id", r.id)
                    .put("label", label)
                    .put("time", r.time)
                    .put("enabled", r.enabled)
            )
        }
        val snaps = JSONArray()
        data.sensorSnapshots.take(15).forEach { s ->
            val hName = data.habits.find { it.id == s.habitId }?.name
            snaps.put(
                JSONObject()
                    .put("habitId", s.habitId)
                    .put("habitName", hName ?: s.habitId)
                    .put("date", s.date)
                    .put("summary", s.readings.entries.joinToString(" · ") { "${it.key}: ${it.value}" }.take(120))
            )
        }
        return JSONObject()
            .put(
                "sleep",
                JSONObject()
                    .put("wakeTime", data.sleep.wakeTime)
                    .put("bedTime", data.sleep.bedTime)
                    .put("windDownMinutes", data.sleep.windDownMinutes)
            )
            .put(
                "server",
                JSONObject()
                    .put("status", statusMessage)
                    .put("httpPort", boundHttpPort)
                    .put("httpsPort", boundHttpsPort)
                    .put("autoOff", autoOffRemainingLabel() ?: JSONObject.NULL)
                    .put("ssid", currentSsid ?: JSONObject.NULL)
            )
            .put("workouts", workouts)
            .put("routines", routines)
            .put("reminders", reminders)
            .put("snapshots", snaps)
            .toString()
    }

    private fun apiUnlog(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val habitId = json.optString("habitId", "")
            if (habitId.isBlank()) return """{"error":"habitId required"}"""
            var data = loadData() ?: return """{"error":"load failed"}"""
            val today = HabitDomain.getToday()
            data = data.withRemovedEntry(today, habitId)
            saveData(data)
            JSONObject().put("ok", true).put("habitId", habitId).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "unlog failed").toString()
        }
    }

    private fun apiSkip(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val habitId = json.optString("habitId", "")
            if (habitId.isBlank()) return """{"error":"habitId required"}"""
            var data = loadData() ?: return """{"error":"load failed"}"""
            val habit = data.habits.find { it.id == habitId }
                ?: return """{"error":"habit not found"}"""
            if (!habit.canSkip) return """{"error":"cannot skip"}"""
            val today = HabitDomain.getToday()
            val entry = HabitEntry(
                value = 0.0,
                note = json.optString("note", ""),
                loggedAt = System.currentTimeMillis(),
                skipped = true
            )
            data = data.withUpdatedEntry(today, habitId, entry)
            saveData(data)
            JSONObject().put("ok", true).put("habitId", habitId).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "skip failed").toString()
        }
    }

    private fun apiCreateHabit(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val name = json.optString("name", "").trim()
            val groupId = json.optString("groupId", "").trim()
            if (name.isBlank() || groupId.isBlank()) {
                return """{"error":"name and groupId required"}"""
            }
            var data = loadData() ?: return """{"error":"load failed"}"""
            if (data.groups.none { it.id == groupId && !it.archived }) {
                return """{"error":"group not found"}"""
            }
            val type = try {
                HabitType.valueOf(json.optString("type", "CHECKBOX").uppercase())
            } catch (_: Exception) {
                HabitType.CHECKBOX
            }
            val id = "h_${UUID.randomUUID().toString().take(8)}"
            val habit = Habit(
                id = id,
                name = name,
                groupId = groupId,
                type = type,
                order = data.habits.count { it.groupId == groupId },
                icon = json.optString("icon", "")
            )
            data = data.withAddedHabit(habit)
            saveData(data)
            JSONObject().put("ok", true).put("id", id).put("name", name).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "create failed").toString()
        }
    }

    private fun apiArchiveHabit(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val habitId = json.optString("habitId", "")
            if (habitId.isBlank()) return """{"error":"habitId required"}"""
            var data = loadData() ?: return """{"error":"load failed"}"""
            data = data.withArchivedHabit(habitId)
            saveData(data)
            JSONObject().put("ok", true).put("habitId", habitId).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "archive failed").toString()
        }
    }

    private fun apiLog(body: String): String {
        val ctx = appContext ?: return """{"error":"no context"}"""
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val habitId = json.optString("habitId", "")
            if (habitId.isBlank()) return """{"error":"habitId required"}"""
            val value = json.optDouble("value", 1.0)
            val note = json.optString("note", "")
            var data = loadData() ?: return """{"error":"load failed"}"""
            val habit = data.habits.find { it.id == habitId }
                ?: return """{"error":"habit not found"}"""
            val today = HabitDomain.getToday()
            val entry = HabitEntry(
                value = value,
                note = note.trim(),
                loggedAt = System.currentTimeMillis()
            )
            data = data.withUpdatedEntry(today, habitId, entry)
            if (value >= 0.5) {
                val result = ExtensionManager.onHabitLogged(ctx, data, habit, entry, today)
                data = result.data
            }
            saveData(data)
            JSONObject().put("ok", true).put("habitId", habitId).put("value", value).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "log failed").toString()
        }
    }

    private fun apiCapture(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val title = json.optString("title", "").trim()
            if (title.isBlank()) return """{"error":"title required"}"""
            val note = json.optString("note", "")
            val tagsArr = json.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
            }
            var data = loadData() ?: return """{"error":"load failed"}"""
            val processed = !data.capturePrefs.goesToInbox(tags)
            val cap = CaptureItem(
                id = "c_${UUID.randomUUID().toString().take(8)}",
                title = title,
                note = note.trim(),
                tags = tags,
                processed = processed
            )
            data = data.withAddedCapture(cap)
            saveData(data)
            JSONObject().put("ok", true).put("id", cap.id).put("processed", processed).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "capture failed").toString()
        }
    }

    private fun apiCaptureDone(body: String): String {
        return try {
            val id = JSONObject(body.ifBlank { "{}" }).optString("id", "")
            if (id.isBlank()) return """{"error":"id required"}"""
            var data = loadData() ?: return """{"error":"load failed"}"""
            val cap = data.captures.find { it.id == id } ?: return """{"error":"not found"}"""
            data = data.withUpdatedCapture(cap.copy(processed = true))
            saveData(data)
            JSONObject().put("ok", true).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "failed").toString()
        }
    }

    private fun apiCaptureDelete(body: String): String {
        return try {
            val id = JSONObject(body.ifBlank { "{}" }).optString("id", "")
            if (id.isBlank()) return """{"error":"id required"}"""
            var data = loadData() ?: return """{"error":"load failed"}"""
            data = data.withoutCapture(id)
            saveData(data)
            JSONObject().put("ok", true).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "failed").toString()
        }
    }

    private fun apiPathCheck(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            var data = loadData() ?: return """{"error":"load failed"}"""
            val check = PathAlignmentCheck(
                id = "pc_${UUID.randomUUID().toString().take(8)}",
                date = HabitDomain.getToday(),
                visionAlignment = json.optInt("vision", 3).coerceIn(1, 5),
                energyTowardDreams = json.optInt("energy", 3).coerceIn(1, 5),
                identityCongruence = json.optInt("identity", 3).coerceIn(1, 5),
                note = json.optString("note", "").trim(),
                loggedAt = System.currentTimeMillis()
            )
            data = data.withAddedPathCheck(check)
            saveData(data)
            JSONObject().put("ok", true).put("id", check.id).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "failed").toString()
        }
    }

    private fun apiPathGoal(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val id = json.optString("id", "")
            if (id.isBlank()) return """{"error":"id required"}"""
            var data = loadData() ?: return """{"error":"load failed"}"""
            val goal = data.goals.find { it.id == id } ?: return """{"error":"not found"}"""
            val progress = json.optDouble("progress", goal.progress.toDouble()).toFloat().coerceIn(0f, 1f)
            val confidence = if (json.has("confidence")) {
                json.optDouble("confidence", goal.confidence.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                goal.confidence
            }
            data = data.withUpdatedGoal(
                goal.copy(
                    progress = progress,
                    confidence = confidence,
                    updatedAt = System.currentTimeMillis()
                )
            )
            saveData(data)
            JSONObject().put("ok", true).put("progress", progress.toDouble()).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "failed").toString()
        }
    }

    private fun apiPomodoroPost(body: String): String {
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val action = json.optString("action", "start")
            var data = loadData() ?: return """{"error":"load failed"}"""
            var p = data.pomodoroPrefs
            when (action) {
                "start" -> {
                    val work = json.optInt("workMin", p.workMin).coerceIn(5, 120)
                    p = p.copy(
                        workMin = work,
                        sessionStartedAt = System.currentTimeMillis(),
                        sessionIsBreak = false
                    )
                }
                "break" -> {
                    val br = json.optInt("breakMin", p.breakMin).coerceIn(1, 60)
                    p = p.copy(
                        breakMin = br,
                        sessionStartedAt = System.currentTimeMillis(),
                        sessionIsBreak = true
                    )
                }
                "stop" -> {
                    p = p.copy(sessionStartedAt = 0L)
                }
            }
            data = data.withPomodoroPrefs(p)
            saveData(data)
            JSONObject()
                .put("ok", true)
                .put("sessionStartedAt", p.sessionStartedAt)
                .put("sessionIsBreak", p.sessionIsBreak)
                .put("workMin", p.workMin)
                .put("breakMin", p.breakMin)
                .toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "pomodoro failed").toString()
        }
    }

    private fun writeResponse(out: OutputStream, code: Int, contentType: String, body: String) {
        writeBytes(out, code, contentType, body.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(out: OutputStream, code: Int, contentType: String, bytes: ByteArray) {
        val status = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            500 -> "Error"
            else -> "Error"
        }
        val header = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Headers: Content-Type, X-Steady-Pin\r\n" +
            "Cache-Control: no-store\r\n" +
            "\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
