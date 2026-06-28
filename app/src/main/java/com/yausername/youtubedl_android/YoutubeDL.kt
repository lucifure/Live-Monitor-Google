package com.yausername.youtubedl_android

import android.content.Context

class YoutubeDLException(message: String) : Exception(message)

interface DownloadProgressCallback {
    fun onProgressUpdate(progress: Float, etaInSeconds: Long, line: String)
}

class YoutubeDLRequest(val url: String) {
    private val options = mutableMapOf<String, String>()
    private val arguments = mutableListOf<String>()

    fun addOption(key: String, value: String): YoutubeDLRequest {
        options[key] = value
        return this
    }

    fun addOption(key: String): YoutubeDLRequest {
        options[key] = ""
        return this
    }

    fun addOption(key: String, value: Int): YoutubeDLRequest {
        options[key] = value.toString()
        return this
    }

    fun addOption(key: String, value: Long): YoutubeDLRequest {
        options[key] = value.toString()
        return this
    }

    fun addArg(arg: String): YoutubeDLRequest {
        arguments.add(arg)
        return this
    }

    fun getOptions(): Map<String, String> = options
    fun getArguments(): List<String> = arguments
}

class YoutubeDLResponse(
    val command: String,
    val out: String,
    val err: String,
    val exitCode: Int,
    val elapsedTime: Long
)

object YoutubeDL {
    private var isInitialized = false

    fun init(context: Context) {
        isInitialized = true
    }

    fun execute(
        request: YoutubeDLRequest,
        callback: DownloadProgressCallback? = null
    ): YoutubeDLResponse {
        return YoutubeDLResponse(
            command = "yt-dlp ${request.url}",
            out = "[yt-dlp] Simulation output in AI Studio sandbox.",
            err = "",
            exitCode = 0,
            elapsedTime = 100
        )
    }

    fun execute(
        request: YoutubeDLRequest,
        commandId: String?,
        callback: DownloadProgressCallback? = null
    ): YoutubeDLResponse {
        return execute(request, callback)
    }
}
