package com.robotkinematicslab.mobile.audit

/** Enabled campaigns must execute at least one declared, distinct seed. */
internal object OptInCampaignInputs {
    fun seeds(raw:String):List<Int> {
        val tokens=raw.split(',').map(String::trim)
        require(tokens.isNotEmpty() && tokens.all { it.isNotEmpty() }) { "An enabled campaign requires a nonempty seed list without empty entries." }
        val seeds=tokens.map { requireNotNull(it.toIntOrNull()) { "Campaign seeds must be integers." } }
        require(seeds.distinct().size==seeds.size) { "Duplicate seeds are not independent campaign repetitions." }
        return seeds
    }
}
