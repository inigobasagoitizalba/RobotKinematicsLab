package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.ml.training.TrainingControlContract
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ui.input.ScientificNumberParser
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import java.io.File
import java.util.Properties

internal data class TrainingControlsDraft(
    val model: TrainingModelKind = TrainingModelKind.AUTOMATIC,
    val resource: TrainingResourceMode = TrainingResourceMode.BALANCED,
    val split: TrainingSplitStrategy = TrainingSplitStrategy.SAMPLE_GROUPED,
    val epochs: String = "40", val batch: String = "128", val rate: String = "0.003", val l2: String = "0.0001",
    val hidden: String = "24", val seed: String = "42", val patience: String = "8"
) {
    val values get() = mapOf("epochs" to epochs,"batch" to batch,"learningRate" to rate,"l2" to l2,"hidden" to hidden,"seed" to seed,"patience" to patience)
    init { require(listOf(epochs,batch,rate,l2,hidden,seed,patience).all { it.length <= 128 }) }
    fun errors(): Map<String,String> {
        val errors = linkedMapOf<String,String>()
        values.forEach { (key,value) ->
            val validNumber = if (key == "learningRate" || key == "l2") ScientificNumberParser.parseDouble(value) != null else ScientificNumberParser.parseInt(value) != null
            if (!validNumber) errors[key] = if (key == "learningRate" || key == "l2") "Enter a finite number." else "Enter a whole number within the signed 32-bit range."
        }
        if (errors.isEmpty()) errors.putAll(TrainingControlContract.issues(configuration()))
        return errors
    }
    fun configuration() = LocalTrainingConfig("control preview","control preview",modelKind=model,resourceMode=resource,splitStrategy=split,
        epochs=requireNotNull(ScientificNumberParser.parseInt(epochs)),batchSize=requireNotNull(ScientificNumberParser.parseInt(batch)),
        learningRate=requireNotNull(ScientificNumberParser.parseDouble(rate)),l2Regularization=requireNotNull(ScientificNumberParser.parseDouble(l2)),
        hiddenUnits=requireNotNull(ScientificNumberParser.parseInt(hidden)),randomSeed=requireNotNull(ScientificNumberParser.parseInt(seed)),earlyStoppingPatience=requireNotNull(ScientificNumberParser.parseInt(patience)))
    val customized: Boolean get() = runCatching { !TrainingControlContract.usesDefaultControls(configuration()) }.getOrDefault(true)
}

/** Draft text is not scientific evidence: intermediate invalid edits survive reopening, but
 * errors() and the engine contract must accept it before execution. Per-project file ownership. */
internal class TrainingControlsDraftRepository(private val file: File) {
    fun load(): TrainingControlsDraft? {
        if (!file.exists()) return null
        val p = Properties().apply { file.inputStream().use(::load) }
        require(p.getProperty("version") == "1") { "Unsupported training control draft." }
        fun value(key:String) = requireNotNull(p.getProperty(key)) { "Missing draft field $key" }
        return TrainingControlsDraft(TrainingModelKind.valueOf(value("model")),TrainingResourceMode.valueOf(value("resource")),TrainingSplitStrategy.valueOf(value("split")),
            value("epochs"),value("batch"),value("learningRate"),value("l2"),value("hidden"),value("seed"),value("patience"))
    }
    fun save(draft: TrainingControlsDraft) {
        val p = Properties().apply {
            setProperty("version","1");setProperty("model",draft.model.name);setProperty("resource",draft.resource.name);setProperty("split",draft.split.name)
            draft.values.forEach { (key,value) -> setProperty(key,value) }
        }
        AtomicFilePublisher.write(file) { temporary ->
            temporary.outputStream().buffered().use { p.store(it,"Editable training controls") }
        }
    }
}
