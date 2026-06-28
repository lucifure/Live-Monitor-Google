package com.yausername.ffmpeg

import android.content.Context

object FFmpeg {
    private var isInitialized = false

    fun init(context: Context) {
        isInitialized = true
    }
}
