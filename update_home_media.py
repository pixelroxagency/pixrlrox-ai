with open("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "r") as f:
    content = f.read()

# Add parameters to HomeScreen
content = content.replace(
    "    onNavigateToSettings: () -> Unit\n)",
    "    onNavigateToSettings: () -> Unit,\n    onNavigateToMediaGallery: () -> Unit = {},\n    onNavigateToPlaylists: () -> Unit = {}\n)"
)

# Add bento card for Media & Playlists inside the LazyColumn
card_code = """
            // BENTO MEDIA & PLAYLISTS CARD
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToMediaGallery() },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Media Gallery", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Videos & Streams", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToPlaylists() },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Playlists", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Manage queues", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
"""

# Insert before the last item or spacer in LazyColumn
content = content.replace("            item {\n                Spacer(modifier = Modifier.height(16.dp))\n            }", card_code + "\n            item {\n                Spacer(modifier = Modifier.height(16.dp))\n            }")

with open("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "w") as f:
    f.write(content)
print("Updated HomeScreen.kt successfully")
