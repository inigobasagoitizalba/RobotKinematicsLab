package com.robotkinematicslab.mobile.storage.project

import android.content.Context
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import android.os.Environment
import java.io.File
import java.util.Properties
import java.util.Locale

data class ResearchProject(
    val id: String,
    val name: String,
    val objective: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val usesLegacyWorkspace: Boolean
)

/**
 * Global index for the application's research projects.
 *
 * The original workspace deliberately remains at the historical root so an upgrade never moves or
 * rewrites existing scientific evidence. New projects receive isolated roots below `projects/`.
 */
class ResearchProjectRepository(
    private val globalRoot: File,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {

    constructor(context: Context) : this(globalRootFor(context))

    private val indexFile = File(globalRoot, INDEX_FILE_NAME)
    private val projectsDirectory = File(globalRoot, PROJECTS_DIRECTORY_NAME)

    init {
        synchronized(INDEX_LOCK) {
            ensureIndex()
        }
    }

    fun listProjects(): List<ResearchProject> = synchronized(INDEX_LOCK) {
        readIndex().projects.sortedWith(
            compareByDescending<ResearchProject> { it.updatedAtEpochMillis }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    fun activeProject(): ResearchProject = synchronized(INDEX_LOCK) {
        val index = readIndex()
        index.projects.firstOrNull { it.id == index.activeProjectId }
            ?: index.projects.first { it.id == LEGACY_PROJECT_ID }
    }

    fun createProject(name: String, objective: String): ResearchProject = synchronized(INDEX_LOCK) {
        val cleanName = validateName(name)
        val cleanObjective = validateObjective(objective)
        val index = readIndex()
        val now = clockMillis().coerceAtLeast(1L)
        val stem = safeIdStem(cleanName)
        var candidate = "$stem-$now"
        var suffix = 2
        while (index.projects.any { it.id == candidate }) {
            candidate = "$stem-$now-$suffix"
            suffix += 1
        }

        val project =
            ResearchProject(
                id = candidate,
                name = cleanName,
                objective = cleanObjective,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                usesLegacyWorkspace = false
            )
        projectRoot(project).mkdirs()
        writeProjectMetadata(project)
        writeIndex(index.copy(projects = index.projects + project))
        project
    }

    fun updateProject(projectId: String, name: String, objective: String): ResearchProject =
        synchronized(INDEX_LOCK) {
            val cleanName = validateName(name)
            val cleanObjective = validateObjective(objective)
            val index = readIndex()
            require(index.projects.any { it.id == projectId }) { "Unknown research project: $projectId" }
            val now = clockMillis().coerceAtLeast(1L)
            var updated: ResearchProject? = null
            val projects =
                index.projects.map { project ->
                    if (project.id == projectId) {
                        project.copy(
                            name = cleanName,
                            objective = cleanObjective,
                            updatedAtEpochMillis = maxOf(now, project.updatedAtEpochMillis)
                        ).also { updated = it }
                    } else {
                        project
                    }
                }
            writeProjectMetadata(checkNotNull(updated))
            writeIndex(index.copy(projects = projects))
            checkNotNull(updated)
        }

    fun activateProject(projectId: String): ResearchProject = synchronized(INDEX_LOCK) {
        val index = readIndex()
        val project = index.projects.firstOrNull { it.id == projectId }
            ?: throw IllegalArgumentException("Unknown research project: $projectId")
        val now = clockMillis().coerceAtLeast(1L)
        val activated = project.copy(updatedAtEpochMillis = maxOf(now, project.updatedAtEpochMillis))
        val projects = index.projects.map { if (it.id == projectId) activated else it }
        projectRoot(activated).mkdirs()
        writeProjectMetadata(activated)
        writeIndex(ProjectIndex(activeProjectId = projectId, projects = projects))
        activated
    }

    fun projectRoot(project: ResearchProject): File {
        if (project.usesLegacyWorkspace) {
            require(project.id == LEGACY_PROJECT_ID) {
                "Only the original research project may use the legacy workspace."
            }
            return globalRoot
        }

        require(isSafeProjectId(project.id)) { "Invalid research project id: ${project.id}" }
        val root = File(projectsDirectory, project.id)
        require(root.canonicalFile.parentFile == projectsDirectory.canonicalFile) {
            "Research project root must remain inside managed project storage."
        }
        return root
    }

    fun activeProjectRoot(): File = projectRoot(activeProject())

    private fun ensureIndex() {
        globalRoot.mkdirs()
        projectsDirectory.mkdirs()
        readIndex()
    }

    private fun readIndex(): ProjectIndex {
        val stored = readIndexOrNull()
        if (stored == null && indexFile.exists()) preserveCorruptIndex()

        val now = clockMillis().coerceAtLeast(1L)
        val legacy = stored?.projects?.firstOrNull { it.id == LEGACY_PROJECT_ID }
            ?: originalWorkspace(now)
        val recoveredProjects = reconcileProjectDirectories(
            projects = (stored?.projects.orEmpty().filterNot { it.id == LEGACY_PROJECT_ID } + legacy)
                .distinctBy { it.id },
            now = now
        )
        val activeProjectId = stored?.activeProjectId
            ?.takeIf { requested -> recoveredProjects.any { it.id == requested } }
            ?: LEGACY_PROJECT_ID
        val repaired = ProjectIndex(activeProjectId = activeProjectId, projects = recoveredProjects)
        if (stored != repaired) writeIndex(repaired)
        return repaired
    }

    private fun preserveCorruptIndex() {
        if (!indexFile.exists()) return
        val stem = "$INDEX_FILE_NAME.corrupt-${clockMillis().coerceAtLeast(1L)}"
        var backup = File(globalRoot, stem)
        var suffix = 2
        while (backup.exists()) {
            backup = File(globalRoot, "$stem-$suffix")
            suffix += 1
        }
        runCatching { indexFile.copyTo(backup, overwrite = false) }
    }

    private fun reconcileProjectDirectories(
        projects: List<ResearchProject>,
        now: Long
    ): List<ResearchProject> {
        val reconciled = linkedMapOf<String, ResearchProject>()
        projects.forEach { project ->
            if (project.usesLegacyWorkspace) {
                if (project.id == LEGACY_PROJECT_ID) reconciled[project.id] = project
            } else if (isSafeProjectId(project.id)) {
                projectRoot(project).mkdirs()
                val projectDirectory = projectRoot(project)
                val metadata = readProjectMetadata(projectDirectory)
                val durable = metadata ?: run {
                    preserveCorruptProjectMetadata(projectDirectory)
                    project.also(::writeProjectMetadata)
                }
                reconciled[durable.id] = durable
            }
        }
        if (LEGACY_PROJECT_ID !in reconciled) {
            reconciled[LEGACY_PROJECT_ID] = originalWorkspace(now)
        }

        projectsDirectory.listFiles()
            .orEmpty()
            .filter { directory -> directory.isDirectory && isSafeProjectId(directory.name) }
            .sortedBy(File::getName)
            .forEach { directory ->
                if (directory.name !in reconciled) {
                    val recovered = readProjectMetadata(directory) ?: recoverDirectoryMetadata(directory, now)
                    reconciled[recovered.id] = recovered
                }
            }
        return reconciled.values.toList()
    }

    private fun originalWorkspace(now: Long): ResearchProject =
        ResearchProject(
            id = LEGACY_PROJECT_ID,
            name = DEFAULT_PROJECT_NAME,
            objective = DEFAULT_PROJECT_OBJECTIVE,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            usesLegacyWorkspace = true
        )

    private fun recoverDirectoryMetadata(directory: File, now: Long): ResearchProject {
        preserveCorruptProjectMetadata(directory)
        val timestamp = directory.lastModified().takeIf { it > 0L } ?: now
        val project = ResearchProject(
            id = directory.name,
            name = "Recovered project (${directory.name})".take(MAX_NAME_LENGTH),
            objective = RECOVERED_PROJECT_OBJECTIVE,
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
            usesLegacyWorkspace = false
        )
        writeProjectMetadata(project)
        return project
    }

    private fun readProjectMetadata(directory: File): ResearchProject? =
        runCatching {
            val file = File(directory, PROJECT_METADATA_FILE_NAME)
            val properties = Properties().apply {
                file.inputStream().buffered().use(::load)
            }
            require(properties.getProperty("schemaVersion") == SCHEMA_VERSION.toString())
            val id = properties.getProperty("id")
            require(id == directory.name && isSafeProjectId(id))
            val createdAt = properties.getProperty("createdAt")?.toLongOrNull()
                ?.takeIf { it > 0L } ?: error("Invalid project creation time")
            val updatedAt = properties.getProperty("updatedAt")?.toLongOrNull()
                ?.takeIf { it >= createdAt } ?: error("Invalid project update time")
            ResearchProject(
                id = id,
                name = validateName(properties.getProperty("name")),
                objective = validateObjective(properties.getProperty("objective", "")),
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = updatedAt,
                usesLegacyWorkspace = false
            )
        }.getOrNull()

    private fun preserveCorruptProjectMetadata(directory: File) {
        val metadataFile = File(directory, PROJECT_METADATA_FILE_NAME)
        if (!metadataFile.isFile) return
        val stem = "$PROJECT_METADATA_FILE_NAME.corrupt-${clockMillis().coerceAtLeast(1L)}"
        var backup = File(directory, stem)
        var suffix = 2
        while (backup.exists()) {
            backup = File(directory, "$stem-$suffix")
            suffix += 1
        }
        runCatching { metadataFile.copyTo(backup, overwrite = false) }
    }

    private fun writeProjectMetadata(project: ResearchProject) {
        if (project.usesLegacyWorkspace) return
        val directory = projectRoot(project)
        directory.mkdirs()
        val properties = Properties().apply {
            setProperty("schemaVersion", SCHEMA_VERSION.toString())
            setProperty("id", project.id)
            setProperty("name", project.name)
            setProperty("objective", project.objective)
            setProperty("createdAt", project.createdAtEpochMillis.toString())
            setProperty("updatedAt", project.updatedAtEpochMillis.toString())
        }
        writePropertiesAtomically(
            properties = properties,
            destination = File(directory, PROJECT_METADATA_FILE_NAME),
            comment = "Robot Kinematics Lab research project"
        )
    }

    private fun readIndexOrNull(): ProjectIndex? =
        runCatching {
            val properties = Properties().apply {
                indexFile.inputStream().buffered().use(::load)
            }
            require(properties.getProperty("schemaVersion") == SCHEMA_VERSION.toString())
            val ids =
                properties.getProperty("projectIds", "")
                    .split(UNIT_SEPARATOR)
                    .filter(String::isNotBlank)
                    .distinct()
            require(ids.isNotEmpty())
            val projects =
                ids.map { id ->
                    val prefix = "project.$id."
                    val createdAt = properties.getProperty("${prefix}createdAt")?.toLongOrNull()
                        ?.takeIf { it > 0L } ?: error("Invalid project creation time")
                    val updatedAt = properties.getProperty("${prefix}updatedAt")?.toLongOrNull()
                        ?.takeIf { it >= createdAt } ?: error("Invalid project update time")
                    ResearchProject(
                        id = id,
                        name = validateName(properties.getProperty("${prefix}name")),
                        objective = validateObjective(properties.getProperty("${prefix}objective", "")),
                        createdAtEpochMillis = createdAt,
                        updatedAtEpochMillis = updatedAt,
                        usesLegacyWorkspace = properties.getProperty("${prefix}legacy", "false").toBooleanStrict()
                    )
                }
            require(projects.any { it.id == LEGACY_PROJECT_ID && it.usesLegacyWorkspace })
            val requestedActive = properties.getProperty("activeProjectId", LEGACY_PROJECT_ID)
            val active = requestedActive.takeIf { id -> projects.any { it.id == id } } ?: LEGACY_PROJECT_ID
            ProjectIndex(activeProjectId = active, projects = projects)
        }.getOrNull()

    private fun writeIndex(index: ProjectIndex) {
        globalRoot.mkdirs()
        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                setProperty("activeProjectId", index.activeProjectId)
                setProperty("projectIds", index.projects.joinToString(UNIT_SEPARATOR.toString()) { it.id })
                index.projects.forEach { project ->
                    val prefix = "project.${project.id}."
                    setProperty("${prefix}name", project.name)
                    setProperty("${prefix}objective", project.objective)
                    setProperty("${prefix}createdAt", project.createdAtEpochMillis.toString())
                    setProperty("${prefix}updatedAt", project.updatedAtEpochMillis.toString())
                    setProperty("${prefix}legacy", project.usesLegacyWorkspace.toString())
                }
            }
        writePropertiesAtomically(properties, indexFile, "Robot Kinematics Lab research projects")
    }

    private fun writePropertiesAtomically(
        properties: Properties,
        destination: File,
        comment: String
    ) {
        AtomicFilePublisher.write(destination) { temporary ->
            temporary.outputStream().buffered().use { output ->
                properties.store(output, comment)
            }
        }
    }

    private fun isSafeProjectId(value: String): Boolean =
        value.length <= MAX_PROJECT_ID_LENGTH && SAFE_PROJECT_ID.matches(value)

    private fun validateName(value: String?): String {
        val raw = value.orEmpty()
        require(raw.none(Char::isISOControl)) {
            "Project name must not contain control characters."
        }
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotBlank()) { "Project name must not be blank." }
        require(clean.length <= MAX_NAME_LENGTH) { "Project name is too long." }
        return clean
    }

    private fun validateObjective(value: String?): String {
        val raw = value.orEmpty()
        require(
            raw.none { character ->
                character.isISOControl() && character != '\n' && character != '\t'
            }
        ) { "Project objective must not contain control characters." }
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        require(clean.length <= MAX_OBJECTIVE_LENGTH) { "Project objective is too long." }
        return clean
    }

    private fun safeIdStem(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_ID_STEM_LENGTH)
            .ifBlank { "research-project" }

    private data class ProjectIndex(
        val activeProjectId: String,
        val projects: List<ResearchProject>
    )

    companion object {
        const val LEGACY_PROJECT_ID = "layer-1-research"
        const val DEFAULT_PROJECT_NAME = "Layer 1 Dissertation"
        const val DEFAULT_PROJECT_OBJECTIVE =
            "Existing robots, datasets, diagnostics, models and scientific evidence."
        private const val INDEX_FILE_NAME = "project-index.properties"
        private const val PROJECTS_DIRECTORY_NAME = "projects"
        private const val PROJECT_METADATA_FILE_NAME = "project.properties"
        private const val UNIT_SEPARATOR = '\u001F'
        private const val SCHEMA_VERSION = 1
        private const val MAX_NAME_LENGTH = 80
        private const val MAX_OBJECTIVE_LENGTH = 280
        private const val MAX_ID_STEM_LENGTH = 36
        private const val MAX_PROJECT_ID_LENGTH = 96
        private const val RECOVERED_PROJECT_OBJECTIVE =
            "Recovered after project index repair; verify its name and objective."
        private val SAFE_PROJECT_ID = Regex("[a-z0-9][a-z0-9-]*")
        private val INDEX_LOCK = Any()

        fun globalRootFor(context: Context): File {
            val documentsRoot =
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
            return File(documentsRoot, "RobotKinematicsLab")
        }

        fun resolveActiveProjectRoot(context: Context): File =
            ResearchProjectRepository(context).activeProjectRoot()
    }
}
