package com.example.ui.screens.calculator

import net.objecthunter.exp4j.ExpressionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

class CalculatorEngineTest {

    private fun evaluate(expression: String): Double {
        return ExpressionBuilder(expression.replace("×", "*").replace("÷", "/")).build().evaluate()
    }

    @Test
    fun testOperatorPrecedence() {
        assertEquals(7.0, evaluate("1+2*3"), 0.001)
        assertEquals(9.0, evaluate("(1+2)*3"), 0.001)
    }

    @Test
    fun testDecimalPrecision() {
        assertEquals(0.3, evaluate("0.1+0.2"), 0.001) // Handled by display formatting usually
    }
    
    @Test
    fun testScientificFunctions() {
        assertEquals(sin(1.0), evaluate("sin(1)"), 0.001)
        assertEquals(cos(1.0), evaluate("cos(1)"), 0.001)
        assertEquals(1.0, evaluate("log10(10)"), 0.001)
    }
    
    @Test
    fun testDivideByZero() {
        var error = false
        try {
            evaluate("1/0") // exp4j throws ArithmeticException
        } catch (e: Exception) {
            error = true
        }
        assertTrue(error)
    }
}
