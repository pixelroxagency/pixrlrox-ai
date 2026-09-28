package com.example.core.security

import org.junit.Assert.*
import org.junit.Test

class SecurityPrivacyHubTest {

    @Test
    fun testPasswordGeneratorLengthAndCategories() {
        val engine = PasswordGeneratorEngine()
        val result = engine.generatePassword(
            length = 20,
            useUppercase = true,
            useLowercase = true,
            useNumbers = true,
            useSymbols = true,
            excludeAmbiguous = true
        )

        assertEquals(20, result.password.length)
        assertTrue(result.password.any { it.isUpperCase() })
        assertTrue(result.password.any { it.isLowerCase() })
        assertTrue(result.password.any { it.isDigit() })
        assertTrue(result.password.any { !it.isLetterOrDigit() })

        // Check ambiguous characters excluded
        val ambiguous = setOf('0', 'O', '1', 'l', 'I')
        for (char in result.password) {
            assertFalse(ambiguous.contains(char))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPasswordGeneratorNoCategoriesThrows() {
        val engine = PasswordGeneratorEngine()
        engine.generatePassword(
            useUppercase = false,
            useLowercase = false,
            useNumbers = false,
            useSymbols = false
        )
    }

    @Test
    fun testPasswordGeneratorNonDeterministic() {
        val engine = PasswordGeneratorEngine()
        val p1 = engine.generatePassword(16)
        val p2 = engine.generatePassword(16)
        // Highly likely to be different due to SecureRandom
        assertNotEquals(p1.password, p2.password)
    }

    @Test
    fun testPasswordStrengthAnalyzer() {
        val veryWeak = PasswordStrengthAnalyzer.analyze("12345")
        assertEquals(PasswordStrengthLevel.VERY_WEAK, veryWeak.level)
        assertTrue(veryWeak.feedback.isNotEmpty())

        val commonWeak = PasswordStrengthAnalyzer.analyze("password")
        assertEquals(PasswordStrengthLevel.VERY_WEAK, commonWeak.level)

        val repeats = PasswordStrengthAnalyzer.analyze("aaaaaaa123!")
        assertTrue(repeats.feedback.any { it.contains("repeating") })

        val sequence = PasswordStrengthAnalyzer.analyze("1234abcdABCD#$")
        assertTrue(sequence.feedback.any { it.contains("sequences") })

        val strong = PasswordStrengthAnalyzer.analyze("Kx9#mP2\$vL8!qZ7%")
        assertTrue(strong.level == PasswordStrengthLevel.STRONG || strong.level == PasswordStrengthLevel.VERY_STRONG)
    }
}
