package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.toKString
import nativeAgent.getCurrentTimeMillis
import platform.posix.perror
import nativeAgent.getCurrentTimeRFC3339

val ioPrevState = mutableMapOf<String, Any>()

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

@ExperimentalUnsignedTypes
fun monitorMemory(params: Map<*, *>): Map<String, Any?> {
    // https://www.kernel.org/doc/Documentation/filesystems/proc.txt
    val result = mutableMapOf<String, Any>()

    readFile("/proc/meminfo").lines().forEach { line ->
        if (line == "") {
            return@forEach
        }

        val fields = line.fields()
        val amount = fields[1].toLong()
        when(fields[0]) {
            "MemTotal:" -> result["total"] = amount
            "MemFree:" -> result["free"] = amount
            "MemAvailable:" -> result["available"] = amount
            "Buffers:" -> result["buffers"] = amount
            "Cached:" -> result["cached"] = amount
            "SwapTotal:" -> result["swapTotal"] = amount
            "SwapFree:" -> result["swapFree"] = amount
        }
    }

    return result
}

fun monitorIO(params: Map<*, *>): Map<String, Any?> {
    // https://www.kernel.org/doc/Documentation/iostats.txt
    val result = mutableMapOf<String, Any>()
    result["devices"] = mutableMapOf<String, Any>()

    readFile("/proc/diskstats").lines().forEach { line ->
        if (line == "") {
            return@forEach
        }

        val fields = line.fields()
        val deviceName = fields[2]
        // Block size according to:
        // https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git
        // /tree/include/linux/types.h?id=v4.4-rc6#n121
        val deviceBlockSize = 512
        val sectorsRead = fields[5].toLong()
        val sectorsWritten = fields[6].toLong()
        val timestamp = getCurrentTimeMillis()
        if (deviceName.startsWith("loop")) {
            return@forEach
        }

        val devicePrevState = ioPrevState[deviceName] as Map<*, *>?
        if (devicePrevState != null) {
            val prevSectorsRead = devicePrevState["sectorsRead"] as Long
            val prevSectorsWritten = devicePrevState["sectorsWritten"] as Long
            val prevTimestamp = devicePrevState["timestamp"] as Long
            val bytesRead = (sectorsRead - prevSectorsRead) * deviceBlockSize
            val bytesWritten = (sectorsWritten - prevSectorsWritten) * deviceBlockSize
            val bytesReadPerSecond = (bytesRead) / ((timestamp - prevTimestamp) / 1000)
            val bytesWrittenPerSecond = (bytesWritten) / ((timestamp - prevTimestamp) / 1000)
            (result["devices"] as MutableList<*>).add(mapOf(
                "device" to deviceName,
                "read" to bytesReadPerSecond,
                "write" to bytesWrittenPerSecond
            ))
        }
        ioPrevState[deviceName] = mapOf(
            "sectorsRead" to sectorsRead,
            "sectorsWritten" to sectorsWritten,
            "timestamp" to timestamp
        )
    }

    if ((result["devices"] as List<*>).size == 0) {
        return emptyMap()
    }

    return result
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
