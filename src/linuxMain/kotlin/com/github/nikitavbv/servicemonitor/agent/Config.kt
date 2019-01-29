package com.github.nikitavbv.servicemonitor.agent

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.SEEK_END
import platform.posix.exit
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.perror
import platform.posix.rewind

const val CONFIG_FILE_NAME = "/sm/config.json"

lateinit var config: AgentConfig

fun checkIfConfigFileExists() = fileExists(CONFIG_FILE_NAME)

@ExperimentalUnsignedTypes
fun loadConfigFile() {
    val file = fopen(CONFIG_FILE_NAME, "r")
    if (file == null) {
        perror("Cannot open config file $CONFIG_FILE_NAME")
        exit(-1)
    }
    fseek(file, 0, SEEK_END)
    val fileSize = ftell(file)
    rewind(file)
    config = AgentConfig.fromJson(memScoped {
        val buffer = allocArray<ByteVar>(fileSize)
        val result = fread(buffer, 1, fileSize.convert(), file)
        if (fileSize != result.toLong()) {
            perror("File read error: read less bytes than expected from $STATE_FILE_NAME")
            exit(-1)
        }
        buffer.toKString()
    })
}
