package com.workpointstracker.pcclient

import com.workpointstracker.pcclient.config.Config
import com.workpointstracker.pcclient.daemon.Daemon
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("workpointsd")

    // Parse CLI args
    var configPath: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--config", "-c" -> {
                configPath = args.getOrNull(i + 1)
                i += 2
            }
            "--help", "-h" -> {
                println("""
                    Work Points Daemon

                    Usage: workpointsd [options]

                    Options:
                      --config, -c <path>   Path to config file (default: ~/.config/workpointsd/config.yaml)
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

    logger.info("Loading config from: ${configPath ?: "~/.config/workpointsd/config.yaml"}")

    val daemon = Daemon(config)
    daemon.start()
}
