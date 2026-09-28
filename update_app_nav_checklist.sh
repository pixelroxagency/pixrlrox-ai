#!/bin/bash
sed -i '/import com.example.ui.screens.notes.NotesScreen/a import com.example.ui.screens.checklist.ChecklistsScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/composable(Screen.Notes.route) {/,+6 a\
            composable(Screen.Checklists.route) {\n\
                ChecklistsScreen(\n\
                    repository = container.checklistRepository,\n\
                    onBack = { navController.popBackStack() }\n\
                )\n\
            }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
