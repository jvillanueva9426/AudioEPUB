package com.ferdyan.audioepub.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    val context = LocalContext.current

    var showPermissionExplanationDialog by remember { mutableStateOf(false) }

    // Interceptación de botón atrás de Android
    BackHandler(enabled = uiState.screenMode != ScreenMode.LIBRARY) {
        viewModel.switchScreenMode(ScreenMode.LIBRARY)
    }

    // Mostrar errores en Snackbar si ocurren
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    // Launcher para importar archivos .epub usando el Explorador de Documentos y Memoria Interna
    val epubPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.importBookFromUri(it)
        }
    }

    // Launcher para solicitar permisos runtime en Android 10 o inferior
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        epubPickerLauncher.launch(arrayOf("*/*"))
    }

    fun hasFullStorageAccess(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    val handleImportBookWithPermissionCheck = {
        if (!hasFullStorageAccess(context)) {
            showPermissionExplanationDialog = true
        } else {
            epubPickerLauncher.launch(arrayOf("*/*"))
        }
    }

    // Diálogo emergente explicativo de solicitud de permisos
    if (showPermissionExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanationDialog = false },
            title = { Text("Permiso de Almacenamiento Necesario") },
            text = {
                Text("Para seleccionar y leer tus libros .epub guardados en la memoria interna de tu teléfono (Descargas, Documentos, Tarjeta SD), AudioEPUB necesita permiso de acceso a archivos.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionExplanationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + context.packageName)
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        } else {
                            storagePermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    }
                ) {
                    Text("Conceder Permiso")
                }
            }
        )
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
                                onImportBook = handleImportBookWithPermissionCheck
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
