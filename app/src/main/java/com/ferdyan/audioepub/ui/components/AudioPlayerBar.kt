package com.ferdyan.audioepub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdyan.audioepub.model.TtsStatus
import com.ferdyan.audioepub.model.TtsVoice

@Composable
fun AudioPlayerBar(
    ttsStatus: TtsStatus,
    currentChapterIndex: Int,
    totalChapters: Int,
    currentParagraphIndex: Int,
    totalParagraphs: Int,
    speechRate: Float,
    availableVoices: List<TtsVoice>,
    selectedVoiceName: String?,
    onPlayPause: () -> Unit,
    onNextParagraph: () -> Unit,
    onPreviousParagraph: () -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetVoice: (String) -> Unit,
    modifier: Modifier = Modifier,
    onStop: () -> Unit = {}
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }

    val selectedVoiceDisplayName = availableVoices.find { it.name == selectedVoiceName }?.localeDisplayName ?: "Voz"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila 1: Texto Indicador de Progreso Centrado
            Text(
                text = "Capítulo ${currentChapterIndex + 1} de $totalChapters • Párrafo ${currentParagraphIndex + 1}/$totalParagraphs",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            // Fila 2: Controles Principales de Reproducción (Anterior, Play/Pausa central, Siguiente)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Retroceder Párrafo
                IconButton(
                    onClick = onPreviousParagraph,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Párrafo anterior",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.size(24.dp))

                // Botón Principal Play / Pausa (Eje central del reproductor)
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (ttsStatus == TtsStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ttsStatus == TtsStatus.PLAYING) "Pausar" else "Reproducir",
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.size(24.dp))

                // Avanzar Párrafo
                IconButton(
                    onClick = onNextParagraph,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Siguiente párrafo",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Fila 3: Opciones Secundarias (Velocidad y Selector de Voz) Centradas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selector de Velocidad
                Box {
                    OutlinedButton(
                        onClick = { showSpeedMenu = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Velocidad: ${speechRate}x", fontSize = 12.sp)
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                            DropdownMenuItem(
                                text = { Text("${rate}x") },
                                onClick = {
                                    onSetSpeechRate(rate)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }

                if (availableVoices.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(12.dp))

                    // Selector de Voz
                    Box {
                        OutlinedButton(
                            onClick = { showVoiceMenu = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedVoiceDisplayName,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        DropdownMenu(
                            expanded = showVoiceMenu,
                            onDismissRequest = { showVoiceMenu = false }
                        ) {
                            availableVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.localeDisplayName) },
                                    onClick = {
                                        onSetVoice(voice.name)
                                        showVoiceMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
