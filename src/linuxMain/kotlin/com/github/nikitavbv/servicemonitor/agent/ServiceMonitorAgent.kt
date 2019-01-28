package com.github.nikitavbv.servicemonitor.agent

import platform.posix.exit
import platform.posix.perror


@ExperimentalUnsignedTypes
fun main() {
    if (!checkIfConfigFileExists()) {
        perror("Config file not found. Consider creating one at $CONFIG_FILE_NAME")
        exit(-1)
    }

    loadConfigFile()
    loadAgentState()
    if (isAgentRegisterRequired()) {
        registerAgent()
    }
    initAgent()

    startTrackingCycle()
}
