package com.robotkinematicslab.mobile.ml.storage

import com.robotkinematicslab.mobile.ml.data.TrainingSplitEvidence
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import java.util.Properties

internal object TrainingSplitMetadata {
    fun write(p:Properties,prefix:String,evidence:TrainingSplitEvidence) {
        p.setProperty("$prefix.splitProtocol",evidence.protocol)
        p.setProperty("$prefix.splitStrategy",evidence.strategy.name)
        p.setProperty("$prefix.splitRows",evidence.rowCounts.joinToString(":"))
        p.setProperty("$prefix.splitGroups",evidence.groupCounts.joinToString(":"))
        p.setProperty("$prefix.splitClasses",evidence.classCounts.joinToString(";") { it.joinToString(":") })
        p.setProperty("$prefix.splitSortedFallback",evidence.usedSortedFallback.toString())
    }
    fun read(p:Properties,prefix:String,expectedRows:List<Int>,expectedStrategy:TrainingSplitStrategy):TrainingSplitEvidence? {
        val protocol=p.getProperty("$prefix.splitProtocol") ?: return null
        fun value(key:String)=requireNotNull(p.getProperty("$prefix.$key")) { "Missing partition evidence: $key" }
        val evidence=TrainingSplitEvidence(protocol,TrainingSplitStrategy.valueOf(value("splitStrategy")),
            value("splitRows").split(':').map(String::toInt),value("splitGroups").split(':').map(String::toInt),
            value("splitClasses").split(';').map { it.split(':').map(String::toInt) },value("splitSortedFallback").toBooleanStrict())
        require(evidence.rowCounts==expectedRows && evidence.strategy==expectedStrategy) { "Partition evidence differs from the stored run." }
        return evidence
    }
}
