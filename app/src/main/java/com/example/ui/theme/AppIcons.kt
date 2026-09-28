package com.example.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.repository.IconPack

object AppIcons {
    fun home(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Home
        IconPack.OUTLINED -> Icons.Outlined.Home
        IconPack.ROUNDED -> Icons.Rounded.Home
        IconPack.SHARP -> Icons.Sharp.Home
        IconPack.DUOTONE -> Icons.Outlined.Home
        IconPack.GLASS_GLOW -> Icons.Rounded.Home
    }

    fun settings(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Settings
        IconPack.OUTLINED -> Icons.Outlined.Settings
        IconPack.ROUNDED -> Icons.Rounded.Settings
        IconPack.SHARP -> Icons.Sharp.Settings
        IconPack.DUOTONE -> Icons.Outlined.Settings
        IconPack.GLASS_GLOW -> Icons.Rounded.Settings
    }

    fun search(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Search
        IconPack.OUTLINED -> Icons.Outlined.Search
        IconPack.ROUNDED -> Icons.Rounded.Search
        IconPack.SHARP -> Icons.Sharp.Search
        IconPack.DUOTONE -> Icons.Outlined.Search
        IconPack.GLASS_GLOW -> Icons.Rounded.Search
    }

    fun back(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.ArrowBack
        IconPack.OUTLINED -> Icons.Outlined.ArrowBack
        IconPack.ROUNDED -> Icons.Rounded.ArrowBack
        IconPack.SHARP -> Icons.Sharp.ArrowBack
        IconPack.DUOTONE -> Icons.Outlined.ArrowBack
        IconPack.GLASS_GLOW -> Icons.Rounded.ArrowBack
    }

    fun tools(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Build
        IconPack.OUTLINED -> Icons.Outlined.Build
        IconPack.ROUNDED -> Icons.Rounded.Build
        IconPack.SHARP -> Icons.Sharp.Build
        IconPack.DUOTONE -> Icons.Outlined.Build
        IconPack.GLASS_GLOW -> Icons.Rounded.Build
    }

    fun play(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.PlayArrow
        IconPack.OUTLINED -> Icons.Outlined.PlayArrow
        IconPack.ROUNDED -> Icons.Rounded.PlayArrow
        IconPack.SHARP -> Icons.Sharp.PlayArrow
        IconPack.DUOTONE -> Icons.Outlined.PlayArrow
        IconPack.GLASS_GLOW -> Icons.Rounded.PlayArrow
    }

    fun pause(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Pause
        IconPack.OUTLINED -> Icons.Outlined.Pause
        IconPack.ROUNDED -> Icons.Rounded.Pause
        IconPack.SHARP -> Icons.Sharp.Pause
        IconPack.DUOTONE -> Icons.Outlined.Pause
        IconPack.GLASS_GLOW -> Icons.Rounded.Pause
    }

    fun video(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.VideoLibrary
        IconPack.OUTLINED -> Icons.Outlined.VideoLibrary
        IconPack.ROUNDED -> Icons.Rounded.VideoLibrary
        IconPack.SHARP -> Icons.Sharp.VideoLibrary
        IconPack.DUOTONE -> Icons.Outlined.VideoLibrary
        IconPack.GLASS_GLOW -> Icons.Rounded.VideoLibrary
    }

    fun audio(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.MusicNote
        IconPack.OUTLINED -> Icons.Outlined.MusicNote
        IconPack.ROUNDED -> Icons.Rounded.MusicNote
        IconPack.SHARP -> Icons.Sharp.MusicNote
        IconPack.DUOTONE -> Icons.Outlined.MusicNote
        IconPack.GLASS_GLOW -> Icons.Rounded.MusicNote
    }

    fun pdf(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.PictureAsPdf
        IconPack.OUTLINED -> Icons.Outlined.PictureAsPdf
        IconPack.ROUNDED -> Icons.Rounded.PictureAsPdf
        IconPack.SHARP -> Icons.Sharp.PictureAsPdf
        IconPack.DUOTONE -> Icons.Outlined.PictureAsPdf
        IconPack.GLASS_GLOW -> Icons.Rounded.PictureAsPdf
    }

    fun palette(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Palette
        IconPack.OUTLINED -> Icons.Outlined.Palette
        IconPack.ROUNDED -> Icons.Rounded.Palette
        IconPack.SHARP -> Icons.Sharp.Palette
        IconPack.DUOTONE -> Icons.Outlined.Palette
        IconPack.GLASS_GLOW -> Icons.Rounded.Palette
    }

    fun reset(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Refresh
        IconPack.OUTLINED -> Icons.Outlined.Refresh
        IconPack.ROUNDED -> Icons.Rounded.Refresh
        IconPack.SHARP -> Icons.Sharp.Refresh
        IconPack.DUOTONE -> Icons.Outlined.Refresh
        IconPack.GLASS_GLOW -> Icons.Rounded.Refresh
    }

    fun download(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Download
        IconPack.OUTLINED -> Icons.Outlined.Download
        IconPack.ROUNDED -> Icons.Rounded.Download
        IconPack.SHARP -> Icons.Sharp.Download
        IconPack.DUOTONE -> Icons.Outlined.Download
        IconPack.GLASS_GLOW -> Icons.Rounded.Download
    }

    fun edit(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Edit
        IconPack.OUTLINED -> Icons.Outlined.Edit
        IconPack.ROUNDED -> Icons.Rounded.Edit
        IconPack.SHARP -> Icons.Sharp.Edit
        IconPack.DUOTONE -> Icons.Outlined.Edit
        IconPack.GLASS_GLOW -> Icons.Rounded.Edit
    }

    fun delete(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Delete
        IconPack.OUTLINED -> Icons.Outlined.Delete
        IconPack.ROUNDED -> Icons.Rounded.Delete
        IconPack.SHARP -> Icons.Sharp.Delete
        IconPack.DUOTONE -> Icons.Outlined.Delete
        IconPack.GLASS_GLOW -> Icons.Rounded.Delete
    }

    fun share(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.Share
        IconPack.OUTLINED -> Icons.Outlined.Share
        IconPack.ROUNDED -> Icons.Rounded.Share
        IconPack.SHARP -> Icons.Sharp.Share
        IconPack.DUOTONE -> Icons.Outlined.Share
        IconPack.GLASS_GLOW -> Icons.Rounded.Share
    }

    fun camera(pack: IconPack): ImageVector = when (pack) {
        IconPack.FILLED -> Icons.Filled.PhotoCamera
        IconPack.OUTLINED -> Icons.Outlined.PhotoCamera
        IconPack.ROUNDED -> Icons.Rounded.PhotoCamera
        IconPack.SHARP -> Icons.Sharp.PhotoCamera
        IconPack.DUOTONE -> Icons.Outlined.PhotoCamera
        IconPack.GLASS_GLOW -> Icons.Rounded.PhotoCamera
    }

    fun appearance(pack: IconPack): ImageVector = palette(pack)
}
