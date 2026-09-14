package com.robotkinematicslab.mobile.storage.evidence

import java.io.File
import java.util.Properties
import java.time.Instant
import java.time.ZoneOffset

data class EvidenceArtifactMetadata(val title:String?=null,val experiment:String?=null,val method:String?=null,val dataset:String?=null,val status:String="No linked provenance recorded",val caption:String?=null) {
    companion object {
        fun read(file:File,root:File,actualSha256:String):EvidenceArtifactMetadata {
            val isFigure=file.extension.equals("png",true)
            val sidecar=if(isFigure) File(file.parentFile,file.nameWithoutExtension+".properties") else if(file.extension=="properties") file else return EvidenceArtifactMetadata()
            if(!sidecar.isFile) return EvidenceArtifactMetadata()
            return runCatching {
                require(sidecar.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && !java.nio.file.Files.isSymbolicLink(sidecar.toPath()))
                require(sidecar.length() in 0..262144)
                val metadataBytes=sidecar.inputStream().use { input -> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=262144);out.write(buffer,0,n) };out.toByteArray() }
                val properties=Properties().apply { metadataBytes.inputStream().use { load(it) } }
                fun value(key:String)=properties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() && it.length<=4096 }
                val recordedSha=value("figureSha256")
                val figureName=value("figureFile")
                val status=when {
                    isFigure && recordedSha==null -> "Figure metadata present; original checksum not recorded"
                    isFigure && recordedSha!=actualSha256 -> "WARNING: figure bytes differ from recorded provenance"
                    isFigure -> "Figure checksum matches provenance"
                    figureName!=null -> {
                        require(figureName==File(figureName).name && figureName !in listOf(".",".."))
                        if(File(file.parentFile,figureName).isFile) "Figure provenance sidecar" else "WARNING: referenced figure is missing"
                    }
                    else -> "Artifact metadata; no figure provenance claim"
                }
                EvidenceArtifactMetadata(value("title") ?: value("runName"),value("executionId") ?: value("runId") ?: value("analysisId"),value("scientificRole"),value("datasetPath"),status,value("caption"))
            }.getOrElse { EvidenceArtifactMetadata(status="WARNING: adjacent metadata is unreadable or invalid") }
        }
    }
}

data class EvidenceLibraryFilter(val query:String="",val category:String="All",val format:String="All",val experiment:String="",val dateFrom:String="",val dateTo:String="")
object EvidenceLibraryCatalog {
    fun visibleName(artifact:ProjectArtifact):String = artifact.displayTitle ?: artifact.fileName.substringBeforeLast('.',artifact.fileName).replace('_',' ').replace('-',' ').trim().ifEmpty { artifact.fileName }
    fun categoryName(category:String):String = when(category) {
        "training" -> "Training results";"models" -> "Trained models";"datasets" -> "Datasets";"sessions" -> "Diagnostic and telemetry sessions";"figures" -> "Scientific figures and provenance";"robots" -> "Robot definitions";else -> category.replaceFirstChar(Char::uppercase)
    }
    fun date(artifact:ProjectArtifact):String = Instant.ofEpochMilli(artifact.modifiedAtEpochMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()
    fun filter(artifacts:List<ProjectArtifact>,filter:EvidenceLibraryFilter):List<ProjectArtifact> {
        require(artifacts.map { it.relativePath }.distinct().size==artifacts.size) { "Duplicate artifact path in project index." }
        artifacts.forEach { require(it.byteCount>=0 && it.modifiedAtEpochMillis>=0 && it.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid indexed artifact." };require(it.relativePath.isNotBlank() && !it.relativePath.startsWith('/') && it.relativePath.split('/').none { part -> part==".." || part=="." }) }
        val from=filter.dateFrom.takeIf(String::isNotBlank)?.let(java.time.LocalDate::parse)
        val to=filter.dateTo.takeIf(String::isNotBlank)?.let(java.time.LocalDate::parse)
        require(from==null || to==null || from<=to) { "The date range is reversed." }
        return artifacts.filter { artifact ->
            val day=Instant.ofEpochMilli(artifact.modifiedAtEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
            (filter.category=="All" || artifact.category==filter.category) && (filter.format=="All" || artifact.format.name==filter.format) &&
                (filter.experiment.isBlank() || artifact.experimentId.orEmpty().contains(filter.experiment,true)) &&
                (filter.query.isBlank() || listOf(visibleName(artifact),artifact.relativePath,artifact.caption.orEmpty(),artifact.experimentId.orEmpty(),artifact.sourceDataset.orEmpty()).any { it.contains(filter.query,true) }) &&
                (from==null || day>=from) && (to==null || day<=to)
        }.sortedWith(compareByDescending<ProjectArtifact> { it.modifiedAtEpochMillis }.thenBy { it.relativePath })
    }
}
