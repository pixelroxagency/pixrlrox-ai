package com.example.core.text

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class RegexMatchResult(
    val index: Int,
    val range: IntRange,
    val value: String,
    val groups: List<String>
)

data class RegexTestOutcome(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val matchCount: Int = 0,
    val matches: List<RegexMatchResult> = emptyList()
)

object RegexTesterEngine {

    fun testRegex(
        patternStr: String,
        targetText: String,
        ignoreCase: Boolean = false,
        multiline: Boolean = false,
        dotMatchesAll: Boolean = false
    ): RegexTestOutcome {
        if (patternStr.isEmpty()) {
            return RegexTestOutcome(isValid = true, matchCount = 0, matches = emptyList())
        }

        var flags = 0
        if (ignoreCase) flags = flags or Pattern.CASE_INSENSITIVE
        if (multiline) flags = flags or Pattern.MULTILINE
        if (dotMatchesAll) flags = flags or Pattern.DOTALL

        val pattern = try {
            Pattern.compile(patternStr, flags)
        } catch (e: PatternSyntaxException) {
            return RegexTestOutcome(
                isValid = false,
                errorMessage = e.description ?: e.message ?: "Invalid regex pattern"
            )
        }

        val matcher = pattern.matcher(targetText)
        val matches = mutableListOf<RegexMatchResult>()
        var matchIndex = 0

        try {
            while (matcher.find() && matchIndex < 500) { // Safety bound
                val groups = mutableListOf<String>()
                for (g in 0..matcher.groupCount()) {
                    groups.add(matcher.group(g) ?: "")
                }
                matches.add(
                    RegexMatchResult(
                        index = matchIndex + 1,
                        range = matcher.start()..matcher.end(),
                        value = matcher.group(),
                        groups = groups
                    )
                )
                matchIndex++
            }
        } catch (e: Exception) {
            return RegexTestOutcome(
                isValid = false,
                errorMessage = "Evaluation error: ${e.message}"
            )
        }

        return RegexTestOutcome(
            isValid = true,
            matchCount = matches.size,
            matches = matches
        )
    }
}
