package com.robotkinematicslab.mobile.dataset

import android.content.Context
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RobotLibraryRepository(
    private val libraryFile: File,
    private val codec: RobotLibraryCodec = RobotLibraryCodec(),
    private val presets: DatasetRobotPresets = DatasetRobotPresets(),
    private val newRobotId: () -> String = { "custom-${UUID.randomUUID()}" }
) {

    constructor(context: Context) : this(
        libraryFile =
            AppStoragePaths(context).also {
                it.ensureStructureAndMigrateLegacyData()
            }.robotLibraryFile
    )

    @Synchronized
    fun loadOrCreateDefaults(): List<SavedRobot> {
        if (!libraryFile.exists() || libraryFile.length() == 0L) {
            val defaults = presets.buildDefaults()
            save(defaults)
            return defaults
        }

        return runCatching {
            libraryFile.inputStream().buffered().use(codec::read).also(::validateLibrary)
        }.getOrElse {
            val recoveryCopy =
                File(
                    libraryFile.parentFile,
                    "${libraryFile.name}.corrupt-${System.currentTimeMillis()}"
                )
            preserveCorruptLibrary(recoveryCopy)

            val defaults = presets.buildDefaults()
            save(defaults)
            defaults
        }
    }

    @Synchronized
    fun save(robots: List<SavedRobot>) {
        validateLibrary(robots)
        libraryFile.parentFile?.mkdirs()
        try {
            AtomicFilePublisher.write(libraryFile) { temporaryFile ->
                temporaryFile.outputStream().buffered().use { output ->
                    codec.write(
                        robots = robots,
                        output = output
                    )
                }
            }
            libraryRevision.update { revision -> revision + 1L }
        } catch (error: Exception) {
            throw IllegalStateException("Could not finish saving the robot library.", error)
        }
    }

    @Synchronized
    fun resetToDefaults(): List<SavedRobot> {
        val defaults = presets.buildDefaults()
        save(defaults)
        return defaults
    }

    /**
     * Creates or updates one definition without letting a UI rebuild the complete library with a
     * stale copy. Updates retain the stable identifier; new definitions receive a new identifier.
     */
    @Synchronized
    fun upsertRobot(existingId: String?, robot: RobotDefinition): RobotLibraryMutation {
        val current = loadOrCreateDefaults()
        val normalizedRobot = robot.copy(name = normalizeRobotName(robot.name))
        val normalizedName = normalizedRobot.name.lowercase(Locale.ROOT)
        require(
            current.none { saved ->
                saved.id != existingId &&
                    normalizeRobotName(saved.robot.name).lowercase(Locale.ROOT) == normalizedName
            }
        ) {
            "A robot named ${normalizedRobot.name} already exists. Robot names must be unique."
        }

        val savedRobot =
            if (existingId == null) {
                val id = newRobotId()
                require(id.isNotBlank() && current.none { it.id == id }) {
                    "The new robot identifier is blank or already exists."
                }
                SavedRobot(id = id, robot = normalizedRobot)
            } else {
                val previous = current.firstOrNull { it.id == existingId }
                require(previous != null) {
                    "Robot $existingId is no longer present; the edit was not saved under a new identity."
                }
                previous.copy(robot = normalizedRobot)
            }

        val updated =
            if (existingId == null) {
                current + savedRobot
            } else {
                current.map { saved -> if (saved.id == existingId) savedRobot else saved }
            }
        save(updated)
        return RobotLibraryMutation(savedRobot = savedRobot, robots = updated)
    }

    /**
     * Emits after a complete, atomically published library write. Screens use this signal to
     * reload the repository instead of retaining independent stale robot lists.
     */
    fun observeChanges(): StateFlow<Long> = libraryChanges

    /** Exact, never-overwritten source files preserved before automatic default recovery. */
    @Synchronized
    fun listRecoveryCopies(): List<File> =
        libraryFile.parentFile
            ?.listFiles()
            .orEmpty()
            .filter { candidate ->
                candidate.isFile && candidate.name.startsWith("${libraryFile.name}.corrupt-")
            }
            .sortedByDescending(File::lastModified)

    private fun preserveCorruptLibrary(destination: File) {
        try {
            Files.move(
                libraryFile.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(libraryFile.toPath(), destination.toPath())
        } catch (error: Exception) {
            throw IllegalStateException(
                "The robot library is corrupt and could not be preserved safely. " +
                    "The original file was left untouched.",
                error
            )
        }
    }

    private fun validateLibrary(robots: List<SavedRobot>) {
        require(
            robots.all { saved ->
                saved.id.isNotBlank() &&
                    saved.id.length <= MAXIMUM_ID_LENGTH &&
                    saved.id.none(Char::isISOControl)
            }
        ) {
            "Every saved robot must have a non-blank, control-character-free identifier of at most $MAXIMUM_ID_LENGTH characters."
        }
        require(robots.map(SavedRobot::id).distinct().size == robots.size) {
            "Saved robot identifiers must be unique."
        }
        val normalizedNames =
            robots.map { saved -> normalizeRobotName(saved.robot.name).lowercase(Locale.ROOT) }
        require(normalizedNames.distinct().size == normalizedNames.size) {
            "Saved robot names must be unique after trimming whitespace and ignoring case."
        }
        val validator = RobotDefinitionValidator()
        robots.forEach { saved ->
            val validation = validator.validate(saved.robot)
            require(validation.isValid) {
                "Robot ${saved.id} is invalid: ${validation.issues.joinToString { it.message }}"
            }
        }
    }

    private companion object {
        const val MAXIMUM_ID_LENGTH = 160
        val libraryRevision = MutableStateFlow(0L)
        val libraryChanges: StateFlow<Long> = libraryRevision.asStateFlow()
    }
}

data class RobotLibraryMutation(
    val savedRobot: SavedRobot,
    val robots: List<SavedRobot>
)

private fun normalizeRobotName(value: String): String =
    value.trim().replace(Regex("\\s+"), " ")
