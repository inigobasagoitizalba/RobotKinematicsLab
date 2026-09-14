package com.robotkinematicslab.mobile.ml.storage

import com.robotkinematicslab.mobile.ml.training.*
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import java.io.*
import java.security.MessageDigest

/** Metrics omitted by legacy summary files are archived exactly; no evaluation is performed on reopen. */
data class HistoricalProfileEvidence(val id: String,val name: String,val candidate: String,val featureNames: List<String>,val bestEpoch: Int,val test: ClassificationMetrics,val slices: List<ClassificationSliceMetrics>,val warnings: List<String>)
data class HistoricalRunEvidence(val runId: String,val datasetPath: String,val summarySha256: String,val profiles: List<HistoricalProfileEvidence>)
object TrainingResultEvidenceArchive {
    const val FILE_NAME="scientific-result-v1.bin"
    private const val LIMIT=16*1024*1024
    fun save(directory: File,result: LocalTrainingRunResult) {
        val summary=File(directory,"summary.properties")
        val snapshot=HistoricalRunEvidence(result.runId,result.config.datasetPath,hash(summary.readBytes()).hex(),result.comparison.variants.map { HistoricalProfileEvidence(it.featureSelectionId,it.featureSelectionName,it.candidateId,it.featureNames,it.bestEpoch,it.testMetrics,it.testSlices,it.datasetWarnings) })
        saveSnapshot(File(directory,FILE_NAME),snapshot)
    }
    internal fun saveSnapshot(file: File,snapshot: HistoricalRunEvidence) {
        validate(snapshot)
        val payload=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeInt(0x52534C54);out.writeInt(1);out.names(com.robotkinematicslab.mobile.ml.data.TrainingLabel.entries.map { it.name });out.writeUTF(snapshot.runId);out.writeUTF(snapshot.datasetPath);out.writeUTF(snapshot.summarySha256)
            out.writeInt(snapshot.profiles.size)
            snapshot.profiles.forEach { profile ->
                out.writeUTF(profile.id);out.writeUTF(profile.name);out.writeUTF(profile.candidate);out.names(profile.featureNames);out.writeInt(profile.bestEpoch)
                out.metrics(profile.test);out.writeInt(profile.slices.size)
                profile.slices.forEach { out.writeUTF(it.id);out.writeUTF(it.displayName);out.metrics(it.metrics) }
                out.names(profile.warnings)
            }
        } }.toByteArray()
        require(payload.size<=LIMIT)
        AtomicFilePublisher.write(file) { temp -> FileOutputStream(temp).use { it.write(payload);it.write(hash(payload));it.fd.sync() } }
    }
    fun load(directory: File,expectedRunId: String): HistoricalRunEvidence {
        val snapshot=readSnapshot(File(directory,FILE_NAME))
        require(snapshot.runId==expectedRunId) { "Historical result belongs to another run." }
        require(snapshot.summarySha256==hash(File(directory,"summary.properties").readBytes()).hex()) { "Run summary changed; historical result provenance cannot be verified." }
        return snapshot
    }
    internal fun readSnapshot(file: File): HistoricalRunEvidence {
        require(file.isFile && file.length() in 40..(LIMIT+32).toLong()) { "Complete result archive is absent (legacy run) or invalid." }
        val bytes=file.inputStream().use { input -> val output=ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0)break;require(output.size()+n<=LIMIT+32);output.write(buffer,0,n) };output.toByteArray() }
        val payload=bytes.copyOfRange(0,bytes.size-32)
        require(MessageDigest.isEqual(hash(payload),bytes.copyOfRange(bytes.size-32,bytes.size))) { "Historical result checksum mismatch." }
        var allocated=0
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            fun count(max:Int):Int { val n=input.readInt();require(n in 0..max);allocated+=n;require(allocated<=200000);return n }
            fun names():List<String> = List(count(4096)) { input.readUTF() }
            fun metrics():ClassificationMetrics {
                val n=input.readInt();val values=List(8) { input.readDouble() }
                val matrix=List(count(3)) { List(count(3)) { input.readInt() } }
                val support=List(count(3)) { input.readInt() }
                val bins=List(count(1000)) { CalibrationBin(input.readDouble(),input.readDouble(),input.readInt(),input.readDouble(),input.readDouble()) }
                return ClassificationMetrics(n,values[0],values[1],values[2],values[3],matrix,values[4],values[5],values[6],bins,support).also { require(values[7]==0.0) }
            }
            require(input.readInt()==0x52534C54 && input.readInt()==1) { "Unsupported historical result format." }
            require(names()==com.robotkinematicslab.mobile.ml.data.TrainingLabel.entries.map { it.name }) { "Historical class order is incompatible." }
            val id=input.readUTF();val dataset=input.readUTF();val summaryHash=input.readUTF()
            val profiles=List(count(100)) {
                val profileId=input.readUTF();val name=input.readUTF();val candidate=input.readUTF();val features=names();val epoch=input.readInt();val test=metrics()
                val slices=List(count(10000)) { ClassificationSliceMetrics(input.readUTF(),input.readUTF(),metrics()) }
                HistoricalProfileEvidence(profileId,name,candidate,features,epoch,test,slices,names())
            }
            require(input.available()==0)
            HistoricalRunEvidence(id,dataset,summaryHash,profiles).also(::validate)
        }
    }
    private fun validate(snapshot:HistoricalRunEvidence) {
        require(snapshot.runId.isNotBlank() && snapshot.datasetPath.isNotBlank() && snapshot.summarySha256.matches(Regex("[a-f0-9]{64}")))
        require(snapshot.profiles.size in 1..100 && snapshot.profiles.map { it.id }.distinct().size==snapshot.profiles.size)
        snapshot.profiles.forEach { profile ->
            require(profile.id.isNotBlank() && profile.name.isNotBlank() && profile.candidate.isNotBlank() && profile.bestEpoch>=0)
            require(profile.featureNames.isNotEmpty() && profile.featureNames.size<=4096 && profile.featureNames.distinct().size==profile.featureNames.size)
            require(profile.slices.size<=10000 && profile.slices.map { it.id }.distinct().size==profile.slices.size)
            (listOf(profile.test)+profile.slices.map { it.metrics }).forEach { metric ->
                require(metric.sampleCount>=0)
                if(metric.confusionMatrix.isNotEmpty()) com.robotkinematicslab.mobile.ui.training.results.ScientificResultEvidence.classes(metric) else require(metric.classSupport.isEmpty())
                com.robotkinematicslab.mobile.ui.training.results.ScientificResultEvidence.calibration(metric)
                require(listOf(metric.accuracy,metric.balancedAccuracy,metric.macroF1,metric.expectedCalibrationError).all { it.isNaN() || it in 0.0..1.0 })
                require(listOf(metric.logLoss,metric.brierScore,metric.inferenceNanosPerSample).all { it.isNaN() || it.isFinite() && it>=0 })
            }
        }
    }
    private fun DataOutputStream.names(values:List<String>) { writeInt(values.size);values.forEach(::writeUTF) }
    private fun DataOutputStream.metrics(value:ClassificationMetrics) {
        writeInt(value.sampleCount);listOf(value.accuracy,value.balancedAccuracy,value.macroF1,value.logLoss,value.inferenceNanosPerSample,value.brierScore,value.expectedCalibrationError,0.0).forEach(::writeDouble)
        writeInt(value.confusionMatrix.size);value.confusionMatrix.forEach { row -> writeInt(row.size);row.forEach(::writeInt) }
        writeInt(value.classSupport.size);value.classSupport.forEach(::writeInt)
        writeInt(value.calibrationBins.size);value.calibrationBins.forEach { writeDouble(it.lowerConfidence);writeDouble(it.upperConfidence);writeInt(it.sampleCount);writeDouble(it.meanConfidence);writeDouble(it.empiricalAccuracy) }
    }
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun ByteArray.hex()=joinToString("") { "%02x".format(it) }
}
