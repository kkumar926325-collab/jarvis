package com.jarvis.assistant.modules.files

import com.jarvis.assistant.core.Format
import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.Match
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.NameMatcher

data class FileEntry(val name: String, val isDirectory: Boolean, val sizeBytes: Long, val uri: String)

/** Backed by the Storage Access Framework: JARVIS only sees the one folder the user picked. */
interface FileSource {
    fun rootUri(): String?
    fun rootName(): String?
    fun setRoot(uriString: String)
    fun clearRoot()
    fun children(dirUri: String): List<FileEntry>
}

sealed class FilesResult {
    data object NoRoot : FilesResult()
    data class Text(val text: String) : FilesResult()
    data class Problem(val message: String) : FilesResult()
}

class FilesModule(private val source: FileSource) : JarvisModule {
    companion object {
        const val EXPLANATION =
            "To browse files, pick a folder. JARVIS can then see only that folder (read-only) through " +
                "Android's file picker, and nothing else on your phone. You can change or remove it any time."
        private const val MAX_LIST = 30
        private const val MAX_HITS = 25
        private const val MAX_DEPTH = 4
        private const val MAX_VISITED = 3000
    }

    override val id = "files"
    override val displayName = "Files"
    override val requiredPermissions: List<String> = emptyList()

    override fun status() =
        source.rootName()?.let { ModuleStatus(true, "Browsing folder: $it") }
            ?: ModuleStatus(false, "No folder chosen yet (you pick one; no storage permission needed)")

    fun setRoot(uri: String): String {
        source.setRoot(uri)
        return source.rootName() ?: "the selected folder"
    }

    fun clearRoot() = source.clearRoot()

    fun list(path: String?): FilesResult {
        val root = source.rootUri() ?: return FilesResult.NoRoot
        val rootName = source.rootName() ?: "Folder"
        var current = root
        var trail = rootName

        val segments = (path ?: "").split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }
        val effective =
            if (segments.isNotEmpty() && NameMatcher.normalize(segments[0]) == NameMatcher.normalize(rootName)) segments.drop(1)
            else segments

        for (seg in effective) {
            val dirs = source.children(current).filter { it.isDirectory }
            val dir = when (val m = NameMatcher.resolve(dirs, seg) { it.name }) {
                is Match.Single -> m.item
                is Match.Many -> return FilesResult.Problem("Several folders match \"$seg\" in $trail: ${m.items.joinToString { it.name }}.")
                Match.None -> return FilesResult.Problem("I couldn't find a folder called \"$seg\" in $trail.")
            }
            current = dir.uri
            trail += "/" + dir.name
        }

        val entries = source.children(current)
        if (entries.isEmpty()) return FilesResult.Text("$trail is empty, or JARVIS no longer has access to it.")
        val shown = entries.take(MAX_LIST).joinToString("\n") { describe(it) }
        val more = entries.size - MAX_LIST
        return FilesResult.Text("$trail\n$shown" + if (more > 0) "\n...and $more more" else "")
    }

    /** Breadth-first search by file name inside the chosen folder, with limits so it always ends. */
    fun search(query: String): FilesResult {
        val root = source.rootUri() ?: return FilesResult.NoRoot
        if (NameMatcher.normalize(query).isEmpty()) return FilesResult.Problem("What should I search for?")

        val hits = ArrayList<Pair<FileEntry, String>>()
        val queue = ArrayDeque<Triple<String, String, Int>>()
        queue.add(Triple(root, source.rootName() ?: "", 0))
        var visited = 0

        while (queue.isNotEmpty() && hits.size < MAX_HITS && visited < MAX_VISITED) {
            val (uri, path, depth) = queue.removeFirst()
            for (entry in source.children(uri)) {
                visited++
                if (NameMatcher.score(entry.name, query) > 0) hits.add(entry to path)
                if (entry.isDirectory && depth < MAX_DEPTH) queue.add(Triple(entry.uri, "$path/${entry.name}", depth + 1))
                if (hits.size >= MAX_HITS) break
            }
        }

        if (hits.isEmpty()) return FilesResult.Text("No files or folders matching \"$query\" in the chosen folder.")
        return FilesResult.Text(hits.joinToString("\n") { (e, path) -> "${describe(e)}\n   in $path" })
    }

    private fun describe(e: FileEntry): String =
        if (e.isDirectory) "[folder] ${e.name}" else "${e.name} (${Format.bytes(e.sizeBytes)})"
}
