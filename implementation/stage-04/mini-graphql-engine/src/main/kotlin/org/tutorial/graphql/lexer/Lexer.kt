package org.tutorial.graphql.lexer

import org.tutorial.graphql.GraphException
import org.tutorial.graphql.Location

enum class TokenKind {
    NAME, 
    INT, 
    FLOAT, 
    STRING, 
    PUNCT, 
    EOF
}

data class Token(
    val kind: TokenKind, 
    val text: String, 
    val location: Location
)

class Lexer(private val source: String) {
    private var index = 0
    private var line = 1
    private var column = 1
    
    private fun peek(offset: Int = 0): Char? = 
        source.getOrNull(index + offset)
    
    private fun consume(): Char = source[index++].also {
        if (it == '\r') {
            line++; 
            column = 1
        } else if (it == '\n') {
            if (index < 2 || source[index - 2] != '\r') {
                line++;
            } 
            column = 1
        } else {
            column++
        }
    }

    
    private fun nextToken(): Token {
        skipIgnored()

        val start = Location(line, column)
        val ch = peek() ?: return Token(TokenKind.EOF, "", start)

        return when {
            ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z' ->
                tokenizeName(start)

            ch == '-' || ch in '0'..'9' ->
                tokenizeNumber(start)

            ch == '"' ->
                tokenizeString(start)

            ch == '.' && peek(1) == '.' && peek(2) == '.' ->
                tokenizeSpread(start)

            ch in "!$&():=@[]{|}" ->
                tokenizePunctuation(start)

            else ->
                fail("Unexpected character '$ch'", start)
        }
    }

    private fun skipIgnored() {
        while (true) {
            // Skip whitespace
            while (peek() in listOf(' ', '\t', '\r', '\n', ',', '\uFEFF')) {
                consume()
            }

            // If the next character is not a comment marker, stop skipping
            if (peek() != '#') {
                break
            }

            // If it is a comment, skip the entire comment line
            while (peek() != null && peek() !in listOf('\r', '\n')) {
                consume()
            }
        }
    }

    private fun tokenizeName(start: Location): Token {
        val text = buildString {
            while (peek()?.let {
                it == '_' ||
                it in 'a'..'z' ||
                it in 'A'..'Z' ||
                it in '0'..'9'
            } == true) {
                append(consume())
            }
        }

        return Token(TokenKind.NAME, text, start)
    }

    private fun tokenizeNumber(start: Location): Token {
        val begin = index

        if (peek() == '-') {
            consume()
        }

        if (peek() == '0') {
            consume()

            if (peek() in '0'..'9') {
                fail("Leading zero is not allowed", start)
            }
        } else {
            if (peek() !in '1'..'9') {
                fail("Expected a digit", start)
            }

            while (peek() in '0'..'9') {
                consume()
            }
        }

        var kind = TokenKind.INT

        if (peek() == '.') {
            kind = TokenKind.FLOAT
            consume()

            if (peek() !in '0'..'9') {
                fail("Expected fraction digits", start)
            }

            while (peek() in '0'..'9') {
                consume()
            }
        }

        if (peek() in listOf('e', 'E')) {
            kind = TokenKind.FLOAT
            consume()

            if (peek() in listOf('+', '-')) {
                consume()
            }

            if (peek() !in '0'..'9') {
                fail("Expected exponent digits", start)
            }

            while (peek() in '0'..'9') {
                consume()
            }
        }

        if (peek()?.let {
            it.isLetterOrDigit() || it == '_' || it == '.'
        } == true) {
            fail("Invalid number suffix", start)
        }

        return Token(kind, source.substring(begin, index), start)
    }

    private fun tokenizeString(start: Location): Token {
        consume() // Consume opening "

        if (peek() == '"' && peek(1) == '"') {
            consume()
            consume()
            return tokenizeBlockString(start)
        }

        val text = buildString {
            while (peek() != '"') {
                val ch = peek() ?: fail("Unterminated string", start)

                if (ch < ' ') {
                    fail("Control character in string", start)
                }

                consume()

                if (ch != '\\') {
                    append(ch)
                } else {
                    val escape = peek()
                        ?: fail("Unterminated escape", start)
                    consume()

                    append(when (escape) {
                        '"', '\\', '/' -> escape
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            val hex = buildString {
                                repeat(4) {
                                    append(
                                        peek()?.also { consume() }
                                            ?: fail("Incomplete Unicode escape", start)
                                    )
                                }
                            }

                            (hex.toIntOrNull(16)
                                ?: fail("Invalid Unicode escape", start)).toChar()
                        }
                        else -> fail("Invalid string escape", start)
                    })
                }
            }
        }

        consume() // Consume closing "

        return Token(TokenKind.STRING, text, start)
    }

    private fun tokenizeBlockString(start: Location): Token {
        // Opening triple quotes have already been consumed
        val raw = buildString {
            while (!(peek() == '"' && peek(1) == '"' && peek(2) == '"')) {
                if (peek() == null) {
                    fail("Unterminated block string", start)
                }

                if (peek() == '\\' &&
                    peek(1) == '"' &&
                    peek(2) == '"' &&
                    peek(3) == '"'
                ) {
                    consume()

                    repeat(3) {
                        append(consume())
                    }
                } else {
                    append(consume())
                }
            }
        }

        // Consume closing triple quotes
        repeat(3) {
            consume()
        }

        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')

        val indent = lines
            .drop(1)
            .filter { it.isNotBlank() }
            .minOfOrNull {
                it.takeWhile { c -> c == ' ' || c == '\t' }.length
            } ?: 0

        val normalized = lines
            .mapIndexed { i, s ->
                if (i == 0) s else s.drop(indent)
            }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")

        return Token(TokenKind.STRING, normalized, start)
    }

    private fun tokenizeSpread(start: Location): Token {
        repeat(3) {
            consume()
        }

        return Token(TokenKind.PUNCT, "...", start)
    }

    private fun tokenizePunctuation(start: Location): Token {
        val ch = consume()
        return Token(TokenKind.PUNCT, ch.toString(), start)
    }
    
    private fun fail(message: String, start: Location): Nothing =
        throw GraphException(message, start)
    
    fun tokens(): List<Token> = buildList {
        while (true) { 
            val token = nextToken()
            add(token)

            if (token.kind == TokenKind.EOF) {
                break
            }
        }
    }
}
