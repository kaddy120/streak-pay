package com.workpointstracker.pcclient.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

data class ApiConfig(
    val base_url: String = "http://localhost:8080",
    val api_key: String = "dev-api-key-change-in-production"
)

data class WebUiConfig(
    val enabled: Boolean = true,
    val port: Int = 8742
)

data class TrackedApp(
    val name: String = "",
    val wm_class: String? = null,
    val title_contains: String? = null
)

data class Config(
    val api: ApiConfig = ApiConfig(),
    val poll_interval_seconds: Int = 2,
    val idle_timeout_seconds: Int = 300,
    val end_after_paused_seconds: Int = 1800,
    val non_tracked_grace_seconds: Int = 120,
    val web_ui: WebUiConfig = WebUiConfig(),
    val tracked_apps: List<TrackedApp> = defaultTrackedApps()
) {
    companion object {
        private val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        fun load(path: String? = null): Config {
            val configFile = if (path != null) {
                File(path)
            } else {
                val configDir = File(System.getProperty("user.home"), ".config/workpointsd")
                File(configDir, "config.yaml")
            }

            return if (configFile.exists()) {
                mapper.readValue(configFile, Config::class.java)
            } else {
                Config()
            }
        }

        fun defaultTrackedApps(): List<TrackedApp> = listOf(
            TrackedApp("VS Code", wm_class = "code"),
            TrackedApp("IntelliJ IDEA", wm_class = "jetbrains-idea"),
            TrackedApp("PyCharm", wm_class = "jetbrains-pycharm"),
            TrackedApp("Android Studio", wm_class = "jetbrains-studio"),
            TrackedApp("Microsoft Teams", wm_class = "teams-for-linux"),
            TrackedApp("Microsoft Teams", title_contains = "Microsoft Teams"),
            TrackedApp("Outlook", title_contains = "Outlook"),
            TrackedApp("Slack", wm_class = "slack"),
            TrackedApp("Zoom", wm_class = "zoom"),
            TrackedApp("Obsidian", wm_class = "obsidian"),
            TrackedApp("Notion", title_contains = "Notion"),
            TrackedApp("Terminal (GNOME)", wm_class = "Gnome-terminal"),
            TrackedApp("Kitty", wm_class = "kitty"),
            TrackedApp("Alacritty", wm_class = "Alacritty"),
            TrackedApp("Firefox", wm_class = "firefox"),
            TrackedApp("Chrome", wm_class = "google-chrome")
        )
    }
}
