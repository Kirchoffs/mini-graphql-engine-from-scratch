package org.tutorial.graphql.lexer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.tutorial.graphql.GraphException
import org.tutorial.graphql.Location

class LexerTest {
    private fun lexer(source: String): List<Token> = Lexer(source).tokens()

    private fun kindsAndTexts(source: String): List<Pair<TokenKind, String>> =
        lexer(source).map { it.kind to it.text }

    @Test fun `empty input yields only EOF`() {
        assertEquals(
            listOf(TokenKind.EOF to ""),
            kindsAndTexts("")
        )
    }

    @Test fun `whitespace and commas are ignored`() {
        assertEquals(
            listOf(TokenKind.NAME to "query", TokenKind.EOF to ""),
            kindsAndTexts(" ,\t\r\n,  query  ,\t\r\n, ")
        )
    }

    @Test fun `BOM is ignored`() {
        assertEquals(
            listOf(TokenKind.NAME to "query", TokenKind.EOF to ""),
            kindsAndTexts("\uFEFFquery\uFEFF")
        )
    }

    @Test fun `line comments are skipped`() {
        assertEquals(
            listOf(TokenKind.NAME to "a", TokenKind.NAME to "b", TokenKind.EOF to ""),
            kindsAndTexts("a # comment 1\n b # comment 2\n")
        )
    }

    @Test fun `names and keywords`() {
        assertEquals(
            listOf(
                TokenKind.NAME to "query",
                TokenKind.NAME to "field_x",
                TokenKind.NAME to "A",
                TokenKind.EOF to ""
            ),
            kindsAndTexts("query field_x A")
        )
    }

    @Test fun `integers`() {
        assertEquals(
            listOf(
                TokenKind.INT to "0",
                TokenKind.INT to "42",
                TokenKind.INT to "-1",
                TokenKind.EOF to ""
            ),
            kindsAndTexts("0 42 -1")
        )
    }

    @Test fun `floats`() {
        assertEquals(
            listOf(
                TokenKind.FLOAT to "3.1414926",
                TokenKind.FLOAT to "-273.15",
                TokenKind.FLOAT to "6.626e-34",
                TokenKind.FLOAT to "6.674E-11",
                TokenKind.EOF to ""
            ),
            kindsAndTexts("3.1414926 -273.15 6.626e-34 6.674E-11")
        )
    }

    @Test fun `leading zero is rejected`() {
        val ex = assertThrows<GraphException> { lexer("007") }
        assertEquals(listOf(Location(1, 1)), ex.error().locations)
    }

    @Test fun `invalid number suffix is rejected`() {
        assertThrows<GraphException> { lexer("123abc") }
    }

    @Test fun `plain string with escapes`() {
        assertEquals(
            listOf(TokenKind.STRING to "a\"b\"\\c\r\n", TokenKind.EOF to ""),
            kindsAndTexts("\"a\\\"b\\\"\\\\c\\r\\n\"")
        )
    }

    @Test fun `unicode escape`() {
        assertEquals(
            listOf(TokenKind.STRING to "\u4E16\u754C", TokenKind.EOF to ""),
            kindsAndTexts("\"\\u4E16\\u754C\"")
        )
    }

    @Test fun `invalid unicode escape is rejected`() {
        assertThrows<GraphException> { lexer("\"\\uWXYZ\"") }
    }

    @Test fun `unterminated string is rejected`() {
        assertThrows<GraphException> { lexer("\"abc") }
    }

    @Test fun `control character in string is rejected`() {
        assertThrows<GraphException> { lexer("\"a\nb\"") }
    }

    @Test fun `block string is normalized`() {
        val source = "\"\"\"\n    hello\n      world\n    \"\"\""
        assertEquals(
            listOf(TokenKind.STRING to "hello\n  world", TokenKind.EOF to ""),
            kindsAndTexts(source)
        )
    }

    @Test fun `block string supports escaped triple quote`() {
        val source = "\"\"\"a \\\"\"\" b\"\"\""
        assertEquals(
            listOf(TokenKind.STRING to "a \"\"\" b", TokenKind.EOF to ""),
            kindsAndTexts(source)
        )
    }

    @Test fun `unterminated block string is rejected`() {
        assertThrows<GraphException> { lexer("\"\"\"abc") }
    }

    @Test fun `punctuators`() {
        assertEquals(
            listOf(
                TokenKind.PUNCT to "!",
                TokenKind.PUNCT to "$",
                TokenKind.PUNCT to "&",
                TokenKind.PUNCT to "(",
                TokenKind.PUNCT to ")",
                TokenKind.PUNCT to ":",
                TokenKind.PUNCT to "=",
                TokenKind.PUNCT to "@",
                TokenKind.PUNCT to "[",
                TokenKind.PUNCT to "]",
                TokenKind.PUNCT to "{",
                TokenKind.PUNCT to "|",
                TokenKind.PUNCT to "}",
                TokenKind.EOF to ""
            ),
            kindsAndTexts("!$&():=@[]{|}")
        )
    }

    @Test fun `spread operator`() {
        assertEquals(
            listOf(TokenKind.PUNCT to "...", TokenKind.EOF to ""),
            kindsAndTexts("...")
        )
    }

    @Test fun `tokens carry source locations`() {
        val tokens = lexer("query {\n  id\n}")
        assertEquals(Location(1, 1), tokens[0].location)   // query
        assertEquals(Location(1, 7), tokens[1].location)   // {
        assertEquals(Location(2, 3), tokens[2].location)   // id
        assertEquals(Location(3, 1), tokens[3].location)   // }
        assertEquals(Location(3, 2), tokens[4].location)   // EOF
    }

    @Test fun `CRLF line endings advance line once`() {
        val tokens = lexer("a\r\nb")
        assertEquals(Location(1, 1), tokens[0].location)   // a
        assertEquals(Location(2, 1), tokens[1].location)   // b
    }

    @Test fun `unexpected character is rejected`() {
        val ex = assertThrows<GraphException> { lexer("?") }
        assertEquals(listOf(Location(1, 1)), ex.error().locations)
    }
}
