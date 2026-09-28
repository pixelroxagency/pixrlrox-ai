package com.example.ui.screens.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.dao.calculator.CalculatorDao
import com.example.core.database.entity.calculator.CalcHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.UUID

class CalculatorViewModel(private val calculatorDao: CalculatorDao) : ViewModel() {
    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _result = MutableStateFlow("")
    val result: StateFlow<String> = _result.asStateFlow()
    
    private val _isScientificMode = MutableStateFlow(false)
    val isScientificMode: StateFlow<Boolean> = _isScientificMode.asStateFlow()

    val history: StateFlow<List<CalcHistoryEntity>> = calculatorDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onAction(action: String) {
        when (action) {
            "AC" -> {
                _expression.value = ""
                _result.value = ""
            }
            "DEL" -> {
                if (_expression.value.isNotEmpty()) {
                    _expression.value = _expression.value.dropLast(1)
                    calculatePreview()
                }
            }
            "=" -> {
                calculateFinalResult()
            }
            "MODE" -> {
                _isScientificMode.value = !_isScientificMode.value
            }
            "sin(", "cos(", "tan(", "log(", "ln(", "sqrt(" -> {
                _expression.value += action
            }
            "^" -> {
                 _expression.value += "^"
            }
            "π" -> {
                 _expression.value += "pi"
                 calculatePreview()
            }
            else -> {
                _expression.value += action
                calculatePreview()
            }
        }
    }

    private fun prepareExpression(raw: String): String {
        return raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace("log(", "log10(")
            .replace("ln(", "log(")
            .replace("π", "pi")
    }

    private fun calculatePreview() {
        if (_expression.value.isBlank()) {
            _result.value = ""
            return
        }
        try {
            val expr = ExpressionBuilder(prepareExpression(_expression.value)).build()
            val res = expr.evaluate()
            _result.value = formatResult(res)
        } catch (e: Exception) {
            // Wait for full valid expression
        }
    }

    private fun calculateFinalResult() {
        try {
            val exprStr = prepareExpression(_expression.value)
            val expr = ExpressionBuilder(exprStr).build()
            val res = expr.evaluate()
            val finalRes = formatResult(res)
            
            viewModelScope.launch {
                calculatorDao.insertHistory(
                    CalcHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        expression = _expression.value,
                        result = finalRes,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            
            _expression.value = finalRes
            _result.value = ""
        } catch (e: Exception) {
            _result.value = "Error"
        }
    }
    
    private fun formatResult(res: Double): String {
        return if (res % 1 == 0.0) {
            res.toLong().toString()
        } else {
            String.format("%.8f", res).trimEnd('0').trimEnd('.')
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            calculatorDao.clearHistory()
        }
    }
    
    fun setExpression(expr: String) {
        _expression.value = expr
        _result.value = ""
    }
}
