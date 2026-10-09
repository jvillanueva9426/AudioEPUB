package com.ferdyan.audioepub.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferdyan.audioepub.model.AppSettings
import com.ferdyan.audioepub.model.EpubUiState
import com.ferdyan.audioepub.model.ReaderThemeMode
import com.ferdyan.audioepub.model.SavedBookMetadata
import com.ferdyan.audioepub.model.ScreenMode
import com.ferdyan.audioepub.model.TtsStatus
import com.ferdyan.audioepub.parser.EpubParser
import com.ferdyan.audioepub.repository.EpubRepository
import com.ferdyan.audioepub.repository.SettingsRepository
import com.ferdyan.audioepub.service.EpubAudioService
import com.ferdyan.audioepub.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EpubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EpubRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val _uiState = MutableStateFlow(EpubUiState())
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()

    private var ttsManager: TtsManager? = null
    private var epubAudioService: EpubAudioService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? EpubAudioService.LocalBinder
            epubAudioService = binder?.getService()?.apply {
                onPlayPauseAction = { playOrPause() }
                onNextParagraphAction = { nextParagraph() }
                onPreviousParagraphAction = { previousParagraph() }
                onStopAction = { stop() }
            }
            isServiceBound = true
            syncServiceNotification()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            epubAudioService = null
            isServiceBound = false
        }
    }

    init {
        // Cargar ajustes guardados previamente
        val savedSettings = settingsRepository.getSettings()
        _uiState.value = _uiState.value.copy(
            appSettings = savedSettings,
            speechRate = savedSettings.defaultSpeechRate
        )

        // Inicializar motor TTS
        ttsManager = TtsManager(
            context = application,
            onInitComplete = { voices ->
                val defaultVoice = voices.firstOrNull()?.name
                _uiState.value = _uiState.value.copy(
                    availableVoices = voices,
                    selectedVoiceName = defaultVoice
                )
            },
            onUtteranceFinished = { utteranceId ->
                viewModelScope.launch(Dispatchers.Main) {
                    onUtteranceCompleted(utteranceId)
                }
            },
            onError = { _ ->
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(ttsStatus = TtsStatus.STOPPED)
                    syncServiceNotification()
                }
            }
        )

        // Vincular servicio de audio para controles de pantalla de bloqueo e isla dinámica
        val serviceIntent = Intent(application, EpubAudioService::class.java)
        try {
            application.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {}

        // Cargar biblioteca inicial
        loadLibrary()
    }

    fun updateFontSize(fontSizeSp: Int) {
        val newSettings = _uiState.value.appSettings.copy(fontSizeSp = fontSizeSp)
        saveSettings(newSettings)
    }

    fun updateReaderThemeMode(themeMode: ReaderThemeMode) {
        val newSettings = _uiState.value.appSettings.copy(themeMode = themeMode)
        saveSettings(newSettings)
    }

    fun updateDefaultSpeechRate(speechRate: Float) {
        val newSettings = _uiState.value.appSettings.copy(defaultSpeechRate = speechRate)
        _uiState.value = _uiState.value.copy(speechRate = speechRate)
        saveSettings(newSettings)
    }

    fun updateAutoAdvanceChapters(enabled: Boolean) {
        val newSettings = _uiState.value.appSettings.copy(autoAdvanceChapters = enabled)
        saveSettings(newSettings)
    }

    private fun saveSettings(newSettings: AppSettings) {
        _uiState.value = _uiState.value.copy(appSettings = newSettings)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveSettings(newSettings)
        }
    }

    fun clearAllLibrary() {
        viewModelScope.launch {
            val books = _uiState.value.savedBooks
            withContext(Dispatchers.IO) {
                for (b in books) {
                    repository.deleteBook(b.id)
                }
            }
            ttsManager?.stop()
            _uiState.value = _uiState.value.copy(
                screenMode = ScreenMode.LIBRARY,
                savedBooks = emptyList(),
                currentBook = null,
                currentBookMetadata = null,
                ttsStatus = TtsStatus.STOPPED
            )
            syncServiceNotification()
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            val savedBooks = withContext(Dispatchers.IO) {
                repository.getSavedBooks()
            }
            _uiState.value = _uiState.value.copy(savedBooks = savedBooks)
        }
    }

    fun importBookFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val metadata = withContext(Dispatchers.IO) {
                    repository.importEpub(getApplication<Application>().contentResolver, uri)
                }
                openBook(metadata)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Error al importar el archivo EPUB."
                )
            }
        }
    }

    fun openBook(metadata: SavedBookMetadata) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                ttsManager?.stop()
                val book = withContext(Dispatchers.IO) {
                    repository.openBookInputStream(metadata.fileName).use { EpubParser.parse(it) }
                }

                _uiState.value = _uiState.value.copy(
                    screenMode = ScreenMode.READER,
                    currentBook = book,
                    currentBookMetadata = metadata,
                    currentChapterIndex = metadata.lastChapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0)),
                    currentParagraphIndex = metadata.lastParagraphIndex,
                    ttsStatus = TtsStatus.STOPPED,
                    isLoading = false
                )
                syncServiceNotification()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Error al abrir el libro seleccionado."
                )
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteBook(bookId)
            }
            if (_uiState.value.currentBookMetadata?.id == bookId) {
                ttsManager?.stop()
                _uiState.value = _uiState.value.copy(
                    screenMode = ScreenMode.LIBRARY,
                    currentBook = null,
                    currentBookMetadata = null,
                    ttsStatus = TtsStatus.STOPPED
                )
                syncServiceNotification()
            }
            loadLibrary()
        }
    }

    fun switchScreenMode(mode: ScreenMode) {
        if (mode == ScreenMode.LIBRARY) {
            loadLibrary()
        }
        _uiState.value = _uiState.value.copy(screenMode = mode)
    }

    fun selectChapter(index: Int) {
        val book = _uiState.value.currentBook ?: return
        if (index in book.chapters.indices) {
            ttsManager?.stop()
            updateProgressAndSave(chapterIndex = index, paragraphIndex = 0)

            if (_uiState.value.ttsStatus == TtsStatus.PLAYING) {
                speakCurrentParagraph()
            } else {
                syncServiceNotification()
            }
        }
    }

    fun playOrPause() {
        val currentBook = _uiState.value.currentBook ?: return
        if (currentBook.chapters.isEmpty()) return

        when (_uiState.value.ttsStatus) {
            TtsStatus.PLAYING -> {
                ttsManager?.stop()
                _uiState.value = _uiState.value.copy(ttsStatus = TtsStatus.PAUSED)
                syncServiceNotification()
            }
            TtsStatus.STOPPED, TtsStatus.PAUSED -> {
                _uiState.value = _uiState.value.copy(ttsStatus = TtsStatus.PLAYING)
                speakCurrentParagraph()
            }
        }
    }

    fun stop() {
        ttsManager?.stop()
        _uiState.value = _uiState.value.copy(ttsStatus = TtsStatus.STOPPED)
        syncServiceNotification()
    }

    fun nextParagraph() {
        val currentState = _uiState.value
        val currentBook = currentState.currentBook ?: return
        val currentChapter = currentBook.chapters.getOrNull(currentState.currentChapterIndex) ?: return

        if (currentState.currentParagraphIndex + 1 < currentChapter.paragraphs.size) {
            updateProgressAndSave(
                chapterIndex = currentState.currentChapterIndex,
                paragraphIndex = currentState.currentParagraphIndex + 1
            )
            if (currentState.ttsStatus == TtsStatus.PLAYING) {
                speakCurrentParagraph()
            } else {
                syncServiceNotification()
            }
        } else if (currentState.currentChapterIndex + 1 < currentBook.chapters.size && currentState.appSettings.autoAdvanceChapters) {
            // Avanzar al siguiente capítulo si está habilitado en ajustes
            selectChapter(currentState.currentChapterIndex + 1)
        } else {
            // Fin del libro o avance automático desactivado
            stop()
        }
    }

    fun previousParagraph() {
        val currentState = _uiState.value
        val currentBook = currentState.currentBook ?: return

        if (currentState.currentParagraphIndex > 0) {
            updateProgressAndSave(
                chapterIndex = currentState.currentChapterIndex,
                paragraphIndex = currentState.currentParagraphIndex - 1
            )
            if (currentState.ttsStatus == TtsStatus.PLAYING) {
                speakCurrentParagraph()
            } else {
                syncServiceNotification()
            }
        } else if (currentState.currentChapterIndex > 0) {
            val prevChapterIndex = currentState.currentChapterIndex - 1
            val prevChapter = currentBook.chapters[prevChapterIndex]
            val lastParagraphIndex = (prevChapter.paragraphs.size - 1).coerceAtLeast(0)

            ttsManager?.stop()
            updateProgressAndSave(
                chapterIndex = prevChapterIndex,
                paragraphIndex = lastParagraphIndex
            )
            if (currentState.ttsStatus == TtsStatus.PLAYING) {
                speakCurrentParagraph()
            } else {
                syncServiceNotification()
            }
        }
    }

    fun selectParagraphDirectly(index: Int) {
        val currentState = _uiState.value
        val currentBook = currentState.currentBook ?: return
        val currentChapter = currentBook.chapters.getOrNull(currentState.currentChapterIndex) ?: return

        if (index in currentChapter.paragraphs.indices) {
            ttsManager?.stop()
            updateProgressAndSave(
                chapterIndex = currentState.currentChapterIndex,
                paragraphIndex = index
            )
            _uiState.value = _uiState.value.copy(ttsStatus = TtsStatus.PLAYING)
            speakCurrentParagraph()
        }
    }

    fun setSpeechRate(rate: Float) {
        _uiState.value = _uiState.value.copy(speechRate = rate)
        if (_uiState.value.ttsStatus == TtsStatus.PLAYING) {
            speakCurrentParagraph()
        }
    }

    fun setVoice(voiceName: String) {
        _uiState.value = _uiState.value.copy(selectedVoiceName = voiceName)
        if (_uiState.value.ttsStatus == TtsStatus.PLAYING) {
            speakCurrentParagraph()
        }
    }

    private fun updateProgressAndSave(chapterIndex: Int, paragraphIndex: Int) {
        val metadata = _uiState.value.currentBookMetadata ?: return
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = chapterIndex,
            currentParagraphIndex = paragraphIndex
        )

        // Guardar progreso en el almacenamiento local en segundo plano
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProgress(metadata.id, chapterIndex, paragraphIndex)
        }
    }

    private fun speakCurrentParagraph() {
        val currentState = _uiState.value
        val currentBook = currentState.currentBook ?: return
        val currentChapter = currentBook.chapters.getOrNull(currentState.currentChapterIndex) ?: return
        val textToSpeak = currentChapter.paragraphs.getOrNull(currentState.currentParagraphIndex) ?: return

        val selectedVoice = currentState.availableVoices.find { it.name == currentState.selectedVoiceName }
        val pitch = selectedVoice?.pitch ?: 1.0f

        val utteranceId = "utt_${currentState.currentChapterIndex}_${currentState.currentParagraphIndex}"
        ttsManager?.speak(
            text = textToSpeak,
            utteranceId = utteranceId,
            speechRate = currentState.speechRate,
            voiceName = currentState.selectedVoiceName,
            pitch = pitch
        )

        syncServiceNotification()
    }

    private fun syncServiceNotification() {
        val currentState = _uiState.value
        val currentBook = currentState.currentBook
        val currentChapter = currentBook?.chapters?.getOrNull(currentState.currentChapterIndex)

        if (currentBook != null && currentChapter != null && currentState.ttsStatus != TtsStatus.STOPPED) {
            val serviceIntent = Intent(getApplication(), EpubAudioService::class.java)
            try {
                ContextCompat.startForegroundService(getApplication(), serviceIntent)
            } catch (_: Exception) {}

            epubAudioService?.updateMediaState(
                bookTitle = currentBook.title,
                chapterTitle = currentChapter.title,
                paragraphIndex = currentState.currentParagraphIndex,
                totalParagraphs = currentChapter.paragraphs.size,
                isPlaying = currentState.ttsStatus == TtsStatus.PLAYING
            )
        } else if (currentState.ttsStatus == TtsStatus.STOPPED) {
            epubAudioService?.stopForegroundService()
        }
    }

    private fun onUtteranceCompleted(utteranceId: String) {
        val currentState = _uiState.value
        val expectedUtteranceId = "utt_${currentState.currentChapterIndex}_${currentState.currentParagraphIndex}"

        if (utteranceId == expectedUtteranceId && currentState.ttsStatus == TtsStatus.PLAYING) {
            nextParagraph()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager?.shutdown()
        if (isServiceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
            isServiceBound = false
        }
    }
}
