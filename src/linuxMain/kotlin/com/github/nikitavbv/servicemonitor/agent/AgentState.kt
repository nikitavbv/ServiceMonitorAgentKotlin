package com.github.nikitavbv.servicemonitor.agent

class AgentState(
    var token: String? = null
) {

    fun toJson(): String {
        return toJson(mapOf(
            "token" to token
        ))
    }

    companion object {
        fun fromJson(json: String): AgentState {
            val data = parseJsonObject(json)
            return AgentState(
                data["token"]?.toString()
            )
        }
    }
}
