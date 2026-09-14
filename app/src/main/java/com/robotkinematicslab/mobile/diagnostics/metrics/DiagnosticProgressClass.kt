package com.robotkinematicslab.mobile.diagnostics.metrics

enum class DiagnosticProgressClass {
    SOLVED,
    NEAR_SOLVED,
    IMPROVED_BUT_NOT_ENOUGH,
    STALLED,
    WORSENED,
    INVALID_NUMERICAL
}
