package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.toKString
import platform.posix.perror
import nativeAgent.getCurrentTimeRFC3339

@ExperimentalUnsignedTypes
fun runTrackingIteration() {
    val monitorTargets = config.monitor
    if (monitorTargets == null || monitorTargets.isEmpty()) {
        perror("Nothing to track")
        return
    }

    println("Run tracking iteration...")
    val metricsData = mutableListOf<Map<String, Any?>>()
    monitorTargets.forEach {
        val targetMapData = it ?: return@forEach
        if (targetMapData !is Map<*, *>) {
            println("Skipping monitor target, not a map: $targetMapData")
        }
        val targetMap = targetMapData as Map<*, *>

        val monitorType = targetMap["type"]
        val result = when(monitorType) {
            "memory" -> monitorMemory(targetMap)
            "io" -> monitorIO(targetMap)
            "diskUsage" -> monitorDiskUsage(targetMap)
            "cpu" -> monitorCPUUsage(targetMap)
            "uptime" -> monitorUptime(targetMap)
            "network" -> monitorNetwork(targetMap)
            "docker" -> monitorDocker(targetMap)
            "nginx" -> monitorNGINX(targetMap)
            "mysql" -> monitorMySQL(targetMap)
            else -> throw AssertionError("Unknown tracking type: $monitorType")
        }
        if (result.isEmpty()) {
            return@forEach
        }
        val nResult = result.toMutableMap()
        nResult["type"] = monitorType
        nResult["tag"] = targetMap["tag"] ?: monitorType
        nResult["timestamp"] = getCurrentTimeRFC3339()?.toKString()

        metricsData.add(result)
    }

    makeAPIRequest("POST", "/api/v1/metric", mapOf(
        "metrics" to metricsData
    ))
}

fun monitorMemory(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorIO(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorDiskUsage(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorCPUUsage(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorUptime(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorDocker(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorNetwork(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorNGINX(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}

fun monitorMySQL(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}
