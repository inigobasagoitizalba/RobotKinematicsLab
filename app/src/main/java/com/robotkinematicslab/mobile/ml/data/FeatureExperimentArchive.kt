package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.ml.ik.*
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import java.io.*
import java.security.MessageDigest

/** Versioned named selections; full source schema is pinned, never reinterpreted by index. */
data class ArchivedFeatureConfiguration(val id: String, val name: String, val source: String, val sourceNames: List<String>, val selectedNames: List<String>)
class FeatureExperimentArchive(private val file: File, private val domain: FeatureSetDomain) {
    private fun sourceNames(source: String): List<String> = when(domain) {
        FeatureSetDomain.CLASSIFICATION -> ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.valueOf(source))
        FeatureSetDomain.VERIFIED_IK -> OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.valueOf(source))
    }
    private fun validate(configs: List<ArchivedFeatureConfiguration>) {
        require(configs.size in 1..100) { "Save between 1 and 100 configurations." }
        require(configs.map { it.id }.distinct().size == configs.size) { "Duplicate configuration ID." }
        require(configs.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == configs.size) { "Duplicate configuration name." }
        require(configs.map { it.source to it.selectedNames }.distinct().size == configs.size) { "Duplicate ordered configuration." }
        configs.forEach {
            require(it.id.matches(Regex("[A-Za-z0-9._-]{1,80}")) && it.name.isNotBlank() && it.name.length <= 256) { "Invalid configuration name or ID." }
            require(it.sourceNames == sourceNames(it.source)) { "Source contract changed; reopen with the original version. No variables were reinterpreted." }
            require(it.selectedNames.isNotEmpty() && it.selectedNames.distinct().size == it.selectedNames.size && it.selectedNames.all(it.sourceNames::contains)) { "Invalid selected variable contract." }
        }
    }
    fun save(configs: List<ArchivedFeatureConfiguration>) {
        validate(configs)
        val payload = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeInt(0x46455850); out.writeInt(1); out.writeUTF(domain.name); out.writeInt(configs.size)
            configs.forEach { value ->
                out.writeUTF(value.id); out.writeUTF(value.name); out.writeUTF(value.source)
                listOf(value.sourceNames, value.selectedNames).forEach { names -> out.writeInt(names.size); names.forEach(out::writeUTF) }
            }
        } }.toByteArray()
        require(payload.size <= MAX_BYTES)
        AtomicFilePublisher.write(file) { temporary -> FileOutputStream(temporary).use { it.write(payload); it.write(hash(payload)); it.fd.sync() } }
    }
    fun load(): List<ArchivedFeatureConfiguration> {
        require(file.isFile && file.length() in 40..(MAX_BYTES + 32).toLong()) { "Saved configuration is absent or has an invalid size." }
        val bytes = file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES + 32) { "Saved configuration exceeds the size limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size in 40..MAX_BYTES + 32) { "Saved configuration exceeds the size limit." }
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        require(MessageDigest.isEqual(hash(payload), bytes.copyOfRange(bytes.size - 32, bytes.size))) { "Saved configuration is corrupt; checksum mismatch." }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == 0x46455850 && input.readInt() == 1) { "Unsupported configuration version." }
            require(input.readUTF() == domain.name) { "Wrong experiment domain." }
            val count = input.readInt(); require(count in 1..100)
            var total = 0
            fun names(): List<String> { val n = input.readInt(); require(n in 1..1024); total += n; require(total <= 102400); return List(n) { input.readUTF() } }
            val configs = List(count) { ArchivedFeatureConfiguration(input.readUTF(), input.readUTF(), input.readUTF(), names(), names()) }
            require(input.available() == 0) { "Unexpected configuration content." }
            validate(configs); configs
        }
    }
    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        fun classification(value: FeatureSelectionSpec) = ArchivedFeatureConfiguration(value.id, value.displayName, value.sourceProfile.name, ScientificDatasetTrainingReader.featureNames(value.sourceProfile), value.includedFeatureNames)
        fun verifiedIk(value: OneMicronFeatureSelectionSpec) = ArchivedFeatureConfiguration(value.id, value.displayName, value.sourceProfile.name, OneMicronIkFeatureEncoder.featureNames(value.sourceProfile), value.includedFeatureNames)
    }
}
