package com.example.core.recorder

enum class RecordingMode(val title: String, val description: String) {
    SCREEN_ONLY("Screen Only", "Record screen video without audio"),
    SCREEN_AUDIO("Screen + Device Audio", "Record screen with internal system/device playback audio"),
    SCREEN_AUDIO_MIC("Screen + Device Audio + Microphone", "Record screen, internal audio and microphone simultaneously")
}

enum class VideoQuality(val title: String, val width: Int, val height: Int) {
    HD_720P("720p HD", 1280, 720),
    FULL_HD_1080P("1080p Full HD", 1920, 1080),
    NATIVE("Device Native", 0, 0)
}

enum class FrameRate(val title: String, val fps: Int) {
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60)
}

enum class RecordingOrientation(val title: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}
