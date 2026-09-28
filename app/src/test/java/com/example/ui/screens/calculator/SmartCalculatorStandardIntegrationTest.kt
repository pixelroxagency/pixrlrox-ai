package com.example.ui.screens.calculator

import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test

class SmartCalculatorStandardIntegrationTest {

    @Test
    fun `util_calc_hub exists in ToolCatalog and redundant utility tools are removed`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }
        
        assertNotNull("util_calc_hub must exist", allTools["util_calc_hub"])
        assertNull("util_calc should be removed from Utilities", allTools["util_calc"])
        assertNull("util_conv should be removed from Utilities", allTools["util_conv"])

        assertTrue("Tools catalog should contain items", allTools.isNotEmpty())
        assertEquals(ToolCatalog.getAllTools().size, allTools.size)
    }

    @Test
    fun `calculator routes are preserved and functional`() {
        assertEquals("calculator", Screen.Calculator.route)
        assertEquals("smart_calculator", Screen.SmartCalculator.route)
    }
}
