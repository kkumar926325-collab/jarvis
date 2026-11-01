package com.jarvis.assistant.modules.apps

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.Match
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.NameMatcher
import com.jarvis.assistant.core.Outcome

data class AppEntry(val label: String, val packageName: String)

interface AppCatalog {
    fun launchableApps(): List<AppEntry>
    fun launch(packageName: String): Boolean

    /** Opens Android's own app details page (where the user can force-stop, clear data or uninstall). */
    fun openAppInfo(packageName: String): Boolean
}

class AppsModule(private val catalog: AppCatalog) : JarvisModule {
    override val id = "apps"
    override val displayName = "Apps"
    override val requiredPermissions: List<String> = emptyList()
    override fun status() = ModuleStatus(true, "Ready (opens apps; manages them through Android's app info page)")

    fun open(name: String): Outcome = withApp(name) { app ->
        if (catalog.launch(app.packageName)) Outcome.Done("Opening ${app.label}.")
        else Outcome.Failed("${app.label} can't be opened.")
    }

    fun openInfo(name: String): Outcome = withApp(name) { app ->
        if (catalog.openAppInfo(app.packageName)) {
            Outcome.Done("Opened ${app.label}'s app info. Force stop, storage and uninstall are there; Android asks you to confirm them.")
        } else {
            Outcome.Failed("I couldn't open app info for ${app.label}.")
        }
    }

    fun list(limit: Int = 40): Outcome {
        val apps = catalog.launchableApps()
        if (apps.isEmpty()) return Outcome.Done("I couldn't find any launchable apps.")
        val shown = apps.take(limit).joinToString(", ") { it.label }
        val more = apps.size - limit
        return Outcome.Done("${apps.size} apps: $shown" + if (more > 0) " ...and $more more" else "")
    }

    private fun withApp(name: String, action: (AppEntry) -> Outcome): Outcome =
        when (val m = NameMatcher.resolve(catalog.launchableApps(), name) { it.label }) {
            is Match.Single -> action(m.item)
            is Match.Many -> Outcome.Done("Several apps match \"$name\": ${m.items.take(5).joinToString { it.label }}. Say the full name.")
            Match.None -> Outcome.Failed("I couldn't find an app called \"$name\".")
        }
}
