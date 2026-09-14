package com.robotkinematicslab.mobile.ml.research

import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import java.io.*
import java.security.MessageDigest
import java.util.UUID

/** Immutable numerical audit results, pinned to original model bytes, corpus and sampled rows. */
object RobustnessEvidenceArchive {
    fun validate(result:StoredModelRobustnessResult) {
        require(result.runId.isNotBlank() && result.modelName.isNotBlank() && result.featureCount>0)
        require(listOf(result.modelSha256,result.corpusSha256).all { it.matches(Regex("[a-f0-9]{64}")) })
        require(result.heldOutSampleCount in 1..100000 && result.sampleRowIds.size==result.heldOutSampleCount && result.sampleRowIds.distinct().size==result.sampleRowIds.size && result.sampleRowIds.all { it>=0 })
        require(result.slices.size in 1..256 && result.slices.map { it.magnitude }.distinct().size==result.slices.size)
        result.slices.forEach {
            require(it.magnitude.isFinite() && it.magnitude>=0 && it.sampleCount==result.heldOutSampleCount)
            require(it.meanLossIncrease in -1.0..1.0 && it.accuracyDrop in -1.0..1.0 && it.failureIntroductionRate in 0.0..1.0)
            if(it.magnitude==0.0) require(kotlin.math.abs(it.meanLossIncrease)<1e-12 && kotlin.math.abs(it.accuracyDrop)<1e-12 && it.failureIntroductionRate==0.0) { "Zero perturbation failed replay equivalence." }
        }
    }
    fun save(directory:File,result:StoredModelRobustnessResult):File {
        validate(result)
        val bytes=ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(1);out.writeUTF(result.runId);out.writeUTF(result.modelName);out.writeUTF(result.modelSha256);out.writeUTF(result.corpusSha256);out.writeUTF(result.perturbationUnit);out.writeInt(result.featureCount)
            out.writeInt(result.sampleRowIds.size);result.sampleRowIds.forEach(out::writeLong)
            out.writeInt(result.slices.size);result.slices.forEach { out.writeDouble(it.magnitude);out.writeInt(it.sampleCount);out.writeDouble(it.meanLossIncrease);out.writeDouble(it.accuracyDrop);out.writeDouble(it.failureIntroductionRate) }
        } }.toByteArray()
        val file=File(directory,"robustness-${result.modelSha256}-${System.currentTimeMillis()}-${UUID.randomUUID()}.bin")
        AtomicFilePublisher.write(file) { temp -> FileOutputStream(temp).use { it.write(bytes);it.write(hash(bytes));it.fd.sync() } }
        return file
    }
    fun latest(directory:File,runId:String,model:File):StoredModelRobustnessResult? {
        require(model.isFile) { "Selected model file is missing." }
        val modelHash=sha256(model)
        val file=directory.listFiles()?.filter { it.name.startsWith("robustness-$modelHash-") && it.extension=="bin" }?.maxByOrNull { it.name } ?: return null
        return read(file).also { require(it.runId==runId && it.modelSha256==modelHash) { "Audit belongs to another run or model." } }
    }
    fun read(file:File):StoredModelRobustnessResult {
        require(file.isFile && file.length() in 40..2_000_000) { "Audit file has invalid size." }
        val bytes=file.inputStream().use { input -> val out=ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2_000_000);out.write(buffer,0,n) };out.toByteArray() };require(bytes.size in 40..2_000_000)
        val payload=bytes.copyOfRange(0,bytes.size-32)
        require(MessageDigest.isEqual(hash(payload),bytes.copyOfRange(bytes.size-32,bytes.size))) { "Saved robustness audit is corrupt." }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt()==1) { "Unsupported audit version." }
            val run=input.readUTF();val model=input.readUTF();val modelHash=input.readUTF();val corpus=input.readUTF();val unit=input.readUTF();val features=input.readInt()
            val count=input.readInt();require(count in 1..100000);val rows=List(count) { input.readLong() }
            val n=input.readInt();require(n in 1..256);val slices=List(n) { RobustnessSlice(input.readDouble(),input.readInt(),input.readDouble(),input.readDouble(),input.readDouble()) }
            require(input.available()==0)
            StoredModelRobustnessResult(model,features,count,unit,slices,run,modelHash,corpus,rows).also(::validate)
        }
    }
    fun sha256(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
}
