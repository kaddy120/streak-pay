package com.workpointstracker.pcclient.daemon

import com.workpointstracker.pcclient.config.TrackedApp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WindowMonitorTest {

    private fun monitor(vararg apps: TrackedApp) = WindowMonitor(apps.toList())

    // ── isTrackedApp: wm_class matching ──

    @Test
    fun `isTrackedApp matches exact wm_class`() {
        val monitor = monitor(TrackedApp("IntelliJ", wm_class = "jetbrains-idea"))
        val window = WindowInfo(wmClass = "jetbrains-idea", title = "MyProject")

        assertTrue(monitor.isTrackedApp(window))
    }

    @Test
    fun `isTrackedApp wm_class matching is case-insensitive`() {
        val monitor = monitor(TrackedApp("Terminal", wm_class = "Gnome-terminal"))
        val window = WindowInfo(wmClass = "gnome-terminal", title = "bash")

        assertTrue(monitor.isTrackedApp(window))
    }

    @Test
    fun `isTrackedApp does not match unrelated wm_class`() {
        val monitor = monitor(TrackedApp("VS Code", wm_class = "code"))
        val window = WindowInfo(wmClass = "Brave-browser", title = "Google")

        assertFalse(monitor.isTrackedApp(window))
    }

    @Test
    fun `isTrackedApp does not partial-match wm_class`() {
        val monitor = monitor(TrackedApp("IntelliJ", wm_class = "jetbrains-idea"))
        val window = WindowInfo(wmClass = "jetbrains-idea-ce", title = "MyProject")

        assertFalse(monitor.isTrackedApp(window))
    }

    // ── isTrackedApp: title_contains matching ──

    @Test
    fun `isTrackedApp matches title_contains substring`() {
        val monitor = monitor(TrackedApp("Teams", title_contains = "Microsoft Teams"))
        val window = WindowInfo(wmClass = "Brave-browser", title = "Microsoft Teams - Chat")

        assertTrue(monitor.isTrackedApp(window))
    }

    @Test
    fun `isTrackedApp title_contains is case-insensitive`() {
        val monitor = monitor(TrackedApp("Outlook", title_contains = "Outlook"))
        val window = WindowInfo(wmClass = "Brave-browser", title = "Inbox - user@company.com - OUTLOOK")

        assertTrue(monitor.isTrackedApp(window))
    }

    @Test
    fun `isTrackedApp does not match when title does not contain string`() {
        val monitor = monitor(TrackedApp("Outlook", title_contains = "Outlook"))
        val window = WindowInfo(wmClass = "Brave-browser", title = "Reddit - Pair Programming")

        assertFalse(monitor.isTrackedApp(window))
    }

    // ── isTrackedApp: combined wm_class + title_contains ──

    @Test
    fun `isTrackedApp matches if either wm_class or title_contains matches`() {
        val monitor = monitor(
            TrackedApp("Teams Native", wm_class = "teams-for-linux"),
            TrackedApp("Teams PWA", title_contains = "Microsoft Teams")
        )

        // Native app via wm_class
        assertTrue(monitor.isTrackedApp(
            WindowInfo(wmClass = "teams-for-linux", title = "Teams")
        ))

        // PWA via title
        assertTrue(monitor.isTrackedApp(
            WindowInfo(wmClass = "Brave-browser", title = "Microsoft Teams - Chat")
        ))

        // Neither
        assertFalse(monitor.isTrackedApp(
            WindowInfo(wmClass = "Brave-browser", title = "YouTube")
        ))
    }

    @Test
    fun `isTrackedApp with app having both wm_class and title_contains matches either`() {
        val monitor = monitor(
            TrackedApp("Notion", wm_class = "notion-app", title_contains = "Notion")
        )

        // Match via wm_class
        assertTrue(monitor.isTrackedApp(
            WindowInfo(wmClass = "notion-app", title = "Some Page")
        ))

        // Match via title
        assertTrue(monitor.isTrackedApp(
            WindowInfo(wmClass = "Brave-browser", title = "My Workspace - Notion")
        ))
    }

    // ── isTrackedApp: multiple tracked apps ──

    @Test
    fun `isTrackedApp checks all tracked apps`() {
        val monitor = monitor(
            TrackedApp("VS Code", wm_class = "code"),
            TrackedApp("IntelliJ", wm_class = "jetbrains-idea"),
            TrackedApp("Terminal", wm_class = "Gnome-terminal"),
            TrackedApp("Teams PWA", title_contains = "Microsoft Teams"),
            TrackedApp("Outlook PWA", title_contains = "Outlook")
        )

        assertTrue(monitor.isTrackedApp(WindowInfo("code", "main.kt")))
        assertTrue(monitor.isTrackedApp(WindowInfo("jetbrains-idea", "Project")))
        assertTrue(monitor.isTrackedApp(WindowInfo("Gnome-terminal", "bash")))
        assertTrue(monitor.isTrackedApp(WindowInfo("Brave-browser", "Microsoft Teams")))
        assertTrue(monitor.isTrackedApp(WindowInfo("Brave-browser", "Inbox - Outlook")))
        assertFalse(monitor.isTrackedApp(WindowInfo("Brave-browser", "YouTube")))
        assertFalse(monitor.isTrackedApp(WindowInfo("nautilus", "Files")))
    }

    @Test
    fun `isTrackedApp with empty tracked apps list returns false`() {
        val monitor = monitor()

        assertFalse(monitor.isTrackedApp(WindowInfo("code", "main.kt")))
    }

    // ── isTrackedApp: PWA browser windows ──

    @Test
    fun `browser window only tracked when title matches a PWA app`() {
        val monitor = monitor(
            TrackedApp("Teams PWA", title_contains = "Microsoft Teams"),
            TrackedApp("Outlook PWA", title_contains = "Outlook")
        )

        // Regular browsing - NOT tracked
        assertFalse(monitor.isTrackedApp(WindowInfo("Brave-browser", "Google Search")))
        assertFalse(monitor.isTrackedApp(WindowInfo("Brave-browser", "Work Dashboard - Brave")))

        // PWA windows - tracked
        assertTrue(monitor.isTrackedApp(WindowInfo("Brave-browser", "Microsoft Teams")))
        assertTrue(monitor.isTrackedApp(WindowInfo("google-chrome", "Outlook (PWA)")))
    }

    // ── getTrackedAppName ──

    @Test
    fun `getTrackedAppName returns name for matched wm_class`() {
        val monitor = monitor(TrackedApp("IntelliJ IDEA", wm_class = "jetbrains-idea"))

        assertEquals("IntelliJ IDEA", monitor.getTrackedAppName(
            WindowInfo("jetbrains-idea", "Project")
        ))
    }

    @Test
    fun `getTrackedAppName returns name for matched title_contains`() {
        val monitor = monitor(TrackedApp("Microsoft Teams", title_contains = "Microsoft Teams"))

        assertEquals("Microsoft Teams", monitor.getTrackedAppName(
            WindowInfo("Brave-browser", "Microsoft Teams - Chat")
        ))
    }

    @Test
    fun `getTrackedAppName returns null for untracked window`() {
        val monitor = monitor(TrackedApp("VS Code", wm_class = "code"))

        assertNull(monitor.getTrackedAppName(
            WindowInfo("Brave-browser", "YouTube")
        ))
    }

    @Test
    fun `getTrackedAppName returns first matching app name`() {
        val monitor = monitor(
            TrackedApp("Teams Native", wm_class = "teams-for-linux"),
            TrackedApp("Teams PWA", title_contains = "Microsoft Teams")
        )

        // Title matches the PWA entry (second), not the native entry
        assertEquals("Teams PWA", monitor.getTrackedAppName(
            WindowInfo("Brave-browser", "Microsoft Teams - Chat")
        ))

        // wm_class matches the native entry (first)
        assertEquals("Teams Native", monitor.getTrackedAppName(
            WindowInfo("teams-for-linux", "Microsoft Teams")
        ))
    }
}
