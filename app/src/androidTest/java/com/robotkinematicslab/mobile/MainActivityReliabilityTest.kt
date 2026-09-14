package com.robotkinematicslab.mobile

import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityReliabilityTest {

    @Test
    fun dynamicComposeHierarchyIsExcludedFromSystemScrollCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val expected =
                    View.SCROLL_CAPTURE_HINT_EXCLUDE or
                        View.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS
                assertEquals(expected, activity.window.decorView.scrollCaptureHint)
            }
        }
    }
}
