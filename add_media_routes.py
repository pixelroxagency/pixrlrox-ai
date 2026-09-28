import re

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "r") as f:
    content = f.read()

# Add imports
imports = """
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import com.example.ui.screens.media.MediaScreen
import com.example.ui.screens.media.VideoPlayerScreen
import com.example.ui.screens.media.PlaylistScreen
import com.example.ui.screens.media.MediaPlayerViewModel
"""

content = imports + content

# Add routes before the last closing braces of NavHost
routes = """
            composable(Screen.MediaGallery.route) {
                val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
                val mediaVm: MediaPlayerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MediaPlayerViewModel(app, container.mediaRepository) as T
                        }
                    }
                )
                MediaScreen(
                    repository = container.mediaRepository,
                    onNavigateToVideo = { url ->
                        mediaVm.playUrl(url)
                        navController.navigate(Screen.VideoPlayer.route)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.VideoPlayer.route) {
                val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
                val mediaVm: MediaPlayerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MediaPlayerViewModel(app, container.mediaRepository) as T
                        }
                    }
                )
                VideoPlayerScreen(
                    viewModel = mediaVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Playlists.route) {
                val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
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
            }
"""

# Insert before the last two closing brackets
content = re.sub(r'(\s*\}\s*\}\s*)$', routes + r'\1', content)

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "w") as f:
    f.write(content)
print("Successfully updated AppNavHost.kt")
