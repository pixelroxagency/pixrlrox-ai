with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "r") as f:
    lines = f.readlines()

# Clean lines where imports are before package or weirdly duplicated
cleaned = []
package_line = "package com.example.ui.navigation\n"

# Remove duplicate imports and fix package location
has_package = False
for line in lines:
    if "package com.example.ui.navigation" in line:
        if not has_package:
            cleaned.insert(0, line)
            has_package = True
    else:
        cleaned.append(line)

with open("app/src/main/java/com/example/ui/navigation/AppNavHost.kt", "w") as f:
    f.writelines(cleaned)

print("Fixed AppNavHost.kt structure")
