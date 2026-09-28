package com.example.data.downloader.extractor

import android.content.Context
import com.example.data.downloader.analyzer.ExtractionFailedException
import com.example.data.downloader.analyzer.ExtractionTimeoutException
import com.example.data.downloader.analyzer.NetworkErrorException
import com.example.data.downloader.analyzer.PrivateOrRestrictedException
import com.example.data.downloader.analyzer.PublicMediaPageException
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class TikTokYtDlpExtractor(
    context: Context? = null
) : BaseYtDlpExtractor(context) {

    override val platformName: String = "TikTok"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com") ||
                host == "vt.tiktok.com" || host == "vm.tiktok.com"
    }
}

