package com.robotkinematicslab.mobile.ui.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import java.util.UUID

enum class ReadAloudResult {
    STARTED,
    INITIALIZING,
    UNAVAILABLE
}

interface ReadAloudController {
    fun speak(text: String, speechRate: Float): ReadAloudResult

    fun stop()

    fun shutdown()
}

class AndroidReadAloudController(context: Context) : ReadAloudController {
    private val applicationContext = context.applicationContext

    @Volatile
    private var initializationStarted = false

    @Volatile
    private var initializationComplete = false

    @Volatile
    private var available = false

    @Volatile
    private var closed = false

    private var engine: TextToSpeech? = null

    override fun speak(text: String, speechRate: Float): ReadAloudResult {
        if (closed) return ReadAloudResult.UNAVAILABLE
        if (!initializationStarted) {
            startEngine()
            return ReadAloudResult.INITIALIZING
        }
        if (!initializationComplete) return ReadAloudResult.INITIALIZING
        val currentEngine = engine ?: return ReadAloudResult.UNAVAILABLE
        if (!available) return ReadAloudResult.UNAVAILABLE
        val languageResult = currentEngine.setLanguage(Locale.getDefault())
        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return ReadAloudResult.UNAVAILABLE
        }

        val safeText = text.trim().take(TextToSpeech.getMaxSpeechInputLength())
        if (safeText.isEmpty()) return ReadAloudResult.UNAVAILABLE
        currentEngine.setSpeechRate(
            speechRate.coerceIn(
                AppAccessibilityPreferences.MINIMUM_SPEECH_RATE,
                AppAccessibilityPreferences.MAXIMUM_SPEECH_RATE
            )
        )
        val result =
            currentEngine.speak(
                safeText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                UUID.randomUUID().toString()
            )
        return if (result == TextToSpeech.SUCCESS) ReadAloudResult.STARTED else ReadAloudResult.UNAVAILABLE
    }

    override fun stop() {
        engine?.stop()
    }

    override fun shutdown() {
        closed = true
        engine?.stop()
        engine?.shutdown()
        engine = null
        available = false
    }

    @Synchronized
    private fun startEngine() {
        if (initializationStarted || closed) return
        initializationStarted = true
        engine =
            TextToSpeech(applicationContext) { status ->
                initializationComplete = true
                available = !closed && status == TextToSpeech.SUCCESS
            }
    }
}

private object NoOpReadAloudController : ReadAloudController {
    override fun speak(text: String, speechRate: Float) = ReadAloudResult.UNAVAILABLE

    override fun stop() = Unit

    override fun shutdown() = Unit
}

val LocalAppAccessibilityPreferences =
    staticCompositionLocalOf { AppAccessibilityPreferences() }

val LocalReadAloudController =
    staticCompositionLocalOf<ReadAloudController> { NoOpReadAloudController }

@Composable
fun ProvideAppAccessibility(
    preferences: AppAccessibilityPreferences,
    readAloudController: ReadAloudController,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAppAccessibilityPreferences provides preferences,
        LocalReadAloudController provides readAloudController,
        content = content
    )
}
