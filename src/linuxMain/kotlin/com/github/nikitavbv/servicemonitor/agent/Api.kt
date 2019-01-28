package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.toKString
import nativeAgent.makeHTTPRequest

@ExperimentalUnsignedTypes
fun makeAPIRequest(method: String, path: String, request: MutableMap<String, Any?>): Map<String, Any> {
    if (!request.containsKey("token")) {
        request["token"] = state.token
    }
    val requestJSON = toJson(request)
    val result = makeHTTPRequest(config.backend + path, method, requestJSON)
        ?: throw RuntimeException("Failed to make http api request")
    return parseJsonObject(result.toKString())
}
