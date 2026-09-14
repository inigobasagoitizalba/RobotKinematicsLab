package com.robotkinematicslab.mobile.ui.input

/** Locale-tolerant parser for scientific form fields without accepting ambiguous mixed separators. */
object ScientificNumberParser {
    fun parseDouble(raw: String): Double? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (value.contains(',') && value.contains('.')) return null
        return value.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    fun parseInt(raw: String): Int? = raw.trim().toIntOrNull()
}
