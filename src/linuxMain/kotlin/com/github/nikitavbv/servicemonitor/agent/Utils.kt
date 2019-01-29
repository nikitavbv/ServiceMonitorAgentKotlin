package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.SEEK_END
import platform.posix.errno
import platform.posix.exit
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.pclose
import platform.posix.perror
import platform.posix.popen
import platform.posix.rewind
import platform.posix.stat
import platform.posix.strerror

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

@ExperimentalUnsignedTypes
fun readFile(fileName: String): String {
    val file = fopen(fileName, "r")
    if (file == null) {
        perror("Cannot open file $fileName")
        exit(-1)
    }
    fseek(file, 0, SEEK_END)
    val fileSize = ftell(file)
    rewind(file)
    return memScoped {
        val buffer = allocArray<ByteVar>(fileSize)
        val result = fread(buffer, 1, fileSize.convert(), file)
        if (fileSize != result.toLong()) {
            perror("File read error: read less bytes than expected from $fileName")
            exit(-1)
        }
        buffer.toKString()
    }
}

fun String.fields(): List<String> {
    return this.split(" ").map { it.trim() }
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
