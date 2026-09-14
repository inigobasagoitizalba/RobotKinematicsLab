package com.robotkinematicslab.mobile.domain.result

data class ValidationIssue(
    val code: ValidationCode,
    val message: String
)
