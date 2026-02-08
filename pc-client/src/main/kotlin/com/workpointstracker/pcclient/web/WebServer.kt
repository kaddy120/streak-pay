package com.workpointstracker.pcclient.web

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.workpointstracker.pcclient.api.ApiClient
import com.workpointstracker.pcclient.daemon.Daemon
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class WebServer(
    private val port: Int,
    private val daemon: Daemon,
    private val apiClient: ApiClient
) {
    private val logger = LoggerFactory.getLogger(WebServer::class.java)
    private var server: ApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowHeader(HttpHeaders.ContentType)
            }
            routing {
                get("/") {
                    call.respondText(dashboardHtml(), ContentType.Text.Html)
                }
                get("/style.css") {
                    call.respondText(dashboardCss(), ContentType.Text.CSS)
                }
                get("/app.js") {
                    call.respondText(dashboardJs(), ContentType.Application.JavaScript)
                }
                get("/api/status") {
                    val now = LocalDateTime.now()
                    val elapsedSeconds = daemon.sessionStartTime?.let { start ->
                        val end = if (daemon.state.name == "PAUSED") {
                            daemon.pausedSince ?: now
                        } else {
                            now
                        }
                        (ChronoUnit.SECONDS.between(start, end) - daemon.totalPausedSeconds).coerceAtLeast(0)
                    } ?: 0

                    call.respond(mapOf(
                        "state" to daemon.state.name,
                        "currentApp" to daemon.currentAppName,
                        "sessionId" to daemon.currentSessionId,
                        "elapsedSeconds" to elapsedSeconds
                    ))
                }
                get("/api/remote-status") {
                    try {
                        val allActive = apiClient.getAllActiveSessions()
                        val deviceId = "pc-${java.net.InetAddress.getLocalHost().hostName}"
                        val remote = allActive.filter { it.deviceId != deviceId }
                        call.respond(remote)
                    } catch (e: Exception) {
                        call.respond(emptyList<Any>())
                    }
                }
                get("/api/stats") {
                    val stats = apiClient.getTodayStats()
                    val points = apiClient.getTotalPoints()
                    val streak = apiClient.getStreakInfo()
                    call.respond(mapOf(
                        "today" to stats,
                        "totalPoints" to points?.totalPoints,
                        "streak" to streak
                    ))
                }
                get("/api/sessions") {
                    val sessions = apiClient.getRecentSessions()
                    call.respond(sessions)
                }
                post("/api/action/start") {
                    daemon.manualStart()
                    call.respond(mapOf("ok" to true, "state" to daemon.state.name))
                }
                post("/api/action/pause") {
                    daemon.manualPause()
                    call.respond(mapOf("ok" to true, "state" to daemon.state.name))
                }
                post("/api/action/resume") {
                    daemon.manualResume()
                    call.respond(mapOf("ok" to true, "state" to daemon.state.name))
                }
                post("/api/action/stop") {
                    daemon.manualStop()
                    call.respond(mapOf("ok" to true, "state" to daemon.state.name))
                }
            }
        }.start(wait = false)

        logger.info("Web dashboard started at http://127.0.0.1:{}", port)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }

    private fun dashboardHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Work Points Dashboard</title>
    <link rel="stylesheet" href="/style.css">
</head>
<body>
    <div class="container">
        <header>
            <h1>Work Points Tracker</h1>
            <div id="status-badge" class="badge idle">IDLE</div>
        </header>

        <section class="status-section">
            <div id="active-info" class="hidden">
                <div class="active-app" id="active-app"></div>
                <div class="timer" id="timer">00:00:00</div>
            </div>
            <div id="idle-info">
                <p class="idle-message">No active session. Open a tracked app or press Start.</p>
            </div>
            <div class="controls" id="controls">
                <button id="btn-start" class="ctrl-btn start" onclick="doAction('start')">Start</button>
                <button id="btn-pause" class="ctrl-btn pause hidden" onclick="doAction('pause')">Pause</button>
                <button id="btn-resume" class="ctrl-btn resume hidden" onclick="doAction('resume')">Resume</button>
                <button id="btn-stop" class="ctrl-btn stop hidden" onclick="doAction('stop')">Stop</button>
            </div>
        </section>

        <section id="remote-section" class="status-section hidden" style="background: #1e3a5f; margin-bottom: 1rem;">
            <div style="font-size: 0.8rem; color: var(--text-dim); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">Remote Session</div>
            <div id="remote-device" style="font-size: 1rem; color: var(--text-dim);"></div>
            <div id="remote-timer" class="timer" style="color: #64b5f6;">00:00:00</div>
            <div id="remote-type" style="font-size: 0.85rem; color: var(--text-dim); margin-top: 0.25rem;"></div>
        </section>

        <section class="stats-grid">
            <div class="stat-card">
                <div class="stat-label">Today's Hours</div>
                <div class="stat-value" id="today-hours">0.0h</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Today's Points</div>
                <div class="stat-value" id="today-points">0.00</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Total Points</div>
                <div class="stat-value" id="total-points">0.00</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Streak</div>
                <div class="stat-value" id="streak">0 days</div>
            </div>
        </section>

        <section class="sessions-section">
            <h2>Recent Sessions</h2>
            <div id="sessions-list" class="sessions-list">
                <p class="empty-message">No sessions yet.</p>
            </div>
        </section>
    </div>

    <script src="/app.js"></script>
</body>
</html>
""".trimIndent()

    private fun dashboardCss(): String = """
:root {
    --bg: #0f0f0f;
    --surface: #1a1a2e;
    --surface-2: #16213e;
    --primary: #e94560;
    --accent: #0f3460;
    --text: #eee;
    --text-dim: #888;
    --success: #4ecca3;
    --warning: #ffc857;
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: var(--bg);
    color: var(--text);
    min-height: 100vh;
}

.container {
    max-width: 800px;
    margin: 0 auto;
    padding: 2rem 1rem;
}

header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2rem;
}

header h1 {
    font-size: 1.5rem;
    font-weight: 600;
}

.badge {
    padding: 0.3rem 1rem;
    border-radius: 2rem;
    font-size: 0.8rem;
    font-weight: 700;
    letter-spacing: 0.05em;
}

.badge.idle { background: var(--accent); }
.badge.active { background: var(--success); color: #000; }
.badge.paused { background: var(--warning); color: #000; }

.status-section {
    background: var(--surface);
    border-radius: 1rem;
    padding: 2rem;
    text-align: center;
    margin-bottom: 2rem;
}

.active-app {
    font-size: 1.1rem;
    color: var(--text-dim);
    margin-bottom: 0.5rem;
}

.timer {
    font-size: 3rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    color: var(--success);
}

.idle-message {
    color: var(--text-dim);
    font-size: 1.1rem;
}

.hidden { display: none; }

.stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 1rem;
    margin-bottom: 2rem;
}

.stat-card {
    background: var(--surface);
    border-radius: 0.75rem;
    padding: 1.25rem;
    text-align: center;
}

.stat-label {
    font-size: 0.8rem;
    color: var(--text-dim);
    margin-bottom: 0.5rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.stat-value {
    font-size: 1.5rem;
    font-weight: 700;
}

.sessions-section h2 {
    font-size: 1.1rem;
    margin-bottom: 1rem;
    color: var(--text-dim);
}

.sessions-list {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.session-item {
    background: var(--surface);
    border-radius: 0.5rem;
    padding: 0.75rem 1rem;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.session-type {
    font-size: 0.75rem;
    padding: 0.15rem 0.5rem;
    border-radius: 0.25rem;
    font-weight: 600;
}

.session-type.DAY_JOB { background: var(--accent); }
.session-type.SIDE_WORK { background: var(--primary); }
.session-type.EARLY_MORNING { background: #6c5ce7; }

.session-meta {
    display: flex;
    align-items: center;
    gap: 1rem;
}

.session-duration { color: var(--text-dim); font-size: 0.9rem; }
.session-points { font-weight: 600; color: var(--success); }

.empty-message { color: var(--text-dim); text-align: center; padding: 1rem; }

.controls {
    display: flex;
    justify-content: center;
    gap: 0.75rem;
    margin-top: 1.25rem;
}

.ctrl-btn {
    padding: 0.6rem 1.5rem;
    border: none;
    border-radius: 0.5rem;
    font-size: 0.95rem;
    font-weight: 600;
    cursor: pointer;
    transition: opacity 0.15s;
}

.ctrl-btn:hover { opacity: 0.85; }
.ctrl-btn:active { opacity: 0.7; }

.ctrl-btn.start { background: var(--success); color: #000; }
.ctrl-btn.pause { background: var(--warning); color: #000; }
.ctrl-btn.resume { background: var(--success); color: #000; }
.ctrl-btn.stop { background: var(--primary); color: #fff; }
""".trimIndent()

    private fun dashboardJs(): String = """
let localElapsed = 0;
let isActive = false;
let timerInterval = null;
let remoteElapsed = 0;
let remoteActive = false;
let remoteTimerInterval = null;

function formatTime(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return [h, m, s].map(v => String(v).padStart(2, '0')).join(':');
}

function formatDuration(minutes) {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? h + 'h ' + m + 'm' : m + 'm';
}

function formatDateTime(isoString) {
    if (!isoString) return '';
    const d = new Date(isoString);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) +
        ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

async function doAction(action) {
    try {
        await fetch('/api/action/' + action, { method: 'POST' });
        await fetchStatus();
        await fetchStats();
        await fetchSessions();
    } catch (e) {
        console.error('Action failed:', e);
    }
}

function updateButtons(state) {
    const btnStart = document.getElementById('btn-start');
    const btnPause = document.getElementById('btn-pause');
    const btnResume = document.getElementById('btn-resume');
    const btnStop = document.getElementById('btn-stop');

    btnStart.classList.toggle('hidden', state !== 'IDLE');
    btnPause.classList.toggle('hidden', state !== 'ACTIVE');
    btnResume.classList.toggle('hidden', state !== 'PAUSED');
    btnStop.classList.toggle('hidden', state === 'IDLE');
}

async function fetchStatus() {
    try {
        const res = await fetch('/api/status');
        const data = await res.json();

        const badge = document.getElementById('status-badge');
        badge.textContent = data.state;
        badge.className = 'badge ' + data.state.toLowerCase();

        const activeInfo = document.getElementById('active-info');
        const idleInfo = document.getElementById('idle-info');
        const appEl = document.getElementById('active-app');

        updateButtons(data.state);

        if (data.state === 'ACTIVE') {
            activeInfo.classList.remove('hidden');
            idleInfo.classList.add('hidden');
            appEl.textContent = data.currentApp || 'Unknown App';
            localElapsed = data.elapsedSeconds || 0;
            if (!isActive) {
                isActive = true;
                startLocalTimer();
            }
        } else if (data.state === 'PAUSED') {
            activeInfo.classList.remove('hidden');
            idleInfo.classList.add('hidden');
            appEl.textContent = (data.currentApp || 'Session') + ' (Paused)';
            localElapsed = data.elapsedSeconds || 0;
            document.getElementById('timer').textContent = formatTime(localElapsed);
            isActive = false;
            stopLocalTimer();
        } else {
            activeInfo.classList.add('hidden');
            idleInfo.classList.remove('hidden');
            isActive = false;
            stopLocalTimer();
            localElapsed = 0;
        }
    } catch (e) {
        console.error('Status fetch failed:', e);
    }
}

async function fetchStats() {
    try {
        const res = await fetch('/api/stats');
        const data = await res.json();

        if (data.today) {
            document.getElementById('today-hours').textContent =
                (data.today.totalMinutes / 60).toFixed(1) + 'h';
            document.getElementById('today-points').textContent =
                data.today.totalPoints.toFixed(2);
        }
        if (data.totalPoints != null) {
            document.getElementById('total-points').textContent =
                data.totalPoints.toFixed(2);
        }
        if (data.streak) {
            const s = data.streak;
            let streakText = s.currentStreak + ' days';
            if (s.streakAtRisk) streakText += ' ⚠️';
            document.getElementById('streak').textContent = streakText;
        }
    } catch (e) {
        console.error('Stats fetch failed:', e);
    }
}

async function fetchSessions() {
    try {
        const res = await fetch('/api/sessions');
        const sessions = await res.json();
        const list = document.getElementById('sessions-list');

        if (sessions.length === 0) {
            list.innerHTML = '<p class="empty-message">No sessions yet.</p>';
            return;
        }

        list.innerHTML = sessions.slice(0, 10).map(s =>
            '<div class="session-item">' +
                '<div>' +
                    '<span class="session-type ' + s.type + '">' + s.type.replace('_', ' ') + '</span>' +
                    ' <span style="color: var(--text-dim); font-size: 0.85rem">' + formatDateTime(s.startTime) + '</span>' +
                '</div>' +
                '<div class="session-meta">' +
                    '<span class="session-duration">' + formatDuration(s.durationMinutes) + '</span>' +
                    '<span class="session-points">+' + s.pointsEarned.toFixed(2) + ' pts</span>' +
                '</div>' +
            '</div>'
        ).join('');
    } catch (e) {
        console.error('Sessions fetch failed:', e);
    }
}

function startLocalTimer() {
    stopLocalTimer();
    timerInterval = setInterval(() => {
        localElapsed++;
        document.getElementById('timer').textContent = formatTime(localElapsed);
    }, 1000);
}

function stopLocalTimer() {
    if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
}

async function fetchRemoteStatus() {
    try {
        const res = await fetch('/api/remote-status');
        const sessions = await res.json();
        const section = document.getElementById('remote-section');

        if (sessions.length > 0) {
            const s = sessions[0];
            section.classList.remove('hidden');
            document.getElementById('remote-device').textContent = s.deviceId;
            document.getElementById('remote-type').textContent = s.type.replace('_', ' ');
            remoteElapsed = s.activeElapsedSeconds || 0;
            document.getElementById('remote-timer').textContent = formatTime(remoteElapsed);
            if (!s.isPaused && !remoteActive) {
                remoteActive = true;
                startRemoteTimer();
            } else if (s.isPaused) {
                remoteActive = false;
                stopRemoteTimer();
            }
        } else {
            section.classList.add('hidden');
            remoteActive = false;
            stopRemoteTimer();
        }
    } catch (e) {
        console.error('Remote status fetch failed:', e);
    }
}

function startRemoteTimer() {
    stopRemoteTimer();
    remoteTimerInterval = setInterval(() => {
        remoteElapsed++;
        document.getElementById('remote-timer').textContent = formatTime(remoteElapsed);
    }, 1000);
}

function stopRemoteTimer() {
    if (remoteTimerInterval) {
        clearInterval(remoteTimerInterval);
        remoteTimerInterval = null;
    }
}

// Initial load
fetchStatus();
fetchStats();
fetchSessions();
fetchRemoteStatus();

// Poll every 5 seconds
setInterval(fetchStatus, 5000);
setInterval(fetchRemoteStatus, 5000);
setInterval(fetchStats, 15000);
setInterval(fetchSessions, 15000);
""".trimIndent()
}
