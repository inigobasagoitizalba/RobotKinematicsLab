package com.robotkinematicslab.mobile.storage

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Publishes one complete file without reusing a predictable staging path.
 *
 * Unique staging files prevent an abandoned legacy `.tmp` entry or a simultaneous writer from
 * corrupting the bytes being prepared. The final replacement remains atomic whenever the backing
 * filesystem supports it.
 */
internal object AtomicFilePublisher {
    fun <T> write(destination: File, writer: (File) -> T): T {
        val absoluteDestination = destination.absoluteFile
        val parent = requireNotNull(absoluteDestination.parentFile) {
            "An atomically published file must have a parent directory."
        }
        check(parent.mkdirs() || parent.isDirectory) {
            "Atomic publication directory is unavailable: ${parent.absolutePath}"
        }
        val temporary =
            Files.createTempFile(
                parent.toPath(),
                ".${absoluteDestination.name}-",
                ".tmp"
            ).toFile()
        try {
            val result = writer(temporary)
            moveReplacing(temporary, absoluteDestination)
            return result
        } finally {
            temporary.delete()
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
