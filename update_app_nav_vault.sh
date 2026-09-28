#!/bin/bash
sed -i '/import com.example.ui.screens.checklist.ChecklistsScreen/a import com.example.ui.screens.vault.VaultScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/composable(Screen.Checklists.route) {/,+6 a\
            composable(Screen.Vault.route) {\n\
                VaultScreen(\n\
                    repository = container.vaultRepository,\n\
                    secretManager = container.secretManager,\n\
                    onBack = { navController.popBackStack() }\n\
                )\n\
            }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
