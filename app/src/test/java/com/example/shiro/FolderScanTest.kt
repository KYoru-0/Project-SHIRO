package com.example.shiro

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FolderScanTest {
    private fun song(id: String, name: String = "same.mp3") = AudioDocument(id, name, "audio/mpeg", 100L, 20L)
    private fun folder(id: String) = AudioDocument(id, id, "vnd.android.document/directory")

    @Test fun scansOnlySelectedDescendantsAndHandlesCyclesDuplicatesAndEmptyFolders() = runBlocking {
        val visited = mutableListOf<String>()
        val result = discoverAudioDocuments("selected") { id ->
            visited += id
            FolderListing(when (id) {
                "selected" -> listOf(song("a"), folder("album"), folder("empty"))
                "album" -> listOf(song("b"), song("a"), folder("selected"), AudioDocument("photo", "cover.jpg", "image/jpeg"))
                "empty" -> emptyList()
                else -> error("Scanner left the selected tree")
            })
        }
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(listOf("selected", "album", "empty"), visited)
        assertTrue(discoverAudioDocuments("empty") { FolderListing(emptyList()) }.isEmpty())
    }

    @Test fun partialOrFailedSubfoldersNeverPublishPartialSongs() = runBlocking {
        for (failure in listOf("partial", "unavailable")) {
            var committed = listOf("previous song")
            try {
                committed = discoverAudioDocuments("root") { id ->
                    if (id == "root") FolderListing(listOf(song("new"), folder("child")))
                    else if (failure == "partial") FolderListing(listOf(song("partial")), complete = false)
                    else throw IOException("Provider disconnected")
                }.map { it.id }
                fail("Incomplete scans must fail")
            } catch (_: IOException) {}
            assertEquals(listOf("previous song"), committed)
        }
    }

    @Test fun cancellationDuringDiscoveryDoesNotReturnAnEmptySuccess() = runBlocking {
        try {
            discoverAudioDocuments("root") { throw CancellationException("New folder selection") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {}
    }

    @Test fun reuseRequiresKnownUnchangedFileFingerprint() {
        val file = song("exact-uri")
        assertTrue(file.matches(100L, 20L))
        assertFalse(file.matches(101L, 20L))
        assertFalse(file.matches(100L, 21L))
        assertFalse(file.matches(null, null))
        assertFalse(file.copy(size = null).matches(null, 20L))
        assertFalse(file.copy(modified = 0L).matches(100L, 0L))
    }

    @Test fun tenThousandUnchangedFilesOpenNoMetadataReadersAndSameNamesStayDistinct() = runBlocking {
        val files = List(10_000) { song("tree/file-$it") }
        val cached = files.associate { it.id to "tags:${it.id}" }
        val result = readScanMetadata(files, cached = { cached[it.id] }, openReader = {
            error("Unchanged metadata must not be re-read")
        })
        assertEquals(files.map { "tags:${it.id}" }, result)
        val added = song("other-tree/new-file") // Same filename, different identity.
        val withNew = readScanMetadata(files.take(2) + added, cached = { cached[it.id] }, openReader = {
            object : ScanMetadataReader<String> {
                override fun read(document: AudioDocument) = "fresh:${document.id}"
                override fun close() {}
            }
        })
        assertEquals("fresh:${added.id}", withNew.last())
    }

    @Test fun largeNewFolderUsesOnlyFourReusableReadersAndRetainsOrder() = runBlocking {
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        val reads = AtomicInteger()
        val firstFour = CountDownLatch(4)
        val files = List(2_000) { song("file-$it") }
        val result = readScanMetadata(files, cached = { null as String? }, openReader = {
            opened.incrementAndGet()
            object : ScanMetadataReader<String> {
                override fun read(document: AudioDocument): String {
                    firstFour.countDown()
                    check(firstFour.await(5, TimeUnit.SECONDS)) { "Four workers must run concurrently" }
                    reads.incrementAndGet()
                    return document.id
                }
                override fun close() { closed.incrementAndGet() }
            }
        })
        assertEquals(4, opened.get())
        assertEquals(opened.get(), closed.get())
        assertEquals(files.size, reads.get())
        assertEquals(files.map { it.id }, result)
    }

    @Test fun failedMetadataWorkClosesAllReaders() = runBlocking {
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        try {
            readScanMetadata(List(50) { song("file-$it") }, cached = { null as String? }, openReader = {
                opened.incrementAndGet()
                object : ScanMetadataReader<String> {
                    override fun read(document: AudioDocument): String = throw IOException("Reader failed")
                    override fun close() { closed.incrementAndGet() }
                }
            })
            fail("Reader failure must propagate")
        } catch (_: IOException) {}
        assertTrue(opened.get() in 1..4)
        assertEquals(opened.get(), closed.get())
    }
}
