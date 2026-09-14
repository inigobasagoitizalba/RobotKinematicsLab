package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.ml.data.FeatureSetCatalog
import com.robotkinematicslab.mobile.ml.data.FeatureSetDomain
import com.robotkinematicslab.mobile.ml.data.ScientificCsvPreviewReader
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSetCatalogTest {
    @Test
    fun registryKeepsNumericLookalikesDistinctAndEverySchemaExact() {
        val entries = FeatureSetCatalog.entries()

        assertEquals(entries.size, entries.map { it.technicalId }.distinct().size)
        assertTrue(entries.all { it.featureCount == it.featureNames.size })
        assertTrue(entries.all { it.featureNames.distinct().size == it.featureCount })
        assertEquals(listOf(108, 130, 152, 169, 191, 209, 233, 383), FeatureSetCatalog.frozenCumulativeCounts)
        assertEquals(468, FeatureSetCatalog.researchCumulativeCounts.last())
        assertEquals(
            2,
            entries.count { it.featureCount == 108 && it.domain in setOf(FeatureSetDomain.CLASSIFICATION, FeatureSetDomain.VERIFIED_IK) }
        )
        assertTrue(entries.filter { it.featureCount == 108 }.map { it.technicalId }.distinct().size == 2)
    }

    @Test
    fun csvPreviewReadsOnlyTheBoundedPrefixWithoutChangingBytes() {
        val file = Files.createTempFile("scientific-preview", ".csv").toFile()
        file.writeText(buildString {
            appendLine("id,name,value")
            repeat(250) { index -> appendLine("$index,\"robot,$index\",${index / 10.0}") }
        })
        val before = file.readBytes()

        val preview = ScientificCsvPreviewReader.read(file, maximumRows = 20)

        assertEquals(listOf("id", "name", "value"), preview.columns)
        assertEquals(20, preview.rows.size)
        assertEquals("robot,0", preview.rows.first()[1])
        assertTrue(preview.hasMoreRows)
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test
    fun csvPreviewAcceptsACompleteSmallFileAndRejectsMalformedOrUnsafeRequests() {
        val valid = Files.createTempFile("scientific-preview-small", ".csv").toFile()
        valid.writeText("a,b\n1,2\n")
        val preview = ScientificCsvPreviewReader.read(valid, maximumRows = 20)
        assertEquals(listOf(listOf("1", "2")), preview.rows)
        assertFalse(preview.hasMoreRows)

        val malformed = Files.createTempFile("scientific-preview-malformed", ".csv").toFile()
        malformed.writeText("a,b\n1\n")
        val malformedBefore = malformed.readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            ScientificCsvPreviewReader.read(malformed, maximumRows = 20)
        }
        assertTrue(malformedBefore.contentEquals(malformed.readBytes()))
        assertThrows(IllegalArgumentException::class.java) {
            ScientificCsvPreviewReader.read(valid, maximumRows = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScientificCsvPreviewReader.read(valid.resolveSibling("missing.csv"), maximumRows = 20)
        }
    }

    @Test
    fun csvPreviewRejectsAnOversizedCorruptRecordWithoutMutatingTheSource() {
        val oversized = Files.createTempFile("scientific-preview-oversized", ".csv").toFile()
        oversized.bufferedWriter().use { writer ->
            writer.appendLine("a,b")
            writer.append("x".repeat(ScientificCsvPreviewReader.MAXIMUM_LINE_CHARACTERS + 1))
            writer.append(",value")
        }
        val bytesBefore = oversized.readBytes()

        val error = assertThrows(IllegalArgumentException::class.java) {
            ScientificCsvPreviewReader.read(oversized, maximumRows = 20)
        }

        assertTrue(error.message.orEmpty().contains("safe preview limit"))
        assertTrue(bytesBefore.contentEquals(oversized.readBytes()))
    }
}
