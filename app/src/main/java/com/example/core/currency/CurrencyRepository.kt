package com.example.core.currency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CurrencyItem(
    val code: String,
    val name: String,
    val symbol: String,
    val flag: String
)

data class LiveRatesResult(
    val baseCode: String = "USD",
    val rates: Map<String, Double>,
    val lastUpdated: String,
    val isLive: Boolean
)

object CurrencyRepository {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    val ALL_CURRENCIES = listOf(
        CurrencyItem("USD", "United States Dollar", "$", "🇺🇸"),
        CurrencyItem("EUR", "Euro", "€", "🇪🇺"),
        CurrencyItem("GBP", "British Pound", "£", "🇬🇧"),
        CurrencyItem("JPY", "Japanese Yen", "¥", "🇯🇵"),
        CurrencyItem("CAD", "Canadian Dollar", "CA$", "🇨🇦"),
        CurrencyItem("AUD", "Australian Dollar", "A$", "🇦🇺"),
        CurrencyItem("CHF", "Swiss Franc", "CHF", "🇨🇭"),
        CurrencyItem("CNY", "Chinese Yuan", "CN¥", "🇨🇳"),
        CurrencyItem("INR", "Indian Rupee", "₹", "🇮🇳"),
        CurrencyItem("BDT", "Bangladeshi Taka", "৳", "🇧🇩"),
        CurrencyItem("PKR", "Pakistani Rupee", "Rs", "🇵🇰"),
        CurrencyItem("AED", "UAE Dirham", "AED", "🇦🇪"),
        CurrencyItem("SAR", "Saudi Riyal", "SAR", "🇸🇦"),
        CurrencyItem("QAR", "Qatari Riyal", "QAR", "🇶🇦"),
        CurrencyItem("KWD", "Kuwaiti Dinar", "KWD", "🇰🇼"),
        CurrencyItem("SGD", "Singapore Dollar", "S$", "🇸🇬"),
        CurrencyItem("MYR", "Malaysian Ringgit", "RM", "🇲🇾"),
        CurrencyItem("THB", "Thai Baht", "฿", "🇹🇭"),
        CurrencyItem("IDR", "Indonesian Rupiah", "Rp", "🇮🇩"),
        CurrencyItem("PHP", "Philippine Peso", "₱", "🇵🇭"),
        CurrencyItem("KRW", "South Korean Won", "₩", "🇰🇷"),
        CurrencyItem("TRY", "Turkish Lira", "₺", "🇹🇷"),
        CurrencyItem("RUB", "Russian Ruble", "₽", "🇷🇺"),
        CurrencyItem("BRL", "Brazilian Real", "R$", "🇧🇷"),
        CurrencyItem("MXN", "Mexican Peso", "MX$", "🇲🇽"),
        CurrencyItem("ZAR", "South African Rand", "R", "🇿🇦"),
        CurrencyItem("NZD", "New Zealand Dollar", "NZ$", "🇳🇿"),
        CurrencyItem("EGP", "Egyptian Pound", "E£", "🇪🇬"),
        CurrencyItem("VND", "Vietnamese Dong", "₫", "🇻🇳"),
        CurrencyItem("SEK", "Swedish Krona", "kr", "🇸🇪"),
        CurrencyItem("NOK", "Norwegian Krone", "kr", "🇳🇴"),
        CurrencyItem("DKK", "Danish Krone", "kr", "🇩🇰"),
        CurrencyItem("PLN", "Polish Zloty", "zł", "🇵🇱"),
        CurrencyItem("HUF", "Hungarian Forint", "Ft", "🇭🇺"),
        CurrencyItem("CZK", "Czech Koruna", "Kč", "🇨🇿"),
        CurrencyItem("ILS", "Israeli Shekel", "₪", "🇮🇱"),
        CurrencyItem("CLP", "Chilean Peso", "CLP$", "🇨🇱"),
        CurrencyItem("COP", "Colombian Peso", "COP$", "🇨🇴"),
        CurrencyItem("ARS", "Argentine Peso", "ARS$", "🇦🇷"),
        CurrencyItem("NGN", "Nigerian Naira", "₦", "🇳🇬"),
        CurrencyItem("KES", "Kenyan Shilling", "KSh", "🇰🇪"),
        CurrencyItem("LKR", "Sri Lankan Rupee", "Rs", "🇱🇰"),
        CurrencyItem("NPR", "Nepalese Rupee", "Rs", "🇳🇵")
    )

    val FALLBACK_RATES = mapOf(
        "USD" to 1.0,
        "EUR" to 0.9215,
        "GBP" to 0.7850,
        "JPY" to 152.30,
        "CAD" to 1.3620,
        "AUD" to 1.5120,
        "CHF" to 0.8950,
        "CNY" to 7.2400,
        "INR" to 83.450,
        "BDT" to 117.50,
        "PKR" to 278.20,
        "AED" to 3.6725,
        "SAR" to 3.7500,
        "QAR" to 3.6400,
        "KWD" to 0.3070,
        "SGD" to 1.3480,
        "MYR" to 4.7100,
        "THB" to 36.500,
        "IDR" to 16250.0,
        "PHP" to 58.200,
        "KRW" to 1380.0,
        "TRY" to 32.800,
        "RUB" to 88.500,
        "BRL" to 5.4500,
        "MXN" to 18.200,
        "ZAR" to 18.100,
        "NZD" to 1.6350,
        "EGP" to 48.300,
        "VND" to 25450.0,
        "SEK" to 10.500,
        "NOK" to 10.600,
        "DKK" to 6.8800,
        "PLN" to 3.9800,
        "HUF" to 362.00,
        "CZK" to 23.200,
        "ILS" to 3.7200,
        "CLP" to 930.00,
        "COP" to 4150.0,
        "ARS" to 910.00,
        "NGN" to 1520.0,
        "KES" to 129.00,
        "LKR" to 305.00,
        "NPR" to 133.50
    )

    suspend fun getExchangeRates(): LiveRatesResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.er-api.com/v6/latest/USD")
                .header("User-Agent", "PixelRox-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = JSONObject(bodyStr)
                        if (json.optString("result") == "success") {
                            val ratesObj = json.optJSONObject("rates")
                            val timeLastUpdate = json.optString("time_last_update_utc", "Recently")
                            if (ratesObj != null) {
                                val ratesMap = mutableMapOf<String, Double>()
                                val keys = ratesObj.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    ratesMap[key] = ratesObj.optDouble(key, 1.0)
                                }
                                return@withContext LiveRatesResult(
                                    baseCode = "USD",
                                    rates = ratesMap,
                                    lastUpdated = timeLastUpdate.take(25),
                                    isLive = true
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        LiveRatesResult(
            baseCode = "USD",
            rates = FALLBACK_RATES,
            lastUpdated = "Offline Cached Rates",
            isLive = false
        )
    }

    fun convert(
        amount: Double,
        fromCode: String,
        toCode: String,
        rates: Map<String, Double>
    ): Double {
        if (amount <= 0.0) return 0.0
        if (fromCode == toCode) return amount

        val rateFrom = rates[fromCode] ?: FALLBACK_RATES[fromCode] ?: 1.0
        val rateTo = rates[toCode] ?: FALLBACK_RATES[toCode] ?: 1.0

        val amountInUsd = amount / rateFrom
        return amountInUsd * rateTo
    }
}
