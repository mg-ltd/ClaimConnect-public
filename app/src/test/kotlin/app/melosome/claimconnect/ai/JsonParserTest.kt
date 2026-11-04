package app.melosome.claimconnect.ai

import org.junit.Assert.*
import org.junit.Test
import app.melosome.claimconnect.ai.JsonParser.Value

class JsonParserTest {
    @Test fun readsObjectsArraysAndLiterals() {
        val root = JsonParser.parse("""{"a":[1,2.5,-3],"b":true,"c":false,"d":null,"e":"x"}""") as Value.Obj
        assertEquals(listOf("1", "2.5", "-3"), (root.map["a"] as Value.Arr).items.map { (it as Value.Num).raw })
        assertEquals(Value.True, root.map["b"])
        assertEquals(Value.False, root.map["c"])
        assertEquals(Value.Null, root.map["d"])
        assertEquals("x", (root.map["e"] as Value.Str).text)
    }
    @Test fun readsEscapesAndUnicode() {
        val root = JsonParser.parse("""{"s":"a\"b\\c\nd\u00e9"}""") as Value.Obj
        assertEquals("a\"b\\c\ndé", (root.map["s"] as Value.Str).text)
    }
    @Test fun rejectsMalformedDocuments() {
        listOf("", "{", "[1,", """{"a":}""", "[01]", "tru", "[1] trailing").forEach { bad ->
            try {
                JsonParser.parse(bad)
                fail("Should reject: $bad")
            } catch (_: JsonParser.JsonException) { }
        }
    }
    @Test fun escapeRoundTrips() {
        val text = "quote\" back\\slash\nnewline\ttab\u0001"
        assertEquals(text, (JsonParser.parse("\"" + JsonParser.escape(text) + "\"") as Value.Str).text)
    }
}
