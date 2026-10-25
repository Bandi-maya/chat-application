package com.example.chat.ui.chats.linked_devices

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders the Chaty contact QR exactly as qr_flutter's QrImageView does for
 *  linked_devices_qr_screen.dart: QR auto-version, error correction H,
 *  square eyes/modules, quiet zone included, modules painted in the theme
 *  foreground color.
 *
 *  Requires gradle dependency (integration owner):
 *      implementation("com.google.zxing:core:3.5.3")
 *  Free, on-device, JVM-friendly (also usable from unit tests). */
object QrCodeRenderer {

    /** Dart QrImageView(data:, size: 230, errorCorrectionLevel: H). */
    fun render(payload: String, sizePx: Int, foregroundArgb: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) foregroundArgb else android.graphics.Color.TRANSPARENT)
            }
        }
        return bitmap
    }
}
