package org.tutorial.graphql.parser

import org.tutorial.graphql.GraphException
import org.tutorial.graphql.Location
import org.tutorial.graphql.lexer.Lexer
import org.tutorial.graphql.lexer.Token
import org.tutorial.graphql.lexer.TokenKind

internal class TokenStream(source: String) {
    private val tokens = Lexer(source).tokens()

    private var index = 0

    internal fun peek(): Token = tokens.getOrElse(index) { tokens.last() }
    
    internal fun advance(): Token = tokens[index++]

    internal fun check(kind: TokenKind, text: String): Boolean =
        peek().kind == kind && peek().text == text

    internal fun match(kind: TokenKind, text: String): Boolean =
        if (check(kind, text)) {
            index++
            true
        } else {
            false
        }

    internal fun expect(kind: TokenKind, text: String) {
        if (!match(kind, text)) {
            fail("Expected '$text', found '${peek().text}'")
        }
    }

    internal fun done(): Boolean = peek().kind == TokenKind.EOF

    internal fun fail(message: String): Nothing = failAt(peek().location, message)

    internal fun failAt(location: Location, message: String): Nothing =
        throw GraphException(message, location)

    internal fun name(): String {
        if (peek().kind != TokenKind.NAME) {
            fail("Expected a name")
        }

        return advance().text
    }

    internal fun string(): String {
        if (peek().kind != TokenKind.STRING) {
            fail("Expected a string")
        }

        return advance().text
    }
}
