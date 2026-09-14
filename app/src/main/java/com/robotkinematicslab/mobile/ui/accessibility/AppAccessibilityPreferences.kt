package com.robotkinematicslab.mobile.ui.accessibility

import android.content.Context
import androidx.core.content.edit

enum class AppContentScale(
    val persistedId: String,
    val displayName: String,
    val multiplier: Float
) {
    COMPACT("compact", "Compact · 90%", 0.90f),
    STANDARD("standard", "Standard · 100%", 1.00f),
    LARGE("large", "Large · 115%", 1.15f),
    EXTRA_LARGE("extra-large", "Extra large · 130%", 1.30f),
    MAXIMUM("maximum", "Maximum · 150%", 1.50f)
}

data class AppAccessibilityPreferences(
    val contentScale: AppContentScale = AppContentScale.STANDARD,
    val highContrast: Boolean = false,
    val boldText: Boolean = false,
    val reduceMotion: Boolean = false,
    val readAloudEnabled: Boolean = false,
    val speechRate: Float = DEFAULT_SPEECH_RATE
) {
    companion object {
        const val MINIMUM_SPEECH_RATE = 0.60f
        const val MAXIMUM_SPEECH_RATE = 1.40f
        const val DEFAULT_SPEECH_RATE = 1.00f
    }
}

internal object AppAccessibilityPreferenceCodec {
    fun decode(
        contentScaleId: String?,
        highContrast: Boolean,
        boldText: Boolean,
        reduceMotion: Boolean,
        readAloudEnabled: Boolean,
        speechRate: Float
    ): AppAccessibilityPreferences =
        AppAccessibilityPreferences(
            contentScale =
                AppContentScale.entries.firstOrNull { scale -> scale.persistedId == contentScaleId }
                    ?: AppContentScale.STANDARD,
            highContrast = highContrast,
            boldText = boldText,
            reduceMotion = reduceMotion,
            readAloudEnabled = readAloudEnabled,
            speechRate =
                speechRate
                    .takeIf(Float::isFinite)
                    ?.coerceIn(
                        AppAccessibilityPreferences.MINIMUM_SPEECH_RATE,
                        AppAccessibilityPreferences.MAXIMUM_SPEECH_RATE
                    )
                    ?: AppAccessibilityPreferences.DEFAULT_SPEECH_RATE
        )
}

class AppAccessibilityRepository(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    fun load(): AppAccessibilityPreferences =
        AppAccessibilityPreferenceCodec.decode(
            contentScaleId = preferences.getString(KEY_CONTENT_SCALE, null),
            highContrast = preferences.getBoolean(KEY_HIGH_CONTRAST, false),
            boldText = preferences.getBoolean(KEY_BOLD_TEXT, false),
            reduceMotion = preferences.getBoolean(KEY_REDUCE_MOTION, false),
            readAloudEnabled = preferences.getBoolean(KEY_READ_ALOUD, false),
            speechRate =
                preferences.getFloat(
                    KEY_SPEECH_RATE,
                    AppAccessibilityPreferences.DEFAULT_SPEECH_RATE
                )
        )

    fun save(value: AppAccessibilityPreferences): AppAccessibilityPreferences {
        val safe =
            AppAccessibilityPreferenceCodec.decode(
                contentScaleId = value.contentScale.persistedId,
                highContrast = value.highContrast,
                boldText = value.boldText,
                reduceMotion = value.reduceMotion,
                readAloudEnabled = value.readAloudEnabled,
                speechRate = value.speechRate
            )
        preferences.edit {
            putString(KEY_CONTENT_SCALE, safe.contentScale.persistedId)
            putBoolean(KEY_HIGH_CONTRAST, safe.highContrast)
            putBoolean(KEY_BOLD_TEXT, safe.boldText)
            putBoolean(KEY_REDUCE_MOTION, safe.reduceMotion)
            putBoolean(KEY_READ_ALOUD, safe.readAloudEnabled)
            putFloat(KEY_SPEECH_RATE, safe.speechRate)
        }
        return safe
    }

    private companion object {
        const val PREFERENCES_FILE = "accessibility-v1"
        const val KEY_CONTENT_SCALE = "content-scale"
        const val KEY_HIGH_CONTRAST = "high-contrast"
        const val KEY_BOLD_TEXT = "bold-text"
        const val KEY_REDUCE_MOTION = "reduce-motion"
        const val KEY_READ_ALOUD = "read-aloud"
        const val KEY_SPEECH_RATE = "speech-rate"
    }
}
