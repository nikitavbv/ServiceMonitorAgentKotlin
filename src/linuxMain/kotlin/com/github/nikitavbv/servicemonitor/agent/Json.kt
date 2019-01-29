package com.github.nikitavbv.servicemonitor.agent

class JsonException(msg: String) : RuntimeException(msg)

fun toJson(obj: Any): String {
    return when (obj) {
        is Map<*, *> -> mapToJson(obj)
        is List<*> -> listToJson(obj)
        is String -> "\"$obj\""
        else -> throw JsonException("Unknown type to convert to json: ${obj::class.simpleName}")
    }
}

fun mapToJson(map: Map<*, *>): String {
    return "{" + map.map { "\"${it.key}\":${toJson(it.value!!)}" }.joinToString(",") + "}"
}

fun listToJson(list: List<*>): String {
    return "[" + list.joinToString(",") { toJson(it!!) } + "]"
}
