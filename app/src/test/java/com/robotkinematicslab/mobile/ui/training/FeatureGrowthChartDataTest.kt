package com.robotkinematicslab.mobile.ui.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FeatureGrowthChartDataTest {

    @Test
    fun `eight feature contracts retain their exact macro F1 after display sorting`() {
        val expected =
            linkedMapOf(
                108 to 0.496445488219,
                130 to 0.506500997511,
                152 to 0.564274093800,
                169 to 0.598736301422,
                191 to 0.598085199415,
                209 to 0.598569355724,
                233 to 0.596572371760,
                383 to 0.619833761743
            )
        val shuffled =
            expected.entries.reversed().map { (featureCount, macroF1) ->
                FeatureGrowthObservation("features-$featureCount", featureCount, macroF1)
            }

        val points = buildFeatureGrowthChartPoints(shuffled)

        assertEquals(expected.keys.map(Int::toDouble), points.map { it.x })
        assertEquals(expected.values.toList(), points.map { it.y })
    }

    @Test
    fun `duplicate feature contract is rejected instead of silently crossing series`() {
        val duplicate =
            listOf(
                FeatureGrowthObservation("same-contract", 108, 0.49),
                FeatureGrowthObservation("same-contract", 383, 0.62)
            )

        assertThrows(IllegalArgumentException::class.java) {
            buildFeatureGrowthChartPoints(duplicate)
        }
    }

    @Test
    fun `corrupt macro F1 is rejected instead of entering the graph`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.01).forEach { corrupt ->
            assertThrows(IllegalArgumentException::class.java) {
                buildFeatureGrowthChartPoints(
                    listOf(FeatureGrowthObservation("corrupt-$corrupt", 108, corrupt))
                )
            }
        }
    }
}
