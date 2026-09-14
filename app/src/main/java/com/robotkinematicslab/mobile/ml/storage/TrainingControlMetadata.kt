package com.robotkinematicslab.mobile.ml.storage

import com.robotkinematicslab.mobile.ml.training.StoredTrainingControls
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import java.util.Properties

internal object TrainingControlMetadata {
    fun write(p: Properties, c: StoredTrainingControls) {
        p.setProperty("controlsSchemaVersion","1")
        p.setProperty("hiddenUnits",c.hiddenUnits.toString())
        p.setProperty("earlyStoppingPatience",c.earlyStoppingPatience.toString())
        p.setProperty("effectiveWorkerBatches",c.workerBatchCounts.entries.joinToString(";") { "${it.key}:${it.value}" })
        // These keys predate the complete-controls contract, but are required once it is declared.
        p.setProperty("requestedModelKind",c.modelKind.name)
        p.setProperty("resourceMode",c.resourceMode.name)
        p.setProperty("epochs",c.epochs.toString())
        p.setProperty("batchSize",c.batchSize.toString())
        p.setProperty("learningRate",c.learningRate.toString())
        p.setProperty("l2Regularization",c.l2Regularization.toString())
        p.setProperty("randomSeed",c.randomSeed.toString())
        p.setProperty("workerCount",c.requestedWorkers.toString())
    }

    fun read(p: Properties): StoredTrainingControls? {
        val version = p.getProperty("controlsSchemaVersion") ?: return null
        require(version == "1") { "Unsupported stored training controls." }
        fun value(key: String) = requireNotNull(p.getProperty(key)) { "Missing stored control: $key" }
        val pairs = value("effectiveWorkerBatches").takeIf(String::isNotBlank)?.split(';').orEmpty().map { item ->
            val parts = item.split(':')
            require(parts.size == 2)
            parts[0].toInt() to parts[1].toLong()
        }
        require(pairs.map { it.first }.distinct().size == pairs.size) { "Duplicate worker evidence." }
        return StoredTrainingControls(TrainingModelKind.valueOf(value("requestedModelKind")),TrainingResourceMode.valueOf(value("resourceMode")),
            value("epochs").toInt(),value("batchSize").toInt(),value("learningRate").toDouble(),value("l2Regularization").toDouble(),
            value("hiddenUnits").toInt(),value("randomSeed").toInt(),value("earlyStoppingPatience").toInt(),value("workerCount").toInt(),pairs.toMap())
    }
}
