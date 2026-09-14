package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.training.LocalTrainingPhase
import com.robotkinematicslab.mobile.ml.training.LocalTrainingProgress
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import org.junit.Assert.*
import org.junit.Test

class TrainingPublicationLifecycleTest {
    private fun update(phase:LocalTrainingPhase) = LocalTrainingProgress(phase,100,100,null,null,null,"phase")
    @Test fun computedResultRemainsActiveUntilPublicationThenCompletesSixOfSix() {
        val lifecycle=TrainingPublicationLifecycle()
        val computation=lifecycle.engine(update(LocalTrainingPhase.COMPLETED))
        assertEquals(LocalTrainingPhase.EVALUATING,computation.progress.phase)
        assertEquals(4,computation.progress.completedWorkUnits)
        val saving=lifecycle.saving()
        assertEquals(5,saving.progress.completedWorkUnits)
        assertEquals(DiagnosticTimelineStatus.RUNNING,trainingTimeline(saving.progress.phase).last().status)
        val complete=lifecycle.completed()
        assertEquals(6,complete.progress.completedWorkUnits);assertEquals(6,complete.progress.totalWorkUnits)
        assertTrue(trainingTimeline(complete.progress.phase).all { it.status == DiagnosticTimelineStatus.COMPLETE })
        assertFalse(lifecycle.isCurrent(computation))
        assertEquals(complete,lifecycle.engine(update(LocalTrainingPhase.TRAINING_CONTEXT)))
    }
    @Test fun cannotPublishEarlyAndFailedSaveNeverBecomesSuccessOrRevertsToAnOldCallback() {
        val lifecycle=TrainingPublicationLifecycle()
        assertThrows(IllegalStateException::class.java) { lifecycle.completed() }
        assertThrows(IllegalStateException::class.java) { lifecycle.saving() }
        lifecycle.engine(update(LocalTrainingPhase.COMPLETED));lifecycle.saving()
        val failure=lifecycle.failed("Disk full")
        assertEquals(5,failure.failedStage)
        assertEquals(DiagnosticTimelineStatus.FAILED,trainingTimeline(failure.progress.phase,failure.failedStage).last().status)
        assertThrows(IllegalStateException::class.java) { lifecycle.completed() }
        assertEquals(failure,lifecycle.engine(update(LocalTrainingPhase.COMPLETED)))
        val retry=TrainingPublicationLifecycle()
        retry.engine(update(LocalTrainingPhase.COMPLETED));retry.saving()
        assertEquals(LocalTrainingPhase.COMPLETED,retry.completed().progress.phase)
    }
    @Test fun failureKeepsTheActualStageForEveryNonterminalPipelinePhase() {
        listOf(LocalTrainingPhase.READING_DATASET,LocalTrainingPhase.PREPARING_SPLITS,LocalTrainingPhase.TRAINING_BASELINE,LocalTrainingPhase.TRAINING_CONTEXT,LocalTrainingPhase.EVALUATING).forEach { phase ->
            val lifecycle=TrainingPublicationLifecycle();lifecycle.engine(update(phase))
            val failure=lifecycle.failed("Failure")
            val timeline=trainingTimeline(failure.progress.phase,failure.failedStage)
            assertEquals(1,timeline.count { it.status==DiagnosticTimelineStatus.FAILED })
            assertEquals(DiagnosticTimelineStatus.FAILED,timeline[trainingStageIndex(phase)].status)
        }
    }
    @Test fun cancellationIsDistinctFromFailureAndEveryStageExplainsItsRealContract() {
        val lifecycle=TrainingPublicationLifecycle()
        lifecycle.engine(update(LocalTrainingPhase.COMPLETED));lifecycle.saving()
        val stopped=lifecycle.failed("Cancelled by user")
        val timeline=trainingTimeline(stopped.progress.phase,stopped.failedStage,cancelled=true)
        assertEquals(1,timeline.count { it.status==DiagnosticTimelineStatus.CANCELLED })
        assertEquals(DiagnosticTimelineStatus.CANCELLED,timeline.last().status)
        assertTrue(timeline.all { it.description.isNotBlank() && it.inputs.isNotBlank() && it.outputs.isNotBlank() && it.reason.isNotBlank() })
        assertEquals(5,timeline.count { it.status==DiagnosticTimelineStatus.COMPLETE })
    }
}
