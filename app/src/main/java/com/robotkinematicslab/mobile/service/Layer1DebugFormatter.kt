package com.robotkinematicslab.mobile.service

import com.robotkinematicslab.mobile.domain.record.ExecutionRecord
import com.robotkinematicslab.mobile.domain.record.FailureRecord
import com.robotkinematicslab.mobile.domain.record.OperationType
import java.util.Locale

class Layer1DebugFormatter {

    fun formatExecution(record: ExecutionRecord): String {
        return buildString {
            appendLine("=== Layer 1 Debug Execution ===")
            appendLine("Operation: ${record.operationType}")
            appendLine("Robot: ${record.robotName}")
            appendLine("Input State: ${formatVector(record.inputState.jointValues)}")

            record.targetPosition?.let {
                appendLine(
                    "Target Position: (${formatNumber(it.x)}, ${formatNumber(it.y)}, ${formatNumber(it.z)})"
                )
            }

            appendLine("Status: ${record.status}")
            appendLine("Classification: ${classifyExecution(record)}")

            record.finalError?.let {
                appendLine("Final Error: ${formatNumber(it)}")
            }

            record.iterations?.let {
                appendLine("Iterations: $it")
            }

            record.metadata?.let {
                appendLine("--- Metadata ---")
                appendLine("Solver: ${it.solverName}")
                appendLine("Max Iterations: ${it.maxIterations}")
                appendLine("Tolerance: ${formatNumber(it.tolerance)}")
                appendLine("Damping: ${formatNumber(it.damping)}")
                appendLine("Max Step: ${formatNumber(it.maxStep)}")
            }

            appendLine("===============================")
        }
    }

    fun formatFailure(record: FailureRecord): String {
        return buildString {
            appendLine("=== Layer 1 Failure ===")
            appendLine("Operation: ${record.operationType}")
            appendLine("Robot: ${record.robotName}")
            appendLine("Input State: ${formatVector(record.inputState.jointValues)}")

            record.targetPosition?.let {
                appendLine(
                    "Target Position: (${formatNumber(it.x)}, ${formatNumber(it.y)}, ${formatNumber(it.z)})"
                )
            }

            appendLine("Failure Type: ${record.failureType}")
            appendLine("Message: ${record.message}")
            appendLine("Severity: ${classifyFailure(record.failureType)}")
            appendLine("=======================")
        }
    }

    private fun classifyExecution(record: ExecutionRecord): String {
        return when (record.operationType) {
            OperationType.FK -> {
                when (record.status) {
                    "SUCCESS" -> "Accepted output"
                    "INVALID_INPUT" -> "Blocked input"
                    "NUMERICAL_FAILURE" -> "Rejected numerical output"
                    else -> "Unknown FK classification"
                }
            }

            OperationType.IK -> {
                when (record.status) {
                    "SUCCESS" -> "Accepted converged solution"
                    "MAX_ITERATIONS_REACHED" -> "Borderline / partial result"
                    "INVALID_INPUT" -> "Blocked input"
                    "NUMERICAL_FAILURE" -> "Rejected numerical output"
                    else -> "Unknown IK classification"
                }
            }
        }
    }

    private fun classifyFailure(failureType: String): String {
        return when (failureType) {
            "INVALID_INPUT" -> "Input rejection"
            "MAX_ITERATIONS_REACHED" -> "Non-converged result"
            "NUMERICAL_FAILURE" -> "Numerical rejection"
            else -> "Unclassified failure"
        }
    }

    private fun formatVector(values: List<Double>): String {
        return values.joinToString(
            separator = ", ",
            prefix = "[",
            postfix = "]"
        ) { formatNumber(it) }
    }

    private fun formatNumber(value: Double): String {
        return if (value.isFinite()) {
            String.format(Locale.US, "%.6f", value)
        } else {
            "NA"
        }
    }
}