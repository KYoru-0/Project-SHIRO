package com.example.shiro

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll

internal data class AudioDocument(
    val id: String, val name: String, val mimeType: String,
    val size: Long? = null, val modified: Long? = null
) {
    val isDirectory get() = mimeType == "vnd.android.document/directory"
    val isAudio get() = mimeType.startsWith("audio/") ||
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    fun matches(size: Long?, modified: Long?): Boolean =
        this.size != null && this.size >= 0L && this.modified != null && this.modified > 0L &&
            this.size == size && this.modified == modified

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")
    }
}

internal data class FolderListing(val entries: List<AudioDocument>, val complete: Boolean = true)

/** Discover only descendants of the selected tree; any incomplete directory fails the whole scan. */
internal suspend fun discoverAudioDocuments(rootId: String,
    children: suspend (String) -> FolderListing): List<AudioDocument> {
    val queue = ArrayDeque<String>().apply { add(rootId) }
    val visited = mutableSetOf<String>()
    val audio = linkedMapOf<String, AudioDocument>()
    while (queue.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val id = queue.removeFirst()
        if (!visited.add(id)) continue
        val listing = children(id)
        if (!listing.complete) throw IOException("Folder provider has not returned a complete listing")
        for (entry in listing.entries) {
            currentCoroutineContext().ensureActive()
            if (entry.isDirectory) queue.add(entry.id)
            else if (entry.isAudio) audio[entry.id] = entry
        }
    }
    return audio.values.toList()
}

internal interface ScanMetadataReader<T> : AutoCloseable {
    fun read(document: AudioDocument): T
}

/** Four reusable readers, not one coroutine/retriever per file. Cached entries open no reader. */
internal suspend fun <T : Any> readScanMetadata(documents: List<AudioDocument>,
    cached: (AudioDocument) -> T?, openReader: () -> ScanMetadataReader<T>): List<T> = coroutineScope {
    val results = MutableList<T?>(documents.size) { null }
    val workers = minOf(4, documents.size)
    val jobs = List(workers) { worker ->
        launch(Dispatchers.IO) {
            var reader: ScanMetadataReader<T>? = null
            try {
                for (index in worker until documents.size step workers) {
                    currentCoroutineContext().ensureActive()
                    val document = documents[index]
                    results[index] = cached(document) ?: run {
                        val source = reader ?: openReader().also { reader = it }
                        source.read(document)
                    }
                }
            } finally {
                reader?.close()
            }
        }
    }
    jobs.joinAll()
    results.map { checkNotNull(it) }
}
