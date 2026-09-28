package com.example.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoAiRouterTest {

    @Test
    fun testDirectAiRoutingRules() {
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Write a professional email"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Explain Docker containers"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("What is n8n?"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Write a bash command to check disk space"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Explain how SSL certificates work"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Summarize this text"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Translate hello to French"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Check my VPS status"))
    }

    @Test
    fun testAmbiguousDefaultsToDirectAi() {
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify("Hello, how are you?"))
        assertEquals(RouteTarget.DIRECT_AI, AutoAiRouter.classify(""))
    }
}
