package com.example.core.text

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

sealed interface JsonValidationResult {
    data class Valid(
        val isObject: Boolean,
        val formatted: String,
        val minified: String,
        val keyCountOrItemCount: Int
    ) : JsonValidationResult

    data class Invalid(
        val errorMessage: String,
        val errorLocation: String? = null
    ) : JsonValidationResult
}

object JsonEngine {

    fun validateAndFormat(rawJson: String, indentSpaces: Int = 2): JsonValidationResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return JsonValidationResult.Invalid("JSON input is empty")
        }

        return try {
            val tokener = JSONTokener(trimmed)
            val nextVal = tokener.nextValue()

            when (nextVal) {
                is JSONObject -> {
                    val formatted = nextVal.toString(indentSpaces)
                    val minified = nextVal.toString()
                    JsonValidationResult.Valid(
                        isObject = true,
                        formatted = formatted,
                        minified = minified,
                        keyCountOrItemCount = nextVal.length()
                    )
                }
                is JSONArray -> {
                    val formatted = nextVal.toString(indentSpaces)
                    val minified = nextVal.toString()
                    JsonValidationResult.Valid(
                        isObject = false,
                        formatted = formatted,
                        minified = minified,
                        keyCountOrItemCount = nextVal.length()
                    )
                }
                else -> {
                    JsonValidationResult.Invalid(
                        "Root must be a JSON Object {...} or Array [...]. Found: ${nextVal?.javaClass?.simpleName ?: "null"}"
                    )
                }
            }
        } catch (e: JSONException) {
            JsonValidationResult.Invalid(
                errorMessage = e.message ?: "Invalid JSON syntax",
                errorLocation = extractErrorLocation(e.message)
            )
        } catch (e: Exception) {
            JsonValidationResult.Invalid(
                errorMessage = "JSON parsing error: ${e.message}"
            )
        }
    }

    fun minify(rawJson: String): String {
        val res = validateAndFormat(rawJson)
        return when (res) {
            is JsonValidationResult.Valid -> res.minified
            is JsonValidationResult.Invalid -> throw IllegalArgumentException(res.errorMessage)
        }
    }

    fun prettyPrint(rawJson: String, indentSpaces: Int = 2): String {
        val res = validateAndFormat(rawJson, indentSpaces)
        return when (res) {
            is JsonValidationResult.Valid -> res.formatted
            is JsonValidationResult.Invalid -> throw IllegalArgumentException(res.errorMessage)
        }
    }

    private fun extractErrorLocation(message: String?): String? {
        if (message == null) return null
        val atIndex = message.indexOf(" at ")
        val afterIndex = message.indexOf(" after ")
        return when {
            atIndex != -1 -> message.substring(atIndex + 1).trim()
            afterIndex != -1 -> message.substring(afterIndex + 1).trim()
            else -> null
        }
    }
}
