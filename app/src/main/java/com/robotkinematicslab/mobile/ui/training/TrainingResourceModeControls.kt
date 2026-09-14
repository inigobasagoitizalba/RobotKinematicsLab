package com.robotkinematicslab.mobile.ui.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadChoiceButton
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadLevel

internal fun TrainingResourceMode.loadLevel(): ResourceLoadLevel =
    when (this) {
        TrainingResourceMode.QUICK -> ResourceLoadLevel.CONSERVATIVE
        TrainingResourceMode.BALANCED -> ResourceLoadLevel.MODERATE
        TrainingResourceMode.MAXIMUM_ACCURACY -> ResourceLoadLevel.HIGH
    }

@Composable
internal fun TrainingResourceModeButton(
    mode: TrainingResourceMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResourceLoadChoiceButton(
        selected = selected,
        label = mode.displayName,
        level = mode.loadLevel(),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    )
}


@Composable
internal fun TrainingSearchBudgetHelp(
    kind: com.robotkinematicslab.mobile.ml.model.TrainingModelKind,
    mode: TrainingResourceMode,
    hiddenUnits: Int?
) {
    androidx.compose.material3.Text(
        "Conservative/Moderate/High label the same automatic search budgets. They change candidate widths only; optimizer controls, row caps and device workers are separate. Maximum accuracy is a name, not a guarantee.",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
    )
    if (hiddenUnits != null && hiddenUnits in 2..512) androidx.compose.material3.Text(
        com.robotkinematicslab.mobile.ml.training.TrainingControlContract.searchDescription(kind,mode,hiddenUnits),
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
    )
}

internal fun trainingControlHint(label: String): String? = when(label) {
    "Maximum epochs per candidate" -> "1–1,000; default 40. An epoch visits the training partition once. More epochs allow more work; validation patience may stop earlier and restores its best epoch."
    "Mini-batch size" -> "1–8,192 training rows per optimizer update; default 128. Larger batches increase concurrent work and change update noise; the final batch may be smaller."
    "Learning rate" -> "Finite 0.000001–1; default 0.003. Adam step size: larger steps can be faster or unstable; smaller steps may require more epochs."
    "L2 regularization" -> "Finite 0–1; default 0.0001. Weight penalty, excluding biases. Higher values shrink weights more and may underfit; zero removes the penalty."
    "Hidden units" -> "2–512; default 24. ReLU-layer width. Used by compact neural networks and the variable-width automatic budgets; linear softmax and Quick ignore this value."
    "Random seed" -> "Signed 32-bit integer; default 42. Controls splits, initialization and row order. Exact replay also requires the corpus, features and effective numeric execution."
    "Early-stopping patience" -> "1–100 validation checks; default 8. Stops after this many non-improving epochs. If patience exceeds maximum epochs it has no stopping effect."
    else -> null
}

internal fun trainingControlFieldError(label:String,value:String):String? {
    if(trainingControlHint(label)==null) return null
    if(value.length>128) return "Enter a shorter numeric value."
    val base=TrainingControlsDraft()
    val (draft,key)=when(label) {
        "Maximum epochs per candidate" -> base.copy(epochs=value) to "epochs"
        "Mini-batch size" -> base.copy(batch=value) to "batch"
        "Learning rate" -> base.copy(rate=value) to "learningRate"
        "L2 regularization" -> base.copy(l2=value) to "l2"
        "Hidden units" -> base.copy(hidden=value) to "hidden"
        "Random seed" -> base.copy(seed=value) to "seed"
        else -> base.copy(patience=value) to "patience"
    }
    return draft.errors()[key]
}
