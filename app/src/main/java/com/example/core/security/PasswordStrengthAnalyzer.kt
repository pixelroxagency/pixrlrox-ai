package com.example.core.security

enum class PasswordStrengthLevel(val label: String) {
    VERY_WEAK("Very Weak"),
    WEAK("Weak"),
    FAIR("Fair"),
    STRONG("Strong"),
    VERY_STRONG("Very Strong")
}

data class PasswordStrengthResult(
    val level: PasswordStrengthLevel,
    val score: Int, // 0 to 100
    val feedback: List<String>
)

object PasswordStrengthAnalyzer {

    private val COMMON_WEAK_PASSWORDS = setOf(
        "password", "123456", "12345678", "123456789", "12345", "1234", "qwerty",
        "abc123", "monkey", "dragon", "master", "sunshine", "princess", "football",
        "baseball", "welcome", "admin", "letmein", "changeme", "iloveyou"
    )

    private val SEQUENCES = listOf(
        "1234567890", "0987654321", "abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba",
        "qwertyuiop", "asdfghjkl", "zxcvbnm"
    )

    fun analyze(password: String): PasswordStrengthResult {
        if (password.isEmpty()) {
            return PasswordStrengthResult(
                PasswordStrengthLevel.VERY_WEAK,
                0,
                listOf("Password is empty.")
            )
        }

        val feedback = mutableListOf<String>()
        var score = 0

        val length = password.length
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        // Length evaluation
        when {
            length < 8 -> {
                score += 10
                feedback.add("Password is too short (minimum recommended is 12 characters).")
            }
            length in 8..11 -> {
                score += 35
                feedback.add("Good length, but longer passwords (12+ chars) offer better security.")
            }
            length in 12..15 -> {
                score += 65
                feedback.add("Strong length.")
            }
            else -> {
                score += 85
                feedback.add("Excellent length.")
            }
        }

        // Diversity evaluation
        var diversityCount = 0
        if (hasLower) diversityCount++
        if (hasUpper) diversityCount++
        if (hasDigit) diversityCount++
        if (hasSymbol) diversityCount++

        when (diversityCount) {
            1 -> {
                score -= 15
                feedback.add("Add a mix of uppercase letters, numbers, and symbols.")
            }
            2 -> {
                score += 5
                feedback.add("Consider adding more character types for higher diversity.")
            }
            3 -> score += 15
            4 -> score += 25
        }

        // Common weak passwords check
        val lowerPass = password.lowercase()
        if (COMMON_WEAK_PASSWORDS.contains(lowerPass) || COMMON_WEAK_PASSWORDS.any { lowerPass.contains(it) }) {
            score = 10.coerceAtMost(score)
            feedback.add("Warning: This matches a known common weak password or pattern.")
        }

        // Repeated characters check (e.g. "aaaa", "1111")
        val hasRepeats = "(.)\\1{2,}".toRegex().containsMatchIn(password)
        if (hasRepeats) {
            score -= 15
            feedback.add("Avoid repeating characters consecutively.")
        }

        // Sequential patterns check
        val hasSequence = SEQUENCES.any { seq ->
            (3..seq.length).any { window ->
                seq.windowed(window).any { sub -> lowerPass.contains(sub) }
            }
        }
        if (hasSequence) {
            score -= 15
            feedback.add("Avoid predictable keyboard or number sequences (e.g. 1234, qwerty).")
        }

        val finalScore = score.coerceIn(0, 100)
        val level = when {
            finalScore < 30 -> PasswordStrengthLevel.VERY_WEAK
            finalScore < 50 -> PasswordStrengthLevel.WEAK
            finalScore < 70 -> PasswordStrengthLevel.FAIR
            finalScore < 90 -> PasswordStrengthLevel.STRONG
            else -> PasswordStrengthLevel.VERY_STRONG
        }

        if (feedback.isEmpty()) {
            feedback.add("Password structure is solid and well-diversified.")
        }

        return PasswordStrengthResult(level, finalScore, feedback)
    }
}
