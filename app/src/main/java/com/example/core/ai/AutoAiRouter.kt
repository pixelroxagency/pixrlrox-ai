package com.example.core.ai

enum class RouteTarget {
    DIRECT_AI
}

object AutoAiRouter {
    fun classify(prompt: String): RouteTarget {
        return RouteTarget.DIRECT_AI
    }
}
