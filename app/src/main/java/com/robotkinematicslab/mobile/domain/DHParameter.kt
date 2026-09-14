package com.robotkinematicslab.mobile.domain

/**
 * One row of the standard Denavit-Hartenberg table.
 *
 * Joint-state contract used by the solvers:
 * - REVOLUTE: RobotState.q replaces [theta], which must be stored as zero.
 * - PRISMATIC: RobotState.q replaces [d], which must be stored as zero.
 *
 * Fixed joint offsets are intentionally not encoded in the active field. If
 * offsets are added in the future they must be represented explicitly so that
 * generators, validators and reach proofs all share the same semantics.
 */
data class DHParameter(
    val theta: Double,
    val d: Double,
    val a: Double,
    val alpha: Double
)
