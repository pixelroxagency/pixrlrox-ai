package com.example.core.image

enum class ImageCompressionPreset(
    val title: String,
    val description: String,
    val defaultQuality: Int
) {
    SMALL_FILE("Small File", "Stronger compression for web & messaging", 40),
    BALANCED("Balanced", "Recommended balance of quality and file size", 70),
    HIGH_QUALITY("High Quality", "Lighter compression preserving fine detail", 88),
    CUSTOM("Custom", "User defined compression level", 80)
}

enum class OutputFormat(
    val title: String,
    val extension: String,
    val mimeType: String,
    val supportsQuality: Boolean,
    val supportsTransparency: Boolean
) {
    JPEG("JPEG", ".jpg", "image/jpeg", supportsQuality = true, supportsTransparency = false),
    WEBP("WebP", ".webp", "image/webp", supportsQuality = true, supportsTransparency = true),
    PNG("PNG", ".png", "image/png", supportsQuality = false, supportsTransparency = true)
}

enum class ResizeScale(
    val title: String,
    val scaleFactor: Float
) {
    ORIGINAL("Original (100%)", 1.0f),
    SEVENTY_FIVE("75% Dimensions", 0.75f),
    FIFTY("50% Half Size", 0.5f),
    TWENTY_FIVE("25% Compact", 0.25f),
    CUSTOM("Custom Max Dimension", 1.0f)
}
