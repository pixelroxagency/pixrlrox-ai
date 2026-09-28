package com.example.data.repository

import com.example.core.database.dao.business.BusinessDao
import com.example.core.database.entity.business.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone

class BusinessRepository(private val businessDao: BusinessDao) {

    val allClients: Flow<List<ClientEntity>> = businessDao.getAllClients()
    val allInvoices: Flow<List<InvoiceEntity>> = businessDao.getAllInvoices()
    val allPayments: Flow<List<PaymentEntity>> = businessDao.getAllPayments()

    suspend fun getClientById(id: String): ClientEntity? = businessDao.getClientById(id)
    suspend fun saveClient(client: ClientEntity) = businessDao.insertClient(client)
    suspend fun deleteClient(client: ClientEntity) = businessDao.deleteClient(client)

    suspend fun getInvoiceById(id: String): InvoiceEntity? = businessDao.getInvoiceById(id)
    suspend fun saveInvoice(invoice: InvoiceEntity, lineItems: List<InvoiceLineItemEntity> = emptyList()) {
        businessDao.insertInvoice(invoice)
        if (lineItems.isNotEmpty()) {
            businessDao.deleteLineItemsForInvoice(invoice.id)
            businessDao.insertLineItems(lineItems)
        }
    }
    suspend fun deleteInvoice(invoice: InvoiceEntity) {
        businessDao.deleteLineItemsForInvoice(invoice.id)
        businessDao.deleteInvoice(invoice)
    }

    fun getLineItemsForInvoice(invoiceId: String): Flow<List<InvoiceLineItemEntity>> =
        businessDao.getLineItemsForInvoice(invoiceId)

    suspend fun savePayment(payment: PaymentEntity) {
        businessDao.insertPayment(payment)
        // Automatically check if invoice is fully paid or partially paid
        updateInvoiceStatusFromPayments(payment.invoiceId)
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        businessDao.deletePayment(payment)
        updateInvoiceStatusFromPayments(payment.invoiceId)
    }

    private suspend fun updateInvoiceStatusFromPayments(invoiceId: String) {
        val invoice = businessDao.getInvoiceById(invoiceId) ?: return
        val payments = businessDao.getPaymentsListForInvoice(invoiceId)
        val totalPaid = payments.sumOf { it.amount }
        val newStatus = when {
            totalPaid >= invoice.totalAmount -> "PAID"
            totalPaid > 0.0 -> "PARTIALLY_PAID"
            invoice.dueDate < System.currentTimeMillis() -> "OVERDUE"
            else -> invoice.status
        }
        if (newStatus != invoice.status) {
            businessDao.insertInvoice(invoice.copy(status = newStatus))
        }
    }

    // Revenue calculations based strictly on Payment dates
    suspend fun getRevenueForMonth(year: Int, month: Int): Map<String, Double> {
        val payments = allPayments.first()
        val cal = Calendar.getInstance(TimeZone.getDefault())
        return payments.filter { payment ->
            cal.timeInMillis = payment.paymentDate
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        }.groupBy { it.currency }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    suspend fun getRevenueForYear(year: Int): Map<String, Double> {
        val payments = allPayments.first()
        val cal = Calendar.getInstance(TimeZone.getDefault())
        return payments.filter { payment ->
            cal.timeInMillis = payment.paymentDate
            cal.get(Calendar.YEAR) == year
        }.groupBy { it.currency }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    suspend fun getOutstandingInvoicesTotal(): Map<String, Double> {
        val invoices = allInvoices.first().filter { it.status != "PAID" && it.status != "CANCELLED" }
        val payments = allPayments.first()
        val paymentsByInvoice = payments.groupBy { it.invoiceId }

        val outstandingByCurrency = mutableMapOf<String, Double>()
        for (inv in invoices) {
            val paid = paymentsByInvoice[inv.id]?.sumOf { it.amount } ?: 0.0
            val remaining = (inv.totalAmount - paid).coerceAtLeast(0.0)
            val curr = inv.currency
            outstandingByCurrency[curr] = (outstandingByCurrency[curr] ?: 0.0) + remaining
        }
        return outstandingByCurrency
    }

    suspend fun getOverdueAmountTotal(): Map<String, Double> {
        val now = System.currentTimeMillis()
        val invoices = allInvoices.first().filter {
            (it.status == "OVERDUE" || (it.dueDate < now && it.status != "PAID" && it.status != "CANCELLED"))
        }
        val payments = allPayments.first().groupBy { it.invoiceId }

        val overdueByCurrency = mutableMapOf<String, Double>()
        for (inv in invoices) {
            val paid = payments[inv.id]?.sumOf { it.amount } ?: 0.0
            val remaining = (inv.totalAmount - paid).coerceAtLeast(0.0)
            val curr = inv.currency
            overdueByCurrency[curr] = (overdueByCurrency[curr] ?: 0.0) + remaining
        }
        return overdueByCurrency
    }
}
