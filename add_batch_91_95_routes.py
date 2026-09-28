import re

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "r") as f:
    content = f.read()

# Add imports
imports = """
import com.example.ui.screens.files.FileManagerScreen
import com.example.ui.screens.notes.NotesScreen
import com.example.ui.screens.checklists.ChecklistsScreen
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.finance.ExpenseScreen
"""

content = imports + content

# Add routes before the last closing braces of NavHost
routes = """
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

content = re.sub(r'(\s*\}\s*\}\s*)$', routes + r'\1', content)

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "w") as f:
    f.write(content)
print("Successfully added #91-#95 routes to AppNavHost.kt")
