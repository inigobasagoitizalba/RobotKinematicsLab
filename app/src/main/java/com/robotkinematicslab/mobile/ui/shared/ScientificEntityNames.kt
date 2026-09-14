package com.robotkinematicslab.mobile.ui.shared

import java.io.File
import java.util.Locale

data class ScientificEntityLabel(
    val primary: String,
    val qualifier: String? = null,
    val technicalId: String
) {
    init {
        require(primary.isNotBlank())
        require(technicalId.isNotBlank())
    }
}

/** Keeps stable identifiers intact while deriving a compact label from real stored metadata. */
object ScientificEntityNameResolver {
    fun dataset(path: String, storedName: String): ScientificEntityLabel = ScientificEntityLabel(
        primary = storedName.trim().replace('_', ' ').replace('-', ' ').ifBlank { "Scientific dataset" },
        technicalId = path
    )

    fun model(path: String, preferredName: String? = null): ScientificEntityLabel {
        val technicalId = File(path).name.ifBlank { path.trim() }
        require(technicalId.isNotBlank()) { "A model path or identifier is required." }
        val preferred = preferredName?.trim()?.takeIf(String::isNotBlank)
        val stem = technicalId.substringBeforeLast('.', technicalId)
        val readable = preferred ?: readableIdentifier(stem, fallback = "Stored model")
        val qualifier =
            listOfNotNull(
                CYCLE.find(stem)?.groupValues?.getOrNull(1)?.let { "cycle $it" },
                ATTEMPT.find(stem)?.groupValues?.getOrNull(1)?.let { "attempt $it" }
            ).takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
        return ScientificEntityLabel(readable, qualifier, technicalId)
    }

    fun run(runId: String, runName: String?): ScientificEntityLabel {
        require(runId.isNotBlank()) { "A run identifier is required." }
        val readable =
            runName?.trim()?.takeIf(String::isNotBlank)
                ?: readableIdentifier(runId, fallback = "Stored training run")
        return ScientificEntityLabel(readable, technicalId = runId)
    }

    internal fun readableIdentifier(raw: String, fallback: String): String {
        val words =
            raw
                .replace(Regex("(?i)(?:^|[-_])(cycle|attempt)[-_]?\\d+(?=$|[-_])"), " ")
                .split(Regex("[-_\\s]+"))
                .filter { token ->
                    token.isNotBlank() &&
                        !token.matches(Regex("\\d{10,}")) &&
                        token.lowercase(Locale.ROOT) !in GENERATED_ONLY_TOKENS
                }
        if (words.isEmpty()) return fallback
        return words
            .joinToString(" ") { token -> token.lowercase(Locale.ROOT) }
            .replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase(Locale.ROOT) else character.toString()
            }
    }

    private val CYCLE = Regex("(?i)(?:^|[-_])cycle[-_]?(\\d+)(?:$|[-_])")
    private val ATTEMPT = Regex("(?i)(?:^|[-_])attempt[-_]?(\\d+)(?:$|[-_])")
    private val GENERATED_ONLY_TOKENS = setOf("training", "model", "rklm")
}
