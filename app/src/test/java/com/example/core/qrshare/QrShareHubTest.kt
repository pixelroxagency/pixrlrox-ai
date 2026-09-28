package com.example.core.qrshare

import com.example.ui.navigation.Screen
import com.example.ui.screens.qrshare.QrShareTab
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrShareHubTest {

    @Test
    fun testQrShareHubExposesScannerTab() {
        val tabTitles = QrShareTab.entries.map { it.title }
        assertTrue("QR & Share Hub must expose Scan QR / Barcode tab", tabTitles.contains("Scan QR / Barcode"))
        val scannerTab = QrShareTab.entries.firstOrNull { it.name == "SCANNER" }
        assertNotNull(scannerTab)
        assertEquals("Scan QR / Barcode", scannerTab?.title)
    }

    @Test
    fun testCatalogPhase11bScannerConsolidation() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        val prodScanner = allTools["prod_scanner"]
        assertNull("prod_scanner must be absent from top-level catalog in phase 11b", prodScanner)

        val qrHub = allTools["qr_share_hub"]
        assertNotNull("qr_share_hub must exist in catalog", qrHub)
        assertEquals(Screen.QrShareHub.route, qrHub?.route)

        // Verify barcode scanner route constant is still preserved
        assertEquals("barcode_scanner", Screen.BarcodeScanner.route)

        val allToolsList = ToolCatalog.getAllTools()
        assertEquals(36, allToolsList.size)
        val uniqueIds = allToolsList.map { it.id }.toSet()
        assertEquals(36, uniqueIds.size)
    }

    @Test
    fun testPhoneNormalizationAndWhatsAppUri() {
        val raw = "+1 (555) 123-4567"
        val normalized = WhatsAppEngine.normalizePhoneNumber(raw)
        assertEquals("15551234567", normalized)

        val uri = WhatsAppEngine.buildWhatsAppUri("1", "(555) 123-4567", "Hello World")
        assertTrue(uri.toString().contains("wa.me/15551234567"))
        assertTrue(uri.toString().contains("Hello%20World"))
    }

    @Test
    fun testQrPayloadsAndWifiEscaping() {
        val plainText = "Hello QR"
        val bmp1 = QrCodeEngine.generateQrBitmap(plainText, 100, 100)
        assertNotNull(bmp1)

        val wifiPayload = QrCodeEngine.formatWifiPayload("My;Network,Home", "WPA", "secret;pass", false)
        assertTrue(wifiPayload.contains("WIFI:S:My\\;Network\\,Home"))
        assertTrue(wifiPayload.contains("P:secret\\;pass"))

        val vcard = QrCodeEngine.formatVCardPayload("John Doe", "123456789", "john@example.com", "PixelRox")
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("FN:John Doe"))
        assertTrue(vcard.contains("TEL:123456789"))
        assertTrue(vcard.contains("EMAIL:john@example.com"))
        assertTrue(vcard.contains("ORG:PixelRox"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun testLinkCleanerEngine() {
        val raw = "https://example.com/path?id=42&utm_source=google&utm_medium=cpc&custom_param=keep#anchor"
        
        // Test with tracking removal and keep fragment
        val res1 = LinkCleanerEngine.cleanUrl(raw, removeTracking = true, removeFragment = false)
        assertNull(res1.errorMessage)
        assertTrue(res1.cleanedUrl.contains("id=42"))
        assertTrue(res1.cleanedUrl.contains("custom_param=keep"))
        assertTrue(res1.cleanedUrl.contains("#anchor"))
        assertFalse(res1.cleanedUrl.contains("utm_source"))
        assertEquals(2, res1.removedParameters.size)

        // Test with fragment removal
        val res2 = LinkCleanerEngine.cleanUrl(raw, removeTracking = true, removeFragment = true)
        assertFalse(res2.cleanedUrl.contains("#anchor"))

        // Test malformed URL
        val malformed = LinkCleanerEngine.cleanUrl("   ")
        assertNotNull(malformed.errorMessage)
    }

    @Test
    fun testPrivateSharePrepSanitization() {
        val raw = "   Hello    World   "
        val sanitized = PrivateSharePrepEngine.sanitizeText(raw, trimWhitespace = true)
        assertEquals("Hello World", sanitized)
    }
}
