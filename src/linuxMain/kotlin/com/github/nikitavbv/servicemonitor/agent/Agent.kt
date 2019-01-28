package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.*
import platform.posix.*

const val STATE_FILE_NAME = "/sm/state.json"
@ExperimentalUnsignedTypes
val trackingInterval = 60.toUInt() // seconds

lateinit var state: AgentState

@ExperimentalUnsignedTypes
fun loadAgentState() {
    if (!fileExists(STATE_FILE_NAME)) {
        state = AgentState()
        return
    }

    val file = fopen(STATE_FILE_NAME, "r")
    if (file == null) {
        perror("Cannot open state file $STATE_FILE_NAME")
        exit(-1)
    }
    fseek(file, 0, SEEK_END)
    val fileSize = ftell(file)
    rewind(file)
    state = AgentState.fromJson(memScoped {
        val buffer = allocArray<ByteVar>(fileSize)
        val result = fread(buffer, 1, fileSize.convert(), file)
        if (fileSize != result.toLong()) {
            perror("File read error: read less bytes than expected from $STATE_FILE_NAME")
            exit(-1)
        }
        val resultStr = buffer.toKString()
        resultStr.substring(resultStr.indexOf("{"), resultStr.lastIndexOf("}") + 1)
    })

    fclose(file)
}

fun saveAgentState() {
    val file = fopen(STATE_FILE_NAME, "w")
    fputs(state.toJson(), file)
    fclose(file)
}

fun isAgentRegisterRequired(): Boolean {
    if (!fileExists(STATE_FILE_NAME)) {
        return true
    }
    return state.token == null
}

@ExperimentalUnsignedTypes
fun registerAgent() {
    val agentName = generateAgentName()
    println("Registering agent")
    println("Agent name: $agentName")

    val registerResult = makeAPIRequest("POST", "/api/v1/agent", mutableMapOf(
        "token" to config.projectToken,
        "name" to agentName
    ))
    state.token = registerResult["apiKey"].toString()
    saveAgentState()
    println("Done registering")
}

fun initAgent() {
    val propertiesMap = mapOf(
        "os" to getOSNameAndVersion(),
        "ipv4" to getIPv4(),
        "ipv6" to getIPv6()
    )

    makeAPIRequest("PUT", "/api/v1/agent", mapOf(
        "properties" to propertiesMap
    ))
}

fun startTrackingCycle() {
    runTrackingIteration()

    while(true) {
        sleep(trackingInterval)
        runTrackingIteration()
    }
}
