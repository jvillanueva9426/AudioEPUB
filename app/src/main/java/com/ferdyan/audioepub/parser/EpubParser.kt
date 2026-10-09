package com.ferdyan.audioepub.parser

import com.ferdyan.audioepub.model.EpubBook
import com.ferdyan.audioepub.model.EpubChapter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object EpubParser {

    fun parse(inputStream: InputStream): EpubBook {
        val filesMap = mutableMapOf<String, ByteArray>()

        // 1. Extraer todas las entradas del ZIP a memoria
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    filesMap[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // 2. Encontrar el archivo OPF en META-INF/container.xml
        val opfPath = findOpfPath(filesMap)
            ?: throw IllegalStateException("No se encontró META-INF/container.xml o la ruta OPF.")

        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
        val opfBytes = filesMap[opfPath]
            ?: throw IllegalStateException("No se pudo leer el archivo OPF en $opfPath")

        // 3. Procesar el XML del archivo OPF
        val opfDoc = parseXml(opfBytes)

        // Extraer metadatos
        val title = opfDoc.getElementsByTagName("dc:title").item(0)?.textContent?.trim() ?: "Sin Título"
        val author = opfDoc.getElementsByTagName("dc:creator").item(0)?.textContent?.trim() ?: "Autor Desconocido"

        // Extraer Manifiesto (id -> href)
        val manifestMap = mutableMapOf<String, String>()
        val manifestNodeList = opfDoc.getElementsByTagName("item")
        for (i in 0 until manifestNodeList.length) {
            val node = manifestNodeList.item(i)
            val id = node.attributes?.getNamedItem("id")?.nodeValue
            val href = node.attributes?.getNamedItem("href")?.nodeValue
            if (id != null && href != null) {
                manifestMap[id] = href
            }
        }

        // Extraer Espina (spine) para orden cronológico de lectura
        val spineList = mutableListOf<String>()
        val spineNodeList = opfDoc.getElementsByTagName("itemref")
        for (i in 0 until spineNodeList.length) {
            val node = spineNodeList.item(i)
            val idref = node.attributes?.getNamedItem("idref")?.nodeValue
            if (idref != null) {
                spineList.add(idref)
            }
        }

        // 4. Cargar capítulos
        val chapters = mutableListOf<EpubChapter>()
        var chapterIndex = 0

        for (idref in spineList) {
            val href = manifestMap[idref] ?: continue
            val fullPath = normalizePath(opfDir + href)
            val chapterBytes = filesMap[fullPath] ?: continue

            val htmlString = String(chapterBytes, StandardCharsets.UTF_8)
            val jsoupDoc = Jsoup.parse(htmlString)

            val chapterTitle = extractChapterTitle(jsoupDoc) ?: "Capítulo ${chapterIndex + 1}"
            val paragraphs = extractParagraphs(jsoupDoc)

            if (paragraphs.isNotEmpty()) {
                chapters.add(
                    EpubChapter(
                        index = chapterIndex,
                        id = idref,
                        title = chapterTitle,
                        paragraphs = paragraphs
                    )
                )
                chapterIndex++
            }
        }

        if (chapters.isEmpty()) {
            throw IllegalStateException("El libro EPUB no contiene párrafos o capítulos legibles.")
        }

        return EpubBook(
            title = title,
            author = author,
            coverBitmap = null,
            chapters = chapters
        )
    }

    private fun findOpfPath(filesMap: Map<String, ByteArray>): String? {
        val containerBytes = filesMap["META-INF/container.xml"] ?: return null
        return try {
            val doc = parseXml(containerBytes)
            val rootfileNodes = doc.getElementsByTagName("rootfile")
            if (rootfileNodes.length > 0) {
                rootfileNodes.item(0).attributes.getNamedItem("full-path")?.nodeValue
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseXml(xmlBytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xmlBytes))
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val stack = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun extractChapterTitle(doc: Document): String? {
        val h1 = doc.select("h1, h2, h3").first()?.text()?.trim()
        if (!h1.isNullOrEmpty()) return h1
        val title = doc.title().trim()
        return if (title.isNotEmpty()) title else null
    }

    private fun extractParagraphs(doc: Document): List<String> {
        val elements = doc.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")
        val paragraphs = mutableListOf<String>()

        for (el in elements) {
            val text = el.text().trim().replace("\\s+".toRegex(), " ")
            if (text.length > 2) {
                // Dividir en oraciones si el párrafo es demasiado largo para evitar pausas bruscas en TTS
                if (text.length > 250) {
                    val sentences = text.split("(?<=[.!?])\\s+".toRegex())
                    for (sentence in sentences) {
                        val cleanSentence = sentence.trim()
                        if (cleanSentence.length > 2) {
                            paragraphs.add(cleanSentence)
                        }
                    }
                } else {
                    paragraphs.add(text)
                }
            }
        }
        return paragraphs
    }
}
