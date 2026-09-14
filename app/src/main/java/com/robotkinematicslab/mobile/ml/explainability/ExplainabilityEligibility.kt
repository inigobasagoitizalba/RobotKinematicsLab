package com.robotkinematicslab.mobile.ml.explainability

import com.robotkinematicslab.mobile.ml.storage.*
import java.io.File

data class ExplainabilityEligibility(val availableTestRows:Int,val effectiveSamples:Int,val modelLabel:String,val modelFileSha256:String)
object ExplainabilityPreflight {
    const val DEFAULT_SAMPLES=512
    const val DEFAULT_STEPS=32
    const val MIN_SAMPLES=16
    const val MAX_SAMPLES=5000
    const val MIN_STEPS=8
    const val MAX_STEPS=256
    fun controlErrors(samples:Int?,steps:Int?):List<String> = buildList {
        if(samples==null || samples !in MIN_SAMPLES..MAX_SAMPLES) add("Held-out sample maximum must be an integer from $MIN_SAMPLES to $MAX_SAMPLES.")
        if(steps==null || steps !in MIN_STEPS..MAX_STEPS) add("Integration steps must be an integer from $MIN_STEPS to $MAX_STEPS.")
    }
    fun validate(repository:TrainingStorageRepository,run:TrainingRunSummary,path:String,samples:Int,steps:Int,cancelled:()->Boolean={false}):ExplainabilityEligibility {
        require(controlErrors(samples,steps).isEmpty()) { controlErrors(samples,steps).joinToString(" ") }
        require(path in run.modelPaths) { "Choose a model belonging to the selected run." }
        require(File(path).isFile) { "The selected model file is missing." }
        val digest=modelDigest(File(path),cancelled)
        val model=repository.loadModel(File(path))
        val (_,split)=TrainingInferenceReplay.rebuild(run,model,cancellationRequested=cancelled)
        require(split.testIndices.isNotEmpty()) { "This model has no verified test rows to explain." }
        require(modelDigest(File(path),cancelled)==digest) { "The model changed during validation. Select it again." }
        return ExplainabilityEligibility(split.testIndices.size,minOf(samples,split.testIndices.size),model.featureSelectionName,digest)
    }
}

/** The work counter contains three preparation units and one aggregation unit in addition to predictions. */
data class ExplainabilityWorkCounts(val completedPredictions:Int,val totalPredictions:Int,val completedAuxiliary:Int,val totalAuxiliary:Int=4)
fun ExplainabilityProgress.workCounts():ExplainabilityWorkCounts {
    require(totalWork>=4 && completedWork in 0..totalWork)
    val total=totalWork-4
    val predictions=when(phase) {
        ExplainabilityPhase.EXPLAINING_SAMPLES -> (completedWork-3).coerceIn(0,total)
        ExplainabilityPhase.AGGREGATING,ExplainabilityPhase.COMPLETED -> total
        else -> 0
    }
    return ExplainabilityWorkCounts(predictions,total,(completedWork-predictions).coerceIn(0,4))
}

private fun modelDigest(file:File,cancelled:()->Boolean):String {
    val digest=java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer=ByteArray(65536)
        while(true) {
            if(cancelled() || Thread.currentThread().isInterrupted) throw ExplainabilityCancelledException()
            val count=input.read(buffer);if(count<0) break
            digest.update(buffer,0,count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
