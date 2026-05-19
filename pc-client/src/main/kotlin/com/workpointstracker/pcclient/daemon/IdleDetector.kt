package com.workpointstracker.pcclient.daemon

import org.slf4j.LoggerFactory

class IdleDetector {

    private val logger = LoggerFactory.getLogger(IdleDetector::class.java)
    private val sessionType: String by lazy {
        System.getenv("XDG_SESSION_TYPE") ?: "unknown"
    }
    private val desktop: String by lazy {
        System.getenv("XDG_CURRENT_DESKTOP") ?: ""
    }

    /**
     * Returns idle time in seconds since last keyboard/mouse input.
     */
    fun getIdleSeconds(): Long {
        return try {
            when {
                sessionType.contains("x11", ignoreCase = true) -> getX11IdleTime()
                desktop.contains("GNOME", ignoreCase = true) -> getGnomeIdleTime()
                else -> getX11IdleTime() // Fallback (XWayland)
            }
        } catch (e: Exception) {
            logger.debug("Failed to get idle time: {}", e.message)
            0L
        }
    }

    private fun getX11IdleTime(): Long {
        // xprintidle returns milliseconds
        val output = runCommand("xprintidle") ?: return 0L
        val millis = output.trim().toLongOrNull() ?: return 0L
        return millis / 1000
    }

    private fun getGnomeIdleTime(): Long {
        val output = runCommand(
            "gdbus", "call", "--session",
            "--dest", "org.gnome.Mutter.IdleMonitor",
            "--object-path", "/org/gnome/Mutter/IdleMonitor/Core",
            "--method", "org.gnome.Mutter.IdleMonitor.GetIdletime"
        ) ?: return 0L

        // Returns: (uint64 12345,) in milliseconds
        val match = Regex("""(\d+)""").find(output)
        val millis = match?.groupValues?.get(1)?.toLongOrNull() ?: return 0L
        return millis / 1000
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
