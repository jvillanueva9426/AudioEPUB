package com.ferdyan.audioepub.model

import android.graphics.Bitmap

/**
 * Representa un libro EPUB cargado en memoria.
 */
data class EpubBook(
    val title: String,
    val author: String,
    val coverBitmap: Bitmap? = null,
    val chapters: List<EpubChapter>
)

/**
 * Representa un capítulo dentro del libro EPUB.
 */
data class EpubChapter(
    val index: Int,
    val id: String,
    val title: String,
    val paragraphs: List<String>
)

/**
 * Metadatos persistentes de un libro guardado en la biblioteca local.
 */
data class SavedBookMetadata(
    val id: String,
    val fileName: String,
    val title: String,
    val author: String,
    val addedDate: Long = System.currentTimeMillis(),
    val lastChapterIndex: Int = 0,
    val lastParagraphIndex: Int = 0,
    val totalChapters: Int = 0
)

/**
 * Modos de tema visual para el lector.
 */
enum class ReaderThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    SEPIA
}

/**
 * Configuraciones generales de la aplicación.
 */
data class AppSettings(
    val fontSizeSp: Int = 17,
    val themeMode: ReaderThemeMode = ReaderThemeMode.SYSTEM,
    val defaultSpeechRate: Float = 1.0f,
    val autoAdvanceChapters: Boolean = true
)

/**
 * Modo de pantalla actual en la aplicación.
 */
enum class ScreenMode {
    LIBRARY,
    READER,
    SETTINGS
}

/**
 * Estado de reproducción del motor Text-To-Speech.
 */
enum class TtsStatus {
    STOPPED,
    PLAYING,
    PAUSED
}

/**
 * Información de una voz disponible en el sistema Android TTS.
 */
data class TtsVoice(
    val name: String,
    val language: String,
    val localeDisplayName: String,
    val pitch: Float = 1.0f
)

/**
 * Estado general de la interfaz de usuario en Jetpack Compose.
 */
data class EpubUiState(
    val screenMode: ScreenMode = ScreenMode.LIBRARY,
    val savedBooks: List<SavedBookMetadata> = emptyList(),
    val currentBook: EpubBook? = null,
    val currentBookMetadata: SavedBookMetadata? = null,
    val currentChapterIndex: Int = 0,
    val currentParagraphIndex: Int = 0,
    val ttsStatus: TtsStatus = TtsStatus.STOPPED,
    val speechRate: Float = 1.0f,
    val availableVoices: List<TtsVoice> = emptyList(),
    val selectedVoiceName: String? = null,
    val appSettings: AppSettings = AppSettings(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
