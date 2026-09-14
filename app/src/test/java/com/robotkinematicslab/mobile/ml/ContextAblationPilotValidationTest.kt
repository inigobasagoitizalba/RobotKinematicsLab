package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.dataset.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContextAblationPilotValidationTest {
    private fun withPilot(check: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("context-pilot-validation").toFile()
        try {
            val file = directory.resolve("pilot.csv")
            val generated = ScientificDatasetGenerator().generate(
                DatasetGenerationConfig("pilot", DatasetRobotPresets().buildDefaults().take(4), 2, 2604,
                    DatasetTargetMode.MIXED, 0.65, DatasetFilterMode.ALL, false,
                    ikConfig = ContextAblationPilotValidator.solverConfig), file, 0, 0)
            assertTrue(generated.completed)
            check(file)
        } finally { directory.deleteRecursively() }
    }

    @Test fun currentGeneratorProducesCompatiblePilot() = withPilot { file ->
        ContextAblationPilotValidator.validate(file, 2)
    }

    @Test fun rejectsHistoricalSolverContractBeforeChangingAnyBytes() = withPilot { file ->
        val rows = file.readLines().toMutableList()
        val header = ScientificDatasetCsvWriter.HEADER
        val changed = rows[1].split(',').toMutableList()
        changed[header.indexOf("ikMaxIterations")] = "800"
        changed[header.indexOf("ikDamping")] = "0.01"
        changed[header.indexOf("ikMaxStep")] = "0.02"
        rows[1] = changed.joinToString(",")
        file.writeText(rows.joinToString("\n", postfix = "\n"))
        val before = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) { ContextAblationPilotValidator.validate(file, 2) }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun rejectsChangedProtocolColumnOrderRobotAndSeed() = withPilot { file ->
        val original = file.readText()
        for ((column, value) in listOf("randomProtocol" to "legacy", "robotId" to "other",
                                     "baseRandomSeed" to "99", "dhAMeters" to "9;9")) {
            val rows = original.lines().filter(String::isNotEmpty).toMutableList()
            val changed = rows[1].split(',').toMutableList()
            changed[ScientificDatasetCsvWriter.HEADER.indexOf(column)] = value
            rows[1] = changed.joinToString(",")
            file.writeText(rows.joinToString("\n", postfix = "\n"))
            assertThrows(IllegalArgumentException::class.java) { ContextAblationPilotValidator.validate(file, 2) }
        }
        file.writeText(ScientificDatasetCsvWriter.HEADER.reversed().joinToString(",") + "\n" + original.substringAfter('\n'))
        assertThrows(IllegalArgumentException::class.java) { ContextAblationPilotValidator.validate(file, 2) }
    }
}
