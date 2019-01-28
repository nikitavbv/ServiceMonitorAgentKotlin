package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.toKString
import nativeAgent.getCurrentTimeMillis
import platform.posix.perror
import nativeAgent.getCurrentTimeRFC3339
import nativeAgent.makeHTTPRequest

val ioPrevState = mutableMapOf<String, Any>()
val cpuPrevState = mutableMapOf<String, Any>()
val networkPrevState = mutableMapOf<String, Any>()
val nginxPrevState = mutableMapOf<String, Any>()

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

@ExperimentalUnsignedTypes
fun monitorIO(params: Map<*, *>): Map<String, Any?> {
    // https://www.kernel.org/doc/Documentation/iostats.txt
    val result = mutableMapOf<String, Any>()
    val deviceList = mutableListOf<MutableMap<String, Any>>()

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
            deviceList.add(mutableMapOf(
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

    if (deviceList.isEmpty()) {
        return emptyMap()
    }

    result["devices"] = deviceList
    return result
}

fun monitorDiskUsage(params: Map<*, *>): Map<String, Any?> {
    val result = mutableMapOf<String, Any>()
    val filesystemList = mutableListOf<Map<String, Any>>()

    runCommand("df -x squashfs -x devtmpfs -x tmpfs -x fuse --output=source,size,used")
        .lines().forEach {line ->
            if (line == "" || line.startsWith("Filesystem")) {
                return@forEach
            }

            val fields = line.fields()
            val filesystem = fields[0]
            val total = fields[1].toLong()
            val used = fields[2].toLong()
            filesystemList.add(mapOf(
                "filesystem" to filesystem,
                "total" to total,
                "used" to used
            ))
        }

    if (filesystemList.isEmpty()) {
        return emptyMap()
    }

    result["filesystems"] = filesystemList
    return emptyMap()
}

@ExperimentalUnsignedTypes
fun monitorCPUUsage(params: Map<*, *>): Map<String, Any?> {
    // http://man7.org/linux/man-pages/man5/proc.5.html
    val result = mutableMapOf<String, Any>()
    val cpus = mutableListOf<Map<String, Any>>()

    val timestamp = getCurrentTimeMillis()
    readFile("/proc/stat").lines().forEach { line ->
        if (line == "") {
            return@forEach
        }

        val fields = line.fields()
        if (!fields[0].startsWith("cpu")) {
            return@forEach
        }
        val cpu = fields[0]
        val user = fields[1].toLong()
        val nice = fields[2].toLong()
        val system = fields[3].toLong()
        val idle = fields[4].toLong()
        val iowait = fields[5].toLong()
        val irq = fields[6].toLong()
        val softirq = fields[7].toLong()
        val steal = fields[8].toLong()
        val guest = fields[9].toLong()
        val guestNice = fields[10].toLong()

        val prevState = cpuPrevState[cpu] as Map<String, Any>?
        if (prevState != null) {
            val prevUser = prevState["user"] as Long
            val prevNice = prevState["nice"] as Long
            val prevSystem = prevState["system"] as Long
            val prevIdle = prevState["idle"] as Long
            val prevIOWait = prevState["iowait"] as Long
            val prevIrq = prevState["irq"] as Long
            val prevSoftIrq = prevState["softirq"] as Long
            val prevSteal = prevState["steal"] as Long
            val prevGuest = prevState["guest"] as Long
            val prevGuestNice = prevState["guestNice"] as Long
            val prevTimestamp = prevState["timestamp"] as Long

            cpus.add(mapOf(
                "cpu" to cpu,
                "user" to (user - prevUser) / ((timestamp - prevTimestamp) / 1000),
                "nice" to (nice - prevNice) / ((timestamp - prevTimestamp) / 1000),
                "system" to (system - prevSystem) / ((timestamp - prevTimestamp) / 1000),
                "idle" to (idle - prevIdle) / ((timestamp - prevTimestamp) / 1000),
                "iowait" to (iowait - prevIOWait) / ((timestamp - prevTimestamp) / 1000),
                "irq" to (irq - prevIrq) / ((timestamp - prevTimestamp) / 1000),
                "softirq" to (softirq - prevSoftIrq) / ((timestamp - prevTimestamp) / 1000),
                "guest" to (guest - prevGuest) / ((timestamp - prevTimestamp) / 1000),
                "steal" to (steal - prevSteal) / ((timestamp - prevTimestamp) / 1000),
                "guestNice" to (guestNice - prevGuestNice) / ((timestamp - prevTimestamp) / 1000)
            ))
        }
        cpuPrevState[cpu] = mapOf(
            "user" to user,
            "nice" to nice,
            "system" to system,
            "idle" to idle,
            "iowait" to iowait,
            "irq" to irq,
            "softirq" to softirq,
            "steal" to steal,
            "guest" to guest,
            "guestNice" to guestNice,
            "timestamp" to timestamp
        )
    }

    if (cpus.isEmpty()) {
        return emptyMap()
    }

    result["cpus"] = cpus
    return result
}

@ExperimentalUnsignedTypes
fun monitorUptime(params: Map<*, *>): Map<String, Any?> {
    // http://man7.org/linux/man-pages/man5/proc.5.html
    return mapOf(
        "uptime" to readFile("/proc/uptime").fields()[0]
    )
}

@ExperimentalUnsignedTypes
fun monitorNetwork(params: Map<*, *>): Map<String, Any?> {
    val devices = mutableListOf<Map<String, Any>>()

    val timestamp = getCurrentTimeMillis()
    readFile("/proc/net/dev").lines().forEach { line ->
        if (line == "" || line.contains("|")) {
            return@forEach
        }

        val fields = line.fields()
        val deviceName = fields[0]
        val bytesReceived = fields[1].toLong()
        val bytesSent = fields[9].toLong()

        val devicePrevState = networkPrevState[deviceName] as Map<String, Any>?
        if (devicePrevState != null) {
            val prevBytesReceived = devicePrevState["bytesReceived"] as Long
            val prevBytesSent = devicePrevState["bytesSent"] as Long
            val prevTimestamp = devicePrevState["timestamp"] as Long

            devices.add(mapOf(
                "device" to deviceName,
                "bytesSent" to (bytesSent - prevBytesSent) / ((timestamp - prevTimestamp) / 1000),
                "bytesReceived" to (bytesReceived-prevBytesReceived) / ((timestamp - prevTimestamp) / 1000)
            ))
        }
        networkPrevState[deviceName] = mapOf(
            "bytesReceived" to bytesReceived,
            "bytesSent" to bytesSent,
            "timestamp" to timestamp
        )
    }

    if (devices.isEmpty()) {
        return emptyMap()
    }

    return mapOf("devices" to devices)
}

fun monitorDocker(params: Map<*, *>): Map<String, Any?> {
    val containers = mutableListOf<Map<String, Any>>()

    runCommand("docker ps --format {{.Image}}|{{.Status}}").lines().forEach { line ->
        if (line == "" || line.startsWith("CONTAINER")) {
            return@forEach
        }

        val fields = line.split("|")
        val containerStatus = fields[1]
        val containerName = fields[0]

        containers.add(mapOf(
            "containerName" to containerName,
            "status" to containerStatus
        ))
    }

    if (containers.isEmpty()) {
        return emptyMap()
    }
    return mapOf("containers" to containers)
}

fun monitorNGINX(params: Map<*, *>): Map<String, Any?> {
    // http://nginx.org/en/docs/http/ngx_http_stub_status_module.html
    val result = mutableMapOf<String, Any?>()
    val endpoint = params["endpoint"].toString()

    val response = makeHTTPRequest(endpoint, "GET", "")
    if (response == null) {
        println("Error while reading nginx response")
        return result
    }
    val timestamp = getCurrentTimeMillis()
    val lines = response.toKString().lines()
    val requests = lines[2].fields()[2].toLong()
    val prevState = nginxPrevState[endpoint] as Map<*, *>?
    if (prevState != null) {
        val prevRequests = prevState["requests"] as Long
        val prevTimestamp = prevState["timestamp"] as Long

        result["requests"] = (requests - prevRequests) / ((timestamp - prevTimestamp) / 1000)
    }
    nginxPrevState[endpoint] = mapOf(
        "requests" to requests,
        "timestamp" to timestamp
    )

    return result
}

fun monitorMySQL(params: Map<*, *>): Map<String, Any?> {
    TODO("implement this")
    return emptyMap()
}
