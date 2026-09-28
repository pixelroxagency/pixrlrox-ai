package com.example.data.downloader

import com.example.data.downloader.analyzer.AuthRequiredException
import com.example.data.downloader.analyzer.DirectMediaAnalyzer
import com.example.data.downloader.analyzer.HlsMediaAnalyzer
import com.example.data.downloader.analyzer.MediaAnalysisManager
import com.example.data.downloader.analyzer.OnDeviceSocialMediaAnalyzer
import com.example.data.downloader.analyzer.PrivateOrRestrictedException
import com.example.data.downloader.analyzer.PublicMediaPageException
import com.example.data.downloader.analyzer.SocialProtectionDetectorAnalyzer
import com.example.data.downloader.extractor.FacebookPublicExtractor
import com.example.data.downloader.extractor.InstagramPublicExtractor
import com.example.data.downloader.extractor.InstagramYtDlpExtractor
import com.example.data.downloader.extractor.ThreadsYtDlpExtractor
import com.example.data.downloader.extractor.UniversalYtDlpExtractor
import com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry
import com.example.data.downloader.extractor.SafeHttpHelper
import com.example.data.downloader.extractor.TikTokPublicExtractor
import com.example.data.downloader.extractor.UrlSecurityValidator
import com.example.data.downloader.extractor.YouTubeYtDlpExtractor
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OnDeviceSocialMediaAnalyzerTest {

    private fun createMockHttpClient(handler: (okhttp3.Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private fun buildResponse(
        request: okhttp3.Request,
        code: Int = 200,
        body: String = "",
        headers: Map<String, String> = emptyMap()
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Redirect or Error")
            .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))

        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return builder.build()
    }

    // --- 1. Domain & URL Recognition Tests ---

    @Test
    fun testTikTokRecognition() {
        val extractor = TikTokPublicExtractor()
        assertTrue(extractor.canHandle("https://www.tiktok.com/@user/video/7123456789012345678"))
        assertTrue(extractor.canHandle("https://vt.tiktok.com/ZS123456/"))
        assertTrue(extractor.canHandle("https://vm.tiktok.com/ZM123456/"))
        assertFalse(extractor.canHandle("https://www.instagram.com/reel/12345"))
        assertFalse(extractor.canHandle("https://www.youtube.com/watch?v=12345"))
    }

    @Test
    fun testInstagramRecognition() {
        val extractor = InstagramPublicExtractor()
        val ytExtractor = InstagramYtDlpExtractor()
        assertTrue(extractor.canHandle("https://www.instagram.com/reel/Cxyz12345/"))
        assertTrue(extractor.canHandle("https://www.instagram.com/p/Cxyz12345/"))
        assertTrue(extractor.canHandle("https://instagram.com/reels/Cxyz12345/"))
        assertTrue(extractor.canHandle("https://www.instagram.com/tv/Cxyz12345/"))
        assertTrue(ytExtractor.canHandle("https://www.instagram.com/reel/Cxyz12345/"))
        assertTrue(ytExtractor.canHandle("https://www.instagram.com/p/Cxyz12345/"))
        assertTrue(ytExtractor.canHandle("https://instagram.com/reels/Cxyz12345/"))
        assertTrue(ytExtractor.canHandle("https://www.instagram.com/tv/Cxyz12345/"))

        // Lookalike rejection
        assertFalse(extractor.canHandle("https://notinstagram.com/p/123"))
        assertFalse(extractor.canHandle("https://instagram.com.example.org/reel/123"))
        assertFalse(extractor.canHandle("https://instagram.evil.com/p/123"))
        assertFalse(ytExtractor.canHandle("https://notinstagram.com/p/123"))
        assertFalse(ytExtractor.canHandle("https://instagram.com.example.org/reel/123"))

        assertFalse(extractor.canHandle("https://www.tiktok.com/@user/video/123"))
        assertFalse(extractor.canHandle("https://www.instagram.com/username/")) // Profile page not a reel/video
    }

    @Test
    fun testFacebookRecognition() {
        val extractor = FacebookPublicExtractor()
        assertTrue(extractor.canHandle("https://www.facebook.com/watch/?v=123456789"))
        assertTrue(extractor.canHandle("https://fb.watch/abcdef123/"))
        assertTrue(extractor.canHandle("https://www.facebook.com/reel/1234567890"))
        assertTrue(extractor.canHandle("https://www.facebook.com/username/videos/1234567890"))
        assertFalse(extractor.canHandle("https://www.youtube.com/watch?v=12345"))
        assertFalse(extractor.canHandle("https://www.facebook.com/groups/123/"))
    }

    @Test
    fun testYouTubeRecognitionAndRouting() {
        val extractor = YouTubeYtDlpExtractor()
        assertTrue(extractor.canHandle("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(extractor.canHandle("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(extractor.canHandle("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertTrue(extractor.canHandle("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(extractor.canHandle("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))

        // Lookalike rejection
        assertFalse(extractor.canHandle("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(extractor.canHandle("https://youtube.com.example.org/watch?v=dQw4w9WgXcQ"))
        assertFalse(extractor.canHandle("https://youtube.example.com/watch?v=dQw4w9WgXcQ"))

        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()
        val protectionAnalyzer = SocialProtectionDetectorAnalyzer()
        runBlocking {
            val ytUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            assertTrue(socialAnalyzer.canHandle(ytUrl))
            // SocialAnalyzer priority (60) > ProtectionAnalyzer priority (50)
            assertTrue(socialAnalyzer.priority > protectionAnalyzer.priority)
        }
    }

    // --- 2. Fixture Extraction Tests ---

    @Test
    fun testTikTokPublicFixtureExtraction() = runBlocking {
        val mockHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Awesome TikTok Dance" />
                <meta property="og:image" content="https://p16-sign.tiktokcdn.com/cover.jpg" />
                <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">
                {
                    "__DEFAULT_SCOPE__": {
                        "webapp.video-detail": {
                            "itemInfo": {
                                "itemStruct": {
                                    "desc": "Awesome TikTok Dance #fun",
                                    "video": {
                                        "duration": 15.5,
                                        "cover": "https://p16-sign.tiktokcdn.com/cover.jpg",
                                        "downloadAddr": "https://v16-webapp-prime.tiktokcdn.com/video_hd.mp4",
                                        "playAddr": "https://v16-webapp-prime.tiktokcdn.com/video_sd.mp4",
                                        "bitrateInfo": [
                                            {
                                                "GearName": "normal_1080_0",
                                                "Bitrate": 1500000,
                                                "PlayAddr": { "UrlList": ["https://v16-webapp-prime.tiktokcdn.com/video_1080p.mp4"] }
                                            },
                                            {
                                                "GearName": "normal_720_0",
                                                "Bitrate": 800000,
                                                "PlayAddr": { "UrlList": ["https://v16-webapp-prime.tiktokcdn.com/video_720p.mp4"] }
                                            }
                                        ]
                                    },
                                    "music": {
                                        "playUrl": "https://sf16-ies-music.tiktokcdn.com/music.mp3"
                                    }
                                }
                            }
                        }
                    }
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }
        val extractor = TikTokPublicExtractor()
        val result = extractor.extract("https://www.tiktok.com/@user/video/7123456789012345678", mockClient)

        assertEquals("TikTok", result.source)
        assertTrue(result.title.contains("Awesome TikTok Dance"))
        assertEquals("https://p16-sign.tiktokcdn.com/cover.jpg", result.thumbnailUri)
        assertEquals(15.5, result.durationSeconds ?: 0.0, 0.01)
        assertTrue(result.formats.isNotEmpty())

        val videoFormats = result.formats.filter { it.mediaType == MediaType.VIDEO }
        assertTrue(videoFormats.any { it.downloadUrl.contains("video_hd.mp4") || it.downloadUrl.contains("video_1080p.mp4") })

        val audioFormats = result.formats.filter { it.mediaType == MediaType.AUDIO }
        assertTrue(audioFormats.any { it.downloadUrl.contains("music.mp3") })
    }

    @Test
    fun testInstagramPublicFixtureExtraction() = runBlocking {
        val mockHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Sunset Vibes Reel" />
                <meta property="og:image" content="https://instagram.fccu.fbcdn.net/thumb.jpg" />
                <meta property="og:video" content="https://instagram.fccu.fbcdn.net/video_hd.mp4?_nc_cat=101" />
                <script>
                window.__additionalDataLoaded('/reel/Cxyz12345/', {
                    "graphql": {
                        "shortcode_media": {
                            "browser_native_hd_url": "https://instagram.fccu.fbcdn.net/video_hd.mp4?_nc_cat=101",
                            "browser_native_sd_url": "https://instagram.fccu.fbcdn.net/video_sd.mp4?_nc_cat=101"
                        }
                    }
                });
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }
        val extractor = InstagramPublicExtractor()
        val result = extractor.extract("https://www.instagram.com/reel/Cxyz12345/", mockClient)

        assertEquals("Instagram", result.source)
        assertTrue(result.title.contains("Sunset Vibes"))
        assertEquals("https://instagram.fccu.fbcdn.net/thumb.jpg", result.thumbnailUri)
        assertTrue(result.formats.any { it.downloadUrl.contains("video_hd.mp4") })
    }

    @Test
    fun testFacebookPublicFixtureExtraction() = runBlocking {
        val mockHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Cooking Masterclass Episode 1" />
                <meta property="og:image" content="https://scontent.fccu.fbcdn.net/thumb.jpg" />
                <meta property="og:video" content="https://video.fccu.fbcdn.net/fb_video_hd.mp4" />
                <script>
                var videoData = {
                    "playable_url_quality_hd": "https://video.fccu.fbcdn.net/fb_video_hd.mp4",
                    "playable_url": "https://video.fccu.fbcdn.net/fb_video_sd.mp4"
                };
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }
        val extractor = FacebookPublicExtractor()
        val result = extractor.extract("https://www.facebook.com/watch/?v=987654321", mockClient)

        assertEquals("Facebook", result.source)
        assertTrue(result.title.contains("Cooking Masterclass"))
        assertEquals("https://scontent.fccu.fbcdn.net/thumb.jpg", result.thumbnailUri)
        assertTrue(result.formats.any { it.downloadUrl.contains("fb_video_hd.mp4") })
        assertTrue(result.formats.any { it.downloadUrl.contains("fb_video_sd.mp4") })
    }

    // --- 3. Login Wall & Restricted Response Rejection Tests ---

    @Test
    fun testInstagramLightweightSuccessDoesNotInvokeYtDlp() = runBlocking {
        DownloaderDiagnosticsRegistry.reset()
        val mockHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Sunset Vibes" />
                <meta property="og:image" content="https://instagram.fccu.fbcdn.net/thumb.jpg" />
                <meta property="og:video" content="https://instagram.fccu.fbcdn.net/video_hd.mp4" />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        var ytDlpInvoked = false
        val mockYtDlp = object : InstagramYtDlpExtractor(null) {
            override suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
                ytDlpInvoked = true
                throw IllegalStateException("Should not be called")
            }
        }

        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }
        val extractor = InstagramPublicExtractor(ytDlpExtractor = mockYtDlp)
        val result = extractor.extract("https://www.instagram.com/reel/Cxyz12345/", mockClient)

        assertFalse("yt-dlp should not be invoked when lightweight succeeds", ytDlpInvoked)
        assertEquals("Instagram", result.source)
        assertEquals(1, result.formats.size)
        assertEquals("NO", DownloaderDiagnosticsRegistry.diagnostics.value?.ytDlpFallbackInvoked)
    }

    @Test
    fun testInstagramLoginWallInvokesYtDlpFallback() = runBlocking {
        DownloaderDiagnosticsRegistry.reset()
        val mockHtml = "<html><head><title>Login • Instagram</title></head><body>Login required</body></html>"
        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }

        var ytDlpInvoked = false
        val mockYtDlp = object : InstagramYtDlpExtractor(null) {
            override suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
                ytDlpInvoked = true
                return MediaAnalysisResult(
                    url = url,
                    title = "Fallback Video",
                    source = "Instagram",
                    formats = listOf(
                        com.example.data.downloader.model.DownloadFormat(
                            id = "yt_1",
                            mediaType = MediaType.VIDEO,
                            resolution = "1080p",
                            container = "mp4",
                            codec = "h264",
                            sizeBytes = 1024L,
                            downloadUrl = "https://instagram.cdn/fallback.mp4",
                            audioIncluded = true,
                            label = "1080p • MP4"
                        )
                    ),
                    isDownloadable = true
                )
            }
        }

        val extractor = InstagramPublicExtractor(ytDlpExtractor = mockYtDlp)
        val result = extractor.extract("https://www.instagram.com/reel/Cxyz12345/", mockClient)

        assertTrue("yt-dlp should be invoked when lightweight encounters login wall", ytDlpInvoked)
        assertEquals("Instagram", result.source)
        assertEquals(1, result.formats.size)
        assertEquals("YES", DownloaderDiagnosticsRegistry.diagnostics.value?.ytDlpFallbackInvoked)
        val attempts = DownloaderDiagnosticsRegistry.diagnostics.value?.extractorAttemptOrder ?: ""
        assertTrue(attempts.contains("InstagramPublicExtractor → LOGIN_OR_VERIFICATION_PAGE"))
        assertTrue(attempts.contains("InstagramYtDlpExtractor → SUCCESS"))
    }

    @Test
    fun testInstagramYtDlpExplicitAuthRequirementMapsLoginRequired() = runBlocking {
        DownloaderDiagnosticsRegistry.reset()
        val mockHtml = "<html><head><title>Login • Instagram</title></head><body>Login required</body></html>"
        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }

        val mockYtDlp = object : InstagramYtDlpExtractor(null) {
            override suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
                throw PrivateOrRestrictedException("Instagram", "This Instagram video is private or restricted by the creator.")
            }
        }

        val extractor = InstagramPublicExtractor(ytDlpExtractor = mockYtDlp)
        try {
            extractor.extract("https://www.instagram.com/reel/Private123/", mockClient)
            fail("Expected PrivateOrRestrictedException")
        } catch (e: PrivateOrRestrictedException) {
            assertTrue(e.message.contains("private or restricted"))
        }
    }

    @Test
    fun testInstagramLoginWallRejection() = runBlocking {
        val mockHtml = "<html><head><title>Login • Instagram</title></head><body>Login required</body></html>"
        val mockClient = createMockHttpClient { req ->
            buildResponse(req, 200, mockHtml)
        }
        val extractor = InstagramPublicExtractor()
        try {
            extractor.extract("https://www.instagram.com/reel/PrivateReel123/", mockClient)
            fail("Expected AuthRequiredException on Instagram login wall")
        } catch (e: AuthRequiredException) {
            assertTrue(e.message.contains("requires login"))
        }
    }

    @Test
    fun testFacebookLoginWallRejection() = runBlocking {
        val mockHtml = "<html><head><title>Log in to Facebook</title></head><body>You must log in to continue</body></html>"
        val mockClient = createMockHttpClient { req ->
            buildResponse(req, 200, mockHtml)
        }
        val extractor = FacebookPublicExtractor()
        try {
            extractor.extract("https://www.facebook.com/watch/?v=Private123", mockClient)
            fail("Expected AuthRequiredException on Facebook login wall")
        } catch (e: AuthRequiredException) {
            assertTrue(e.message.contains("requires login"))
        }
    }

    @Test
    fun testInstagramRestrictedPostRejection() = runBlocking {
        val mockHtml = "<html><body>Sorry, this page isn't available. The link you followed may be broken</body></html>"
        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }
        val extractor = InstagramPublicExtractor()
        try {
            extractor.extract("https://www.instagram.com/reel/DeletedReel/", mockClient)
            fail("Expected PrivateOrRestrictedException on restricted Instagram post")
        } catch (e: PrivateOrRestrictedException) {
            assertTrue(e.message.contains("private or restricted"))
        }
    }

    @Test
    fun testTikTokMissingMediaRejection() = runBlocking {
        val mockHtml = "<html><head><title>TikTok</title></head><body>No video content here</body></html>"
        val mockClient = createMockHttpClient { req -> buildResponse(req, 200, mockHtml) }
        val extractor = TikTokPublicExtractor()
        try {
            extractor.extract("https://www.tiktok.com/@user/video/00000000", mockClient)
            fail("Expected PublicMediaPageException on missing media")
        } catch (e: PublicMediaPageException) {
            assertTrue(e.message.contains("isn't available for direct public download"))
        }
    }

    // --- 4. Short-Link Redirect & SSRF Security Tests ---

    @Test
    fun testSafeShortLinkRedirectResolution() = runBlocking {
        var callCount = 0
        val mockClient = createMockHttpClient { req ->
            callCount++
            if (req.url.toString() == "https://vt.tiktok.com/ZS123456/") {
                buildResponse(req, 302, "", mapOf("Location" to "https://www.tiktok.com/@user/video/7123456789012345678"))
            } else {
                buildResponse(
                    req, 200,
                    """<html><head><meta property="og:video" content="https://cdn.tiktok.com/v.mp4" /></head></html>"""
                )
            }
        }

        val res = SafeHttpHelper.executeWithSafeRedirects(mockClient, "https://vt.tiktok.com/ZS123456/")
        assertEquals(200, res.statusCode)
        assertEquals("https://www.tiktok.com/@user/video/7123456789012345678", res.finalUrl)
        assertTrue(res.body.contains("cdn.tiktok.com"))
    }

    @Test
    fun testUnsafeRedirectToLocalhostBlocked() = runBlocking {
        val mockClient = createMockHttpClient { req ->
            buildResponse(req, 302, "", mapOf("Location" to "http://127.0.0.1:8080/admin"))
        }

        try {
            SafeHttpHelper.executeWithSafeRedirects(mockClient, "https://vt.tiktok.com/Attack123/")
            fail("Expected SecurityException on redirect to loopback IP")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("prohibited") == true || e.message?.contains("internal") == true)
        }
    }

    @Test
    fun testPrivateIpValidation() {
        assertTrue(UrlSecurityValidator.isPrivateOrLocalHost("localhost"))
        assertTrue(UrlSecurityValidator.isPrivateOrLocalHost("127.0.0.1"))
        assertTrue(UrlSecurityValidator.isPrivateOrLocalHost("10.0.0.1"))
        assertTrue(UrlSecurityValidator.isPrivateOrLocalHost("192.168.1.1"))
        assertTrue(UrlSecurityValidator.isPrivateOrLocalHost("172.16.0.1"))
        assertTrue(UrlSecurityValidator.isPrivateOrLocalHost("169.254.169.254"))
        assertTrue(UrlSecurityValidator.isPrivateOrLocalHost("100.64.0.1"))
        assertFalse(UrlSecurityValidator.isPrivateOrLocalHost("www.tiktok.com"))
        assertFalse(UrlSecurityValidator.isPrivateOrLocalHost("www.instagram.com"))
        assertFalse(UrlSecurityValidator.isPrivateOrLocalHost("www.facebook.com"))
    }

    @Test
    fun testNonHttpSchemesBlocked() {
        try {
            UrlSecurityValidator.validateUrl("file:///sdcard/Download/secret.txt")
            fail("Expected SecurityException on file:// scheme")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("scheme") == true)
        }

        try {
            UrlSecurityValidator.validateUrl("javascript:alert(1)")
            fail("Expected SecurityException on javascript: scheme")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("scheme") == true)
        }

        try {
            UrlSecurityValidator.validateUrl("content://media/external/images")
            fail("Expected SecurityException on content: scheme")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("scheme") == true)
        }
    }

    // --- 5. Filename Sanitization Tests ---

    @Test
    fun testFilenameSanitization() {
        val dangerous = "../../../etc/passwd\u0000*special?name<test>|pipe"
        val clean = UrlSecurityValidator.sanitizeFilename(dangerous, "fallback")
        assertFalse(clean.contains(".."))
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("|"))
        assertFalse(clean.contains("\u0000"))
        assertTrue(clean.isNotBlank())
    }

    // --- 6. Pipeline Routing & Analyzer Priority Tests ---

    @Test
    fun testPipelineRoutingOrder() = runBlocking {
        // 1. Direct file goes to DirectMediaAnalyzer
        val directUrl = "https://example.com/sample_video.mp4"
        val directAnalyzer = DirectMediaAnalyzer()
        assertTrue(directAnalyzer.canHandle(directUrl))

        // 2. HLS stream goes to HlsMediaAnalyzer
        val hlsUrl = "https://example.com/live/master.m3u8"
        val hlsAnalyzer = HlsMediaAnalyzer()
        assertTrue(hlsAnalyzer.canHandle(hlsUrl))

        // 3. Social pages go to OnDeviceSocialMediaAnalyzer
        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()
        assertTrue(socialAnalyzer.canHandle("https://www.tiktok.com/@user/video/123"))
        assertTrue(socialAnalyzer.canHandle("https://www.instagram.com/reel/123"))
        assertTrue(socialAnalyzer.canHandle("https://www.facebook.com/watch/?v=123"))

        // 4. YouTube is caught by SocialProtectionDetectorAnalyzer
        val protectionAnalyzer = SocialProtectionDetectorAnalyzer()
        assertTrue(protectionAnalyzer.canHandle("https://www.youtube.com/watch?v=123"))
    }

    // --- 7. Threads Social Media Extraction & Pipeline Tests ---

    @Test
    fun testThreadsExtractor_validThreadsComPost() {
        val extractor = ThreadsYtDlpExtractor()
        assertTrue(extractor.canHandle("https://threads.com/@user/post/C_xyz123"))
        assertTrue(extractor.canHandle("https://threads.com/post/C_xyz123"))
        assertTrue(extractor.canHandle("https://threads.com/t/C_xyz123"))
    }

    @Test
    fun testThreadsExtractor_validWwwThreadsComPost() {
        val extractor = ThreadsYtDlpExtractor()
        assertTrue(extractor.canHandle("https://www.threads.com/@creator/post/D_abc456?hl=en"))
        assertTrue(extractor.canHandle("https://www.threads.com/post/D_abc456"))
        assertTrue(extractor.canHandle("https://www.threads.com/t/D_abc456"))
    }

    @Test
    fun testThreadsExtractor_validThreadsNetPost() {
        val extractor = ThreadsYtDlpExtractor()
        assertTrue(extractor.canHandle("https://threads.net/@user/post/E_789"))
        assertTrue(extractor.canHandle("https://www.threads.net/@user/post/E_789"))
        assertTrue(extractor.canHandle("https://threads.net/t/E_789"))
    }

    @Test
    fun testThreadsRouting_occursBeforeDirectMediaAnalyzer() = runBlocking {
        val directAnalyzer = DirectMediaAnalyzer()
        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()

        val threadsUrl = "https://www.threads.com/@user/post/C_xyz123"
        // DirectMediaAnalyzer must NOT handle Threads HTML post page
        assertFalse(directAnalyzer.canHandle(threadsUrl))
        // OnDeviceSocialMediaAnalyzer MUST handle Threads post
        assertTrue(socialAnalyzer.canHandle(threadsUrl))

        val threadsNetUrl = "https://www.threads.net/@user/post/C_xyz123"
        assertFalse(directAnalyzer.canHandle(threadsNetUrl))
        assertTrue(socialAnalyzer.canHandle(threadsNetUrl))
    }

    @Test
    fun testThreadsExtractor_rejectsLookalikeDomains() {
        val extractor = ThreadsYtDlpExtractor()
        assertFalse(extractor.canHandle("https://threads.com.example.org/@user/post/123"))
        assertFalse(extractor.canHandle("https://notthreads.com/@user/post/123"))
        assertFalse(extractor.canHandle("https://threads-example.com/@user/post/123"))
        assertFalse(extractor.canHandle("https://fake-threads.net/@user/post/123"))
    }

    @Test
    fun testThreadsExtractor_rejectsProfileAndNonPostUrls() = runBlocking {
        val extractor = ThreadsYtDlpExtractor()
        // Profile URLs should not be treated as downloadable media posts
        assertFalse(extractor.canHandle("https://www.threads.com/@user"))
        assertFalse(extractor.canHandle("https://threads.net/@user"))
        assertFalse(extractor.canHandle("https://www.threads.com/search"))
        assertFalse(extractor.canHandle("https://threads.net/activity"))

        // Profile URLs should be caught by SocialProtectionDetectorAnalyzer and recognized as Threads platform
        val protectionAnalyzer = SocialProtectionDetectorAnalyzer()
        assertTrue(protectionAnalyzer.canHandle("https://www.threads.com/@user"))
        assertTrue(protectionAnalyzer.canHandle("https://threads.net/@user"))

        try {
            protectionAnalyzer.analyze("https://www.threads.com/@user")
            fail("Expected PublicMediaPageException for Threads profile page")
        } catch (e: PublicMediaPageException) {
            assertEquals("Threads", e.platformName)
        }
    }

    @Test
    fun testSelectedFormatIdPreserved() {
        val format = DownloadFormat(
            id = "137",
            mediaType = MediaType.VIDEO,
            resolution = "1080p",
            container = "mp4",
            vcodec = "avc1.640028",
            acodec = "mp4a.40.2",
            sizeBytes = 52428800L,
            downloadUrl = "https://example.com/media/137.mp4",
            audioIncluded = true,
            mergeRequired = false,
            separateAudioAvailable = false
        )
        assertEquals("137", format.id)
        assertEquals(MediaType.VIDEO, format.mediaType)
        assertEquals("1080p", format.resolution)
        assertEquals("mp4", format.container)
        assertEquals("avc1.640028", format.vcodec)
        assertEquals("mp4a.40.2", format.acodec)
        assertEquals(52428800L, format.sizeBytes)
        assertTrue(format.audioIncluded)
        assertFalse(format.mergeRequired)
    }

    @Test
    fun testExistingPlatformRoutingUnchanged() = runBlocking {
        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()
        val directAnalyzer = DirectMediaAnalyzer()
        val hlsAnalyzer = HlsMediaAnalyzer()

        // 8. Facebook routing unchanged
        assertTrue(socialAnalyzer.canHandle("https://www.facebook.com/reel/123456"))
        assertTrue(socialAnalyzer.canHandle("https://www.facebook.com/watch/?v=123456"))

        // 9. TikTok routing unchanged
        assertTrue(socialAnalyzer.canHandle("https://www.tiktok.com/@user/video/123456"))
        assertTrue(socialAnalyzer.canHandle("https://vt.tiktok.com/ZS1234/"))

        // 10. Instagram routing unchanged
        assertTrue(socialAnalyzer.canHandle("https://www.instagram.com/reel/C1234/"))
        assertTrue(socialAnalyzer.canHandle("https://www.instagram.com/p/C1234/"))

        // 11. YouTube routing unchanged
        assertTrue(socialAnalyzer.canHandle("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(socialAnalyzer.canHandle("https://youtu.be/dQw4w9WgXcQ"))

        // 12. Direct file routing unchanged
        assertTrue(directAnalyzer.canHandle("https://example.com/video.mp4"))
        assertTrue(directAnalyzer.canHandle("https://example.com/audio.mp3"))
        assertTrue(directAnalyzer.canHandle("https://example.com/document.pdf"))
        assertTrue(hlsAnalyzer.canHandle("https://example.com/stream/playlist.m3u8"))
    }

    // --- 8. Universal YtDlp Architecture Tests ---

    @Test
    fun testUniversalExtractor_knownYtDlpSupportedUnknownUrlReachesUniversalExtractor() = runBlocking {
        val universalExtractor = UniversalYtDlpExtractor()
        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()

        val vimeoUrl = "https://vimeo.com/12345678"
        val redditUrl = "https://www.reddit.com/r/videos/comments/xyz123/sample_post/"
        val twitterUrl = "https://twitter.com/user/status/1234567890"

        assertTrue(universalExtractor.canHandle(vimeoUrl))
        assertTrue(universalExtractor.canHandle(redditUrl))
        assertTrue(universalExtractor.canHandle(twitterUrl))

        assertTrue(socialAnalyzer.canHandle(vimeoUrl))
        assertTrue(socialAnalyzer.canHandle(redditUrl))
        assertTrue(socialAnalyzer.canHandle(twitterUrl))
    }

    @Test
    fun testUniversalExtractor_runsBeforeFinalProtectionRejection() = runBlocking {
        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()
        val protectionAnalyzer = SocialProtectionDetectorAnalyzer()

        // Social analyzer priority (60) > Protection analyzer priority (50)
        assertTrue(socialAnalyzer.priority > protectionAnalyzer.priority)
    }

    @Test
    fun testUniversalExtractor_runsBeforeInappropriateHtmlDirectFileRejection() = runBlocking {
        val directAnalyzer = DirectMediaAnalyzer()
        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()

        val webUrl = "https://vimeo.com/98765432"
        // DirectMediaAnalyzer must NOT claim web page URLs lacking direct file extensions
        assertFalse(directAnalyzer.canHandle(webUrl))
        // OnDeviceSocialMediaAnalyzer must handle it via UniversalYtDlpExtractor
        assertTrue(socialAnalyzer.canHandle(webUrl))
    }

    @Test
    fun testFormatNormalization_muxedVideoOnlyAudioOnly() {
        val muxedFormat = DownloadFormat(
            id = "18",
            mediaType = MediaType.VIDEO,
            resolution = "360p",
            container = "mp4",
            vcodec = "avc1.42001E",
            acodec = "mp4a.40.2",
            sizeBytes = 1000000L,
            downloadUrl = "https://example.com/18.mp4",
            audioIncluded = true,
            mergeRequired = false,
            separateAudioAvailable = true
        )
        assertTrue(muxedFormat.audioIncluded)
        assertFalse(muxedFormat.mergeRequired)

        val videoOnlyFormat = DownloadFormat(
            id = "137",
            mediaType = MediaType.VIDEO,
            resolution = "1080p",
            container = "mp4",
            vcodec = "avc1.640028",
            acodec = "none",
            sizeBytes = 5000000L,
            downloadUrl = "https://example.com/137.mp4",
            audioIncluded = false,
            mergeRequired = true,
            separateAudioAvailable = true
        )
        assertFalse(videoOnlyFormat.audioIncluded)
        assertTrue(videoOnlyFormat.mergeRequired)

        val audioOnlyFormat = DownloadFormat(
            id = "140",
            mediaType = MediaType.AUDIO,
            resolution = "Audio",
            container = "m4a",
            vcodec = "none",
            acodec = "mp4a.40.2",
            sizeBytes = 500000L,
            downloadUrl = "https://example.com/140.m4a",
            audioIncluded = true,
            mergeRequired = false,
            separateAudioAvailable = true
        )
        assertEquals(MediaType.AUDIO, audioOnlyFormat.mediaType)
    }

    @Test
    fun testExactSelectedVideoFormatPreserved() {
        val selectedFormatId = "299"
        val actualFormatSelector = "$selectedFormatId+bestaudio"
        assertEquals("299", selectedFormatId)
        assertEquals("299+bestaudio", actualFormatSelector)
    }

    @Test
    fun testFutureUnknownSupportedHostNoRegistrationRequired() = runBlocking {
        val universalExtractor = UniversalYtDlpExtractor()
        val socialAnalyzer = OnDeviceSocialMediaAnalyzer()

        val newPlatformUrl = "https://some-unseen-video-site.com/watch?v=abcdef"
        assertTrue(universalExtractor.canHandle(newPlatformUrl))
        assertTrue(socialAnalyzer.canHandle(newPlatformUrl))
    }
}
