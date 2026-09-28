with open("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "r") as f:
    content = f.read()

# Add parameters to HomeScreen
content = content.replace(
    "    onNavigateToMediaGallery: () -> Unit = {},\n    onNavigateToPlaylists: () -> Unit = {}\n)",
    "    onNavigateToMediaGallery: () -> Unit = {},\n    onNavigateToPlaylists: () -> Unit = {},\n    onNavigateToFileManager: () -> Unit = {},\n    onNavigateToNotes: () -> Unit = {},\n    onNavigateToChecklists: () -> Unit = {},\n    onNavigateToCalendar: () -> Unit = {},\n    onNavigateToExpenses: () -> Unit = {}\n)"
)

# Add bento cards for Productivity & Finance
cards = """
            // BENTO PRODUCTIVITY & FINANCE CARDS
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Productivity & Finance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f).clickable { onNavigateToFileManager() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Files", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f).clickable { onNavigateToNotes() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Notes", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f).clickable { onNavigateToChecklists() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Checklists", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f).clickable { onNavigateToCalendar() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Calendar", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f).clickable { onNavigateToExpenses() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Expenses", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
"""

content = content.replace("            item {\n                Spacer(modifier = Modifier.height(16.dp))\n            }", cards + "\n            item {\n                Spacer(modifier = Modifier.height(16.dp))\n            }")

with open("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "w") as f:
    f.write(content)
print("Updated HomeScreen for #91-#95 successfully")
