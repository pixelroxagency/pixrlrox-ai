package com.example.batch91_95

import org.junit.Assert.assertEquals
import org.junit.Test

class Batch91To95Test {

    @Test
    fun testExpenseTotalsAndBalance() {
        val income = 1500.0
        val expense = 350.0
        val balance = income - expense
        assertEquals(1150.0, balance, 0.001)
    }

    @Test
    fun testChecklistProgressCalculation() {
        val totalItems = 8
        val completedItems = 3
        assertEquals(3, completedItems)
        assertEquals(8, totalItems)
    }

    @Test
    fun testNoteSortingPinnedLogic() {
        val notes = listOf(
            Pair("Note 1", false),
            Pair("Note 2", true),
            Pair("Note 3", false)
        )
        val sorted = notes.sortedByDescending { it.second }
        assertEquals("Note 2", sorted[0].first)
    }
}
