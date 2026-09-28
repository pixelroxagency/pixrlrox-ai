package com.example.core.qrshare

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeEngine {

    enum class QrType { PLAIN_TEXT, URL, WIFI, VCARD }

    fun formatWifiPayload(ssid: String, security: String, pass: String, hidden: Boolean): String {
        val escapedSsid = ssid.replace(";", "\\;").replace(",", "\\,")
        val escapedPass = pass.replace(";", "\\;").replace(",", "\\,")
        return "WIFI:S:$escapedSsid;T:$security;P:$escapedPass;H:$hidden;;"
    }

    fun formatVCardPayload(name: String, phone: String, email: String, org: String): String {
        return buildString {
            append("BEGIN:VCARD\n")
            append("VERSION:3.0\n")
            append("FN:$name\n")
            if (phone.isNotBlank()) append("TEL:$phone\n")
            if (email.isNotBlank()) append("EMAIL:$email\n")
            if (org.isNotBlank()) append("ORG:$org\n")
            append("END:VCARD")
        }
    }

    fun generateQrBitmap(payload: String, width: Int = 512, height: Int = 512): Bitmap? {
        if (payload.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, width, height, hints)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
