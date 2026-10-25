package com.example.chat

import com.example.chat.ui.media.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfProcessingParityTest {

    private fun svc() = PdfProcessingService(FileSanitizationService { File("/tmp") })

    @Test fun `text pdf bytes are valid and round-trip through extraction`() {
        val tmp = File("/tmp/pdf-parity"); tmp.mkdirs()
        val src = File(tmp, "notes.txt").apply { writeText("Hello world\nsecond line\n(parens) and \\ backslash") }
        val result = svc().convertTextToPdf(ConversionRequest(
            id = "t1", type = ConversionType.textToPdf,
            sourceFilePaths = listOf(src.absolutePath),
            outputDirectory = tmp.absolutePath,
            outputFileName = "My Notes",
        ))
        assertTrue(result.success)
        assertEquals(1, result.outputFilePaths.size)
        val bytes = File(result.outputFilePaths[0]).readBytes()
        val head = String(bytes, 0, 8)
        assertEquals("%PDF-1.4", head)
        val body = String(bytes, Charsets.ISO_8859_1)
        assertTrue(body.contains("/Type /Catalog"))
        assertTrue(body.contains("startxref"))
        assertTrue(body.endsWith("%%EOF\n"))
        assertEquals(result.totalBytes, bytes.size.toLong())
        // Dart sanitizes the same way -> same filename contract
        assertTrue(result.outputFilePaths[0].endsWith(".pdf"))
        // extraction round-trip: escapes preserved, title line present
        val text = svc().extractTextFromPdf(result.outputFilePaths[0])
        assertTrue(text.contains("Hello world"))
        assertTrue(text.contains("second line"))
        assertTrue(text.contains("- Page 1/1"))
    }

    @Test fun `image pdf embeds bytes with png flate vs jpeg dct filter`() {
        val tmp = File("/tmp/pdf-parity-img"); tmp.mkdirs()
        val png = File(tmp, "a.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 1, 2, 3, 4)) }
        val jpg = File(tmp, "b.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 5, 6, 7)) }
        val result = svc().convertImagesToPdf(ConversionRequest(
            id = "t2", type = ConversionType.imagesToPdf,
            sourceFilePaths = listOf(png.absolutePath, jpg.absolutePath, "/nonexistent/x.png"),
            outputDirectory = tmp.absolutePath,
            outputFileName = "scan.pdf",
        ))
        assertTrue(result.success)
        assertEquals("scan.pdf", File(result.outputFilePaths[0]).name) // no double .pdf
        val body = String(File(result.outputFilePaths[0]).readBytes(), Charsets.ISO_8859_1)
        assertTrue(body.contains("/FlateDecode"))
        assertTrue(body.contains("/DCTDecode"))
        assertTrue(body.contains("/Count 2"))
    }

    @Test fun `no valid images fails with exact dart message`() {
        val r = svc().convertImagesToPdf(ConversionRequest(
            id = "t3", type = ConversionType.imagesToPdf,
            sourceFilePaths = listOf("/nope/1.png"),
            outputDirectory = "/tmp/pdf-parity-img",
            outputFileName = "x",
        ))
        assertFalse(r.success)
        assertEquals("No accessible source images were provided.", r.errorMessage)
    }

    @Test fun `empty source list and missing file fail with exact dart messages`() {
        val r1 = svc().convertTextToPdf(ConversionRequest(id = "t4", type = ConversionType.textToPdf, sourceFilePaths = listOf(), outputDirectory = "/tmp", outputFileName = "x"))
        assertEquals("No source text file specified.", r1.errorMessage)
        val r2 = svc().convertTextToPdf(ConversionRequest(id = "t5", type = ConversionType.textToPdf, sourceFilePaths = listOf("/nope.txt"), outputDirectory = "/tmp", outputFileName = "x"))
        assertEquals("Source file does not exist.", r2.errorMessage)
    }

    @Test fun `extract from missing or plain-text file matches dart fallbacks`() {
        assertEquals("", svc().extractTextFromPdf("/definitely/not/here.pdf"))
        val f = File("/tmp/plain.bin").apply { writeText("no pdf markers") }
        assertEquals("No accessible extractable text stream found in this PDF.", svc().extractTextFromPdf(f.absolutePath))
    }
}
