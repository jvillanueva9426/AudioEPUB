package com.ferdyan.audioepub.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.ferdyan.audioepub.model.SavedBookMetadata
import com.ferdyan.audioepub.parser.EpubParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class EpubRepository(private val context: Context) {

    private val libraryDir = File(context.filesDir, "library").apply {
        if (!exists()) mkdirs()
    }

    private val metadataFile = File(context.filesDir, "library_metadata.json")

    @Synchronized
    fun getSavedBooks(): List<SavedBookMetadata> {
        if (!metadataFile.exists()) return emptyList()

        return try {
            val jsonString = metadataFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonString)
            val books = mutableListOf<SavedBookMetadata>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                books.add(
                    SavedBookMetadata(
                        id = obj.getString("id"),
                        fileName = obj.getString("fileName"),
                        title = obj.getString("title"),
                        author = obj.getString("author"),
                        addedDate = obj.optLong("addedDate", System.currentTimeMillis()),
                        lastChapterIndex = obj.optInt("lastChapterIndex", 0),
                        lastParagraphIndex = obj.optInt("lastParagraphIndex", 0),
                        totalChapters = obj.optInt("totalChapters", 0)
                    )
                )
            }
            books.sortedByDescending { it.addedDate }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun importEpub(contentResolver: ContentResolver, uri: Uri): SavedBookMetadata {
        val bookId = UUID.randomUUID().toString()
        val fileName = "$bookId.epub"
        val targetFile = File(libraryDir, fileName)

        // 1. Copiar el archivo EPUB a la biblioteca interna
        contentResolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) throw IllegalStateException("No se pudo leer el archivo seleccionado.")
            FileOutputStream(targetFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // 2. Extraer metadatos leyendo el archivo interno recién copiado
        val book = FileInputStream(targetFile).use { EpubParser.parse(it) }

        val metadata = SavedBookMetadata(
            id = bookId,
            fileName = fileName,
            title = book.title,
            author = book.author,
            addedDate = System.currentTimeMillis(),
            lastChapterIndex = 0,
            lastParagraphIndex = 0,
            totalChapters = book.chapters.size
        )

        // 3. Guardar metadatos en el JSON local
        val currentBooks = getSavedBooks().toMutableList()
        currentBooks.add(0, metadata)
        saveMetadataList(currentBooks)

        return metadata
    }

    @Synchronized
    fun updateProgress(bookId: String, chapterIndex: Int, paragraphIndex: Int) {
        val currentBooks = getSavedBooks().map { book ->
            if (book.id == bookId) {
                book.copy(
                    lastChapterIndex = chapterIndex,
                    lastParagraphIndex = paragraphIndex
                )
            } else {
                book
            }
        }
        saveMetadataList(currentBooks)
    }

    @Synchronized
    fun deleteBook(bookId: String) {
        val currentBooks = getSavedBooks()
        val bookToDelete = currentBooks.find { it.id == bookId }

        if (bookToDelete != null) {
            val file = File(libraryDir, bookToDelete.fileName)
            if (file.exists()) {
                file.delete()
            }
            val updatedList = currentBooks.filter { it.id != bookId }
            saveMetadataList(updatedList)
        }
    }

    fun openBookInputStream(fileName: String): InputStream {
        val file = File(libraryDir, fileName)
        if (!file.exists()) {
            throw IllegalStateException("El archivo del libro no existe en la biblioteca.")
        }
        return FileInputStream(file)
    }

    private fun saveMetadataList(books: List<SavedBookMetadata>) {
        try {
            val jsonArray = JSONArray()
            for (book in books) {
                val obj = JSONObject().apply {
                    put("id", book.id)
                    put("fileName", book.fileName)
                    put("title", book.title)
                    put("author", book.author)
                    put("addedDate", book.addedDate)
                    put("lastChapterIndex", book.lastChapterIndex)
                    put("lastParagraphIndex", book.lastParagraphIndex)
                    put("totalChapters", book.totalChapters)
                }
                jsonArray.put(obj)
            }
            metadataFile.writeText(jsonArray.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
