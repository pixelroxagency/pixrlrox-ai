package com.example.batch116_120

import com.example.core.database.entity.business.InvoiceEntity
import com.example.core.database.entity.business.PaymentEntity
import com.example.core.database.entity.project.ProjectEntity
import com.example.core.database.entity.project.ProjectTaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class Batch116To120UnitTest {

    @Test
    fun testRevenueMultiCurrencyGrouping() {
        val payments = listOf(
            PaymentEntity(id = "p1", invoiceId = "i1", clientId = "c1", amount = 100.0, currency = "USD", paymentDate = System.currentTimeMillis()),
            PaymentEntity(id = "p2", invoiceId = "i2", clientId = "c1", amount = 250.0, currency = "USD", paymentDate = System.currentTimeMillis()),
            PaymentEntity(id = "p3", invoiceId = "i3", clientId = "c2", amount = 150.0, currency = "EUR", paymentDate = System.currentTimeMillis()),
            PaymentEntity(id = "p4", invoiceId = "i4", clientId = "c3", amount = 5000.0, currency = "BDT", paymentDate = System.currentTimeMillis())
        )

        val grouped = payments.groupBy { it.currency }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        assertEquals(3, grouped.size)
        assertEquals(350.0, grouped["USD"]!!, 0.001)
        assertEquals(150.0, grouped["EUR"]!!, 0.001)
        assertEquals(5000.0, grouped["BDT"]!!, 0.001)
    }

    @Test
    fun testRevenueStrictPaymentDateFilter() {
        val cal = Calendar.getInstance(TimeZone.getDefault())

        cal.set(2026, Calendar.JANUARY, 15)
        val janTime = cal.timeInMillis

        cal.set(2026, Calendar.FEBRUARY, 20)
        val febTime = cal.timeInMillis

        val payments = listOf(
            PaymentEntity(id = "p1", invoiceId = "i1", clientId = "c1", amount = 1000.0, currency = "USD", paymentDate = janTime),
            PaymentEntity(id = "p2", invoiceId = "i2", clientId = "c1", amount = 2000.0, currency = "USD", paymentDate = febTime)
        )

        val janPayments = payments.filter { p ->
            cal.timeInMillis = p.paymentDate
            cal.get(Calendar.YEAR) == 2026 && cal.get(Calendar.MONTH) == Calendar.JANUARY
        }

        assertEquals(1, janPayments.size)
        assertEquals(1000.0, janPayments.sumOf { it.amount }, 0.001)
    }

    @Test
    fun testProjectProgressCalculation() {
        val tasks = listOf(
            ProjectTaskEntity(id = "t1", projectId = "proj1", title = "Setup DB", isCompleted = true),
            ProjectTaskEntity(id = "t2", projectId = "proj1", title = "Write Repository", isCompleted = true),
            ProjectTaskEntity(id = "t3", projectId = "proj1", title = "Build UI", isCompleted = false),
            ProjectTaskEntity(id = "t4", projectId = "proj1", title = "Write Tests", isCompleted = false)
        )

        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val progressPercent = if (total > 0) completed.toFloat() / total.toFloat() else 0f

        assertEquals(4, total)
        assertEquals(2, completed)
        assertEquals(0.5f, progressPercent, 0.001f)
    }

    @Test
    fun testDockerCriticalServiceDetection() {
        val criticalNames = listOf(
            "traefik", "authentik-server", "hermes-api", "n8n-main",
            "postgres-db", "mysql-cluster", "mongodb-service", "redis-cache",
            "app-db", "caddy-reverse-proxy", "nginx-ingress"
        )

        val nonCriticalNames = listOf(
            "my-node-app", "blog-frontend", "analytics-worker", "test-runner"
        )

        val criticalKeywords = listOf(
            "traefik", "authentik", "hermes", "n8n", "postgres",
            "mysql", "mongodb", "redis", "db", "caddy", "nginx"
        )

        fun isCritical(name: String): Boolean {
            val lower = name.lowercase()
            return criticalKeywords.any { lower.contains(it) }
        }

        for (name in criticalNames) {
            assertTrue("Expected '$name' to be detected as critical", isCritical(name))
        }

        for (name in nonCriticalNames) {
            assertFalse("Expected '$name' to NOT be detected as critical", isCritical(name))
        }
    }
}
