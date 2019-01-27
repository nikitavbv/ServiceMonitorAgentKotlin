package com.github.nikitavbv.servicemonitor.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class JsonTests {

    @Test
    fun testTrim() {
        assertEquals("abc", "       abc     ".trim())
    }

    @Test
    fun testEmptyJsonObject() {
        val result = parseJsonObject("{}")
        assertEquals(0, result.size)
    }

    @Test
    fun testEmptyString() {
        assertFailsWith<JsonParseError>("Empty object string") {
            parseJsonObject("")
        }
    }

    @Test
    fun testParseNotAnObject() {
        assertFailsWith<JsonParseError>("Not a json object") {
            parseJsonObject("[1, 2, 3]")
        }
    }

    @Test
    fun testFieldWithoutQuote() {
        assertFailsWith<JsonParseError>("Expected json object field name to start with a quote") {
            parseJsonObject("{fieldName: \"value\"}")
        }
    }

    @Test
    fun testFieldWithoutColon() {
        assertFailsWith<JsonParseError>("Expected colon after json object field name (foo)") {
            parseJsonObject("{\"foo\" \"bar\"}")
        }
    }

    @Test
    fun testUnknownFieldType() {
        assertFailsWith<JsonParseError>("Unknown field type of foo") {
            parseJsonObject("{\"foo\": *bar*}")
        }
    }

    @Test
    fun testNoComma() {
        assertFailsWith<JsonParseError>("Expected comma after json object field (foo)") {
            parseJsonObject("{\"foo\": \"bar\" \"hello\": \"world\"}")
        }
    }

    @Test
    fun testParseJsonObject() {
        val result = parseJsonObject("{\"token\": \"abc-def-123-456\", \"foo\": \"bar\"}")
        assertEquals(2, result.size)
        assertEquals("abc-def-123-456", result["token"])
        assertEquals("bar", result["foo"])
    }

    @Test
    fun testParseObjectWithEmptyString() {
        val result = parseJsonObject("{\"foo\": \"\", \"bar\": \"hello world\"}")
        assertEquals(2, result.size)
        assertEquals("", result["foo"])
        assertEquals("hello world", result["bar"])
    }

    @Test
    fun testFindingStringEnd() {
        val result = findStringEnd("abc \"lorem ipsum\" str \"foo bar\"", 4)
        assertEquals(16, result)
    }

    @Test
    fun testFindingEmptyStringEnd() {
        val result = findStringEnd("abc \"\" str \"foo bar\"", 4)
        assertEquals(5, result)
    }

    @Test
    fun testFindingStringEndInvalidLength() {
        assertFailsWith<AssertionError>("Start index >= string length") {
            findStringEnd("abc \"abc\"", 9)
        }
    }

    @Test
    fun testEscapeQuotes() {
        val result = findStringEnd("start \"lorem \\\"ipsum\\\"\" end", 7)
        assertEquals(22, result)
    }

    @Test
    fun testEscapeSlashes() {
        val result = findStringEnd("start \"foo \\\\\" end", 7)
        assertEquals(13, result)
    }

    @Test
    fun testUnknownEscapeSymbol() {
        assertFailsWith<JsonParseError>("Unknown symbol for escape: f at 4 in oh\"\\foobar\"") {
            findStringEnd("oh\"\\foobar\"", 2)
        }
    }

    @Test
    fun testStringEndNoClosingQuote() {
        assertFailsWith<JsonParseError>("No matching closing quote found for index 3 in foo\"bar") {
            findStringEnd("foo\"bar", 3)
        }
    }
}
