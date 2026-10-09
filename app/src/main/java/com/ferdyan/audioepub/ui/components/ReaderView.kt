package com.ferdyan.audioepub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdyan.audioepub.model.EpubChapter
import com.ferdyan.audioepub.model.ReaderThemeMode

@Composable
fun ReaderView(
    chapter: EpubChapter,
    currentParagraphIndex: Int,
    onParagraphClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 17,
    themeMode: ReaderThemeMode = ReaderThemeMode.SYSTEM
) {
    val listState = rememberLazyListState()

    // Los colores semánticos se derivan automáticamente del MaterialTheme.colorScheme actualizado (Claro, Oscuro, Sepia, Sistema)
    val containerBgColor = MaterialTheme.colorScheme.background
    val defaultTextColor = MaterialTheme.colorScheme.onBackground
    val activeParagraphBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    val activeParagraphTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val titleColor = MaterialTheme.colorScheme.primary

    // Auto-scroll al párrafo activo durante la reproducción
    LaunchedEffect(currentParagraphIndex) {
        if (currentParagraphIndex in chapter.paragraphs.indices) {
            listState.animateScrollToItem(currentParagraphIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerBgColor)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Título del capítulo
            item {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSizeSp + 6).sp
                    ),
                    color = titleColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            // Párrafos del capítulo
            itemsIndexed(chapter.paragraphs) { index, paragraphText ->
                val isCurrent = index == currentParagraphIndex
                val backgroundColor = if (isCurrent) activeParagraphBgColor else Color.Transparent
                val textColor = if (isCurrent) activeParagraphTextColor else defaultTextColor

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable { onParagraphClick(index) }
                        .padding(12.dp)
                ) {
                    Text(
                        text = paragraphText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = (fontSizeSp * 1.55).sp,
                            fontSize = fontSizeSp.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}
