#!/bin/bash
sed -i '/onNavigateToSettings = { navController.navigate(Screen.Settings.route) }/a \
                    onNavigateToNotes = { navController.navigate(Screen.Notes.route) },\n\
                    onNavigateToChecklists = { navController.navigate(Screen.Checklists.route) },\n\
                    onNavigateToFinance = { navController.navigate(Screen.Finance.route) },\n\
                    onNavigateToVault = { navController.navigate(Screen.Vault.route) }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
