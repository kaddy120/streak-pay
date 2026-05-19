package com.workpointstracker.pcclient.daemon

import com.workpointstracker.pcclient.config.TrackedApp
import org.slf4j.LoggerFactory

data class WindowInfo(
    val wmClass: String,
    val title: String
)

class WindowMonitor(private val trackedApps: List<TrackedApp>) {

    private val logger = LoggerFactory.getLogger(WindowMonitor::class.java)
    private val sessionType: String by lazy { detectSessionType() }

    private fun detectSessionType(): String {
        val xdgSession = System.getenv("XDG_SESSION_TYPE") ?: ""
        return when {
            xdgSession.contains("x11", ignoreCase = true) -> "x11"
            xdgSession.contains("wayland", ignoreCase = true) -> detectWaylandCompositor()
            else -> "unknown"
        }
    }

    private fun detectWaylandCompositor(): String {
        // Check for common Wayland compositors
        val desktop = System.getenv("XDG_CURRENT_DESKTOP") ?: ""
        return when {
            desktop.contains("GNOME", ignoreCase = true) -> "gnome-wayland"
            desktop.contains("sway", ignoreCase = true) -> "sway"
            desktop.contains("Hyprland", ignoreCase = true) -> "hyprland"
            else -> "wayland-generic"
        }
    }

    fun getActiveWindow(): WindowInfo? {
        return try {
            when (sessionType) {
                "x11" -> getX11ActiveWindow()
                "gnome-wayland" -> getGnomeWaylandActiveWindow()
                "sway" -> getSwayActiveWindow()
                "hyprland" -> getHyprlandActiveWindow()
                else -> getX11ActiveWindow() // Fallback to X11 (XWayland)
            }
        } catch (e: Exception) {
            logger.debug("Failed to get active window: {}", e.message)
            null
        }
    }

    private fun getX11ActiveWindow(): WindowInfo? {
        val windowId = runCommand("xdotool", "getactivewindow")?.trim() ?: return null

        // Use xprop for WM_CLASS (xdotool getwindowclassname missing in older versions)
        val classOutput = runCommand("xprop", "-id", windowId, "WM_CLASS") ?: return null
        // WM_CLASS(STRING) = "instance", "ClassName"
        val classValues = Regex(""""([^"]*)"""").findAll(classOutput).map { it.groupValues[1] }.toList()
        val wmClass = classValues.lastOrNull()?.takeIf { it.isNotBlank() }
            ?: classValues.firstOrNull() ?: return null

        val titleOutput = runCommand("xprop", "-id", windowId, "_NET_WM_NAME") ?: ""
        val title = Regex("""= "(.*)"$""").find(titleOutput)?.groupValues?.get(1)
            ?: runCommand("xprop", "-id", windowId, "WM_NAME")
                ?.let { Regex("""= "(.*)"$""").find(it)?.groupValues?.get(1) }
            ?: ""

        return WindowInfo(wmClass, title)
    }

    private fun getGnomeWaylandActiveWindow(): WindowInfo? {
        // Use gdbus to get active window info via GNOME Shell eval
        val script = """
            global.get_window_actors()
                .map(a => a.meta_window)
                .find(w => w.has_focus())
                ?.get_wm_class() || ''
        """.trimIndent().replace("\n", "")

        val wmClass = runCommand(
            "gdbus", "call", "--session",
            "--dest", "org.gnome.Shell",
            "--object-path", "/org/gnome/Shell",
            "--method", "org.gnome.Shell.Eval",
            script
        )?.let { parseGdbusResult(it) } ?: return null

        val titleScript = """
            global.get_window_actors()
                .map(a => a.meta_window)
                .find(w => w.has_focus())
                ?.get_title() || ''
        """.trimIndent().replace("\n", "")

        val title = runCommand(
            "gdbus", "call", "--session",
            "--dest", "org.gnome.Shell",
            "--object-path", "/org/gnome/Shell",
            "--method", "org.gnome.Shell.Eval",
            titleScript
        )?.let { parseGdbusResult(it) } ?: ""

        return if (wmClass.isNotBlank()) WindowInfo(wmClass, title) else null
    }

    private fun getSwayActiveWindow(): WindowInfo? {
        val output = runCommand("swaymsg", "-t", "get_tree") ?: return null
        // Parse JSON to find focused window
        val focusedMatch = Regex(""""focused":\s*true""").find(output) ?: return null
        val before = output.substring(0, focusedMatch.range.first)
        val lastBrace = before.lastIndexOf('{')
        val block = output.substring(lastBrace)

        val appId = Regex(""""app_id":\s*"([^"]*?)"""").find(block)?.groupValues?.get(1) ?: ""
        val name = Regex(""""name":\s*"([^"]*?)"""").find(block)?.groupValues?.get(1) ?: ""

        return if (appId.isNotBlank() || name.isNotBlank()) WindowInfo(appId, name) else null
    }

    private fun getHyprlandActiveWindow(): WindowInfo? {
        val output = runCommand("hyprctl", "activewindow", "-j") ?: return null
        val wmClass = Regex(""""class":\s*"([^"]*?)"""").find(output)?.groupValues?.get(1) ?: return null
        val title = Regex(""""title":\s*"([^"]*?)"""").find(output)?.groupValues?.get(1) ?: ""
        return WindowInfo(wmClass, title)
    }

    fun isTrackedApp(window: WindowInfo): Boolean {
        return trackedApps.any { app ->
            val wmClassMatch = app.wm_class?.let { wmClass ->
                window.wmClass.equals(wmClass, ignoreCase = true)
            } ?: false
            val titleMatch = app.title_contains?.let { titleContains ->
                window.title.contains(titleContains, ignoreCase = true)
            } ?: false
            wmClassMatch || titleMatch
        }
    }

    fun getTrackedAppName(window: WindowInfo): String? {
        return trackedApps.find { app ->
            val wmClassMatch = app.wm_class?.let { wmClass ->
                window.wmClass.equals(wmClass, ignoreCase = true)
            } ?: false
            val titleMatch = app.title_contains?.let { titleContains ->
                window.title.contains(titleContains, ignoreCase = true)
            } ?: false
            wmClassMatch || titleMatch
        }?.name
    }

    private fun parseGdbusResult(output: String): String {
        // gdbus returns: (true, 'result_string')
        val match = Regex("""'([^']*)'""").findAll(output).lastOrNull()
        return match?.groupValues?.get(1) ?: ""
    }

    private fun runCommand(vararg command: String): String? {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) output else null
        } catch (e: Exception) {
            null
        }
    }
}
