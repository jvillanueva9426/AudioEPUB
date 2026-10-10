package com.ferdyan.audioepub.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.ferdyan.audioepub.model.TtsVoice
import java.util.Locale

class TtsManager(
    private val context: Context,
    private val onInitComplete: (List<TtsVoice>) -> Unit,
    private val onUtteranceFinished: (String) -> Unit,
    private val onError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                stop()
            }
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale("es", "ES")

            // Configurar listener de fin de locución de oraciones
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { onUtteranceFinished(it) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { onError("Error al leer locución: $it") }
                }
            })

            // Obtener lista de voces disponibles en el dispositivo
            val voices = getAvailableVoices()
            onInitComplete(voices)
        } else {
            onError("No se pudo inicializar el motor de Text-To-Speech en el dispositivo.")
        }
    }

    fun speak(
        text: String,
        utteranceId: String,
        speechRate: Float = 1.0f,
        voiceName: String? = null,
        pitch: Float = 1.0f
    ) {
        if (!isInitialized || tts == null) return

        // Solicitar Audio Focus SÓLO cuando se vaya a reproducir de forma activa
        if (!requestAudioFocus()) {
            onError("No se pudo obtener el canal de audio del sistema.")
            return
        }

        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)

        // Asignar voz seleccionada si existe
        if (!voiceName.isNullOrEmpty()) {
            val matchedVoice = tts?.voices?.find { it.name == voiceName }
            if (matchedVoice != null) {
                tts?.voice = matchedVoice
            }
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
        // Liberar canal de audio inmediatamente al pausar o detener para no interferir con otras apps
        abandonAudioFocus()
    }

    fun shutdown() {
        stop()
        if (isInitialized) {
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun getAvailableVoices(): List<TtsVoice> {
        val resultList = mutableListOf<TtsVoice>()
        try {
            val systemVoices = tts?.voices ?: return emptyList()
            val distinctVoices = systemVoices.distinctBy { it.name }
            val spanishLocaleEs = Locale("es", "ES")

            val langNamesEs = mapOf(
                "es" to "Español",
                "en" to "Inglés",
                "pt" to "Portugués",
                "it" to "Italiano",
                "ru" to "Ruso",
                "ja" to "Japonés",
                "zh" to "Chino",
                "ko" to "Coreano",
                "hi" to "Hindi",
                "fr" to "Francés",
                "de" to "Alemán"
            )

            // Agrupar voces por código de idioma
            val voicesByLang = distinctVoices.groupBy { (it.locale?.language ?: "").lowercase() }

            val sortedLangs = voicesByLang.keys.sortedWith { lang1, lang2 ->
                when {
                    lang1 == "es" -> -1
                    lang2 == "es" -> 1
                    else -> {
                        val name1 = langNamesEs[lang1] ?: lang1
                        val name2 = langNamesEs[lang2] ?: lang2
                        name1.compareTo(name2)
                    }
                }
            }

            for (langCode in sortedLangs) {
                val voiceList = voicesByLang[langCode] ?: continue
                if (voiceList.isEmpty()) continue

                val langDisplayName = langNamesEs[langCode]
                    ?: voiceList.firstOrNull()?.locale?.getDisplayLanguage(spanishLocaleEs)?.replaceFirstChar { it.uppercase() }
                    ?: langCode.uppercase()

                val realFemaleVoices = voiceList.filter { detectGender(it) == "Femenino" }
                val realMaleVoices = voiceList.filter { detectGender(it) == "Masculino" }

                val femaleVoice = realFemaleVoices.firstOrNull() ?: voiceList[0]
                val maleVoice = realMaleVoices.firstOrNull() ?: voiceList.getOrNull(1) ?: voiceList[0]

                // Voz Femenina (Pitch = 1.05f)
                resultList.add(
                    TtsVoice(
                        name = femaleVoice.name,
                        language = femaleVoice.locale?.toLanguageTag() ?: langCode,
                        localeDisplayName = "$langDisplayName - Femenino",
                        pitch = 1.05f
                    )
                )

                // Voz Masculina (Pitch = 0.72f para simular frecuencia grave masculina real)
                resultList.add(
                    TtsVoice(
                        name = maleVoice.name,
                        language = maleVoice.locale?.toLanguageTag() ?: langCode,
                        localeDisplayName = "$langDisplayName - Masculino",
                        pitch = 0.72f
                    )
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultList
    }

    private fun detectGender(voice: Voice): String {
        val nameLower = voice.name.lowercase()

        try {
            if (voice.features != null) {
                if (voice.features.contains("gender=female")) return "Femenino"
                if (voice.features.contains("gender=male")) return "Masculino"
            }
        } catch (_: Exception) {
            // Ignorar excepciones de compatibilidad
        }

        val maleKeywords = listOf("male", "-m-", "_m_", "-m_", "_m-", "man", "boy", "iom", "jol", "mdf", "ptd", "hsm")
        for (kw in maleKeywords) {
            if (nameLower.contains(kw)) return "Masculino"
        }

        val femaleKeywords = listOf("female", "-f-", "_f_", "-f_", "_f-", "woman", "girl", "sfg", "eaf", "eea", "efd", "jab", "htm", "lfg", "kdf")
        for (kw in femaleKeywords) {
            if (nameLower.contains(kw)) return "Femenino"
        }

        return "Desconocido"
    }
}
