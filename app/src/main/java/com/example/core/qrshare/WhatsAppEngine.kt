package com.example.core.qrshare

import android.net.Uri

object WhatsAppEngine {
    fun normalizePhoneNumber(input: String): String {
        return input.filter { it.isDigit() }
    }

    fun buildWhatsAppUri(countryCode: String, phoneNumber: String, message: String): Uri {
        val cleanCountry = normalizePhoneNumber(countryCode)
        val cleanPhone = normalizePhoneNumber(phoneNumber)
        val fullNumber = if (cleanCountry.isNotEmpty()) "$cleanCountry$cleanPhone" else cleanPhone
        val encodedMessage = android.net.Uri.encode(message)
        val url = if (encodedMessage.isNotBlank()) {
            "https://wa.me/$fullNumber?text=$encodedMessage"
        } else {
            "https://wa.me/$fullNumber"
        }
        return Uri.parse(url)
    }
}
