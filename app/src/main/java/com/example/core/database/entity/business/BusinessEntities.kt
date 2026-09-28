package com.example.core.database.entity.business

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val company: String,
    val address: String,
    val currency: String = "USD",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val invoiceNumber: String,
    val issueDate: Long,
    val dueDate: Long,
    val currency: String = "USD",
    val status: String = "DRAFT", // DRAFT, SENT, PAID, PARTIALLY_PAID, OVERDUE, CANCELLED
    val totalAmount: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "invoice_line_items")
data class InvoiceLineItemEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val amount: Double
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val clientId: String,
    val amount: Double,
    val currency: String = "USD",
    val paymentDate: Long,
    val paymentMethod: String = "BANK_TRANSFER",
    val reference: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
