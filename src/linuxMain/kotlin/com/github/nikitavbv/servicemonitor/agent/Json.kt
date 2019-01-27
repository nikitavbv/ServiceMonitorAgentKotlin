package com.github.nikitavbv.servicemonitor.agent

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
        var fieldValue: Any
        if (jsonStr[0] == '"') {
            // string field
            val valueEndIndex = findStringEnd(jsonStr, 0)
            fieldValue = jsonStr.substring(1, valueEndIndex)
            jsonStr = jsonStr.substring(valueEndIndex + 1).trimStart()
        } else {
            throw JsonParseError("Unknown field type of $fieldName")
        }

        result[fieldName] = fieldValue

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
    for (i in startIndex+1 until jsonStr.length) {
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
