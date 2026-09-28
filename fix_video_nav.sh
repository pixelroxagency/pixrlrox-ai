#!/bin/bash
sed -i '/data object QrScanner : Screen("qr_scanner")/a \    data object Video : Screen("video")' app/src/main/java/com/example/ui/navigation/NavRoutes.kt

sed -i '/import com.example.ui.screens.qr.QrScannerScreen/a import com.example.ui.screens.media.VideoPlayerScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/composable(Screen.QrScanner.route) {/,+5 a\
            composable(Screen.Video.route) {\n\
                VideoPlayerScreen(\n\
                    videoUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",\n\
                    onBack = { navController.popBackStack() }\n\
                )\n\
            }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
