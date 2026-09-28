#!/bin/bash
sed -i '/data object Video : Screen("video")/a \    data object MediaGallery : Screen("media_gallery")' app/src/main/java/com/example/ui/navigation/NavRoutes.kt

sed -i '/import com.example.ui.screens.media.VideoPlayerScreen/a import com.example.ui.screens.media.MediaScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/composable(Screen.PdfReader.route) {/,+5 a\
            composable(Screen.MediaGallery.route) {\n\
                MediaScreen(\n\
                    repository = container.mediaRepository,\n\
                    onNavigateToVideo = { url -> \n\
                        // For simplicity in this non-arg route, we will just navigate to the default video screen or you can add args to NavRoutes\n\
                        navController.navigate(Screen.Video.route)\n\
                    },\n\
                    onBack = { navController.popBackStack() }\n\
                )\n\
            }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
            
sed -i '/onNavigateToVideo = { navController.navigate(Screen.Video.route) }/a \
                    onNavigateToMediaGallery = { navController.navigate(Screen.MediaGallery.route) }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/onNavigateToVideo: () -> Unit = {}/a \
    onNavigateToMediaGallery: () -> Unit = {}' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
    
# Replace the Video button with Media Gallery
sed -i 's/Button(onClick = onNavigateToVideo, modifier = Modifier.weight(1f)) { Text("Video Player") }/Button(onClick = onNavigateToMediaGallery, modifier = Modifier.weight(1f)) { Text("Media Gallery") }/g' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt

