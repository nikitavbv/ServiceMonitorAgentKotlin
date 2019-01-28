package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.*
import platform.linux.exec
import platform.linux.getifaddrs
import platform.linux.ifaddrs
import platform.posix.*

val IP_V4_TO_IGNORE = listOf("127.0.0.1")
val IP_V6_TO_IGNORE = emptyList<String>()

fun fileExists(fileName: String): Boolean {
    val statBuf = nativeHeap.alloc<stat>()
    return stat(fileName, statBuf.ptr) == 0
}

fun runCommand(cmd: String): String {
    var result: String = ""
    memScoped {
        val bufferLength = 128
        val buffer = allocArray<ByteVar>(bufferLength)
        val pipe = popen(cmd, "r")
            ?: throw RuntimeException("Failed to run $cmd: popen() failed: ${strerror(errno)?.toKString()}")
        while (fgets(buffer, bufferLength, pipe) != null) {
            result += buffer.toKString()
        }
        pclose(pipe)
    }
    return result
}

fun getOSNameAndVersion(): String {
    val lsbOutput = runCommand("lsb_release -d")
    return lsbOutput.replace("Description:", "").trim()
}

fun getIPv4(): List<String> {
    return runCommand("ip addr").lines().map { it.trim() }
        .filter { it.startsWith("inet ") }
        .map { it.split(" ")[1].split('/')[0] }
        .filter { !IP_V4_TO_IGNORE.contains(it) }
}

fun getIPv6(): List<String> {
    return runCommand("ip -6 addr").lines().map { it.trim() }
        .filter { it.startsWith("inet6 ") }
        .map { it.split(" ")[1].split('/')[0] }
        .filter { !IP_V6_TO_IGNORE.contains(it) }
}

fun generateAgentName() = getOSNameAndVersion()
