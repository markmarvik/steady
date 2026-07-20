package com.steady.habittracker.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
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
import com.steady.habittracker.data.withAddedCapture
import com.steady.habittracker.data.withAddedHabit
import com.steady.habittracker.data.withArchivedHabit
import com.steady.habittracker.data.withPomodoroPrefs
import com.steady.habittracker.data.withRemovedEntry
import com.steady.habittracker.data.withUpdatedEntry
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
import java.time.LocalDate
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
 * - Optional HTTPS on [LocalWebPrefs.port] + 1 with bundled self-signed cert
 *
 * Prefer starting via [LocalWebService] so Android keeps the process alive.
 */
object LocalWebServer {
    private const val TAG = "SteadyWeb"
    private const val KEYSTORE_ASSET = "steady_local.p12"
    private const val KEYSTORE_PASS = "steady-local"

    private val lock = Any()
    private val running = AtomicBoolean(false)
    private val httpSocket = AtomicReference<ServerSocket?>(null)
    private val httpsSocket = AtomicReference<ServerSocket?>(null)
    private val prefsRef = AtomicReference(LocalWebPrefs())
    private var appContext: Context? = null

    /** Last status for UI (thread-safe). */
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

    fun isRunning(): Boolean = running.get()

    fun setEnabled(context: Context, data: AppData) {
        appContext = context.applicationContext
        prefsRef.set(data.localWebPrefs)
        if (data.localWebPrefs.enabled) {
            LocalWebService.start(context.applicationContext)
        } else {
            LocalWebService.stop(context.applicationContext)
            stop()
        }
    }

    /**
     * Start listening. Safe to call repeatedly.
     * Called from [LocalWebService] on a background thread.
     */
    fun start(context: Context, prefs: LocalWebPrefs = prefsRef.get()): Boolean {
        synchronized(lock) {
            appContext = context.applicationContext
            prefsRef.set(prefs)
            stopInternal()

            val httpPort = prefs.port.coerceIn(1024, 65534)
            val httpsPort = (httpPort + 1).coerceAtMost(65535)
            lastError = null

            return try {
                // Explicit wildcard — all interfaces (Wi‑Fi + localhost)
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
                            // Accept self-signed / LAN browsers
                            https.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
                            httpsSocket.set(https)
                            boundHttpsPort = https.localPort
                            httpsOk = true
                        } else {
                            Log.w(TAG, "HTTPS keystore missing; HTTP only")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "HTTPS bind failed", e)
                        boundHttpsPort = 0
                        lastError = "HTTPS failed: ${e.message}"
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
            lastError = null
            boundHttpPort = 0
            boundHttpsPort = 0
        }
    }

    private fun stopInternal() {
        running.set(false)
        try {
            httpSocket.getAndSet(null)?.close()
        } catch (_: Exception) { }
        try {
            httpsSocket.getAndSet(null)?.close()
        } catch (_: Exception) { }
        // Brief pause so the OS releases the port before rebind
        try {
            Thread.sleep(80)
        } catch (_: InterruptedException) { }
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

    /** Best-effort LAN IPv4 addresses (Wi‑Fi first). */
    fun lanIpv4Addresses(): List<String> {
        val found = linkedSetOf<String>()
        // ConnectivityManager active network (API 23+)
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
        } catch (_: Exception) { }

        // Enumerate all interfaces
        try {
            val ifaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (ni in ifaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name.lowercase()
                val addrs = Collections.list(ni.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        // Prefer wlan/eth
                        if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                            found.add(host)
                        } else {
                            found.add(host)
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        // Deprecated WifiManager fallback
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
        } catch (_: Exception) { }

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

    /** @deprecated use [httpUrls] */
    fun localAddressHint(context: Context): String {
        appContext = context.applicationContext
        return httpUrls().firstOrNull() ?: "http://127.0.0.1:${prefsRef.get().port}"
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
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else ""

            val prefs = prefsRef.get()
            if (prefs.pin.isNotBlank() && path.startsWith("/api")) {
                val pinOk = body.contains("\"pin\":\"${prefs.pin}\"") ||
                    query.contains("pin=${prefs.pin}")
                if (!pinOk) {
                    writeResponse(socket.getOutputStream(), 401, "application/json", """{"error":"pin required"}""")
                    return
                }
            }

            when {
                path == "/" || path == "/index.html" ->
                    writeResponse(socket.getOutputStream(), 200, "text/html; charset=utf-8", htmlDashboard())
                path == "/habits" ->
                    writeResponse(socket.getOutputStream(), 200, "text/html; charset=utf-8", htmlHabits())
                path == "/api/status" && method == "GET" ->
                    writeResponse(
                        socket.getOutputStream(), 200, "application/json",
                        JSONObject()
                            .put("running", running.get())
                            .put("httpPort", boundHttpPort)
                            .put("httpsPort", boundHttpsPort)
                            .put("status", statusMessage)
                            .toString()
                    )
                path == "/api/today" && method == "GET" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiToday())
                path == "/api/habits" && method == "GET" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiHabits())
                path == "/api/groups" && method == "GET" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiGroups())
                path == "/api/history" && method == "GET" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiHistory())
                path == "/api/pomodoro" && method == "GET" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiPomodoro())
                path == "/api/quote" && method == "GET" -> {
                    val q = MotivationalQuotes.forToday()
                    writeResponse(
                        socket.getOutputStream(), 200, "application/json",
                        JSONObject().put("text", q.text).put("attribution", q.attribution).toString()
                    )
                }
                path == "/api/log" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiLog(body))
                path == "/api/unlog" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiUnlog(body))
                path == "/api/habit" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiCreateHabit(body))
                path == "/api/archive" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiArchiveHabit(body))
                path == "/api/capture" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiCapture(body))
                path == "/api/pomodoro" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiPomodoroPost(body))
                else -> writeResponse(socket.getOutputStream(), 404, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "client error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) { }
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

    private fun apiToday(): String {
        val data = loadData() ?: return """{"error":"load failed"}"""
        return try {
            val today = HabitDomain.getToday()
            val sections = HabitDomain.timelineSectionsForToday(data)
            val arr = JSONArray()
            sections.forEach { sec ->
                val habits = JSONArray()
                sec.habits.forEach { h ->
                    val entry = data.entries[today]?.get(h.id)
                    habits.put(
                        JSONObject()
                            .put("id", h.id)
                            .put("name", h.name)
                            .put("icon", h.icon)
                            .put("extension", h.extensionType.name)
                            .put("done", (entry?.value ?: 0.0) >= 0.5)
                            .put("skipped", entry?.skipped == true)
                            .put("note", entry?.note ?: "")
                    )
                }
                arr.put(
                    JSONObject()
                        .put("group", sec.group.name)
                        .put("isNow", sec.isNow)
                        .put("habits", habits)
                )
            }
            val caps = JSONArray()
            data.captures.filter { !it.processed }.take(20).forEach { c ->
                caps.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("title", c.title)
                        .put("tags", JSONArray(c.tags))
                )
            }
            JSONObject()
                .put("date", today)
                .put("sections", arr)
                .put("pendingCaptures", data.captures.count { !it.processed })
                .put("captures", caps)
                .put("momentum", HabitDomain.computeDayPoints(data, today))
                .toString()
        } catch (_: Exception) {
            """{"error":"load failed"}"""
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
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("name", h.name)
                    .put("groupId", h.groupId)
                    .put("type", h.type.name)
                    .put("extension", h.extensionType.name)
                    .put("icon", h.icon)
                    .put("doneToday", (entry?.value ?: 0.0) >= 0.5)
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
        val days = JSONArray()
        for (i in 0 until 14) {
            val d = today.minusDays(i.toLong())
            val ds = d.toString()
            val pts = HabitDomain.computeDayPoints(data, ds)
            val due = HabitDomain.habitsDueOn(data, d)
            val entries = data.entries[ds] ?: emptyMap()
            val done = due.count { h -> (entries[h.id]?.value ?: 0.0) >= 0.5 }
            days.put(
                JSONObject()
                    .put("date", ds)
                    .put("points", pts)
                    .put("done", done)
                    .put("due", due.size)
            )
        }
        return JSONObject()
            .put("streak", HabitDomain.computeStreak(data))
            .put("lifetimePoints", data.score.lifetimePoints)
            .put("days", days)
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
            val cap = CaptureItem(
                id = "c_${UUID.randomUUID().toString().take(8)}",
                title = title,
                note = note.trim(),
                tags = tags
            )
            data = data.withAddedCapture(cap)
            saveData(data)
            JSONObject().put("ok", true).put("id", cap.id).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "capture failed").toString()
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

    private fun htmlDashboard(): String {
        val quote = MotivationalQuotes.forToday()
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Steady · Local</title>
<style>
  body { font-family: system-ui, sans-serif; background:#0f1419; color:#e7ecf1; margin:0; padding:24px; max-width:720px; }
  h1 { font-size:1.5rem; margin:0 0 8px; }
  .sub { color:#8b9aab; margin-bottom:16px; }
  card { display:block; background:#1a2332; border-radius:12px; padding:16px; margin-bottom:12px; }
  .now { border-left:4px solid #3dcea8; }
  .habit { padding:10px 0; border-bottom:1px solid #243044; display:flex; justify-content:space-between; align-items:center; gap:8px; }
  .done { color:#3dcea8; }
  .quote { font-style:italic; color:#b8c5d4; margin:12px 0 20px; }
  button { background:#3dcea8; color:#0f1419; border:0; border-radius:8px; padding:8px 12px; font-weight:600; cursor:pointer; font-size:13px; }
  button.sec { background:#5b8def; }
  button.ghost { background:#243044; color:#e7ecf1; }
  #timer { font-size:2.5rem; font-variant-numeric:tabular-nums; margin:12px 0; }
  input, textarea { width:100%; box-sizing:border-box; background:#0f1419; border:1px solid #243044; color:#e7ecf1; border-radius:8px; padding:8px; margin:4px 0 8px; }
  .row { display:flex; gap:8px; flex-wrap:wrap; }
  .tag { font-size:10px; color:#8b9aab; }
  a { color:#3dcea8; }
</style>
</head>
<body>
  <h1>Steady</h1>
  <p class="sub">Local LAN · <a href="/">Today</a> · <a href="/habits">Habits</a> · Capture · Pomodoro</p>
  <p class="quote">"${quote.text.replace("\"", "&quot;")}" — ${quote.attribution}</p>
  <card>
    <strong>Pomodoro</strong>
    <div id="timer">25:00</div>
    <div class="row">
      <button onclick="startPomodoro(25)">Start 25m</button>
      <button class="sec" onclick="startPomodoro(5)">5m break</button>
      <button class="ghost" onclick="stopPomodoro()">Stop</button>
    </div>
  </card>
  <card>
    <strong>Quick capture</strong>
    <input id="capTitle" placeholder="Title / idea"/>
    <textarea id="capNote" rows="2" placeholder="Note (optional)"></textarea>
    <div class="row">
      <button onclick="sendCapture('Ideas')">Ideas</button>
      <button onclick="sendCapture('Notes')">Notes</button>
      <button onclick="sendCapture('Reminders')">Reminders</button>
      <button class="ghost" onclick="sendCapture()">Save</button>
    </div>
  </card>
  <div id="today">Loading…</div>
<script>
let endAt = 0;
function tick() {
  if (!endAt) return;
  const left = Math.max(0, Math.floor((endAt - Date.now())/1000));
  const m = String(Math.floor(left/60)).padStart(2,'0');
  const s = String(left%60).padStart(2,'0');
  document.getElementById('timer').textContent = m+':'+s;
  if (left <= 0) { endAt = 0; document.getElementById('timer').textContent = 'Done'; }
}
setInterval(tick, 250);
async function startPomodoro(mins) {
  const m = mins || 25;
  endAt = Date.now() + m*60*1000;
  tick();
  const action = m <= 10 ? 'break' : 'start';
  try {
    await fetch('/api/pomodoro', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify(action==='break' ? {action:'break', breakMin:m} : {action:'start', workMin:m}) });
  } catch(e) {}
}
async function stopPomodoro() {
  endAt = 0;
  document.getElementById('timer').textContent = '—';
  try {
    await fetch('/api/pomodoro', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({action:'stop'}) });
  } catch(e) {}
}
async function logHabit(id) {
  try {
    await fetch('/api/log', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({habitId:id, value:1}) });
    loadToday();
  } catch(e) { alert('Log failed'); }
}
async function unlogHabit(id) {
  try {
    await fetch('/api/unlog', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({habitId:id}) });
    loadToday();
  } catch(e) { alert('Undo failed'); }
}
async function sendCapture(tag) {
  const title = document.getElementById('capTitle').value.trim();
  if (!title) { alert('Title required'); return; }
  const note = document.getElementById('capNote').value.trim();
  const tags = tag ? [tag] : [];
  try {
    await fetch('/api/capture', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({title, note, tags}) });
    document.getElementById('capTitle').value = '';
    document.getElementById('capNote').value = '';
    loadToday();
  } catch(e) { alert('Capture failed'); }
}
async function loadToday() {
  try {
    const r = await fetch('/api/today');
    const j = await r.json();
    let html = '<p class="tag">'+ (j.date||'') +' · pts '+(j.momentum||0)+' · inbox '+(j.pendingCaptures||0)+'</p>';
    (j.sections||[]).forEach(sec => {
      html += '<card class="'+(sec.isNow?'now':'')+'"><strong>'+esc(sec.group)+(sec.isNow?' · now':'')+'</strong>';
      (sec.habits||[]).forEach(h => {
        html += '<div class="habit"><span>'+esc(h.name)+
          (h.extension&&h.extension!=='NONE'?' <span class="tag">'+h.extension+'</span>':'')+
          '</span>';
        if (h.done) html += '<button class="ghost" onclick="unlogHabit(\''+h.id+'\')">Undo</button>';
        else html += '<button onclick="logHabit(\''+h.id+'\')">Log</button>';
        html += '</div>';
      });
      html += '</card>';
    });
    if (!(j.sections||[]).length) html += '<card>No pending habits — nice work.</card>';
    document.getElementById('today').innerHTML = html;
  } catch(e) {
    document.getElementById('today').textContent = 'Failed to load — is the phone on the same Wi‑Fi?';
  }
}
function esc(s) {
  return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');
}
loadToday();
setInterval(loadToday, 12000);
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun htmlHabits(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Steady · Habits</title>
<style>
  body { font-family: system-ui, sans-serif; background:#0f1419; color:#e7ecf1; margin:0; padding:24px; max-width:720px; }
  h1 { font-size:1.4rem; }
  a { color:#3dcea8; }
  card { display:block; background:#1a2332; border-radius:12px; padding:14px; margin-bottom:10px; }
  .row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
  input, select { background:#0f1419; border:1px solid #243044; color:#e7ecf1; border-radius:8px; padding:8px; }
  button { background:#3dcea8; color:#0f1419; border:0; border-radius:8px; padding:8px 12px; font-weight:600; cursor:pointer; }
  button.ghost { background:#243044; color:#e7ecf1; }
  button.danger { background:#7f1d1d; color:#fecaca; }
  .habit { display:flex; justify-content:space-between; gap:8px; padding:8px 0; border-bottom:1px solid #243044; }
  .tag { font-size:10px; color:#8b9aab; }
</style>
</head>
<body>
  <h1>Habits</h1>
  <p class="tag"><a href="/">← Today</a> · create & archive · desktop CRUD</p>
  <card>
    <strong>New habit</strong>
    <div class="row" style="margin-top:8px">
      <input id="name" placeholder="Name" style="flex:1;min-width:140px"/>
      <select id="group"></select>
      <button onclick="createHabit()">Add</button>
    </div>
  </card>
  <card>
    <strong>14-day history</strong>
    <div id="hist" class="tag">Loading…</div>
  </card>
  <div id="list">Loading…</div>
<script>
async function loadGroups() {
  const r = await fetch('/api/groups');
  const j = await r.json();
  const sel = document.getElementById('group');
  sel.innerHTML = '';
  (j.groups||[]).forEach(g => {
    const o = document.createElement('option');
    o.value = g.id; o.textContent = g.name;
    sel.appendChild(o);
  });
}
async function loadHabits() {
  const r = await fetch('/api/habits');
  const j = await r.json();
  let html = '<card><strong>Catalog ('+((j.habits||[]).length)+')</strong>';
  (j.habits||[]).forEach(h => {
    html += '<div class="habit"><span>'+esc(h.name)+
      (h.extension&&h.extension!=='NONE'?' <span class="tag">'+h.extension+'</span>':'')+
      (h.doneToday?' <span class="tag">done today</span>':'')+
      '</span><button class="danger" onclick="archive(\''+h.id+'\')">Archive</button></div>';
  });
  html += '</card>';
  document.getElementById('list').innerHTML = html;
}
async function loadHistory() {
  try {
    const r = await fetch('/api/history');
    const j = await r.json();
    let s = 'Streak '+ (j.streak||0) +' · lifetime '+ (j.lifetimePoints||0) +'<br/>';
    (j.days||[]).slice(0,7).forEach(d => {
      s += d.date +': '+d.done+'/'+d.due+' · '+d.points+' pts<br/>';
    });
    document.getElementById('hist').innerHTML = s;
  } catch(e) { document.getElementById('hist').textContent = 'Failed'; }
}
async function createHabit() {
  const name = document.getElementById('name').value.trim();
  const groupId = document.getElementById('group').value;
  if (!name || !groupId) { alert('Name + group required'); return; }
  await fetch('/api/habit', { method:'POST', headers:{'Content-Type':'application/json'},
    body: JSON.stringify({name, groupId}) });
  document.getElementById('name').value = '';
  loadHabits();
}
async function archive(id) {
  if (!confirm('Archive this habit?')) return;
  await fetch('/api/archive', { method:'POST', headers:{'Content-Type':'application/json'},
    body: JSON.stringify({habitId:id}) });
  loadHabits();
}
function esc(s) {
  return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');
}
loadGroups(); loadHabits(); loadHistory();
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun writeResponse(out: OutputStream, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
