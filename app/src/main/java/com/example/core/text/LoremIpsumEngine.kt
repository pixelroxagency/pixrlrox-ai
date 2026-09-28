package com.example.core.text

import kotlin.random.Random

enum class LoremType(val displayName: String) {
    PARAGRAPHS("Paragraphs"),
    SENTENCES("Sentences"),
    WORDS("Words")
}

object LoremIpsumEngine {

    private val WORDS = listOf(
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit",
        "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore",
        "magna", "aliqua", "enim", "ad", "minim", "veniam", "quis", "nostrud",
        "exercitation", "ullamco", "laboris", "nisi", "aliquip", "ex", "ea", "commodo",
        "consequat", "duis", "aute", "irure", "in", "reprehenderit", "voluptate",
        "velit", "esse", "cillum", "fugiat", "nulla", "pariatur", "excepteur", "sint",
        "occaecat", "cupidatat", "non", "proident", "sunt", "culpa", "qui", "officia",
        "deserunt", "mollit", "anim", "id", "est", "laborum"
    )

    fun generateWords(count: Int): String {
        val safeCount = count.coerceIn(1, 1000)
        val list = mutableListOf<String>()
        for (i in 0 until safeCount) {
            list.add(WORDS[i % WORDS.size])
        }
        return list.joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    fun generateSentences(count: Int): String {
        val safeCount = count.coerceIn(1, 100)
        val sentences = mutableListOf<String>()
        val rnd = Random(42)

        for (i in 0 until safeCount) {
            val length = rnd.nextInt(6, 14)
            val words = (0 until length).map { WORDS[rnd.nextInt(WORDS.size)] }
            val sentence = words.joinToString(" ").replaceFirstChar { it.uppercase() } + "."
            sentences.add(sentence)
        }
        return sentences.joinToString(" ")
    }

    fun generateParagraphs(count: Int): String {
        val safeCount = count.coerceIn(1, 50)
        val paragraphs = mutableListOf<String>()

        for (i in 0 until safeCount) {
            if (i == 0) {
                // Classic standard first paragraph
                paragraphs.add("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.")
            } else {
                paragraphs.add(generateSentences(4))
            }
        }
        return paragraphs.joinToString("\n\n")
    }

    fun generate(type: LoremType, count: Int): String {
        return when (type) {
            LoremType.WORDS -> generateWords(count)
            LoremType.SENTENCES -> generateSentences(count)
            LoremType.PARAGRAPHS -> generateParagraphs(count)
        }
    }
}
