package com.github.nikitavbv.servicemonitor.agent

import kotlin.math.min

// Simple json parser
// It is better to replace it later with kotlinx.serialization

class JsonParseError(msg: String) : RuntimeException(msg)

fun parseJsonObject(objStr: String): Map<String, Any> {
    val result = mutableMapOf<String, Any>()

    if (objStr.isEmpty()) {
        throw JsonParseError("Empty object string")
    }

    var jsonStr = objStr.trim()
    if (jsonStr[0] != '{' || jsonStr[jsonStr.length - 1] != '}') {
        throw JsonParseError("Not a json object")
    }
    jsonStr = jsonStr.substring(1, jsonStr.length - 1).trim()

    while (jsonStr.isNotEmpty()) {
        if (jsonStr[0] != '"') {
            throw JsonParseError("Expected json object field name to start with a quote")
        }
        // parse field name
        val fieldNameEndIndex = findStringEnd(jsonStr, 0)
        val fieldName = jsonStr.substring(1, fieldNameEndIndex)
        jsonStr = jsonStr.substring(fieldNameEndIndex + 1).trimStart()
        if (jsonStr[0] != ':') {
            throw JsonParseError("Expected colon after json object field name ($fieldName)")
        }
        jsonStr = jsonStr.substring(1).trimStart()

        // parse field value
        val parsedFieldValue = parseValue(jsonStr)
        jsonStr = jsonStr.substring(min(parsedFieldValue.second + 1, jsonStr.length)).trimStart()
        result[fieldName] = parsedFieldValue.first

        if (jsonStr.isNotEmpty()) {
            if (jsonStr[0] != ',') {
                throw JsonParseError("Expected comma after json object field ($fieldName)")
            }

            jsonStr = jsonStr.substring(1).trimStart()
        }
    }

    return result
}

fun findStringEnd(jsonStr: String, startIndex: Int): Int {
    if (startIndex >= jsonStr.length) {
        throw AssertionError("Start index >= string length")
    }

    var escapeNext = false
    for (i in startIndex + 1 until jsonStr.length) {
        if (jsonStr[i] == '"') {
            if (escapeNext) {
                escapeNext = false
            } else {
                return i
            }
        } else if (jsonStr[i] == '\\') {
            escapeNext = !escapeNext
        } else if (escapeNext) {
            throw JsonParseError("Unknown symbol for escape: ${jsonStr[i]} at $i in $jsonStr")
        }
    }
    throw JsonParseError("No matching closing quote found for index $startIndex in $jsonStr")
}

fun findObjectEnd(jsonStr: String, startIndex: Int): Int {
    if (startIndex >= jsonStr.length) {
        throw AssertionError("Start index >= string length")
    }

    var i = startIndex + 1
    while (i < jsonStr.length) {
        when {
            jsonStr[i] == '}' -> return i
            jsonStr[i] == '"' -> i = findStringEnd(jsonStr, i) + 1
            else -> i++
        }
    }

    throw JsonParseError("No matching close brace found for index $startIndex in $jsonStr")
}

fun parseJsonArray(arrStr: String): List<Any> {
    val result = mutableListOf<Any>()

    if (arrStr.isEmpty()) {
        throw JsonParseError("Empty array string")
    }

    var jsonStr = arrStr.trim()
    if (jsonStr[0] != '[' || jsonStr[jsonStr.length - 1] != ']') {
        throw JsonParseError("Not a json array")
    }
    jsonStr = jsonStr.substring(1, jsonStr.length - 1).trim()

    while (jsonStr.isNotEmpty()) {
        val parsedValue: Pair<Any, Int> = parseValue(jsonStr)
        jsonStr = jsonStr.substring(min(parsedValue.second + 1, jsonStr.length)).trimStart()
        result.add(parsedValue.first)

        if (jsonStr.isNotEmpty()) {
            if (jsonStr[0] != ',') {
                throw JsonParseError("Expected comma after json array element")
            }

            jsonStr = jsonStr.substring(1).trimStart()
        }
    }

    return result
}

fun parseValue(jsonStr: String): Pair<Any, Int> {
    val fieldValue: Any
    val valueEndIndex: Int
    when {
        jsonStr[0] == '"' -> {
            // string field
            valueEndIndex = findStringEnd(jsonStr, 0)
            fieldValue = jsonStr.substring(1, valueEndIndex)
        }
        jsonStr[0] == '{' -> {
            // object field
            valueEndIndex = findObjectEnd(jsonStr, 0)
            fieldValue = parseJsonObject(jsonStr.substring(0, valueEndIndex + 1))
        }
        jsonStr[0] == '[' -> {
            // array field
            valueEndIndex = findArrayEnd(jsonStr, 0)
            fieldValue = parseJsonArray(jsonStr.substring(0, valueEndIndex + 1))
        }
        jsonStr[0].isDigit() || jsonStr[0] == '-' || jsonStr[0] == '+' -> {
            // numeric field
            valueEndIndex = findNumericEnd(jsonStr, 0)
            fieldValue = jsonStr.substring(0, valueEndIndex + 1).toDouble()
        }
        else -> throw JsonParseError("Unknown field type: $jsonStr")
    }
    return fieldValue to valueEndIndex
}

fun findArrayEnd(jsonStr: String, startIndex: Int): Int {
    if (startIndex >= jsonStr.length) {
        throw AssertionError("Start index >= string length")
    }

    var i = startIndex + 1
    while (i < jsonStr.length) {
        when {
            jsonStr[i] == ']' -> return i
            jsonStr[i] == '"' -> i = findStringEnd(jsonStr, i) + 1
            else -> i++
        }
    }

    throw JsonParseError("No matching close bracket found for index $startIndex in $jsonStr")
}

fun findNumericEnd(jsonStr: String, startIndex: Int): Int {
    if (startIndex >= jsonStr.length) {
        throw AssertionError("Start index >= string length")
    }

    var i = startIndex
    var point = false
    while (i < jsonStr.length) {
        if (jsonStr[i] == '.') {
            if (point) {
                throw NumberFormatException("Invalid number format: more than one point: $jsonStr")
            } else {
                point = true
            }
        } else if (jsonStr[i] == '+' || jsonStr[i] == '-') {
            if (i != startIndex) {
                throw NumberFormatException("Unexpected number sign: $jsonStr")
            }
        } else if (!jsonStr[i].isDigit()) {
            i--
            break
        }

        if (i + 1 < jsonStr.length) {
            i++
        } else {
            break
        }
    }

    return i
}
