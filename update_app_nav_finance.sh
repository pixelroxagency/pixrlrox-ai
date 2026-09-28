#!/bin/bash
sed -i '/import com.example.ui.screens.vault.VaultScreen/a import com.example.ui.screens.finance.CurrencyConverterScreen' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/composable(Screen.Finance.route) {/,+6 a\
            composable(Screen.CurrencyConverter.route) {\n\
                CurrencyConverterScreen(\n\
                    onBack = { navController.popBackStack() }\n\
                )\n\
            }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt
            
sed -i '/data object Finance : Screen("finance")/a \    data object CurrencyConverter : Screen("currency_converter")' app/src/main/java/com/example/ui/navigation/NavRoutes.kt

sed -i '/onNavigateToFinance: () -> Unit = {}/a \
    onNavigateToCurrencyConverter: () -> Unit = {}' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
    
sed -i '/onNavigateToFinance = { navController.navigate(Screen.Finance.route) }/a \
                    onNavigateToCurrencyConverter = { navController.navigate(Screen.CurrencyConverter.route) }' app/src/main/java/com/example/ui/navigation/AppNavHost.kt

sed -i '/Button(onClick = onNavigateToFinance, modifier = Modifier.weight(1f)) { Text("Finance") }/a \
                    Button(onClick = onNavigateToCurrencyConverter, modifier = Modifier.weight(1f)) { Text("Converter") }' app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
