package com.robotkinematicslab.mobile.ui.training.explainability

/** Verified against ScientificDatasetTrainingReader.encode/baseFeatureNames; unknown IDs stay literal. */
internal object ExplainabilityFeatureNames {
    fun describe(id:String):String {
        val match=Regex("joint_([1-9]|10)_(present|is_prismatic|dh_theta|dh_d|dh_a|dh_alpha|minimum|maximum|home|seed)").matchEntire(id)
            ?: return "$id · verified description unavailable"
        val description=when(match.groupValues[2]) {
            "present" -> "presence indicator"
            "is_prismatic" -> "prismatic-joint indicator"
            "dh_theta" -> "DH theta angle"
            "dh_d" -> "DH d offset"
            "dh_a" -> "DH a link length"
            "dh_alpha" -> "DH alpha twist angle"
            "minimum" -> "minimum joint limit"
            "maximum" -> "maximum joint limit"
            "home" -> "home joint value"
            else -> "initial joint value (solver seed)"
        }
        return "Joint ${match.groupValues[1]} $description · $id"
    }
}
