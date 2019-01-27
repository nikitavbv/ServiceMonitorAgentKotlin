package com.github.nikitavbv.servicemonitor.agent

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTests {

    @Test
    fun testEmptyMapToJson() {
        val result = toJson(emptyMap<String, Any>())
        assertEquals("{}", result)
    }

    @Test
    fun testEmptyListToJson() {
        val result = toJson(emptyList<String>())
        assertEquals("[]", result)
    }

    @Test
    fun testUnknownTypeToJson() {
        assertFailsWith<JsonException>("Unknown type to convert to json: KClass") {
            toJson(Random)
        }
    }

    @Test
    fun testMapToJson() {
        val map = mutableMapOf<String, Any>()
        map["foo"] = "bar"
        map["hello"] = "world"
        val result = toJson(map)
        assertEquals("{\"foo\":\"bar\",\"hello\":\"world\"}", result)
    }

    @Test
    fun testListToJson() {
        val list = listOf("foo", "bar")
        val result = toJson(list)
        assertEquals("[\"foo\",\"bar\"]", result)
    }

    @Test
    fun testNestedMapsToJson() {
        val map = mutableMapOf<String, Any>()
        map["foo"] = "bar"
        map["bar"] = mapOf(
            "hello" to "world"
        )
        val result = toJson(map)
        assertEquals("{\"foo\":\"bar\",\"bar\":{\"hello\":\"world\"}}", result)
    }

    @Test
    fun testNestedListsToJson() {
        val list = listOf(
            "hello",
            listOf("foo", "bar"),
            "world"
        )
        val result = toJson(list)
        assertEquals("[\"hello\",[\"foo\",\"bar\"],\"world\"]", result)
    }
}
