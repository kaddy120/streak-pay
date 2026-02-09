package com.workpointstracker.pcclient

import com.workpointstracker.pcclient.api.ApiClient
import com.workpointstracker.pcclient.config.Config
import com.workpointstracker.pcclient.daemon.Daemon
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("workpointsd")

    // Parse CLI args
    var configPath: String? = null
    var showStatus = false

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--config", "-c" -> {
                configPath = args.getOrNull(i + 1)
                i += 2
            }
            "--status", "-s" -> {
                showStatus = true
                i++
            }
            "--help", "-h" -> {
                println("""
                    Work Points Daemon

                    Usage: workpointsd [options]

                    Options:
                      --config, -c <path>   Path to config file (default: ~/.config/workpointsd/config.yaml)
                      --status, -s          Show current daemon status and exit
                      --help, -h            Show this help
                """.trimIndent())
                return
            }
            else -> {
                logger.warn("Unknown argument: {}", args[i])
                i++
            }
        }
    }

    val config = Config.load(configPath)

    if (showStatus) {
        val apiClient = ApiClient(config.api.base_url, config.api.api_key)
        val stats = apiClient.getTodayStats()
        val streak = apiClient.getStreakInfo()
        val points = apiClient.getTotalPoints()

        println("=== Work Points Status ===")
        println("Today: ${stats?.totalMinutes?.let { it / 60.0 }?.let { "%.1fh".format(it) } ?: "N/A"}")
        println("Today's points: ${stats?.totalPoints?.let { "%.2f".format(it) } ?: "N/A"}")
        println("Total points: ${points?.totalPoints?.let { "%.2f".format(it) } ?: "N/A"}")
        println("Streak: ${streak?.currentStreak ?: 0} days")
        if (streak?.streakAtRisk == true) {
            println("  Streak at risk!")
        }
        return
    }

    logger.info("Loading config from: ${configPath ?: "~/.config/workpointsd/config.yaml"}")

    val daemon = Daemon(config)
    daemon.start()
}
