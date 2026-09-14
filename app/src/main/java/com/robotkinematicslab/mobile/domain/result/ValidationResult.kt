package com.robotkinematicslab.mobile.domain.result

data class ValidationResult(
    val isValid: Boolean,
    val issues: List<ValidationIssue>
) {
    companion object {
        fun success(): ValidationResult {
            return ValidationResult(
                isValid = true,
                issues = emptyList()
            )
        }

        fun failure(issues: List<ValidationIssue>): ValidationResult {
            return ValidationResult(
                isValid = false,
                issues = issues
            )
        }
    }
}