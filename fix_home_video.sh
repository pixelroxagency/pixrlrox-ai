#!/bin/bash
sed -i '/onNavigateToQrScanner: () -> Unit = {}/a \
    onNavigateToVideo: () -> Unit = {}' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
    
sed -i '/onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.route) }/a \
                    onNavigateToVideo = { navController.navigate(Screen.Video.route) }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/Button(onClick = onNavigateToQrScanner, modifier = Modifier.weight(1f)) { Text("QR Scanner") }/a \
                    Button(onClick = onNavigateToVideo, modifier = Modifier.weight(1f)) { Text("Video Player") }' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
