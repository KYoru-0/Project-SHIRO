package com.example.shiro

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Returns a complete folder scan using high-performance SAF contract or DocumentFile fallback. */
suspend fun scanAudioFilesFromFolder(context: Context, treeUri: Uri): List<Track> {
    return try {
        scanWithSafTreeContract(context, treeUri)
    } catch (_: Exception) {
        val fallbackTracks = scanWithDocumentFile(context, treeUri)
        if (fallbackTracks.isNotEmpty()) {
            fallbackTracks
        } else {
            throw IOException("Folder is unavailable")
        }
    }
}

private suspend fun scanWithSafTreeContract(context: Context, treeUri: Uri): List<Track> {
    val audioTracks = mutableListOf<Track>()
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
    val rootCursor = context.contentResolver.query(
        rootUri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null
    ) ?: throw IOException("Folder is unavailable")
    rootCursor.use {
        if (!it.moveToFirst() || it.getString(0) != DocumentsContract.Document.MIME_TYPE_DIR) {
            throw IOException("Folder is unavailable")
        }
    }
    val dirQueue = ArrayDeque<String>().apply { add(rootId) }
    val visited = mutableSetOf<String>()
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    val audioExts = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")
    val retriever = MediaMetadataRetriever()
    try {
        while (dirQueue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val documentId = dirQueue.removeFirst()
            if (!visited.add(documentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                ?: throw IOException("Folder provider returned no result")
            cursor.use {
                if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING) ||
                    it.extras.containsKey(DocumentsContract.EXTRA_ERROR)) {
                    throw IOException("Folder provider has not returned a complete listing")
                }
                val idIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = it.getString(idIndex) ?: throw IOException("Missing document ID")
                    val name = it.getString(nameIndex) ?: throw IOException("Missing document name")
                    val mime = it.getString(mimeIndex).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirQueue.add(id)
                    } else if (name.substringAfterLast('.', "").lowercase() in audioExts || mime.startsWith("audio/")) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        audioTracks.add(extractTrackMetadata(context, uri, name, retriever))
                    }
                }
            }
        }
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
    return audioTracks.sortedBy { it.title.lowercase() }
}

private fun scanWithDocumentFile(context: Context, treeUri: Uri): List<Track> {
    val audioTracks = mutableListOf<Track>()
    val rootFile = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val retriever = MediaMetadataRetriever()
    val audioExts = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")

    try {
        fun traverse(dir: DocumentFile) {
            val files = dir.listFiles()
            for (file in files) {
                if (file.isDirectory) {
                    traverse(file)
                } else if (file.isFile) {
                    val name = file.name ?: ""
                    val lowerName = name.lowercase()
                    val mime = file.type ?: ""
                    val ext = lowerName.substringAfterLast('.', "")
                    if (ext in audioExts || mime.startsWith("audio/")) {
                        audioTracks.add(extractTrackMetadata(context, file.uri, name, retriever))
                    }
                }
            }
        }
        traverse(rootFile)
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
    return audioTracks.sortedBy { it.title.lowercase() }
}
