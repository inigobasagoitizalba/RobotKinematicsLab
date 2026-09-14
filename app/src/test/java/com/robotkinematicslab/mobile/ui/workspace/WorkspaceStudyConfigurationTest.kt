package com.robotkinematicslab.mobile.ui.workspace

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.ui.help.ResearchGlossaryCatalog
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import com.robotkinematicslab.mobile.workspace.storage.RobotWorkspaceStudyRepository
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WorkspaceStudyConfigurationTest {
    @Test fun presetCardsMatchAppliedNumbersAndPreserveSeed() {
        val expected = listOf(Triple(2048,16,2), Triple(8192,20,4), Triple(32768,28,8))
        WorkspaceQualityPreset.entries.zip(expected).forEach { (preset, values) ->
            val config = preset.configuration(-17)
            assertEquals(values.first, config.sampleCount)
            assertEquals(values.second, config.voxelResolution)
            assertEquals(values.third, config.replicationCount)
            assertEquals(-17, config.randomSeed)
            val help = ResearchGlossaryCatalog.entries.single { it.term == "${preset.label} workspace preset" }
            assertTrue(help.whyItMatters.contains(preset.exactSummary))
        }
    }

    @Test fun manualBoundsAreAcceptedAndEveryInvalidDimensionIsRejected() {
        assertEquals(RobotWorkspaceAnalysisConfig(256,Int.MIN_VALUE,10,1), workspaceConfiguration("256","10","1",Int.MIN_VALUE.toString()))
        assertEquals(RobotWorkspaceAnalysisConfig(50000,Int.MAX_VALUE,32,8), workspaceConfiguration("50000","32","8",Int.MAX_VALUE.toString()))
        val invalid = listOf(
            listOf("255","20","4","42"), listOf("50001","20","4","42"),
            listOf("8192","9","4","42"), listOf("8192","33","4","42"),
            listOf("8192","20","0","42"), listOf("8192","20","9","42"),
            listOf("8192","20","4","2147483648"), listOf("8.192","20","4","42"),
            listOf("","20","4","42"), listOf("8192","20","4","NaN")
        )
        invalid.forEach { v -> assertThrows(IllegalArgumentException::class.java) { workspaceConfiguration(v[0],v[1],v[2],v[3]) } }
    }

    @Test fun workSummaryChangesWithGeometryAndUsesTheAnalyzerWorkerLimit() {
        val robot = DatasetRobotPresets().buildDefaults().first().robot
        val config = RobotWorkspaceAnalysisConfig()
        val summary = workspaceWorkSummary(robot,config,999)
        assertTrue(summary.contains("8192 FK evaluations total"))
        assertTrue(summary.contains("20 × 20 × 20 = 8000"))
        assertTrue(summary.contains("effective ${RobotWorkspaceAnalyzer.effectiveWorkerCount(999,8192)}"))
        val changed = robot.copy(name = "Changed", dhParameters = robot.dhParameters.map { it.copy(a = it.a * 2.0) })
        assertNotEquals(summary, workspaceWorkSummary(changed,config,999))
        assertEquals(1, RobotWorkspaceAnalyzer.effectiveWorkerCount(0,256))
        assertEquals(8, RobotWorkspaceAnalyzer.effectiveWorkerCount(999,256))
        assertThrows(IllegalArgumentException::class.java) { RobotWorkspaceAnalyzer.effectiveWorkerCount(1,0) }
    }

    @Test fun storedManualConfigurationAndActualWorkersSurvivePresetChanges() {
        val root = Files.createTempDirectory("workspace-effective-contract").toFile()
        try {
            val robot = DatasetRobotPresets().buildDefaults().first().robot
            val config = workspaceConfiguration("256","10","3","-17")
            val study = RobotWorkspaceAnalyzer().analyze(robot,config,requestedWorkerCount = 99)
            val repository = RobotWorkspaceStudyRepository(root)
            val summary = repository.save(study)
            WorkspaceQualityPreset.RESEARCH.configuration(42)
            val loaded = repository.load(summary)
            assertEquals(config,loaded.config)
            assertEquals(8,loaded.workerCount)
            assertEquals(study.samples,loaded.samples)
            val bytes = java.io.File(summary.dataPath).readBytes()
            assertThrows(IllegalArgumentException::class.java) { repository.save(study.copy(config=config.copy(sampleCount=257))) }
            assertArrayEquals(bytes,java.io.File(summary.dataPath).readBytes())
        } finally { root.deleteRecursively() }
    }
}
