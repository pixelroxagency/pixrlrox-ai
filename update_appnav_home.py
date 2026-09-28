with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "r") as f:
    content = f.read()

content = content.replace(
    "                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }",
    "                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },\n                    onNavigateToMediaGallery = { navController.navigate(Screen.MediaGallery.route) },\n                    onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) }"
)

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "w") as f:
    f.write(content)
print("Updated AppNavHost.kt successfully")
