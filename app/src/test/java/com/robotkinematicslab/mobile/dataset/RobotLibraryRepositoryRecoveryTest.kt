package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.domain.DHParameter
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RobotLibraryRepositoryRecoveryTest {

    @Test
    fun corruptLibraryIsPreservedAndReplacedWithValidatedDefaults() {
        val directory = Files.createTempDirectory("robot-library-recovery").toFile()
        val file = File(directory, "robots.rklb").apply { writeText("not a robot library") }
        val corruptBytes = file.readBytes()
        val expected = DatasetRobotPresets().buildDefaults()
        val repository = RobotLibraryRepository(file)

        val restored = repository.loadOrCreateDefaults()

        assertEquals(expected, restored)
        assertEquals(expected, RobotLibraryCodec().read(file.inputStream()))
        assertEquals(1, repository.listRecoveryCopies().size)
        assertTrue(corruptBytes.contentEquals(repository.listRecoveryCopies().single().readBytes()))
    }

    @Test
    fun validCreateAndEditUseOneAtomicLibraryAndRetainStableIdentity() {
        val directory = Files.createTempDirectory("robot-library-upsert").toFile()
        val file = File(directory, "robots.rklb")
        val repository =
            RobotLibraryRepository(
                libraryFile = file,
                newRobotId = { "custom-stable-id" }
            )
        val template = DatasetRobotPresets().buildDefaults()[4].robot

        val created = repository.upsertRobot(null, template.copy(name = "  Research   arm  "))

        assertEquals("custom-stable-id", created.savedRobot.id)
        assertEquals("Research arm", created.savedRobot.robot.name)
        assertEquals(created.robots, repository.loadOrCreateDefaults())

        val replacement =
            DatasetRobotPresets().buildDefaults()[3].robot.copy(name = "Research arm v2")
        val edited = repository.upsertRobot(created.savedRobot.id, replacement)

        assertEquals(created.savedRobot.id, edited.savedRobot.id)
        assertEquals(replacement, edited.savedRobot.robot)
        assertEquals(1, edited.robots.count { it.id == created.savedRobot.id })
        assertEquals(edited.robots, RobotLibraryCodec().read(file.inputStream()))
    }

    @Test
    fun invalidDuplicateOrMissingIdentityCannotAlterPublishedLibrary() {
        val directory = Files.createTempDirectory("robot-library-upsert-invalid").toFile()
        val file = File(directory, "robots.rklb")
        val repository = RobotLibraryRepository(file)
        val defaults = repository.loadOrCreateDefaults()
        val publishedBytes = file.readBytes()

        assertThrows(IllegalArgumentException::class.java) {
            repository.upsertRobot(
                existingId = defaults.last().id,
                robot = defaults.last().robot.copy(
                    name = "  ${defaults.first().robot.name.uppercase()}  "
                )
            )
        }
        assertTrue(publishedBytes.contentEquals(file.readBytes()))

        assertThrows(IllegalArgumentException::class.java) {
            repository.upsertRobot(
                existingId = "missing-robot-id",
                robot = defaults.first().robot.copy(name = "Unique replacement")
            )
        }
        assertTrue(publishedBytes.contentEquals(file.readBytes()))
        assertEquals(defaults, repository.loadOrCreateDefaults())
    }

    @Test
    fun invalidRobotsAndDuplicateIdentifiersCannotBePersisted() {
        val file = Files.createTempFile("robot-library-invalid", ".rklb").toFile().apply { delete() }
        val valid = DatasetRobotPresets().buildDefaults().first()
        val invalid =
            valid.copy(
                id = "invalid",
                robot =
                    valid.robot.copy(
                        dhParameters =
                            valid.robot.dhParameters.toMutableList().also { rows ->
                                rows[0] = rows[0].copy(a = Double.NaN)
                            }
                    )
            )

        assertRejected { RobotLibraryRepository(file).save(listOf(invalid)) }
        assertRejected { RobotLibraryRepository(file).save(listOf(valid, valid)) }
        assertRejected {
            RobotLibraryRepository(file).save(listOf(valid.copy(id = "x".repeat(161))))
        }
    }

    @Test
    fun changeSignalAdvancesOnlyAfterAValidLibraryIsPublished() {
        val directory = Files.createTempDirectory("robot-library-change-signal").toFile()
        val repository = RobotLibraryRepository(File(directory, "robots.rklb"))
        val valid = DatasetRobotPresets().buildDefaults()
        val revisionBefore = repository.observeChanges().value

        assertRejected { repository.save(valid + valid.first()) }
        assertEquals(revisionBefore, repository.observeChanges().value)

        repository.save(valid)
        assertEquals(revisionBefore + 1L, repository.observeChanges().value)
    }

    @Test
    fun staleLegacyTemporaryDirectoryCannotBlockRobotLibraryPublication() {
        val directory = Files.createTempDirectory("robot-library-stale-temp").toFile()
        val file = File(directory, "robots.rklb")
        val staleTemporaryEntry = File(directory, "robots.rklb.tmp")
        assertTrue(staleTemporaryEntry.mkdir())
        val expected = DatasetRobotPresets().buildDefaults()

        RobotLibraryRepository(file).save(expected)

        assertEquals(expected, RobotLibraryCodec().read(file.inputStream()))
        assertTrue(staleTemporaryEntry.isDirectory)
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
