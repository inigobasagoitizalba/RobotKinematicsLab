package com.robotkinematicslab.mobile.domain

data class RobotDefinition(
    val name: String,
    val dhParameters: List<DHParameter>,
    val joints: List<JointDefinition>
)