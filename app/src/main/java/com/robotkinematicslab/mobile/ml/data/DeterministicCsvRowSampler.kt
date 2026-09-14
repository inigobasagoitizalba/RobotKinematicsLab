package com.robotkinematicslab.mobile.ml.data

import java.util.Random

/** Uniform, deterministic sampling without replacement over zero-based CSV data rows. */
object DeterministicCsvRowSampler {

    fun indices(totalRows: Int, maximumRows: Int, seed: Int): Set<Int> {
        require(totalRows > 0)
        require(maximumRows > 0)
        val selectedCount = minOf(totalRows, maximumRows)
        val selected = HashSet<Int>((selectedCount * 4 / 3 + 1).coerceAtLeast(16))
        val random = Random(seed.toLong())

        // Floyd's algorithm produces a uniform k-subset without allocating or
        // shuffling an array proportional to the full corpus.
        for (candidate in totalRows - selectedCount until totalRows) {
            val draw = random.nextInt(candidate + 1)
            if (!selected.add(draw)) selected.add(candidate)
        }
        check(selected.size == selectedCount)
        return selected
    }

    /**
     * Selects a bounded full-corpus sample while guaranteeing that the newest committed rows are
     * present. Closed-loop training uses this after an append so a fixed row cap cannot silently
     * keep training on an old CSV prefix.
     */
    fun indicesIncludingTail(
        totalRows: Int,
        maximumRows: Int,
        seed: Int,
        requiredTailRows: Int
    ): Set<Int> {
        require(totalRows > 0)
        require(maximumRows > 0)
        require(requiredTailRows in 0..minOf(totalRows, maximumRows)) {
            "Required newest rows must fit inside the selected training row cap."
        }
        if (requiredTailRows == 0) return indices(totalRows, maximumRows, seed)
        val selectedCount = minOf(totalRows, maximumRows)
        val tailStart = totalRows - requiredTailRows
        val olderSlots = selectedCount - requiredTailRows
        val selected = HashSet<Int>((selectedCount * 4 / 3 + 1).coerceAtLeast(16))
        if (olderSlots > 0 && tailStart > 0) {
            selected += indices(tailStart, olderSlots, seed)
        }
        for (index in tailStart until totalRows) selected += index
        check(selected.size == selectedCount)
        return selected
    }
}
