package com.kazaeza.muzykalka.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Ticket M-09: the Taper's voice. Russian TTS; no Russian voice → silent, text only. */
class TtsAnnouncer(private val context: Context) : Announcer {
    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private val speaking = MutableStateFlow(false)
    private val availableFlow = MutableStateFlow<Boolean?>(null)

    override val isSpeaking: StateFlow<Boolean> = speaking.asStateFlow()
    override val available: StateFlow<Boolean?> = availableFlow.asStateFlow()

    @Volatile
    override var lastSpeechEndNanos: Long = 0L
        private set

    private fun init(): CompletableDeferred<Boolean> {
        ready?.let { return it }
        val deferred = CompletableDeferred<Boolean>()
        ready = deferred
        tts = TextToSpeech(context.applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS &&
                (tts?.setLanguage(Locale.forLanguageTag("ru-RU")) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
            tts?.setSpeechRate(1.05f)
            availableFlow.value = ok
            deferred.complete(ok)
        }
        return deferred
    }

    override suspend fun say(text: String) {
        val ok = withTimeoutOrNull(4_000) { init().await() } ?: false
        val engine = tts
        if (!ok || engine == null) return
        speaking.value = true
        try {
            suspendCancellableCoroutine { cont ->
                val id = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                cont.invokeOnCancellation { engine.stop() }
            }
        } finally {
            speaking.value = false
            lastSpeechEndNanos = System.nanoTime()
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = null
    }
}
