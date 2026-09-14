package com.robotkinematicslab.mobile.ml.comparison

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.storage.*
import java.io.File

internal data class VerifiedModelTest(val model: StoredLocalModel,val samples: List<EncodedTrainingSample>)
internal data class VerifiedComparisonPair(val left: VerifiedModelTest,val right: VerifiedModelTest)
data class ComparisonEligibility(val testCount: Int,val leftLabel: String,val rightLabel: String,val leftModelSha256:String,val rightModelSha256:String)

internal object ComparisonReplayContract {
    fun selectedPaths(run:TrainingRunSummary,left:String?,right:String?):Pair<String,String> {
        require(run.modelPaths.size>=2) { "This run contains fewer than two models. Its saved results remain available, but a paired comparison needs two distinct models." }
        val l=left ?: run.modelPaths.first()
        val r=right ?: run.modelPaths.firstOrNull { File(it).canonicalFile != File(l).canonicalFile }
        require(l in run.modelPaths) { "Choose a left model belonging to this run." }
        require(r != null && r in run.modelPaths) { "Choose a right model belonging to this run." }
        require(File(l).canonicalFile != File(r).canonicalFile) { "Choose two distinct models; left and right currently identify the same file." }
        require(File(l).isFile && File(r).isFile) { "A selected model file is missing. Refresh the saved run or restore its original model." }
        return l to r
    }
    fun verifyContracts(run:TrainingRunSummary,left:StoredLocalModel,right:StoredLocalModel) {
        require(left.runId==run.runId && right.runId==run.runId) { "Both models must belong to the selected run." }
        val a=requireNotNull(left.inferenceContract) { "Left legacy model has no exact inference contract. Retrain to compare verified test predictions." }
        val b=requireNotNull(right.inferenceContract) { "Right legacy model has no exact inference contract. Retrain to compare verified test predictions." }
        require(a.modelSha256 != b.modelSha256) { "The selections contain the same model, feature schema and normalization." }
        require(a.corpusSha256==b.corpusSha256 && a.maximumRows==b.maximumRows && a.randomSeed==b.randomSeed &&
            a.splitStrategy==b.splitStrategy && a.sampleAcrossEntireFile==b.sampleAcrossEntireFile && a.expectedDataRowCount==b.expectedDataRowCount &&
            a.requiredNewestRows==b.requiredNewestRows && a.protocolVersion==b.protocolVersion) { "The selected models do not share the same original corpus, sampling, split and replay protocol." }
    }
    fun verifyOrderedTest(left:List<EncodedTrainingSample>,right:List<EncodedTrainingSample>) {
        require(left.isNotEmpty() && left.size==right.size) { "The models have different held-out test populations." }
        require(left.zip(right).all { (a,b) -> a.sourceRowIndex==b.sourceRowIndex && a.labelIndex==b.labelIndex && a.splitFingerprint==b.splitFingerprint && a.robotFingerprint==b.robotFingerprint }) {
            "The held-out rows, their order, grouping identities or labels differ; paired comparison is blocked."
        }
    }
    fun rebuild(storage:TrainingStorageRepository,run:TrainingRunSummary,left:String?,right:String?,cancelled:()->Boolean):VerifiedComparisonPair {
        val (l,r)=selectedPaths(run,left,right)
        val lm=storage.loadModel(File(l));val rm=storage.loadModel(File(r))
        verifyContracts(run,lm,rm)
        fun replay(model:StoredLocalModel):VerifiedModelTest {
            val (dataset,split)=TrainingInferenceReplay.rebuild(run,model,cancellationRequested=cancelled,checkCancellation={
                if(cancelled() || Thread.currentThread().isInterrupted) throw ModelComparisonCancelledException()
            })
            return VerifiedModelTest(model,split.testIndices.map { dataset.samples[it] })
        }
        val a=replay(lm);val b=replay(rm)
        verifyOrderedTest(a.samples,b.samples)
        return VerifiedComparisonPair(a,b)
    }
    fun label(model:StoredLocalModel) = "${model.featureSelectionName} (${model.featureNames.size}) · ${model.model.kind.displayName}"
}
