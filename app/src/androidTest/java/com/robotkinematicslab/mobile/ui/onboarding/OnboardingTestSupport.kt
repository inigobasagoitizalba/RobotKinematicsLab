package com.robotkinematicslab.mobile.ui.onboarding

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.activity.ComponentActivity
import com.robotkinematicslab.mobile.ui.launch.APP_LAUNCH_ENTER_PROJECTS_TAG

/**
 * Enters the project library when an application test starts at the retained launch screen.
 *
 * The historic method name is kept to avoid obscuring what changed in older regression tests;
 * the current build does not contain a first-run tutorial.
 */
fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>
    .dismissFirstRunTutorialIfPresent() {
    val enterProjects = onAllNodes(hasTestTag(APP_LAUNCH_ENTER_PROJECTS_TAG)).fetchSemanticsNodes()
    if (enterProjects.isNotEmpty()) {
        onNode(hasTestTag(APP_LAUNCH_ENTER_PROJECTS_TAG)).performClick()
        waitForIdle()
    }
}
