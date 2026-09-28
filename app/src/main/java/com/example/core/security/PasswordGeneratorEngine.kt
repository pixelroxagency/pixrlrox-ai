package com.example.core.security

import java.security.SecureRandom

data class GeneratedPasswordResult(
    val password: String,
    val estimatedEntropyBits: Double
)

class PasswordGeneratorEngine(private val secureRandom: SecureRandom = SecureRandom()) {

    companion object {
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val NUMBERS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?/~"
        const val AMBIGUOUS = "0O1lI"
    }

    fun generatePassword(
        length: Int = 16,
        useUppercase: Boolean = true,
        useLowercase: Boolean = true,
        useNumbers: Boolean = true,
        useSymbols: Boolean = true,
        excludeAmbiguous: Boolean = false
    ): GeneratedPasswordResult {
        val clampedLength = length.coerceIn(8, 128)
        
        val categories = mutableListOf<String>()
        if (useUppercase) categories.add(UPPERCASE)
        if (useLowercase) categories.add(LOWERCASE)
        if (useNumbers) categories.add(NUMBERS)
        if (useSymbols) categories.add(SYMBOLS)

        require(categories.isNotEmpty()) { "At least one character category must be selected." }

        val passChars = mutableListOf<Char>()
        
        // 1. Guarantee at least one character from each selected category
        for (cat in categories) {
            val filteredCat = if (excludeAmbiguous) cat.filter { it !in AMBIGUOUS } else cat
            if (filteredCat.isNotEmpty()) {
                passChars.add(filteredCat[secureRandom.nextInt(filteredCat.length)])
            }
        }

        // 2. Build combined allowed pool
        var combinedPool = categories.joinToString("")
        if (excludeAmbiguous) {
            combinedPool = combinedPool.filter { it !in AMBIGUOUS }
        }

        require(combinedPool.isNotEmpty()) { "Allowed character pool is empty after exclusions." }

        // 3. Fill remaining positions
        while (passChars.size < clampedLength) {
            passChars.add(combinedPool[secureRandom.nextInt(combinedPool.length)])
        }

        // 4. Secure shuffle
        for (i in passChars.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = passChars[i]
            passChars[i] = passChars[j]
            passChars[j] = temp
        }

        val password = passChars.joinToString("")
        
        // 5. Estimate entropy (bits = length * log2(poolSize))
        val poolSize = combinedPool.length.toDouble()
        val entropyBits = if (poolSize > 1) clampedLength * (ln(poolSize) / ln(2.0)) else 0.0

        return GeneratedPasswordResult(password, entropyBits)
    }

    private fun ln(d: Double): Double = kotlin.math.ln(d)
}
