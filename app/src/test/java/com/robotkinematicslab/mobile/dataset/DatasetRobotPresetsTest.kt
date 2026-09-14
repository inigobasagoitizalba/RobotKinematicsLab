package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTopologyAuditor
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetRobotPresetsTest {

    @Test
    fun defaultsContainTenUniqueValidBenchmarkRobots() {
        val robots = DatasetRobotPresets().buildDefaults()
        val validator = RobotDefinitionValidator()
        val auditor = DiagnosticTopologyAuditor()

        assertEquals(10, robots.size)
        assertEquals(robots.size, robots.map { it.id }.distinct().size)
        assertEquals(robots.size, robots.map { it.robot.name }.distinct().size)
        assertTrue(robots.all { validator.validate(it.robot).isValid })
        assertTrue(robots.all { auditor.audit(it.robot).acceptedForBenchmark })
        assertEquals(DatasetRobotPresets.SYNTHETIC_PRESET_IDS, robots.map { it.id }.toSet())
        assertTrue(robots.all { it.robot.name.startsWith("Synthetic ") })
        assertTrue(robots.all { it.robot.name.contains("mathematical preset") })
    }
}
