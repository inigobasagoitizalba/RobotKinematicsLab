package com.robotkinematicslab.mobile.storage.evidence

import com.robotkinematicslab.mobile.storage.project.ResearchProject
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvidenceLibraryCatalogTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun artifact(index:Int)=ProjectArtifact("figures/$index.png","figures",ProjectArtifactFormat.PNG,1024,1700000000000,"a".repeat(64),displayTitle="Measured figure $index",experimentId="run-${index%3}",method="automatic-final-figure")
    @Test fun zeroOne167And400PreserveCountsAndDoNotMergeDuplicateNames() {
        listOf(0,1,167,400).forEach { n -> val artifacts=List(n,::artifact);assertEquals(n,EvidenceLibraryCatalog.filter(artifacts,EvidenceLibraryFilter()).size) }
        val duplicateNames=listOf(artifact(0).copy(displayTitle="Same title"),artifact(1).copy(displayTitle="Same title"))
        assertEquals(2,EvidenceLibraryCatalog.filter(duplicateNames,EvidenceLibraryFilter(query="same title")).size)
        assertEquals(1,EvidenceLibraryCatalog.filter(List(167,::artifact),EvidenceLibraryFilter(query="figure 166")).size)
        assertTrue(EvidenceLibraryCatalog.filter(List(167,::artifact),EvidenceLibraryFilter(query="absent evidence")).isEmpty())
    }
    @Test fun explicitDateTypeExperimentFiltersAndInvalidIndexDoNotBroadenSilently() {
        val artifacts=List(167,::artifact)
        assertEquals(56,EvidenceLibraryCatalog.filter(artifacts,EvidenceLibraryFilter(experiment="run-0",format="PNG",dateFrom="2023-11-14",dateTo="2023-11-14")).size)
        assertTrue(runCatching { EvidenceLibraryCatalog.filter(artifacts,EvidenceLibraryFilter(dateFrom="bad date")) }.isFailure)
        assertTrue(runCatching { EvidenceLibraryCatalog.filter(artifacts,EvidenceLibraryFilter(dateFrom="2024-01-01",dateTo="2023-01-01")) }.isFailure)
        assertTrue(runCatching { EvidenceLibraryCatalog.filter(listOf(artifact(0),artifact(0)),EvidenceLibraryFilter()) }.isFailure)
        assertTrue(runCatching { EvidenceLibraryCatalog.filter(listOf(artifact(0).copy(relativePath="../outside.png")),EvidenceLibraryFilter()) }.isFailure)
        assertTrue(runCatching { EvidenceLibraryCatalog.filter(listOf(artifact(0).copy(byteCount=-1)),EvidenceLibraryFilter()) }.isFailure)
    }
    @Test fun metadataDistinguishesVerifiedOrphanMissingReferenceAndChangedBytes() {
        val figure=File(temporary.root,"figure.png").apply { writeBytes(byteArrayOf(1,2,3)) }
        val sha=MessageDigest.getInstance("SHA-256").digest(figure.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("No linked provenance recorded",EvidenceArtifactMetadata.read(figure,temporary.root,sha).status)
        val sidecar=File(temporary.root,"figure.properties")
        Properties().apply { setProperty("title","Original title");setProperty("figureFile",figure.name);setProperty("figureSha256",sha);setProperty("executionId","run-original") }.also { sidecar.outputStream().use { stream -> it.store(stream,null) } }
        val metadata=EvidenceArtifactMetadata.read(figure,temporary.root,sha)
        assertEquals("Original title",metadata.title);assertEquals("run-original",metadata.experiment);assertEquals("Figure checksum matches provenance",metadata.status)
        assertTrue(EvidenceArtifactMetadata.read(figure,temporary.root,"b".repeat(64)).status.contains("WARNING"))
        figure.delete();assertTrue(EvidenceArtifactMetadata.read(sidecar,temporary.root,"c".repeat(64)).status.contains("missing"))
        sidecar.writeText("title=\\uZZZZ");assertTrue(EvidenceArtifactMetadata.read(sidecar,temporary.root,"c".repeat(64)).status.contains("invalid"))
    }
    @Test fun openingAnIndexedArtifactRejectsMissingOrReplacedBytesWithoutDeletingSources() {
        val root=File(temporary.root,"project").apply { mkdirs() };val table=File(root,"datasets/observations.csv").apply { parentFile.mkdirs();writeText("x,y\n1,2\n") }
        val repo=ProjectEvidencePackRepository(ResearchProject("p","Project","Objective",1,1,false),root)
        val original=repo.inspect().artifacts.single();assertEquals(original.sha256,repo.preview(original).artifact.sha256)
        table.writeText("x,y\n9,8\n");assertTrue(runCatching { repo.preview(original) }.isFailure);assertTrue(table.isFile)
        table.delete();assertTrue(runCatching { repo.preview(original) }.isFailure)
    }
}
