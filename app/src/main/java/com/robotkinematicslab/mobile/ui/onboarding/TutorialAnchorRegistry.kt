package com.robotkinematicslab.mobile.ui.onboarding

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

private val NoOpTutorialActionReporter = TutorialActionReporter { }

/** Compatibility sink for optional tutorial events. */
val LocalTutorialActionReporter =
    staticCompositionLocalOf<TutorialActionReporter> { NoOpTutorialActionReporter }

/** Optional tutorial anchor. */
fun Modifier.tutorialAnchor(
    targetId: TutorialTargetId,
    actionEnabled: Boolean = true
): Modifier = this
