package com.example.shiro

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private data class DiscoveredAudioFile(val uri: Uri, val fileName: String)

/** Returns a complete folder scan using high-performance SAF contract, DocumentFile, or MediaStore fallback. */
suspend fun scanAudioFilesFromFolder(
    context: Context,
    treeUri: Uri,
    cachedTracks: Map<String, Track> = emptyMap()
): List<Track> {
    val rootFile = DocumentFile.fromTreeUri(context, treeUri)
    if (rootFile != null && rootFile.exists() && rootFile.canRead()) {
        try {
            val safTracks = scanWithSafTreeContract(context, treeUri, cachedTracks)
            if (safTracks.isNotEmpty()) return safTracks
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        val docTracks = scanWithDocumentFile(context, rootFile, cachedTracks)
        if (docTracks.isNotEmpty()) return docTracks
    }

    // MediaStore Fallback for internal phone storage (primary:)
    val displayPath = getDisplayPathFromUri(treeUri)
    val mediaStoreTracks = scanWithMediaStore(context, displayPath)
    if (mediaStoreTracks.isNotEmpty()) {
        return mediaStoreTracks
    }

    throw IOException("Folder is unavailable: $displayPath")
}

private suspend fun resolveTracksMetadata(
    context: Context,
    discovered: List<DiscoveredAudioFile>,
    cachedTracks: Map<String, Track>
): List<Track> = coroutineScope {
    if (discovered.isEmpty()) return@coroutineScope emptyList()

    // 8 concurrent retrievers to balance throughput with file descriptor safety
    val semaphore = Semaphore(8)

    val deferreds = discovered.map { file ->
        val cached = cachedTracks[file.uri.toString()] ?: cachedTracks[file.fileName]
        if (cached != null) {
            async(Dispatchers.Default) {
                if (cached.uri == file.uri) cached else cached.copy(uri = file.uri)
            }
        } else {
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    currentCoroutineContext().ensureActive()
                    val retriever = MediaMetadataRetriever()
                    try {
                        extractTrackMetadata(context, file.uri, file.fileName, retriever)
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    deferreds.awaitAll().sortedBy { it.title.lowercase() }
}

private suspend fun scanWithSafTreeContract(
    context: Context,
    treeUri: Uri,
    cachedTracks: Map<String, Track>
): List<Track> {
    val rootId = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: Exception) {
        throw IOException("Cannot extract tree document ID")
    }

    val dirQueue = ArrayDeque<String>().apply { add(rootId) }
    val visited = mutableSetOf<String>()
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    val audioExts = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")
    val discovered = mutableListOf<DiscoveredAudioFile>()

    while (dirQueue.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val documentId = dirQueue.removeFirst()
        if (!visited.add(documentId)) continue

        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        } catch (_: Exception) {
            continue
        }

        val cursor = try {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        } catch (_: Exception) {
            null
        } ?: continue

        cursor.use {
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            if (idIndex != -1 && nameIndex != -1 && mimeIndex != -1) {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = it.getString(idIndex) ?: continue
                    val name = it.getString(nameIndex) ?: continue
                    val mime = it.getString(mimeIndex).orEmpty()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirQueue.add(id)
                    } else if (name.substringAfterLast('.', "").lowercase() in audioExts || mime.startsWith("audio/")) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        discovered.add(DiscoveredAudioFile(uri, name))
                    }
                }
            }
        }
    }

    return resolveTracksMetadata(context, discovered, cachedTracks)
}

private suspend fun scanWithDocumentFile(
    context: Context,
    rootFile: DocumentFile,
    cachedTracks: Map<String, Track>
): List<Track> {
    val audioExts = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")
    val discovered = mutableListOf<DiscoveredAudioFile>()

    fun traverse(dir: DocumentFile) {
        val files = try { dir.listFiles() } catch (_: Exception) { emptyArray() }
        for (file in files) {
            if (file.isDirectory) {
                traverse(file)
            } else if (file.isFile) {
                val name = file.name ?: ""
                val lowerName = name.lowercase()
                val mime = file.type ?: ""
                val ext = lowerName.substringAfterLast('.', "")
                if (ext in audioExts || mime.startsWith("audio/")) {
                    discovered.add(DiscoveredAudioFile(file.uri, name))
                }
            }
        }
    }
    traverse(rootFile)

    return resolveTracksMetadata(context, discovered, cachedTracks)
}

private fun scanWithMediaStore(context: Context, displayPath: String): List<Track> {
    val audioTracks = mutableListOf<Track>()
    val contentResolver = context.contentResolver

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.COMPOSER,
        MediaStore.Audio.Media.DATA
    )

    val relativePath = if (displayPath.startsWith("/storage/emulated/0/")) {
        displayPath.substringAfter("/storage/emulated/0/").trim('/')
    } else {
        ""
    }

    val selection = if (relativePath.isNotEmpty()) {
        "${MediaStore.Audio.Media.DATA} LIKE ?"
    } else null

    val selectionArgs = if (relativePath.isNotEmpty()) {
        arrayOf("%/$relativePath/%")
    } else null

    try {
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val composerIdx = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val fileName = cursor.getString(nameIdx) ?: "Track"
                val rawTitle = cursor.getString(titleIdx) ?: fileName.substringBeforeLast(".")
                val rawArtist = cursor.getString(artistIdx) ?: ""
                val rawComposer = cursor.getString(composerIdx) ?: ""
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                val cleanTitle = if (rawTitle.isNotBlank() && rawTitle != "<unknown>") rawTitle.trim() else fileName.substringBeforeLast(".")
                val cleanArtist = if (rawArtist.isNotBlank() && rawArtist != "<unknown>") rawArtist.trim() else ""
                val cleanComposer = if (rawComposer.isNotBlank() && rawComposer != "<unknown>") rawComposer.trim() else ""

                audioTracks.add(
                    Track(
                        uri = contentUri,
                        title = cleanTitle,
                        fileName = fileName,
                        artist = cleanArtist,
                        composer = cleanComposer
                    )
                )
            }
        }
    } catch (_: Exception) {}

    return audioTracks.sortedBy { it.title.lowercase() }
}
