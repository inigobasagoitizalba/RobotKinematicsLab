package com.robotkinematicslab.mobile.reproducibility

/**
 * Versioned deterministic random protocol used by scientific exports.
 *
 * The protocol is owned by the application instead of Kotlin's default Random implementation,
 * so upgrading Kotlin cannot silently change an experiment's pseudo-random sequence.
 */
object ScientificRandomProtocol {
    const val ID = "rkl-splitmix64-seed-v1"
    const val LEGACY_UNVERSIONED_ID = "legacy-kotlin-random-unversioned"

    fun deriveSeed(
        baseSeed: Int,
        vararg coordinates: String
    ): Long {
        var value = mix64(baseSeed.toLong() xor DERIVATION_DOMAIN)

        coordinates.forEach { coordinate ->
            value = mix64(value xor stableStringHash(coordinate))
        }

        return value
    }

    internal fun mix64(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * MIX_MULTIPLIER_1
        value = (value xor (value ushr 27)) * MIX_MULTIPLIER_2
        return value xor (value ushr 31)
    }

    private fun stableStringHash(value: String): Long {
        var hash = FNV_OFFSET_BASIS

        value.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and 0xffL)
            hash *= FNV_PRIME
        }

        return hash
    }

    private const val DERIVATION_DOMAIN = 0x524B4C5345454401L
    private const val MIX_MULTIPLIER_1 = -4658895280553007687L
    private const val MIX_MULTIPLIER_2 = -7723592293110705685L
    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}

/** Minimal SplitMix64 generator with an explicitly locked implementation. */
class ScientificRandom(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        return ScientificRandomProtocol.mix64(state)
    }

    fun nextInt(until: Int): Int {
        require(until > 0) { "Random upper bound must be positive." }

        val mask = until - 1
        var candidateBits = nextLong() ushr 1

        if (until and mask == 0) {
            return (candidateBits and mask.toLong()).toInt()
        }

        var candidate = (candidateBits % until.toLong()).toInt()
        while (candidateBits + mask.toLong() - candidate.toLong() < 0L) {
            candidateBits = nextLong() ushr 1
            candidate = (candidateBits % until.toLong()).toInt()
        }

        return candidate
    }

    fun nextDouble(
        from: Double,
        until: Double
    ): Double {
        require(from.isFinite() && until.isFinite() && until > from) {
            "Random bounds must be finite and ordered."
        }

        val width = until - from
        require(width.isFinite()) {
            "Random bound width must be finite."
        }

        val candidate = from + width * nextUnitDouble()
        return if (candidate < until) candidate else Math.nextDown(until)
    }

    private fun nextUnitDouble(): Double {
        return (nextLong() ushr 11).toDouble() * DOUBLE_UNIT
    }

    companion object {
        private const val GOLDEN_GAMMA = -7046029254386353131L
        private const val DOUBLE_UNIT = 1.0 / 9007199254740992.0
    }
}
