package com.example.chat

import com.example.chat.data.local.EditKind
import com.example.chat.data.local.EditedOutputValidator
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class EditedOutputValidatorTest {
    @Test fun defaultValidatorDeniesEveryKind() {
        val f = File.createTempFile("val", ".part"); f.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        try { EditKind.values().forEach { assertFalse(EditedOutputValidator.DENY.isValid(f, it)) } } finally { f.delete() }
    }
}
