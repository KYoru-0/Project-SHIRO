package com.example.shiro

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val folderScanLock = Mutex()

/** Complete, tree-scoped scans only. Never fall back to a device-wide MediaStore query. */
suspend fun scanAudioFilesFromFolder(context: Context, treeUri: Uri,
    cachedTracks: Map<String, Track> = emptyMap()): List<Track> = folderScanLock.withLock {
    withContext(Dispatchers.IO) {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
    val rootCursor = context.contentResolver.query(
        rootUri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null
    ) ?: throw IOException("Folder is unavailable")
    rootCursor.use {
        if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING) ||
            it.extras.containsKey(DocumentsContract.EXTRA_ERROR) ||
            !it.moveToFirst() || it.getString(0) != DocumentsContract.Document.MIME_TYPE_DIR) {
            throw IOException("Folder is unavailable")
        }
    }
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    val documents = discoverAudioDocuments(rootId) { documentId ->
        currentCoroutineContext().ensureActive()
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
            val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            fun optionalLong(index: Int) = if (index < 0 || it.isNull(index)) null else it.getLong(index)
            val entries = mutableListOf<AudioDocument>()
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                entries.add(AudioDocument(
                    it.getString(idIndex) ?: throw IOException("Missing document ID"),
                    it.getString(nameIndex) ?: throw IOException("Missing document name"),
                    it.getString(mimeIndex) ?: throw IOException("Missing document type"),
                    optionalLong(sizeIndex), optionalLong(modifiedIndex)
                ))
            }
            FolderListing(entries, complete = !it.extras.getBoolean(DocumentsContract.EXTRA_LOADING) &&
                !it.extras.containsKey(DocumentsContract.EXTRA_ERROR))
        }
    }
    fun uri(document: AudioDocument) = DocumentsContract.buildDocumentUriUsingTree(treeUri, document.id)
    readScanMetadata(documents, cached = { document ->
        // Exact URI plus provider fingerprint: duplicate names and edited tags cannot cross-contaminate.
        cachedTracks[uri(document).toString()]?.takeIf {
            it.fileName == document.name && document.matches(it.sourceSize, it.sourceModified)
        }
    }, openReader = {
        object : ScanMetadataReader<Track> {
            private val retriever = MediaMetadataRetriever()
            override fun read(document: AudioDocument): Track = extractTrackMetadata(
                context, uri(document), document.name, retriever, document.size, document.modified)
            override fun close() { try { retriever.release() } catch (_: Exception) {} }
        }
    }).sortedBy { it.title.lowercase() }
    }
}
