package com.robotkinematicslab.mobile.ml.data

import java.security.MessageDigest

/** Index is one-based within the selected RegisteredFeatureSet, never a CSV row or another domain. */
data class FeatureDefinition(
    val index:Int,
    val technicalId:String,
    val humanName:String,
    val family:String,
    val definition:String,
    val origin:String,
    val calculation:String,
    val units:String,
    val dependencies:List<String>,
    val meaning:String,
    val possibleUse:String,
    val limitations:String,
    val implementationSource:String,
    val formulaVersion:String
) {
    init {
        require(index>0)
        require(listOf(technicalId,humanName,family,definition,origin,calculation,units,meaning,possibleUse,limitations,implementationSource,formulaVersion).all(String::isNotBlank))
        require(dependencies.isNotEmpty() && dependencies.none(String::isBlank))
    }
}

internal object FeatureDefinitionIntegrity {
    const val VERSION="feature-dictionary-v1"
    fun fingerprint(definitions:List<FeatureDefinition>):String {
        val digest=MessageDigest.getInstance("SHA-256")
        fun add(text:String) { val bytes=text.toByteArray(Charsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array());digest.update(bytes) }
        add(VERSION)
        definitions.forEach { d ->
            listOf(d.index.toString(),d.technicalId,d.humanName,d.family,d.definition,d.origin,d.calculation,d.units,
                d.dependencies.joinToString("\u001f"),d.meaning,d.possibleUse,d.limitations,d.implementationSource,d.formulaVersion).forEach(::add)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun validate(names:List<String>,definitions:List<FeatureDefinition>,expectedFingerprint:String?=null) {
        require(names.isNotEmpty() && names.distinct().size==names.size) { "Feature contract contains duplicate names." }
        require(definitions.map { it.index } == (1..names.size).toList()) { "Dictionary indices must cover the complete ordered contract exactly once." }
        require(definitions.map { it.technicalId } == names) { "Dictionary names/order drifted from the encoder." }
        if(expectedFingerprint!=null) require(fingerprint(definitions)==expectedFingerprint) { "Dictionary definition/formula/version changed; review the scientific cards." }
    }
}
