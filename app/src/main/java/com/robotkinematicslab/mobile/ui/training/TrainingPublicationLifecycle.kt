package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.training.LocalTrainingPhase
import com.robotkinematicslab.mobile.ml.training.LocalTrainingProgress

internal data class TrainingPublicationSnapshot(val revision: Long, val progress: LocalTrainingProgress, val failedStage: Int? = null)

/** An engine finishing evaluation is not a saved run. Publication alone owns terminal success. */
internal class TrainingPublicationLifecycle {
    private var evaluationFinished = false
    private var terminal = false
    private var publishing = false
    var current = TrainingPublicationSnapshot(0,LocalTrainingProgress(LocalTrainingPhase.IDLE,0,6,null,null,null,"Preparing training."))
        private set
    @Synchronized fun engine(update: LocalTrainingProgress): TrainingPublicationSnapshot {
        if (terminal || publishing) return current
        if (update.phase == LocalTrainingPhase.COMPLETED) evaluationFinished = true
        val phase = if (update.phase == LocalTrainingPhase.COMPLETED) LocalTrainingPhase.EVALUATING else update.phase
        current = TrainingPublicationSnapshot(current.revision+1,update.copy(phase=phase,completedWorkUnits=trainingStageIndex(phase),totalWorkUnits=6))
        return current
    }
    @Synchronized fun saving(): TrainingPublicationSnapshot {
        check(evaluationFinished && !terminal && !publishing) { "Evidence cannot be saved before evaluation finishes." }
        publishing = true
        return update(LocalTrainingPhase.SAVING,5,"Saving models, normalization, provenance and iteration history.")
    }
    @Synchronized fun completed(): TrainingPublicationSnapshot {
        check(publishing && !terminal) { "A run is complete only after its publication step succeeds." }
        terminal = true
        return update(LocalTrainingPhase.COMPLETED,6,"Training evidence saved successfully.")
    }
    @Synchronized fun failed(message: String): TrainingPublicationSnapshot {
        if (terminal) return current
        val stage = trainingStageIndex(current.progress.phase)
        terminal = true
        current = TrainingPublicationSnapshot(current.revision+1,current.progress.copy(phase=LocalTrainingPhase.FAILED,completedWorkUnits=stage,totalWorkUnits=6,message=message),stage)
        return current
    }
    @Synchronized fun isCurrent(snapshot: TrainingPublicationSnapshot): Boolean = snapshot.revision == current.revision
    private fun update(phase:LocalTrainingPhase,count:Int,message:String):TrainingPublicationSnapshot {
        current = TrainingPublicationSnapshot(current.revision+1,LocalTrainingProgress(phase,count,6,null,null,null,message))
        return current
    }
}

internal fun trainingStageIndex(phase: LocalTrainingPhase): Int = when(phase) {
    LocalTrainingPhase.IDLE, LocalTrainingPhase.READING_DATASET -> 0
    LocalTrainingPhase.PREPARING_SPLITS -> 1
    LocalTrainingPhase.TRAINING_BASELINE, LocalTrainingPhase.TRAINING_CONTEXT -> 2
    LocalTrainingPhase.EVALUATING -> 4
    LocalTrainingPhase.SAVING -> 5
    LocalTrainingPhase.COMPLETED -> 6
    LocalTrainingPhase.FAILED -> 0 // The snapshot retains the actual failed stage separately.
}
