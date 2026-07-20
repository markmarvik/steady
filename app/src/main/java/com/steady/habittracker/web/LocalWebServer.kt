package com.steady.habittracker.web

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureItem
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.LocalWebPrefs
import com.steady.habittracker.data.MotivationalQuotes
import com.steady.habittracker.data.withAddedCapture
import com.steady.habittracker.data.withPomodoroPrefs
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Minimal LAN-only HTTP server for desktop access (#38).
 * No external deps — plain ServerSocket + JSON/HTML.
 *
 * Endpoints:
 *  GET  /              HTML dashboard + Pomodoro
 *  GET  /api/today     sections + habits + captures
 *  GET  /api/pomodoro
 *  GET  /api/quote
 *  POST /api/log       {habitId, value?, note?}
 *  POST /api/capture   {title, note?, tags?}
 *  POST /api/pomodoro  {action: start|stop|break, workMin?, breakMin?}
 */
object LocalWebServer {
    private val running = AtomicBoolean(false)
    private val serverRef = AtomicReference<ServerSocket?>(null)
    private val prefsRef = AtomicReference(LocalWebPrefs())
    private var appContext: Context? = null

    fun setEnabled(context: Context, data: AppData) {
        appContext = context.applicationContext
        prefsRef.set(data.localWebPrefs)
        if (data.localWebPrefs.enabled) {
            start(context.applicationContext, data.localWebPrefs.port)
        } else {
            stop()
        }
    }

    fun start(context: Context, port: Int = 8787) {
        appContext = context.applicationContext
        if (running.get()) {
            // Restart if port changed
            val current = serverRef.get()
            if (current != null && !current.isClosed && current.localPort == port) return
            stop()
        }
        thread(name = "steady-web", isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                serverRef.set(ss)
                running.set(true)
                while (running.get()) {
                    try {
                        val client = ss.accept()
                        thread(isDaemon = true) { handleClient(client) }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            } catch (_: Exception) {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverRef.getAndSet(null)?.close()
        } catch (_: Exception) { }
    }

    fun isRunning(): Boolean = running.get()

    fun localAddressHint(context: Context): String {
        val port = prefsRef.get().port
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
            if (ip == "0.0.0.0") "http://<phone-ip>:$port" else "http://$ip:$port"
        } catch (_: Exception) {
            "http://<phone-ip>:$port"
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15_000
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
                path == "/api/today" && method == "GET" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiToday())
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
                path == "/api/capture" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiCapture(body))
                path == "/api/pomodoro" && method == "POST" ->
                    writeResponse(socket.getOutputStream(), 200, "application/json", apiPomodoroPost(body))
                else -> writeResponse(socket.getOutputStream(), 404, "text/plain", "Not found")
            }
        } catch (_: Exception) {
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
</style>
</head>
<body>
  <h1>Steady</h1>
  <p class="sub">Local LAN · Today · Capture · Pomodoro</p>
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
        if (h.done) html += '<span class="done">✓</span>';
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
