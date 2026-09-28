with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "r") as f:
    content = f.read()

content = content.replace(
    "                    onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) }",
    "                    onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) },\n                    onNavigateToFileManager = { navController.navigate(Screen.FileManager.route) },\n                    onNavigateToNotes = { navController.navigate(Screen.Notes.route) },\n                    onNavigateToChecklists = { navController.navigate(Screen.Checklists.route) },\n                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },\n                    onNavigateToExpenses = { navController.navigate(Screen.Expenses.route) }"
)

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "w") as f:
    f.write(content)
print("Updated AppNavHost.kt calls successfully")
