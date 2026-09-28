import re
with open("app/src/main/java/com/example/ui/navigation/NavRoutes.kt", "r") as f:
    content = f.read()

new_routes = """
    data object Notes : Screen("notes")
    data object Checklists : Screen("checklists")
    data object Calendar : Screen("calendar")
    data object Finance : Screen("finance")
    data object Vault : Screen("vault")
    data object QrScanner : Screen("qr_scanner")
"""

content = re.sub(r'(\n\})', new_routes + r'\1', content)

with open("app/src/main/java/com/example/ui/navigation/NavRoutes.kt", "w") as f:
    f.write(content)
