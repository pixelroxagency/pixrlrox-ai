package com.example.data.util

import org.junit.Assert.*
import org.junit.Test

class M3uPlaylistParserTest {

    @Test
    fun test1_standardExtInfPlaylist() {
        val content = """
            #EXTM3U
            #EXTINF:-1,BBC World News
            https://example.com/bbc.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(1, result.channels.size)
        assertEquals("BBC World News", result.channels[0].title)
        assertEquals("https://example.com/bbc.m3u8", result.channels[0].url)
        assertFalse(result.isSingleHlsManifest)
    }

    @Test
    fun test2_multipleChannels() {
        val content = """
            #EXTM3U
            #EXTINF:-1,Channel One
            https://example.com/ch1.m3u8
            #EXTINF:-1,Channel Two
            https://example.com/ch2.m3u8
            #EXTINF:-1,Channel Three
            https://example.com/ch3.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(3, result.channels.size)
        assertEquals("Channel One", result.channels[0].title)
        assertEquals("Channel Two", result.channels[1].title)
        assertEquals("Channel Three", result.channels[2].title)
    }

    @Test
    fun test3_groupTitleAttribute() {
        val content = """
            #EXTM3U
            #EXTINF:-1 group-title="Sports",Sky Sports News
            https://example.com/skysports.m3u8
            #EXTINF:-1 group-title="Documentary",National Geographic
            https://example.com/natgeo.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(2, result.channels.size)
        assertEquals("Sports", result.channels[0].groupTitle)
        assertEquals("Sky Sports News", result.channels[0].title)
        assertEquals("Documentary", result.channels[1].groupTitle)
        assertEquals("National Geographic", result.channels[1].title)
    }

    @Test
    fun test4_tvgLogoAndAttributes() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn.us" tvg-name="CNN" tvg-logo="https://example.com/logos/cnn.png" group-title="News",CNN International
            https://example.com/cnn.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(1, result.channels.size)
        val ch = result.channels[0]
        assertEquals("CNN International", ch.title)
        assertEquals("cnn.us", ch.tvgId)
        assertEquals("CNN", ch.tvgName)
        assertEquals("https://example.com/logos/cnn.png", ch.tvgLogo)
        assertEquals("News", ch.groupTitle)
        assertEquals("https://example.com/cnn.m3u8", ch.url)
    }

    @Test
    fun test5_crlfLineEndings() {
        val content = "#EXTM3U\r\n#EXTINF:-1,CRLF Channel\r\nhttps://example.com/live.m3u8\r\n"
        val result = M3uPlaylistParser.parse(content)
        assertEquals(1, result.channels.size)
        assertEquals("CRLF Channel", result.channels[0].title)
        assertEquals("https://example.com/live.m3u8", result.channels[0].url)
    }

    @Test
    fun test6_blankLines() {
        val content = """
            #EXTM3U
            
            #EXTINF:-1,Channel With Blanks
            
            https://example.com/blank.m3u8
            
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(1, result.channels.size)
        assertEquals("Channel With Blanks", result.channels[0].title)
        assertEquals("https://example.com/blank.m3u8", result.channels[0].url)
    }

    @Test
    fun test7_commentsAndIgnoredDirectives() {
        val content = """
            #EXTM3U
            # This is a comment from the IPTV provider
            # Some other comment line
            #EXTINF:-1,Channel After Comment
            # Inline comment
            https://example.com/stream.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(1, result.channels.size)
        assertEquals("Channel After Comment", result.channels[0].title)
        assertEquals("https://example.com/stream.m3u8", result.channels[0].url)
    }

    @Test
    fun test8_missingOptionalAttributes() {
        val content = """
            #EXTM3U
            #EXTINF:-1,Simple Title Only
            https://example.com/simple.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(1, result.channels.size)
        val ch = result.channels[0]
        assertEquals("Simple Title Only", ch.title)
        assertNull(ch.tvgId)
        assertNull(ch.tvgName)
        assertNull(ch.tvgLogo)
        assertNull(ch.groupTitle)
    }

    @Test
    fun test9_duplicateUrlHandling() {
        val content = """
            #EXTM3U
            #EXTINF:-1,Duplicate First
            https://example.com/same_url.m3u8
            #EXTINF:-1,Duplicate Second
            https://example.com/same_url.m3u8
            #EXTINF:-1,Unique Third
            https://example.com/different_url.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(2, result.channels.size)
        assertEquals("Duplicate First", result.channels[0].title)
        assertEquals("Unique Third", result.channels[1].title)
    }

    @Test
    fun test10_malformedEntrySkipping() {
        val content = """
            #EXTM3U
            #EXTINF:-1,Valid Channel 1
            https://example.com/valid1.m3u8
            #EXTINF:-1,Broken Entry with Invalid scheme
            ftp://invalid-scheme.com/file.m3u8
            #EXTINF:-1,Broken Entry with non-url
            some random invalid string not a url
            #EXTINF:-1,Valid Channel 2
            https://example.com/valid2.m3u8
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(2, result.channels.size)
        assertEquals("Valid Channel 1", result.channels[0].title)
        assertEquals("Valid Channel 2", result.channels[1].title)
    }

    @Test
    fun test11_emptyPlaylist() {
        val content = """
            #EXTM3U
            # Just comments and empty lines
            
        """.trimIndent()

        val result = M3uPlaylistParser.parse(content)
        assertEquals(0, result.channels.size)
        assertFalse(result.isSingleHlsManifest)
    }

    @Test
    fun test12_hlsMediaPlaylistIsNotInterpretedAsIptvChannels() {
        val hlsManifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:10.0,
            segment001.ts
            #EXTINF:10.0,
            segment002.ts
            #EXTINF:10.0,
            segment003.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = M3uPlaylistParser.parse(hlsManifest)
        assertTrue("Must detect single HLS stream manifest", result.isSingleHlsManifest)
        assertEquals(0, result.channels.size)
    }
}
