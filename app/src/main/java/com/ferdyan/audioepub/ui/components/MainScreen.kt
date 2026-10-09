package com.ferdyan.audioepub.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.ferdyan.audioepub.model.ScreenMode
import com.ferdyan.audioepub.ui.EpubViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: EpubViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Interceptar botón atrás de Android
    BackHandler(enabled = uiState.screenMode != ScreenMode.LIBRARY) {
        viewModel.switchScreenMode(ScreenMode.LIBRARY)
    }

    // Mostrar errores en Snackbar si ocurren
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    // Launcher para importar archivos .epub
    val epubPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importBookFromUri(it) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = uiState.screenMode == ScreenMode.READER && uiState.currentBook != null,
        drawerContent = {
            val book = uiState.currentBook
            if (book != null) {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Text(
                        text = "Capítulos (${book.chapters.size})",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(book.chapters) { index, chapter ->
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = chapter.title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (index == uiState.currentChapterIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selected = index == uiState.currentChapterIndex,
                                onClick = {
                                    viewModel.selectChapter(index)
                                    scope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    unselectedContainerColor = Color.Transparent,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        val titleText = when (uiState.screenMode) {
                            ScreenMode.LIBRARY -> "Mi Biblioteca"
                            ScreenMode.READER -> uiState.currentBook?.title ?: "Lector EPUB"
                            ScreenMode.SETTINGS -> "Ajustes"
                        }
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (uiState.screenMode != ScreenMode.LIBRARY) {
                            IconButton(onClick = { viewModel.switchScreenMode(ScreenMode.LIBRARY) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver a Biblioteca"
                                )
                            }
                        }
                    },
                    actions = {
                        if (uiState.screenMode == ScreenMode.READER && uiState.currentBook != null) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menú de capítulos"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = "Biblioteca") },
                        label = { Text("Biblioteca") },
                        selected = uiState.screenMode == ScreenMode.LIBRARY,
                        onClick = { viewModel.switchScreenMode(ScreenMode.LIBRARY) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Book, contentDescription = "Lector") },
                        label = { Text("Lector") },
                        selected = uiState.screenMode == ScreenMode.READER,
                        enabled = uiState.currentBook != null,
                        onClick = { viewModel.switchScreenMode(ScreenMode.READER) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                        label = { Text("Ajustes") },
                        selected = uiState.screenMode == ScreenMode.SETTINGS,
                        onClick = { viewModel.switchScreenMode(ScreenMode.SETTINGS) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Procesando libro EPUB...")
                    }
                } else {
                    when (uiState.screenMode) {
                        ScreenMode.LIBRARY -> {
                            LibraryView(
                                savedBooks = uiState.savedBooks,
                                onOpenBook = { metadata -> viewModel.openBook(metadata) },
                                onDeleteBook = { bookId -> viewModel.deleteBook(bookId) },
                                onImportBook = { epubPickerLauncher.launch("application/epub+zip") }
                            )
                        }

                        ScreenMode.READER -> {
                            val currentChapter = uiState.currentBook?.chapters?.getOrNull(uiState.currentChapterIndex)
                            if (currentChapter != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    ReaderView(
                                        chapter = currentChapter,
                                        currentParagraphIndex = uiState.currentParagraphIndex,
                                        fontSizeSp = uiState.appSettings.fontSizeSp,
                                        themeMode = uiState.appSettings.themeMode,
                                        onParagraphClick = { index ->
                                            viewModel.selectParagraphDirectly(index)
                                        }
                                    )

                                    AudioPlayerBar(
                                        ttsStatus = uiState.ttsStatus,
                                        currentChapterIndex = uiState.currentChapterIndex,
                                        totalChapters = uiState.currentBook?.chapters?.size ?: 1,
                                        currentParagraphIndex = uiState.currentParagraphIndex,
                                        totalParagraphs = currentChapter.paragraphs.size,
                                        speechRate = uiState.speechRate,
                                        availableVoices = uiState.availableVoices,
                                        selectedVoiceName = uiState.selectedVoiceName,
                                        onPlayPause = { viewModel.playOrPause() },
                                        onStop = { viewModel.stop() },
                                        onNextParagraph = { viewModel.nextParagraph() },
                                        onPreviousParagraph = { viewModel.previousParagraph() },
                                        onSetSpeechRate = { rate -> viewModel.setSpeechRate(rate) },
                                        onSetVoice = { voice -> viewModel.setVoice(voice) },
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }

                        ScreenMode.SETTINGS -> {
                            SettingsView(
                                appSettings = uiState.appSettings,
                                onUpdateFontSize = { viewModel.updateFontSize(it) },
                                onUpdateReaderThemeMode = { viewModel.updateReaderThemeMode(it) },
                                onClearLibrary = { viewModel.clearAllLibrary() }
                            )
                        }
                    }
                }
            }
        }
    }
}
