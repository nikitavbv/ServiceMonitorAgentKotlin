package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.toKString
import nativeAgent.makeHTTPRequest

@ExperimentalUnsignedTypes
fun makeAPIRequest(method: String, path: String, request: Map<String, Any?>): Map<String, Any> {
    val requestData = mutableMapOf<String, Any?>()
    requestData.putAll(request)
    if (!requestData.containsKey("token")) {
        requestData["token"] = state.token
    }
    val requestJSON = toJson(requestData)
    val result = makeHTTPRequest(config.backend + path, method, requestJSON)
        ?: throw RuntimeException("Failed to make http api request")
    return parseJsonObject(result.toKString())
}
