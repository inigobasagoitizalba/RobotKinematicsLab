package com.robotkinematicslab.mobile.storage

import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AtomicFilePublisherTest {

    @Test
    fun concurrentPublishersExposeOneWholePayloadAndLeaveNoUniqueStagingFiles() {
        val directory = Files.createTempDirectory("atomic-publisher-concurrent")
        val destination = directory.resolve("state.bin")
        val staleLegacyEntry = directory.resolve("state.bin.tmp").also(Files::createDirectory)
        val payloads =
            (0 until 12).map { writerIndex ->
                ByteArray(256 * 1024) { byteIndex ->
                    ((writerIndex * 31 + byteIndex) and 0xff).toByte()
                }
            }
        val start = CountDownLatch(1)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(4)
        payloads.forEach { payload ->
            executor.execute {
                try {
                    start.await()
                    AtomicFilePublisher.write(destination.toFile()) { temporary ->
                        temporary.outputStream().buffered().use { output -> output.write(payload) }
                    }
                } catch (error: Throwable) {
                    failures += error
                }
            }
        }

        start.countDown()
        executor.shutdown()
        assertTrue("Concurrent publications timed out.", executor.awaitTermination(20, TimeUnit.SECONDS))
        assertTrue("Concurrent publication failed: $failures", failures.isEmpty())
        assertTrue(payloads.any { payload -> payload.contentEquals(destination.toFile().readBytes()) })
        assertTrue(Files.isDirectory(staleLegacyEntry))
        assertFalse(
            directory.toFile().listFiles().orEmpty().any { file ->
                file.isFile && file.name.startsWith(".state.bin-") && file.name.endsWith(".tmp")
            }
        )
    }

    @Test
    fun failedWriterPreservesPublishedVersionAndCleansItsUniqueStagingFile() {
        val directory = Files.createTempDirectory("atomic-publisher-failure").toFile()
        val destination = directory.resolve("state.txt").apply { writeText("known-good") }

        assertThrows(IllegalStateException::class.java) {
            AtomicFilePublisher.write(destination) { temporary ->
                temporary.writeText("incomplete")
                error("simulated interruption")
            }
        }

        assertEquals("known-good", destination.readText())
        assertFalse(
            directory.listFiles().orEmpty().any { file ->
                file.isFile && file.name.startsWith(".state.txt-") && file.name.endsWith(".tmp")
            }
        )
    }
}
