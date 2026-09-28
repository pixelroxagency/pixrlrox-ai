package com.example.ui.screens.calculator

import com.example.core.database.dao.calculator.CalculatorDao
import com.example.core.database.entity.calculator.CalcHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeCalculatorDao : CalculatorDao {
    private val list = mutableListOf<CalcHistoryEntity>()

    override fun getAllHistory(): Flow<List<CalcHistoryEntity>> = flowOf(list)

    override suspend fun insertHistory(history: CalcHistoryEntity) {
        list.add(history)
    }

    override suspend fun clearHistory() {
        list.clear()
    }
}

class SmartCalculatorLayoutTest {

    private lateinit var viewModel: CalculatorViewModel
    private lateinit var dao: FakeCalculatorDao

    @Before
    fun setUp() {
        dao = FakeCalculatorDao()
        viewModel = CalculatorViewModel(dao)
    }

    @Test
    fun testModeToggleStandardToScientific() {
        assertFalse(viewModel.isScientificMode.value)
        viewModel.onAction("MODE")
        assertTrue(viewModel.isScientificMode.value)
        viewModel.onAction("MODE")
        assertFalse(viewModel.isScientificMode.value)
    }

    @Test
    fun testScientificInputExpressions() {
        viewModel.onAction("MODE")
        assertTrue(viewModel.isScientificMode.value)

        // Type sin(30) or sin(0)
        viewModel.onAction("sin(")
        viewModel.onAction("0")
        viewModel.onAction(")")
        assertEquals("sin(0)", viewModel.expression.value)
        
        viewModel.onAction("=")
        assertEquals("0", viewModel.expression.value)
    }

    @Test
    fun testComplexExpressionCalculation() {
        viewModel.onAction("2")
        viewModel.onAction("+")
        viewModel.onAction("3")
        viewModel.onAction("×")
        viewModel.onAction("4")
        assertEquals("2+3×4", viewModel.expression.value)
        assertEquals("14", viewModel.result.value)

        viewModel.onAction("=")
        assertEquals("14", viewModel.expression.value)
    }

    @Test
    fun testClearAndBackspace() {
        viewModel.onAction("1")
        viewModel.onAction("2")
        viewModel.onAction("3")
        assertEquals("123", viewModel.expression.value)

        viewModel.onAction("DEL")
        assertEquals("12", viewModel.expression.value)

        viewModel.onAction("AC")
        assertEquals("", viewModel.expression.value)
        assertEquals("", viewModel.result.value)
    }
}
