#!/bin/bash
cat << 'EOF_ROUTES' >> app/src/main/java/com/example/ui/navigation/NavRoutes.kt
    data object MediaGallery : Screen("media_gallery")
    data object VideoPlayer : Screen("video_player")
    data object Playlists : Screen("playlists")
EOF_ROUTES
