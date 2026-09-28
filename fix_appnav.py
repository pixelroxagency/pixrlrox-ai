with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "r") as f:
    content = f.read()

# Remove misplaced imports at top
content = content.replace("""
import com.example.ui.screens.files.FileManagerScreen
import com.example.ui.screens.notes.NotesScreen
import com.example.ui.screens.checklists.ChecklistsScreen
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.finance.ExpenseScreen
""", "")

# Put package at top and proper imports right after
new_header = """package com.example.ui.navigation

import com.example.ui.screens.files.FileManagerScreen
import com.example.ui.screens.notes.NotesScreen
import com.example.ui.screens.checklists.ChecklistsScreen
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.finance.ExpenseScreen
"""

content = new_header + content.replace("package com.example.ui.navigation\n", "")

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "w") as f:
    f.write(content)
print("Fixed AppNavHost.kt header")
