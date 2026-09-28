package com.example.ui.screens.business

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.business.ClientEntity
import com.example.core.database.entity.business.InvoiceEntity
import com.example.core.database.entity.business.PaymentEntity
import com.example.data.repository.BusinessRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BusinessUiState(
    val clients: List<ClientEntity> = emptyList(),
    val invoices: List<InvoiceEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList()
)

class BusinessViewModel(
    private val repository: BusinessRepository
) : ViewModel() {

    val uiState: StateFlow<BusinessUiState> = combine(
        repository.allClients,
        repository.allInvoices,
        repository.allPayments
    ) { clients, invoices, payments ->
        BusinessUiState(
            clients = clients,
            invoices = invoices,
            payments = payments
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BusinessUiState())

    fun addClient(client: ClientEntity) {
        viewModelScope.launch { repository.saveClient(client) }
    }

    fun addInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch { repository.saveInvoice(invoice) }
    }

    fun addPayment(payment: PaymentEntity) {
        viewModelScope.launch { repository.savePayment(payment) }
    }
}
