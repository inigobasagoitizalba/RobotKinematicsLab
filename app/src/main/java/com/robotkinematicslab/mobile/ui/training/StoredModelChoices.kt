package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ui.shared.ScientificEntityNameResolver
import java.io.File

internal data class StoredModelChoice(val path:String,val label:String,val technicalId:String,val error:String?=null)
internal fun storedModelChoices(repository:TrainingStorageRepository,run:TrainingRunSummary):List<StoredModelChoice> = run.modelPaths.map { path ->
    runCatching {
        val model=repository.loadModel(File(path))
        require(model.runId==run.runId)
        val name=ScientificEntityNameResolver.model(path,model.featureSelectionName)
        StoredModelChoice(path,"${name.primary} · ${model.model.kind.displayName} · ${model.featureNames.size} variables",name.technicalId)
    }.getOrElse { StoredModelChoice(path,"Unavailable model",File(path).name,it.message ?: "Model is unavailable") }
}
