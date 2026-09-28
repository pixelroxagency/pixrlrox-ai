#!/bin/bash
sed -i '/onNavigateToSettings: () -> Unit/a \
    onNavigateToNotes: () -> Unit = {},\n\
    onNavigateToChecklists: () -> Unit = {},\n\
    onNavigateToFinance: () -> Unit = {},\n\
    onNavigateToVault: () -> Unit = {}' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt

# Insert the grid of buttons at line 548
sed -i '548 i\
            item {\n\
                Row(\n\
                    modifier = Modifier.fillMaxWidth(),\n\
                    horizontalArrangement = Arrangement.spacedBy(12.dp)\n\
                ) {\n\
                    Button(onClick = onNavigateToNotes, modifier = Modifier.weight(1f)) { Text("Notes") }\n\
                    Button(onClick = onNavigateToChecklists, modifier = Modifier.weight(1f)) { Text("Checklist") }\n\
                }\n\
                Spacer(modifier = Modifier.height(12.dp))\n\
                Row(\n\
                    modifier = Modifier.fillMaxWidth(),\n\
                    horizontalArrangement = Arrangement.spacedBy(12.dp)\n\
                ) {\n\
                    Button(onClick = onNavigateToFinance, modifier = Modifier.weight(1f)) { Text("Finance") }\n\
                    Button(onClick = onNavigateToVault, modifier = Modifier.weight(1f)) { Text("Vault") }\n\
                }\n\
            }' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
