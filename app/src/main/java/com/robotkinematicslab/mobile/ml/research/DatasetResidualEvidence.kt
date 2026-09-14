package com.robotkinematicslab.mobile.ml.research

import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.DeterministicCsvRowSampler
import java.io.File
import java.util.Random
import java.util.concurrent.CancellationException
import kotlin.math.ceil

data class DatasetResidualObservation(
    val sampleId: Long,
    val robotId: String,
    val initialErrorMeters: Double,
    val finalErrorMeters: Double?,
    val accepted: Boolean
) {
    init {
        require(robotId.isNotBlank())
        require(initialErrorMeters.isFinite() && initialErrorMeters >= 0.0)
        require(finalErrorMeters == null || (finalErrorMeters.isFinite() && finalErrorMeters >= 0.0))
    }
}

data class DatasetResidualSlice(
    val robotId: String,
    val sampleCount: Int,
    val acceptedRate: Double,
    val finiteResidualRate: Double,
    val medianFinalErrorMeters: Double
)

data class DatasetResidualEvidence(
    val observations: List<DatasetResidualObservation>,
    val sampleCount: Int,
    val finiteResidualCount: Int,
    val acceptedRate: Double,
    val nonFiniteResidualRate: Double,
    val medianInitialErrorMeters: Double,
    val medianFinalErrorMeters: Double,
    val p95FinalErrorMeters: Double,
    val p99FinalErrorMeters: Double,
    val cumulativeInitialErrorMeters: Double,
    val cumulativeFiniteFinalErrorMeters: Double,
    val finiteResidualReductionFraction: Double,
    val byRobot: List<DatasetResidualSlice>
)

object DatasetResidualEvidenceCalculator {

    fun calculate(observations: List<DatasetResidualObservation>): DatasetResidualEvidence {
        require(observations.isNotEmpty())
        require(observations.map(DatasetResidualObservation::sampleId).distinct().size == observations.size)
        val initial = observations.map(DatasetResidualObservation::initialErrorMeters).sorted()
        val final = observations.mapNotNull(DatasetResidualObservation::finalErrorMeters).sorted()
        val cumulativeInitialForFinite =
            accurateSum(observations.asSequence().filter { it.finalErrorMeters != null }.map { it.initialErrorMeters })
        val cumulativeFinal = accurateSum(final.asSequence())
        return DatasetResidualEvidence(
            observations = observations,
            sampleCount = observations.size,
            finiteResidualCount = final.size,
            acceptedRate = observations.count(DatasetResidualObservation::accepted).toDouble() / observations.size,
            nonFiniteResidualRate = 1.0 - final.size.toDouble() / observations.size,
            medianInitialErrorMeters = quantile(initial, 0.50),
            medianFinalErrorMeters = quantileOrNaN(final, 0.50),
            p95FinalErrorMeters = quantileOrNaN(final, 0.95),
            p99FinalErrorMeters = quantileOrNaN(final, 0.99),
            cumulativeInitialErrorMeters = accurateSum(observations.asSequence().map { it.initialErrorMeters }),
            cumulativeFiniteFinalErrorMeters = cumulativeFinal,
            finiteResidualReductionFraction =
                if (cumulativeInitialForFinite > 0.0) {
                    (cumulativeInitialForFinite - cumulativeFinal) / cumulativeInitialForFinite
                } else {
                    0.0
                },
            byRobot = observations.groupBy(DatasetResidualObservation::robotId).toSortedMap().map { (robot, rows) ->
                val finite = rows.mapNotNull(DatasetResidualObservation::finalErrorMeters).sorted()
                DatasetResidualSlice(
                    robotId = robot,
                    sampleCount = rows.size,
                    acceptedRate = rows.count(DatasetResidualObservation::accepted).toDouble() / rows.size,
                    finiteResidualRate = finite.size.toDouble() / rows.size,
                    medianFinalErrorMeters = quantileOrNaN(finite, 0.50)
                )
            }
        )
    }

    private fun quantileOrNaN(values: List<Double>, probability: Double): Double =
        if (values.isEmpty()) Double.NaN else quantile(values, probability)

    /** Neumaier summation keeps small residuals visible beside much larger errors. */
    private fun accurateSum(values: Sequence<Double>): Double {
        var sum = 0.0
        var correction = 0.0
        values.forEach { value ->
            val next = sum + value
            correction +=
                if (kotlin.math.abs(sum) >= kotlin.math.abs(value)) {
                    (sum - next) + value
                } else {
                    (value - next) + sum
                }
            sum = next
        }
        return sum + correction
    }

    private fun quantile(sorted: List<Double>, probability: Double): Double {
        val position = probability.coerceIn(0.0, 1.0) * sorted.lastIndex
        val lower = position.toInt()
        val upper = ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val fraction = position - lower
        return sorted[lower] * (1.0 - fraction) + sorted[upper] * fraction
    }
}

/** Uniform bounded reader so ordered robot blocks do not bias residual evidence. */
object DatasetResidualCsvReader {

    fun read(
        file: File,
        maximumRows: Int,
        samplingSeed: Int,
        expectedDataRowCount: Long? = null,
        cancellationRequested: () -> Boolean = { false }
    ): DatasetResidualEvidence {
        require(file.isFile)
        require(maximumRows > 0)
        require(expectedDataRowCount == null || expectedDataRowCount in 1..Int.MAX_VALUE.toLong())
        val reservoir = ArrayList<DatasetResidualObservation>(maximumRows)
        val random = Random(samplingSeed.toLong())
        val selectedRawRows = expectedDataRowCount?.let { expected ->
            DeterministicCsvRowSampler.indices(expected.toInt(), maximumRows, samplingSeed)
        }
        var validSeen = 0
        file.bufferedReader().use { reader ->
            val header = ScientificDatasetTrainingReader.parseCsvLine(reader.readLine() ?: error("Dataset is empty."))
            val column = header.withIndex().associate { it.value to it.index }
            REQUIRED_COLUMNS.forEach { require(it in column) { "Dataset residual audit is missing '$it'." } }
            var sourceRow = 0L
            while (true) {
                if (cancellationRequested()) {
                    throw CancellationException(CANCELLATION_MESSAGE)
                }
                val line = reader.readLine() ?: break
                sourceRow++
                if (selectedRawRows != null && sourceRow.toInt() - 1 !in selectedRawRows) continue
                val values = runCatching { ScientificDatasetTrainingReader.parseCsvLine(line) }.getOrNull() ?: continue
                val row = parse(values, column, sourceRow) ?: continue
                validSeen++
                if (reservoir.size < maximumRows) {
                    reservoir += row
                } else {
                    val replacement = random.nextInt(validSeen)
                    if (replacement < maximumRows) reservoir[replacement] = row
                }
            }
            if (expectedDataRowCount != null) {
                require(sourceRow == expectedDataRowCount) {
                    "Dataset manifest declares $expectedDataRowCount rows but the CSV contains $sourceRow."
                }
            }
        }
        return DatasetResidualEvidenceCalculator.calculate(reservoir.sortedBy(DatasetResidualObservation::sampleId))
    }

    private fun parse(values: List<String>, column: Map<String, Int>, sourceRow: Long): DatasetResidualObservation? =
        runCatching {
            fun text(name: String) = values[column.getValue(name)]
            val initial = text("initialError").toDouble().also { require(it.isFinite() && it >= 0.0) }
            val rawFinal = text("finalError").toDouble()
            val final = rawFinal.takeIf { it.isFinite() && it >= 0.0 }
            DatasetResidualObservation(
                sampleId = sourceRow,
                robotId = text("robotId").also { require(it.isNotBlank()) },
                initialErrorMeters = initial,
                finalErrorMeters = final,
                accepted = text("solverAccepted").toBooleanStrict()
            )
        }.getOrNull()

    private val REQUIRED_COLUMNS = listOf("robotId", "initialError", "finalError", "solverAccepted")
    internal const val CANCELLATION_MESSAGE = "Dataset residual audit was cancelled."
}
