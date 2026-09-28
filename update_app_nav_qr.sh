#!/bin/bash
sed -i '/import com.example.ui.screens.finance.FinanceScreen/a import com.example.ui.screens.qr.QrScannerScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/composable(Screen.Finance.route) {/,+6 a\
            composable(Screen.QrScanner.route) {\n\
                QrScannerScreen(\n\
                    onBack = { navController.popBackStack() }\n\
                )\n\
            }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
            
sed -i '/onNavigateToVault: () -> Unit = {}/a \
    onNavigateToQrScanner: () -> Unit = {}' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
    
sed -i '/onNavigateToVault = { navController.navigate(Screen.Vault.route) }/a \
                    onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.route) }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

# Inject the button for QR Scanner in HomeScreen
sed -i '/Button(onClick = onNavigateToVault, modifier = Modifier.weight(1f)) { Text("Vault") }/a \
                    Button(onClick = onNavigateToQrScanner, modifier = Modifier.weight(1f)) { Text("QR Scanner") }' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
