package com.example.ui.screens.converter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UnitCategory(val name: String, val units: List<UnitDef>)
data class UnitDef(val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

class ConverterViewModel : ViewModel() {
    val categories = listOf(
        UnitCategory("Length", listOf(
            UnitDef("Meter", { it }, { it }),
            UnitDef("Kilometer", { it * 1000 }, { it / 1000 }),
            UnitDef("Centimeter", { it / 100 }, { it * 100 }),
            UnitDef("Millimeter", { it / 1000 }, { it * 1000 }),
            UnitDef("Inch", { it * 0.0254 }, { it / 0.0254 }),
            UnitDef("Foot", { it * 0.3048 }, { it / 0.3048 }),
            UnitDef("Yard", { it * 0.9144 }, { it / 0.9144 }),
            UnitDef("Mile", { it * 1609.344 }, { it / 1609.344 })
        )),
        UnitCategory("Weight/Mass", listOf(
            UnitDef("Kilogram", { it }, { it }),
            UnitDef("Gram", { it / 1000 }, { it * 1000 }),
            UnitDef("Milligram", { it / 1000000 }, { it * 1000000 }),
            UnitDef("Ounce", { it * 0.0283495 }, { it / 0.0283495 }),
            UnitDef("Pound", { it * 0.453592 }, { it / 0.453592 })
        )),
        UnitCategory("Temperature", listOf(
            UnitDef("Celsius", { it }, { it }),
            UnitDef("Fahrenheit", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }),
            UnitDef("Kelvin", { it - 273.15 }, { it + 273.15 })
        )),
        UnitCategory("Area", listOf(
            UnitDef("Square Meter", { it }, { it }),
            UnitDef("Square Kilometer", { it * 1e6 }, { it / 1e6 }),
            UnitDef("Square Foot", { it * 0.092903 }, { it / 0.092903 }),
            UnitDef("Square Yard", { it * 0.836127 }, { it / 0.836127 }),
            UnitDef("Acre", { it * 4046.86 }, { it / 4046.86 }),
            UnitDef("Hectare", { it * 10000 }, { it / 10000 })
        )),
        UnitCategory("Volume", listOf(
            UnitDef("Liter", { it }, { it }),
            UnitDef("Milliliter", { it / 1000 }, { it * 1000 }),
            UnitDef("Cubic Meter", { it * 1000 }, { it / 1000 }),
            UnitDef("Teaspoon", { it * 0.00492892 }, { it / 0.00492892 }),
            UnitDef("Tablespoon", { it * 0.0147868 }, { it / 0.0147868 }),
            UnitDef("Cup", { it * 0.24 }, { it / 0.24 }),
            UnitDef("Fluid Ounce", { it * 0.0295735 }, { it / 0.0295735 }),
            UnitDef("Gallon", { it * 3.78541 }, { it / 3.78541 })
        )),
        UnitCategory("Speed", listOf(
            UnitDef("Meter/Second", { it }, { it }),
            UnitDef("Kilometer/Hour", { it / 3.6 }, { it * 3.6 }),
            UnitDef("Mile/Hour", { it * 0.44704 }, { it / 0.44704 }),
            UnitDef("Knot", { it * 0.514444 }, { it / 0.514444 })
        )),
        UnitCategory("Time", listOf(
            UnitDef("Second", { it }, { it }),
            UnitDef("Minute", { it * 60 }, { it / 60 }),
            UnitDef("Hour", { it * 3600 }, { it / 3600 }),
            UnitDef("Day", { it * 86400 }, { it / 86400 }),
            UnitDef("Week", { it * 604800 }, { it / 604800 })
        )),
        UnitCategory("Data", listOf(
            UnitDef("Byte", { it }, { it }),
            UnitDef("Kilobyte (KB)", { it * 1000 }, { it / 1000 }),
            UnitDef("Megabyte (MB)", { it * 1e6 }, { it / 1e6 }),
            UnitDef("Gigabyte (GB)", { it * 1e9 }, { it / 1e9 }),
            UnitDef("Terabyte (TB)", { it * 1e12 }, { it / 1e12 }),
            UnitDef("Kibibyte (KiB)", { it * 1024 }, { it / 1024 }),
            UnitDef("Mebibyte (MiB)", { it * 1048576 }, { it / 1048576 }),
            UnitDef("Gibibyte (GiB)", { it * 1073741824 }, { it / 1073741824 })
        )),
        UnitCategory("Energy", listOf(
            UnitDef("Joule", { it }, { it }),
            UnitDef("Kilojoule", { it * 1000 }, { it / 1000 }),
            UnitDef("Calorie", { it * 4.184 }, { it / 4.184 }),
            UnitDef("Kilocalorie", { it * 4184 }, { it / 4184 }),
            UnitDef("Watt-hour", { it * 3600 }, { it / 3600 }),
            UnitDef("Kilowatt-hour", { it * 3.6e6 }, { it / 3.6e6 })
        ))
    )

    private val _selectedCategory = MutableStateFlow(categories[0])
    val selectedCategory: StateFlow<UnitCategory> = _selectedCategory.asStateFlow()

    private val _sourceUnit = MutableStateFlow(categories[0].units[0])
    val sourceUnit: StateFlow<UnitDef> = _sourceUnit.asStateFlow()

    private val _targetUnit = MutableStateFlow(categories[0].units[1])
    val targetUnit: StateFlow<UnitDef> = _targetUnit.asStateFlow()

    private val _inputValue = MutableStateFlow("")
    val inputValue: StateFlow<String> = _inputValue.asStateFlow()

    private val _resultValue = MutableStateFlow("")
    val resultValue: StateFlow<String> = _resultValue.asStateFlow()

    fun selectCategory(category: UnitCategory) {
        _selectedCategory.value = category
        _sourceUnit.value = category.units[0]
        _targetUnit.value = category.units[1]
        calculate()
    }

    fun selectSourceUnit(unit: UnitDef) {
        _sourceUnit.value = unit
        calculate()
    }

    fun selectTargetUnit(unit: UnitDef) {
        _targetUnit.value = unit
        calculate()
    }

    fun setInputValue(value: String) {
        _inputValue.value = value
        calculate()
    }
    
    fun swapUnits() {
        val temp = _sourceUnit.value
        _sourceUnit.value = _targetUnit.value
        _targetUnit.value = temp
        calculate()
    }

    private fun calculate() {
        val input = _inputValue.value.toDoubleOrNull()
        if (input == null) {
            _resultValue.value = ""
            return
        }
        val baseValue = _sourceUnit.value.toBase(input)
        val targetValue = _targetUnit.value.fromBase(baseValue)
        
        _resultValue.value = if (targetValue % 1 == 0.0) {
            targetValue.toLong().toString()
        } else {
            String.format("%.6f", targetValue).trimEnd('0').trimEnd('.')
        }
    }
}
