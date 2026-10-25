package com.example.chat.ui.media

import java.io.File
import java.nio.charset.StandardCharsets

/** Exact port of lib/features/media/services/pdf_processing_service.dart.
 *
 *  Clean-room standard PDF byte generator (images -> PDF, text -> PDF) and
 *  BT...ET text extraction. No platform PDF APIs involved; works anywhere.
 */
class PdfProcessingService(
    private val sanitizer: FileSanitizationService,
) {
    /** Converts multiple image files into a single, clean, valid PDF byte stream. */
    fun convertImagesToPdf(request: ConversionRequest): ConversionResult {
        val start = System.nanoTime()
        try {
            val validImages = request.sourceFilePaths
                .map { File(it) }
                .filter { it.exists() }
            if (validImages.isEmpty()) {
                return ConversionResult(
                    success = false,
                    errorMessage = "No accessible source images were provided.",
                )
            }

            val outDir = File(request.outputDirectory)
            if (!outDir.exists()) outDir.mkdirs()

            val safeName = sanitizer.sanitizeFileName(request.outputFileName)
            val outPath = "${outDir.path}/$safeName${if (safeName.endsWith(".pdf")) "" else ".pdf"}"

            val pdfBytes = buildPdfFromImages(validImages)
            File(outPath).writeBytes(pdfBytes)

            return ConversionResult(
                success = true,
                outputFilePaths = listOf(outPath),
                totalBytes = pdfBytes.size.toLong(),
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        } catch (e: Exception) {
            return ConversionResult(
                success = false,
                errorMessage = "Image to PDF conversion failed: $e",
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        }
    }

    /** Converts plain text or markdown content into a structured, readable PDF. */
    fun convertTextToPdf(request: ConversionRequest): ConversionResult {
        val start = System.nanoTime()
        try {
            if (request.sourceFilePaths.isEmpty()) {
                return ConversionResult(success = false, errorMessage = "No source text file specified.")
            }
            val source = File(request.sourceFilePaths.first())
            if (!source.exists()) {
                return ConversionResult(success = false, errorMessage = "Source file does not exist.")
            }

            val text = source.readText()
            val outDir = File(request.outputDirectory)
            if (!outDir.exists()) outDir.mkdirs()

            val safeName = sanitizer.sanitizeFileName(request.outputFileName)
            val outPath = "${outDir.path}/$safeName${if (safeName.endsWith(".pdf")) "" else ".pdf"}"

            val pdfBytes = buildPdfFromText(text, title = source.name)
            File(outPath).writeBytes(pdfBytes)

            return ConversionResult(
                success = true,
                outputFilePaths = listOf(outPath),
                totalBytes = pdfBytes.size.toLong(),
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        } catch (e: Exception) {
            return ConversionResult(
                success = false,
                errorMessage = "Text to PDF conversion failed: $e",
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        }
    }

    /** Extracts textual contents from PDF streams. */
    fun extractTextFromPdf(pdfPath: String): String {
        val file = File(pdfPath)
        if (!file.exists()) return ""

        try {
            val bytes = file.readBytes()
            val latin1Str = String(bytes, StandardCharsets.ISO_8859_1)
            val buffer = StringBuilder()

            // Extract text within BT ... ET blocks
            val btRegex = Regex("BT[\\s\\S]*?ET")
            val matches = btRegex.findAll(latin1Str)

            for (m in matches) {
                val block = m.value
                // Look for (text) Tj
                val tjRegex = Regex("\\((.*?)\\)\\s*Tj")
                for (tj in tjRegex.findAll(block)) {
                    val t = tj.groupValues[1]
                    if (t.isNotEmpty()) {
                        buffer.append("$t ")
                    }
                }
            }
            val extracted = buffer.toString().trim()
            return extracted.ifEmpty { "No accessible extractable text stream found in this PDF." }
        } catch (e: Exception) {
            return "Error extracting text: $e"
        }
    }

    /** Low-level clean-room standard PDF byte generator for images. */
    fun buildPdfFromImages(images: List<File>): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        fun write(s: String) { buffer.write(s.toByteArray(StandardCharsets.UTF_8)) }

        // Header
        write("%PDF-1.4\n%âãÏÓ\n")

        val offsets = mutableListOf<Int>()
        var currentOffset = buffer.size()

        // 1: Catalog
        offsets.add(currentOffset)
        write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        currentOffset = buffer.size()

        // Obj 2: Pages; per image i: Obj (3+i*3) Page, (4+i*3) XObject, (5+i*3) content
        val pageCount = images.size
        val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 3} 0 R" }

        offsets.add(currentOffset)
        write("2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $pageCount >>\nendobj\n")
        currentOffset = buffer.size()

        for (i in 0 until pageCount) {
            val imgBytes = images[i].readBytes()
            val isPng = imgBytes.size > 4 &&
                (imgBytes[0].toInt() and 0xFF) == 0x89 &&
                (imgBytes[1].toInt() and 0xFF) == 0x50

            // Page dimensions (standard A4: 595 x 842 pt)
            val pageWidth = 595
            val pageHeight = 842

            val pageObjIndex = 3 + i * 3
            val imgObjIndex = 4 + i * 3
            val contentObjIndex = 5 + i * 3

            // Page Object
            offsets.add(currentOffset)
            write("$pageObjIndex 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 $pageWidth $pageHeight ] /Resources << /XObject << /Im$i $imgObjIndex 0 R >> >> /Contents $contentObjIndex 0 R >>\nendobj\n")
            currentOffset = buffer.size()

            // Image Object (DCTDecode for JPEG or Flate for standard)
            offsets.add(currentOffset)
            val filter = if (isPng) "/FlateDecode" else "/DCTDecode"
            write("$imgObjIndex 0 obj\n<< /Type /XObject /Subtype /Image /Width $pageWidth /Height $pageHeight /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter $filter /Length ${imgBytes.size} >>\nstream\n")
            buffer.write(imgBytes)
            write("\nendstream\nendobj\n")
            currentOffset = buffer.size()

            // Content stream (Draw image scaled to page)
            val contentStream = "q\n$pageWidth 0 0 $pageHeight 0 0 cm\n/Im$i Do\nQ\n"
            val contentBytes = contentStream.toByteArray(StandardCharsets.UTF_8)
            offsets.add(currentOffset)
            write("$contentObjIndex 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n$contentStream\nendstream\nendobj\n")
            currentOffset = buffer.size()
        }

        // XRef table
        val xrefOffset = currentOffset
        val totalObjs = 2 + pageCount * 3 + 1
        write("xref\n0 $totalObjs\n0000000000 65535 f \n")
        for (off in offsets) {
            write("${off.toString().padStart(10, '0')} 00000 n \n")
        }

        // Trailer
        write("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

        return buffer.toByteArray()
    }

    /** Low-level clean-room PDF generator for text documents. */
    fun buildPdfFromText(text: String, title: String = "Document"): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        fun write(s: String) { buffer.write(s.toByteArray(StandardCharsets.UTF_8)) }

        write("%PDF-1.4\n%âãÏÓ\n")

        val offsets = mutableListOf<Int>()
        var currentOffset = buffer.size()

        // 1: Catalog
        offsets.add(currentOffset)
        write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        currentOffset = buffer.size()

        // Split text into chunks/pages
        val lines = text.split("\n")
        val linesPerPage = 42
        val pagesList = mutableListOf<List<String>>()
        var i = 0
        while (i < lines.size) {
            pagesList.add(lines.subList(i, minOf(i + linesPerPage, lines.size)))
            i += linesPerPage
        }
        if (pagesList.isEmpty()) pagesList.add(listOf("[Empty Document]"))

        val pageCount = pagesList.size
        val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 2} 0 R" }

        // 2: Pages
        offsets.add(currentOffset)
        write("2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $pageCount >>\nendobj\n")
        currentOffset = buffer.size()

        for (p in 0 until pageCount) {
            val pageLines = pagesList[p]
            val pageObjIndex = 3 + p * 2
            val contentObjIndex = 4 + p * 2

            // Page Object
            offsets.add(currentOffset)
            write("$pageObjIndex 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> /Contents $contentObjIndex 0 R >>\nendobj\n")
            currentOffset = buffer.size()

            // Stream content
            val streamBuilder = StringBuilder()
            streamBuilder.append("BT\n/F1 14 Tf\n50 800 Td\n(${escapePdfText(title)} - Page ${p + 1}/$pageCount) Tj\n/F1 10 Tf\n0 -25 Td\n")
            for (line in pageLines) {
                val safeLine = escapePdfText(line.trimEnd())
                streamBuilder.append("($safeLine) Tj\n0 -16 Td\n")
            }
            streamBuilder.append("ET\n")

            val contentString = streamBuilder.toString()
            val contentBytes = contentString.toByteArray(StandardCharsets.UTF_8)
            offsets.add(currentOffset)
            write("$contentObjIndex 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n$contentString\nendstream\nendobj\n")
            currentOffset = buffer.size()
        }

        val xrefOffset = currentOffset
        val totalObjs = 2 + pageCount * 2 + 1
        write("xref\n0 $totalObjs\n0000000000 65535 f \n")
        for (off in offsets) {
            write("${off.toString().padStart(10, '0')} 00000 n \n")
        }

        write("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return buffer.toByteArray()
    }

    private fun escapePdfText(text: String): String {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }
}
