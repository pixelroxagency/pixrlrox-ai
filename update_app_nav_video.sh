#!/bin/bash
sed -i '/import com.example.ui.screens.qr.QrScannerScreen/a import com.example.ui.screens.media.VideoPlayerScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

# Inject the route mapping in the NavHost block
# Since we need to pass a videoUrl we can just pass a dummy one for now or hardcode for demo purposes, or modify NavRoutes to accept arguments.
# For simplicity in this demo, let's just show a test video or allow passing a string.
