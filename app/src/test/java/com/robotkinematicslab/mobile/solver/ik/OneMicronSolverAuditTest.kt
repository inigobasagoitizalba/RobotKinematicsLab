package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OneMicronSolverAuditTest {

    @Test
    fun compareCandidateOneMicronContractsAcrossAllPresetRobots() {
        assumeTrue(System.getenv("RKL_ONE_MICRON_SOLVER_AUDIT") == "1")
        val casesPerRobot = System.getenv("RKL_ONE_MICRON_CASES_PER_ROBOT")?.toIntOrNull() ?: 100
        val output = File(
            System.getenv("RKL_ONE_MICRON_SOLVER_OUTPUT")
                ?: "audit-artifacts/one-micron-ik/solver-contract-audit.csv"
        )
        output.parentFile?.mkdirs()
        val configs =
            listOf(
                "balanced" to IKConfig(maxIterations = 400, tolerance = ONE_MICRON_METERS, damping = 0.05, maxStep = 0.02),
                "precision" to IKConfig(maxIterations = 800, tolerance = ONE_MICRON_METERS, damping = 0.01, maxStep = 0.02),
                "deep_precision" to IKConfig(maxIterations = 1_600, tolerance = ONE_MICRON_METERS, damping = 0.005, maxStep = 0.01)
            )
        val lines = mutableListOf(
            "config,robot_id,joints,cases,successes,success_rate,median_error,p95_error,max_error,mean_iterations,elapsed_ms"
        )
        var totalSuccesses = 0

        configs.forEach { (configName, config) ->
            DatasetRobotPresets().buildDefaults().forEach { saved ->
                val random =
                    ScientificRandom(
                        ScientificRandomProtocol.deriveSeed(
                            2604,
                            "one-micron-solver-audit",
                            "paired-solver-configurations",
                            saved.id
                        )
                    )
                val fk = ForwardKinematicsSolver()
                val solver = InverseKinematicsSolver(fk, config)
                val errors = mutableListOf<Double>()
                var successes = 0
                var iterations = 0L
                val started = System.nanoTime()
                repeat(casesPerRobot) {
                    val source = RobotState(saved.robot.joints.map { joint -> random.nextDouble(joint.minValue, joint.maxValue) })
                    val seed = RobotState(saved.robot.joints.map { joint -> random.nextDouble(joint.minValue, joint.maxValue) })
                    val targetFk = fk.solve(saved.robot, source)
                    assertTrue("A preset target must have valid FK; do not omit this requested audit case.", targetFk.status == FKStatus.SUCCESS || targetFk.status == FKStatus.SUCCESS_WITH_WARNING)
                    val result = solver.solve(saved.robot, seed, targetFk.endEffectorPosition)
                    assertTrue(
                        "$configName/${saved.id}: the returned state must never contain NaN or infinity",
                        result.state.jointValues.all(Double::isFinite)
                    )
                    result.state.jointValues.zip(saved.robot.joints).forEachIndexed { index, (value, joint) ->
                        assertTrue(
                            "$configName/${saved.id}: joint $index escaped its physical limits",
                            value >= joint.minValue && value <= joint.maxValue
                        )
                    }

                    val independentlyChecked = fk.solve(saved.robot, result.state)
                    assertTrue(
                        "$configName/${saved.id}: returned state must remain valid for independent FK",
                        independentlyChecked.status == FKStatus.SUCCESS ||
                            independentlyChecked.status == FKStatus.SUCCESS_WITH_WARNING
                    )
                    val independentError =
                        (independentlyChecked.endEffectorPosition - targetFk.endEffectorPosition).norm()
                    assertTrue(
                        "$configName/${saved.id}: independent residual must remain finite",
                        independentError.isFinite()
                    )
                    assertEquals(
                        "$configName/${saved.id}: solver residual must equal independent FK residual",
                        independentError,
                        result.finalError,
                        RESIDUAL_AGREEMENT_METERS
                    )

                    val successStatus =
                        result.status == IKStatus.SUCCESS || result.status == IKStatus.SUCCESS_WITH_WARNING
                    if (successStatus || result.converged) {
                        assertTrue(
                            "$configName/${saved.id}: accepted result exceeded the one-micron contract",
                            successStatus && result.converged && independentError <= ONE_MICRON_METERS
                        )
                    }
                    val success =
                        successStatus && result.converged && independentError <= ONE_MICRON_METERS
                    if (success) successes++
                    if (result.finalError.isFinite()) errors += result.finalError
                    iterations += result.iterations
                }
                totalSuccesses += successes
                val sorted = errors.sorted()
                val median = sorted.quantile(0.50)
                val p95 = sorted.quantile(0.95)
                val max = sorted.maxOrNull() ?: Double.NaN
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                lines +=
                    listOf(
                        configName,
                        saved.id,
                        saved.robot.joints.size,
                        casesPerRobot,
                        successes,
                        successes.toDouble() / casesPerRobot,
                        median,
                        p95,
                        max,
                        iterations.toDouble() / casesPerRobot,
                        elapsedMs
                    ).joinToString(",")
            }
        }
        output.writeText(lines.joinToString("\n", postfix = "\n"))
        assertTrue("At least one preset case must reach the one-micron contract", totalSuccesses > 0)
    }

    private fun List<Double>.quantile(fraction: Double): Double {
        if (isEmpty()) return Double.NaN
        return get(((size - 1) * fraction).toInt().coerceIn(indices))
    }

    companion object {
        const val ONE_MICRON_METERS = 1e-6
        const val RESIDUAL_AGREEMENT_METERS = 1e-12
    }
}
