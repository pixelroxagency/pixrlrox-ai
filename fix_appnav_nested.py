with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "r") as f:
    content = f.read()

# Extract the new composables block
new_screens = """
            composable(Screen.FileManager.route) {
                FileManagerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Notes.route) {
                NotesScreen(
                    repository = container.noteRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Checklists.route) {
                ChecklistsScreen(
                    repository = container.checklistRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    repository = container.eventRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Expenses.route) {
                ExpenseScreen(
                    repository = container.expenseRepository,
                    onBack = { navController.popBackStack() }
                )
            }
"""

# Remove them from the bottom
content = content.replace(new_screens, "")

# Insert them right before the closing of NavHost (before line 265)
content = content.replace(
    """            composable(Screen.Playlists.route) {
                val app = LocalContext.current.applicationContext as Application
                val mediaVm: MediaPlayerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MediaPlayerViewModel(app, container.mediaRepository) as T
                        }
                    }
                )
                PlaylistScreen(
                    viewModel = mediaVm,
                    onSelectPlaylist = { playlistId ->
                        mediaVm.loadPlaylistItems(playlistId)
                    },
                    onBack = { navController.popBackStack() }
                )
            }""",
    """            composable(Screen.Playlists.route) {
                val app = LocalContext.current.applicationContext as Application
                val mediaVm: MediaPlayerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MediaPlayerViewModel(app, container.mediaRepository) as T
                        }
                    }
                )
                PlaylistScreen(
                    viewModel = mediaVm,
                    onSelectPlaylist = { playlistId ->
                        mediaVm.loadPlaylistItems(playlistId)
                    },
                    onBack = { navController.popBackStack() }
                )
            }""" + new_screens
)

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "w") as f:
    f.write(content)
print("Successfully nested new screens inside NavHost")
