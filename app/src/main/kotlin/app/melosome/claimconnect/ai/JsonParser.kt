package app.melosome.claimconnect.ai

/** Minimal strict JSON reader (objects, arrays, strings, numbers, literals) with no dependencies. */
object JsonParser {
    sealed interface Value {
        data class Obj(val map: Map<String, Value>) : Value
        data class Arr(val items: List<Value>) : Value
        data class Str(val text: String) : Value
        data class Num(val raw: String) : Value
        data object True : Value
        data object False : Value
        data object Null : Value
    }
    class JsonException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): Value {
        val parser = Reader(text)
        val value = parser.value()
        parser.spaces()
        if (!parser.end()) throw JsonException("Trailing characters after JSON value")
        return value
    }

    /** Escapes a string for inclusion in a JSON document. */
    fun escape(text: String): String = buildString(text.length + 2) {
        text.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    private class Reader(val text: String) {
        var pos = 0
        fun end(): Boolean = pos >= text.length
        fun spaces() { while (!end() && text[pos].isWhitespace()) pos++ }
        fun value(): Value {
            spaces()
            if (end()) throw JsonException("Unexpected end of JSON")
            return when (val c = text[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> Value.Str(string())
                't' -> literal("true", Value.True)
                'f' -> literal("false", Value.False)
                'n' -> literal("null", Value.Null)
                '-', in '0'..'9' -> number()
                else -> throw JsonException("Unexpected character '$c'")
            }
        }
        fun obj(): Value.Obj {
            pos++ // {
            val map = linkedMapOf<String, Value>()
            spaces()
            if (!end() && text[pos] == '}') {
                pos++
                return Value.Obj(map)
            }
            while (true) {
                spaces()
                if (end() || text[pos] != '"') throw JsonException("Expected string key")
                val key = string()
                spaces()
                if (end() || text[pos] != ':') throw JsonException("Expected ':'")
                pos++
                map[key] = value()
                spaces()
                if (end()) throw JsonException("Unclosed object")
                if (text[pos] == '}') {
                    pos++
                    return Value.Obj(map)
                }
                if (text[pos] != ',') throw JsonException("Expected ',' or '}'")
                pos++
            }
        }
        fun arr(): Value.Arr {
            pos++ // [
            val items = mutableListOf<Value>()
            spaces()
            if (!end() && text[pos] == ']') {
                pos++
                return Value.Arr(items)
            }
            while (true) {
                items.add(value())
                spaces()
                if (end()) throw JsonException("Unclosed array")
                if (text[pos] == ']') {
                    pos++
                    return Value.Arr(items)
                }
                if (text[pos] != ',') throw JsonException("Expected ',' or ']'")
                pos++
            }
        }
        fun string(): String {
            pos++ // "
            val out = StringBuilder()
            while (true) {
                if (end()) throw JsonException("Unclosed string")
                val c = text[pos++]
                if (c == '"') return out.toString()
                if (c != '\\') {
                    out.append(c)
                    continue
                }
                if (end()) throw JsonException("Unclosed escape")
                when (val e = text[pos++]) {
                    '"', '\\', '/' -> out.append(e)
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (pos + 4 > text.length) throw JsonException("Bad unicode escape")
                        out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                        pos += 4
                    }
                    else -> throw JsonException("Bad escape '\\$e'")
                }
            }
        }
        fun number(): Value.Num {
            val start = pos
            if (!end() && text[pos] == '-') pos++
            if (end() || !text[pos].isDigit()) throw JsonException("Bad number")
            if (text[pos] == '0') {
                pos++
                if (!end() && text[pos].isDigit()) throw JsonException("Bad number")
            } else {
                while (!end() && text[pos].isDigit()) pos++
            }
            if (!end() && text[pos] == '.') {
                pos++
                if (end() || !text[pos].isDigit()) throw JsonException("Bad number")
                while (!end() && text[pos].isDigit()) pos++
            }
            if (!end() && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (!end() && (text[pos] == '+' || text[pos] == '-')) pos++
                if (end() || !text[pos].isDigit()) throw JsonException("Bad number")
                while (!end() && text[pos].isDigit()) pos++
            }
            return Value.Num(text.substring(start, pos))
        }
        fun literal(word: String, value: Value): Value {
            if (!text.startsWith(word, pos)) throw JsonException("Bad literal")
            pos += word.length
            return value
        }
    }
}
