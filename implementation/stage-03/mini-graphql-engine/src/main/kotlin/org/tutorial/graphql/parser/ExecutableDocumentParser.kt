package org.tutorial.graphql.parser

import org.tutorial.graphql.ast.ExecutableDocument
import org.tutorial.graphql.ast.Field
import org.tutorial.graphql.ast.Fragment
import org.tutorial.graphql.ast.InlineFragment
import org.tutorial.graphql.ast.Operation
import org.tutorial.graphql.ast.Selection
import org.tutorial.graphql.ast.Spread
import org.tutorial.graphql.ast.VariableDefinition
import org.tutorial.graphql.lexer.TokenKind

class ExecutableDocumentParser(source: String) {
    private val tokens = TokenStream(source)

    fun document(): ExecutableDocument {
        val operations = mutableListOf<Operation>()
        val operationNames = mutableSetOf<String>()
        val fragments = linkedMapOf<String, Fragment>()

        while (!tokens.done()) {
            if (tokens.check(TokenKind.PUNCT, "{")) { // anonymous query
                operations += Operation("query", null, emptyList(), selections())
            } else {
                when (val kind = tokens.name()) {
                    "query", "mutation", "subscription" -> {
                        val nameLocation = tokens.peek().location
                        val operationName = if (tokens.peek().kind == TokenKind.NAME) {
                            val name = tokens.name()
                            if (!operationNames.add(name)) {
                                tokens.failAt(nameLocation, "Duplicate operation name '$name'")
                            }
                            name
                        } else {
                            null
                        }

                        val variables =
                            if (tokens.match(TokenKind.PUNCT, "(")) {
                                buildList {
                                    if (tokens.check(TokenKind.PUNCT, ")")) {
                                        tokens.fail("Variables cannot be empty")
                                    }

                                    val variableNames = mutableSetOf<String>()
                                    while (!tokens.match(TokenKind.PUNCT, ")")) {
                                        tokens.expect(TokenKind.PUNCT, "$")
                                        val variableLocation = tokens.peek().location
                                        val variableName = tokens.name()
                                        if (!variableNames.add(variableName)) {
                                            tokens.failAt(
                                                variableLocation,
                                                "Duplicate variable '$variableName'"
                                            )
                                        }
                                        tokens.expect(TokenKind.PUNCT, ":")
                                        val type = tokens.typeRef()
                                        add(
                                            VariableDefinition(
                                                variableName,
                                                type,
                                                if (tokens.match(TokenKind.PUNCT, "=")) {
                                                    tokens.value(true)
                                                } else {
                                                    null
                                                }
                                            )
                                        )
                                    }
                                }
                            } else {
                                emptyList()
                            }

                        val operationDirectives = tokens.directiveUsages()
                        val operationSelections = selections()

                        operations += Operation(
                            kind,
                            operationName,
                            variables,
                            operationSelections,
                            operationDirectives
                        )
                    }
                    "fragment" -> {
                        val nameLocation = tokens.peek().location
                        val fragmentName = tokens.name()
                        if (fragmentName == "on" || fragments.containsKey(fragmentName)) {
                            tokens.failAt(
                                nameLocation,
                                "Invalid or duplicate fragment '$fragmentName'"
                            )
                        }

                        tokens.expect(TokenKind.NAME, "on")

                        val type = tokens.name()
                        val fragmentDirectives = tokens.directiveUsages()
                        val fragmentSelections = selections()

                        fragments[fragmentName] =
                            Fragment(fragmentName, type, fragmentSelections, fragmentDirectives)
                    }
                    else -> tokens.fail("Unknown definition '$kind'")
                }
            }
        }

        if (operations.isEmpty()) {
            tokens.fail("Expected an operation")
        }

        if (operations.size > 1 && operations.any { it.name == null }) {
            tokens.fail("Anonymous operation must be alone")
        }

        return ExecutableDocument(operations, fragments)
    }

    private fun selections(): List<Selection> {
        tokens.expect(TokenKind.PUNCT, "{")
        if (tokens.check(TokenKind.PUNCT, "}")) {
            tokens.fail("Selection sets cannot be empty")
        }

        return buildList {
            while (!tokens.match(TokenKind.PUNCT, "}")) {
                if (tokens.match(TokenKind.PUNCT, "...")) {
                    if (tokens.match(TokenKind.NAME, "on")) {
                        val type = tokens.name()
                        val directives = tokens.directiveUsages()
                        add(InlineFragment(type, selections(), directives))
                    } else if (
                        tokens.check(TokenKind.PUNCT, "@") ||
                        tokens.check(TokenKind.PUNCT, "{")
                    ) {
                        val directives = tokens.directiveUsages()
                        add(InlineFragment(null, selections(), directives))
                    } else {
                        add(Spread(tokens.name(), tokens.directiveUsages()))
                    }
                } else {
                    val location = tokens.peek().location
                    val first = tokens.name()
                    val alias = if (tokens.match(TokenKind.PUNCT, ":")) first else null
                    val field = if (alias != null) tokens.name() else first
                    val args = tokens.argumentValues()
                    val directives = tokens.directiveUsages()
                    val children = if (tokens.check(TokenKind.PUNCT, "{")) {
                        selections()
                    } else {
                        emptyList()
                    }

                    add(Field(field, alias, args, directives, children, location))
                }
            }
        }
    }
}
