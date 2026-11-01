package com.jarvis.assistant.core

data class ModuleStatus(val ready: Boolean, val detail: String)

/** Every feature module implements this so the UI can show what is ready and what is not. */
interface JarvisModule {
    val id: String
    val displayName: String

    /** Runtime permissions this module may ask for (never requested at startup). */
    val requiredPermissions: List<String>

    fun status(): ModuleStatus
}

/** Abstraction over Android's permission check so modules can be tested on the JVM. */
interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}
