package com.jarvis.assistant

import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.UiRequest
import com.jarvis.assistant.modules.apps.AppEntry
import com.jarvis.assistant.modules.apps.AppsModule
import com.jarvis.assistant.modules.device.BatteryStatus
import com.jarvis.assistant.modules.device.DeviceModule
import com.jarvis.assistant.modules.files.FileEntry
import com.jarvis.assistant.modules.files.FilesModule
import com.jarvis.assistant.modules.files.FilesResult
import com.jarvis.assistant.modules.notifications.CapturedNotification
import com.jarvis.assistant.modules.notifications.NotificationBuffer
import com.jarvis.assistant.modules.notifications.NotificationsModule
import com.jarvis.assistant.modules.settings.SettingsModule
import com.jarvis.assistant.modules.settings.SettingsShortcut
import com.jarvis.assistant.modules.settings.SettingsShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemModulesTest {
    // ---- files -------------------------------------------------------------------------

    private fun fileTree() = FakeFileSource(
        tree = mapOf(
            "root" to listOf(
                FileEntry("Download", true, 0, "d1"),
                FileEntry("notes.txt", false, 2048, "f1"),
            ),
            "d1" to listOf(
                FileEntry("Reports", true, 0, "d2"),
                FileEntry("budget 2026.xlsx", false, 5_242_880, "f2"),
            ),
            "d2" to listOf(FileEntry("budget summary.pdf", false, 1024, "f3")),
        ),
        root = "root",
    )

    @Test fun filesWithoutFolderAsksForOne() {
        assertEquals(FilesResult.NoRoot, FilesModule(FakeFileSource()).list(null))
        assertEquals(FilesResult.NoRoot, FilesModule(FakeFileSource()).search("x"))
    }

    @Test fun filesListAndNavigate() {
        val m = FilesModule(fileTree())
        assertTrue((m.list(null) as FilesResult.Text).text.contains("[folder] Download"))
        val inner = (m.list("Download/Reports") as FilesResult.Text).text
        assertTrue(inner.contains("budget summary.pdf"))
        assertTrue(m.list("Nope") is FilesResult.Problem)
    }

    @Test fun filesSearchFindsNestedMatches() {
        val text = (FilesModule(fileTree()).search("budget") as FilesResult.Text).text
        assertTrue(text.contains("budget 2026.xlsx"))
        assertTrue(text.contains("budget summary.pdf"))
    }

    // ---- notifications -----------------------------------------------------------------

    @Test fun notificationBufferKeepsNewestWithinCapacity() {
        val b = NotificationBuffer(3)
        (1..5).forEach { b.add(CapturedNotification("App", "t$it", "", it.toLong())) }
        assertEquals(listOf("t5", "t4", "t3"), b.recent(10).map { it.title })
        b.clear()
        assertTrue(b.recent(10).isEmpty())
    }

    @Test fun notificationsOffExplainsAndAsksUi() {
        val out = NotificationsModule(FakeNotificationSource(enabled = false)).read(5)
        assertEquals(UiRequest.ENABLE_NOTIFICATION_ACCESS, (out as Outcome.NeedsUi).request)
    }

    @Test fun notificationsOnShowsContent() {
        val src = FakeNotificationSource(true, listOf(CapturedNotification("Chat", "Rahul", "see you", 0L)))
        val out = NotificationsModule(src).read(5) as Outcome.Done
        assertTrue(out.message.contains("Rahul: see you"))
    }

    // ---- apps --------------------------------------------------------------------------

    @Test fun appsOpenManageAndAmbiguity() {
        val cat = FakeAppCatalog(listOf(AppEntry("WhatsApp", "com.whatsapp"), AppEntry("Camera", "cam"), AppEntry("Camera Pro", "cam2")))
        val m = AppsModule(cat)
        m.open("whatsapp")
        assertEquals(listOf("com.whatsapp"), cat.launched)
        m.openInfo("whatsapp")
        assertEquals(listOf("com.whatsapp"), cat.infoOpened)
        assertTrue(m.open("nothing") is Outcome.Failed)
        // "Camera" exactly matches one app, so it wins over "Camera Pro".
        m.open("camera")
        assertEquals("cam", cat.launched.last())
    }

    // ---- device ------------------------------------------------------------------------

    @Test fun deviceMessages() {
        val src = FakeDeviceSource(BatteryStatus(55, true, "USB", 31.5f))
        val m = DeviceModule(src)
        assertTrue(m.batteryMessage().contains("55%"))
        assertTrue(m.batteryMessage().contains("charging (USB)"))
        assertTrue(m.deviceMessage().contains("Acme Phone 1"))
        assertTrue(m.deviceMessage().contains("64.0 GB free"))
        src.battery = null
        assertTrue(m.batteryMessage().contains("couldn't"))
    }

    // ---- settings ----------------------------------------------------------------------

    @Test fun settingsResolve() {
        assertEquals(SettingsShortcut.WIFI, SettingsShortcuts.resolve("wi-fi"))
        assertEquals(SettingsShortcut.WIFI, SettingsShortcuts.resolve("Wifi"))
        assertEquals(SettingsShortcut.BATTERY_SAVER, SettingsShortcuts.resolve("battery saver"))
        assertEquals(SettingsShortcut.NOTIFICATION_ACCESS, SettingsShortcuts.resolve("notification access"))
        assertEquals(SettingsShortcut.DATE, SettingsShortcuts.resolve("date and time"))
        assertNull(SettingsShortcuts.resolve("flashlight"))
    }

    @Test fun settingsOpensOnlyKnownShortcuts() {
        val l = FakeSettingsLauncher()
        val m = SettingsModule(l)
        m.resolve("bluetooth")?.let { m.open(it) }
        assertEquals(listOf(SettingsShortcut.BLUETOOTH), l.opened)
    }
}
