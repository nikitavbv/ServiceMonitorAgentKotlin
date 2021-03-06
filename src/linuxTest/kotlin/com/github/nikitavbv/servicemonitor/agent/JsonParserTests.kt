package com.github.nikitavbv.servicemonitor.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonParserTests {

    @Test
    fun testTrim() {
        assertEquals("abc", "       abc     ".trim())
    }

    @Test
    fun testTrimTabs() {
        assertEquals("abc", " \t   abc     \t ".trim())
    }

    @Test
    fun testTrimNewLines() {
        assertEquals("abc", "\t \n  abc \t \n ".trim())
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

    @Test
    fun testNestedEmptyObject() {
        val result = parseJsonObject("{\"foo\": {}}")
        assertEquals(1, result.size)
        assertEquals(0, (result["foo"] as Map<*, *>).size)
    }

    @Test
    fun testNestedObject() {
        val result = parseJsonObject("{\"foo\": {\"bar\": \"42\"}}")
        assertEquals(1, result.size)
        val nestedResult = result["foo"] as Map<*, *>
        assertEquals(1, nestedResult.size)
        assertEquals("42", nestedResult["bar"])
    }

    @Test
    fun testFindObjectEnd() {
        val result = findObjectEnd("foo {\"hello\": \"world\"} bar", 4)
        assertEquals(21, result)
    }

    @Test
    fun testFindEmptyObjectEnd() {
        val result = findObjectEnd("foo {} bar", 4)
        assertEquals(5, result)
    }

    @Test
    fun testFindObjectEndEscapedStrings() {
        val result = findObjectEnd("foo {\"hello\": \"}world}\"} bar", 4)
        assertEquals(23, result)
    }

    @Test
    fun testFindObjectEndInvalidStringLength() {
        assertFailsWith<AssertionError>("Start index >= string length") {
            findObjectEnd("foo {} bar", 20)
        }
    }

    @Test
    fun testFindObjectEndNoMatchingBrace() {
        assertFailsWith<JsonParseError>("No matching close brace found for index 4 in foo { bar") {
            findObjectEnd("foo { bar", 4)
        }
    }

    @Test
    fun testParseJsonArray() {
        val result = parseJsonArray("[\"foo\", \"bar\"]")
        assertEquals(2, result.size)
        assertEquals("foo", result[0])
        assertEquals("bar", result[1])
    }

    @Test
    fun testParseEmptyJsonArray() {
        val result = parseJsonArray("[]")
        assertEquals(0, result.size)
    }

    @Test
    fun testParseNestedArrays() {
        val result = parseJsonArray("[\"foo\", [\"hello\"], \"bar\"]")
        assertEquals(3, result.size)
        assertEquals("foo", result[0])
        assertEquals("bar", result[2])

        val nestedArray = result[1] as List<*>
        assertEquals(1, nestedArray.size)
        assertEquals("hello", nestedArray[0])
    }

    @Test
    fun testParseArrayEmptyString() {
        assertFailsWith<JsonParseError>("Empty array string") {
            parseJsonArray("")
        }
    }

    @Test
    fun testParseNotAnArray() {
        assertFailsWith<JsonParseError>("Noa a json array") {
            parseJsonArray("{}")
        }
    }

    @Test
    fun testParseArrayWithoutComma() {
        assertFailsWith<JsonParseError>("Expected comma after json array element") {
            parseJsonArray("[\"foo\" \"bar\"]")
        }
    }

    @Test
    fun testParseJsonObjectWithNestedArray() {
        val result = parseJsonObject("{\"foo\": \"bar\", \"arr\": [\"hello\", \"world\"]}")
        assertEquals(2, result.size)
        assertEquals("bar", result["foo"])

        val nestedArray = result["arr"] as List<*>
        assertEquals(2, nestedArray.size)
        assertEquals("hello", nestedArray[0])
        assertEquals("world", nestedArray[1])
    }

    @Test
    fun testFindArrayEnd() {
        val result = findArrayEnd("foo [\"hello\", \"world\"] bar", 4)
        assertEquals(21, result)
    }

    @Test
    fun testFindEmptyArrayEnd() {
        val result = findArrayEnd("foo [] bar", 4)
        assertEquals(5, result)
    }

    @Test
    fun testFindArrayEndEscapedStrings() {
        val result = findArrayEnd("foo [\"hello\", \"]world]\"] bar", 4)
        assertEquals(23, result)
    }

    @Test
    fun testFindArrayEndInvalidStringLength() {
        assertFailsWith<AssertionError>("Start index >= string length") {
            findArrayEnd("foo [] bar", 20)
        }
    }

    @Test
    fun testFindArrayEndNoMatchingBrace() {
        assertFailsWith<JsonParseError>("No matching close bracket found for index 4 in foo [ bar") {
            findArrayEnd("foo [ bar", 4)
        }
    }

    @Test
    fun testNumberArray() {
        val result = parseJsonArray("[1, 21, 3, 4.2, -5]")
        assertEquals(5, result.size)
        assertEquals(1.0, result[0])
        assertEquals(21.0, result[1])
        assertEquals(3.0, result[2])
        assertEquals(4.2, result[3])
        assertEquals(-5.0, result[4])
    }

    @Test
    fun testNumberObject() {
        val result = parseJsonObject("{\"foo\": 4.2, \"bar\": 0}")
        assertEquals(2, result.size)
        assertEquals(4.2, result["foo"])
        assertEquals(0.0, result["bar"])
    }

    @Test
    fun testFindNumericEnd() {
        val result = findNumericEnd("abc 123456 def", 4)
        assertEquals(9, result)
    }

    @Test
    fun testFindNumericEndSingleDigit() {
        val result = findNumericEnd("abc 1 def", 4)
        assertEquals(4, result)
    }

    @Test
    fun testFindNumericEndWithPoint() {
        val result = findNumericEnd("abc 123.456 def", 4)
        assertEquals(10, result)
    }

    @Test
    fun testFindNumericEndOutOfBounds() {
        assertFailsWith<AssertionError>("Start index >= string length") {
            findNumericEnd("abc 123 def", 20)
        }
    }

    @Test
    fun testFindNumericEndMoreThanOnePoint() {
        assertFailsWith<NumberFormatException>("Invalid number format: more than one point: abc 1.2.3 def") {
            findNumericEnd("abc 1.2.3 def", 4)
        }
    }

    @Test
    fun testFindNumericEndPositive() {
        val result = findNumericEnd("abc +123 def", 4)
        assertEquals(7, result)
    }

    @Test
    fun testFindNumericEndNegative() {
        val result = findNumericEnd("abc -123 def", 4)
        assertEquals(7, result)
    }

    @Test
    fun testFindNumericEndUnexpectedSign() {
        assertFailsWith<NumberFormatException>("Unexpected number sign: abc +1+0 def") {
            findNumericEnd("abc +1+0 def", 4)
        }
    }
}
