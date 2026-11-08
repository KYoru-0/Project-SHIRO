package com.example.shiro

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

private val playlistCacheLock = Any()

/** Tree identity is embedded in every scanned URI, including files in nested directories. */
fun Track.belongsToFolder(folderUri: Uri): Boolean = try {
    uri.authority == folderUri.authority &&
        DocumentsContract.getTreeDocumentId(uri) == DocumentsContract.getTreeDocumentId(folderUri)
} catch (_: IllegalArgumentException) {
    false
}

fun savePlaylistCache(context: Context, tracks: List<Track>): Boolean = synchronized(playlistCacheLock) {
    val file = AtomicFile(File(context.filesDir, "playlist_cache.json"))
    var stream: FileOutputStream? = null
    try {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().apply {
                put("uri", track.uri.toString())
                put("title", track.title)
                put("fileName", track.fileName)
                put("artist", track.artist)
                put("composer", track.composer)
                track.sourceSize?.let { put("sourceSize", it) }
                track.sourceModified?.let { put("sourceModified", it) }
            })
        }
        val bytes = array.toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        stream = output
        output.write(bytes)
        output.fd.sync()
        file.finishWrite(output)
        stream = null
        // Some platform implementations log commit failures rather than throwing them.
        file.openRead().use { it.readBytes().contentEquals(bytes) }
    } catch (_: Exception) {
        stream?.let { file.failWrite(it) }
        false
    }
}

fun loadPlaylistCache(context: Context): List<Track> = synchronized(playlistCacheLock) {
    try {
        val file = AtomicFile(File(context.filesDir, "playlist_cache.json"))
        val content = file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(content)
        val folders = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
            .getString("folder_data", "").orEmpty().split("|||").mapNotNull {
                val parts = it.split("||")
                if (parts.size == 2) Uri.parse(parts[0]) else null
            }
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Track(
                uri = Uri.parse(item.getString("uri")),
                title = item.getString("title"),
                fileName = item.getString("fileName"),
                artist = item.optString("artist", ""),
                composer = item.optString("composer", ""),
                sourceSize = if (item.has("sourceSize")) item.getLong("sourceSize") else null,
                sourceModified = if (item.has("sourceModified")) item.getLong("sourceModified") else null
            )
        }.filter { track -> folders.any { track.belongsToFolder(it) } }
        // A failed save must not resurrect explicitly removed folders on restart.
    } catch (_: Exception) {
        emptyList()
    }
}
