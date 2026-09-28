#!/bin/bash
sed -i '/import com.example.ui.screens.media.VideoPlayerScreen/a import com.example.ui.screens.calendar.CalendarScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/composable(Screen.Video.route) {/,+6 a\
            composable(Screen.Calendar.route) {\n\
                CalendarScreen(\n\
                    repository = container.eventRepository,\n\
                    onBack = { navController.popBackStack() }\n\
                )\n\
            }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
            
sed -i '/onNavigateToVideo: () -> Unit = {}/a \
    onNavigateToCalendar: () -> Unit = {}' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
    
sed -i '/onNavigateToVideo = { navController.navigate(Screen.Video.route) }/a \
                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/Button(onClick = onNavigateToVideo, modifier = Modifier.weight(1f)) { Text("Video Player") }/a \
                    Button(onClick = onNavigateToCalendar, modifier = Modifier.weight(1f)) { Text("Calendar") }' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
