package com.example.core.text

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

class DevTextToolkitTest {

    // 1. Case transformations
    @Test
    fun testCaseTransformations() {
        val original = "hello World! test 123"
        assertEquals("HELLO WORLD! TEST 123", TextTransformEngine.toUpperCase(original))
        assertEquals("hello world! test 123", TextTransformEngine.toLowerCase(original))
        assertEquals("Hello World! Test 123", TextTransformEngine.toTitleCase(original))
        assertEquals("Hello world! Test 123", TextTransformEngine.toSentenceCase("hello world! test 123"))
    }

    // 2. Duplicate line removal & sorting
    @Test
    fun testLineOperations() {
        val lines = "zebra\napple\nbanana\napple\nbanana"
        val deduplicated = TextTransformEngine.removeDuplicateLines(lines)
        assertEquals("zebra\napple\nbanana", deduplicated)

        val sorted = TextTransformEngine.sortLinesAZ(deduplicated)
        assertEquals("apple\nbanana\nzebra", sorted)

        val sortedReverse = TextTransformEngine.sortLinesZA(deduplicated)
        assertEquals("zebra\nbanana\napple", sortedReverse)
    }

    // 3. Slug generator
    @Test
    fun testSlugGeneration() {
        val title = "  PixelRox Developer & Text Toolkit v2.0!  "
        val slug = TextTransformEngine.generateSlug(title)
        assertEquals("pixelrox-developer-text-toolkit-v2-0", slug)
    }

    // 4. JSON valid/invalid parsing
    @Test
    fun testJsonParsing() {
        val validJson = """{"name":"PixelRox","active":true,"version":2}"""
        val validRes = JsonEngine.validateAndFormat(validJson)
        assertTrue(validRes is JsonValidationResult.Valid)
        val validObj = validRes as JsonValidationResult.Valid
        assertTrue(validObj.isObject)
        assertEquals(3, validObj.keyCountOrItemCount)

        val minified = JsonEngine.minify(validJson)
        assertFalse(minified.contains("\n"))

        val invalidJson = """{"name":"PixelRox", invalid}"""
        val invalidRes = JsonEngine.validateAndFormat(invalidJson)
        assertTrue(invalidRes is JsonValidationResult.Invalid)
        val invalidObj = invalidRes as JsonValidationResult.Invalid
        assertNotNull(invalidObj.errorMessage)
    }

    // 5. Base64 UTF-8 round trip
    @Test
    fun testBase64RoundTrip() {
        val text = "PixelRox 🚀 Native Kotlin Text & Developer Toolkit @ 2026"
        val standardEncoded = Base64CodecEngine.encodeText(text, Base64Mode.STANDARD)
        val decoded = Base64CodecEngine.decodeText(standardEncoded, Base64Mode.STANDARD)
        assertEquals(text, decoded)

        val urlSafeEncoded = Base64CodecEngine.encodeText(text, Base64Mode.URL_SAFE)
        val urlSafeDecoded = Base64CodecEngine.decodeText(urlSafeEncoded, Base64Mode.URL_SAFE)
        assertEquals(text, urlSafeDecoded)
    }

    // 6. URL encode/decode round trip & malformed error handling
    @Test
    fun testUrlCodec() {
        val sample = "https://example.com/api?query=hello world & symbol=%20#anchor"
        val encoded = UrlCodecEngine.encode(sample, UrlEncodingMode.RFC3986_PERCENT)
        assertTrue(encoded.contains("%20"))
        assertFalse(encoded.contains(" "))

        val decoded = UrlCodecEngine.decode(encoded)
        assertEquals(sample, decoded)

        // Malformed percent check
        assertThrows(IllegalArgumentException::class.java) {
            UrlCodecEngine.decode("https://example.com?bad=%2G&param=1")
        }
    }

    // 7. Seconds vs milliseconds timestamp conversion
    @Test
    fun testTimestampConversion() {
        val epochSeconds = 1774166400L // Example epoch seconds
        val epochMillis = epochSeconds * 1000L

        val resSeconds = TimestampConverterEngine.fromEpoch(epochSeconds, TimestampUnit.SECONDS)
        val resMillis = TimestampConverterEngine.fromEpoch(epochMillis, TimestampUnit.MILLISECONDS)

        assertEquals(epochSeconds, resSeconds.unixSeconds)
        assertEquals(epochMillis, resMillis.unixMilliseconds)
        assertEquals(resSeconds.iso8601Utc, resMillis.iso8601Utc)
    }

    // 8. UUID v4 validity
    @Test
    fun testUuidGeneration() {
        val uuid = UuidGeneratorEngine.generateSingle(UuidConfig(uppercase = false, includeHyphens = true))
        val parsed = UUID.fromString(uuid)
        assertEquals(4, parsed.version())

        val batch = UuidGeneratorEngine.generateBatch(10)
        assertEquals(10, batch.size)
        assertEquals(10, batch.distinct().size)
    }

    // 9. Regex matching and invalid patterns
    @Test
    fun testRegexMatching() {
        val pattern = "(\\d{4})-(\\d{2})-(\\d{2})"
        val text = "Release dates: 2026-09-21 and 2026-10-15."

        val outcome = RegexTesterEngine.testRegex(pattern, text)
        assertTrue(outcome.isValid)
        assertEquals(2, outcome.matchCount)
        assertEquals("2026-09-21", outcome.matches[0].value)
        assertEquals("2026", outcome.matches[0].groups[1])
        assertEquals("09", outcome.matches[0].groups[2])
        assertEquals("21", outcome.matches[0].groups[3])

        val invalidOutcome = RegexTesterEngine.testRegex("[a-z(", text)
        assertFalse(invalidOutcome.isValid)
        assertNotNull(invalidOutcome.errorMessage)
    }

    // 10. Hash known test vectors
    @Test
    fun testHashVectors() {
        val input = "PixelRox"

        // Known SHA-256 for "PixelRox"
        val expectedSha256 = "c082855fcefb4a77a909fc72aa6455648f5aa204f762dd271b306fc6bfd31484"
        val actualSha256 = HashEngine.hashText(input, HashAlgorithm.SHA256)
        assertEquals(expectedSha256, actualSha256.hexValue)

        // Known MD5 for "PixelRox"
        val expectedMd5 = "5e022f42a00c6d999f8d1e3ee89600a9"
        val actualMd5 = HashEngine.hashText(input, HashAlgorithm.MD5)
        assertEquals(expectedMd5, actualMd5.hexValue)
    }

    // 11. Streamed hash and normalized comparison
    @Test
    fun testFileHashNormalization() {
        val rawInput = "   C082855F-CEFB-4A77-A909-FC72AA6455648F5AA204F762DD271B306FC6BFD31484  \n "
        val normalized = FileHashEngine.normalizeHash(rawInput)
        assertEquals("c082855fcefb4a77a909fc72aa6455648f5aa204f762dd271b306fc6bfd31484", normalized)
    }

    // 12. WhatsApp markdown formatting
    @Test
    fun testWhatsAppFormatting() {
        assertEquals("*hello*", WhatsAppFormatterEngine.wrapBold("hello"))
        assertEquals("_world_", WhatsAppFormatterEngine.wrapItalic("world"))
        assertEquals("~strike~", WhatsAppFormatterEngine.wrapStrikethrough("strike"))
        assertEquals("```code```", WhatsAppFormatterEngine.wrapMonospace("code"))
    }
}
