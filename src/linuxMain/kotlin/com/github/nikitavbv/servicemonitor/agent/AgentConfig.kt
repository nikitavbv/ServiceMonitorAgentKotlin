package com.github.nikitavbv.servicemonitor.agent

class AgentConfig(
    val backend: String?,
    val projectToken: String?,
    val monitor: List<*>?
) {

    companion object {
        fun fromJson(json: String): AgentConfig {
            val data = parseJsonObject(json)
            return AgentConfig(
                data["backend"]?.toString(),
                data["projectToken"]?.toString(),
                data["monitor"] as List<*>?
            )
        }
    }
}
