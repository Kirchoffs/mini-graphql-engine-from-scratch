package org.tutorial.graphql.parser

import org.tutorial.graphql.ast.Directive
import org.tutorial.graphql.ast.TypeRef
import org.tutorial.graphql.ast.Value
import org.tutorial.graphql.lexer.TokenKind

internal fun TokenStream.typeRef(): TypeRef {
    val ref = if (match(TokenKind.PUNCT, "[")) {
        TypeRef(element = this.typeRef()).also { expect(TokenKind.PUNCT, "]") }
    } else {
        TypeRef(name = name())
    }

    return ref.copy(nonNull = match(TokenKind.PUNCT, "!"))
}

internal fun TokenStream.value(constant: Boolean = false): Value {
    val stream = this
    val start = peek().location

    return when {
        match(TokenKind.PUNCT, "$") -> {
            if (constant) {
                failAt(start, "Variables are not allowed in constant values")
            }
            Value.Variable(name())
        }
        match(TokenKind.PUNCT, "[") -> {
            Value.ListValue(buildList {
                while (!stream.match(TokenKind.PUNCT, "]")) {
                    add(stream.value(constant))
                }
            })
        }
        match(TokenKind.PUNCT, "{") -> {
            Value.ObjectValue(buildMap {
                while (!stream.match(TokenKind.PUNCT, "}")) {
                    val keyLocation = stream.peek().location
                    val key = stream.name()
                    stream.expect(TokenKind.PUNCT, ":")
                    if (containsKey(key)) {
                        stream.failAt(keyLocation, "Duplicate input field '$key'")
                    }

                    put(key, stream.value(constant))
                }
            })
        }
        peek().kind == TokenKind.STRING -> {
            Value.Literal(string())
        }
        peek().kind == TokenKind.INT -> {
            val token = advance()
            Value.Literal(
                token.text.toIntOrNull()
                    ?: failAt(token.location, "Integer out of range")
            )
        }
        peek().kind == TokenKind.FLOAT -> {
            val token = advance()
            val number = token.text.toDouble()
            if (!number.isFinite()) {
                failAt(token.location, "Float out of range")
            }
            Value.Literal(number)
        }
        else -> {
            when (val name = name()) {
                "true" -> Value.Literal(true)
                "false" -> Value.Literal(false)
                "null" -> Value.Literal(null)
                else -> Value.EnumValue(name)
            }
        }
    }
}

internal fun TokenStream.argumentValues(constant: Boolean = false): Map<String, Value> {
    val stream = this

    return if (!stream.match(TokenKind.PUNCT, "(")) {
        emptyMap()
    } else {
        buildMap {
            if (stream.check(TokenKind.PUNCT, ")")) {
                stream.fail("Arguments cannot be empty")
            }

            while (!stream.match(TokenKind.PUNCT, ")")) {
                val keyLocation = stream.peek().location
                val key = stream.name()
                stream.expect(TokenKind.PUNCT, ":")
                if (containsKey(key)) {
                    stream.failAt(keyLocation, "Duplicate argument '$key'")
                }

                put(key, stream.value(constant))
            }
        }
    }
}

internal fun TokenStream.directiveUsages(constant: Boolean = false): List<Directive> {
    val stream = this

    return buildList {
        while (stream.match(TokenKind.PUNCT, "@")) {
            add(Directive(stream.name(), stream.argumentValues(constant)))
        }
    }
}
