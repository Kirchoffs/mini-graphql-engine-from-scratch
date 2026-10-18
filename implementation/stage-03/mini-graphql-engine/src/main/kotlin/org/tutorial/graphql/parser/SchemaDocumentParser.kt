package org.tutorial.graphql.parser

import org.tutorial.graphql.ast.DirectiveDefinition
import org.tutorial.graphql.ast.EnumTypeDefinition
import org.tutorial.graphql.ast.EnumTypeExtension
import org.tutorial.graphql.ast.EnumValueDefinition
import org.tutorial.graphql.ast.FieldDefinition
import org.tutorial.graphql.ast.InputObjectTypeDefinition
import org.tutorial.graphql.ast.InputObjectTypeExtension
import org.tutorial.graphql.ast.InputValueDefinition
import org.tutorial.graphql.ast.InterfaceTypeDefinition
import org.tutorial.graphql.ast.InterfaceTypeExtension
import org.tutorial.graphql.ast.ObjectTypeDefinition
import org.tutorial.graphql.ast.ObjectTypeExtension
import org.tutorial.graphql.ast.RootOperationTypeDefinition
import org.tutorial.graphql.ast.ScalarTypeDefinition
import org.tutorial.graphql.ast.ScalarTypeExtension
import org.tutorial.graphql.ast.SchemaDefinition
import org.tutorial.graphql.ast.SchemaDocument
import org.tutorial.graphql.ast.SchemaExtension
import org.tutorial.graphql.ast.SchemaRootDefinition
import org.tutorial.graphql.ast.UnionTypeDefinition
import org.tutorial.graphql.ast.UnionTypeExtension
import org.tutorial.graphql.lexer.TokenKind

class SchemaDocumentParser(source: String) {
    private val tokens = TokenStream(source)

    fun schema(): SchemaDocument {
        val definitions = mutableListOf<SchemaDefinition>()

        while (!tokens.done()) {
            definitions += definition()
        }

        if (definitions.isEmpty()) {
            tokens.fail("Expected a schema definition")
        }

        return SchemaDocument(definitions)
    }

    private fun definition(): SchemaDefinition {
        val description = description()
        val keywordLocation = tokens.peek().location

        return when (val kind = tokens.name()) {
            "schema" -> schemaDefinition(description)
            "scalar" -> scalarTypeDefinition(description)
            "type" -> objectTypeDefinition(description)
            "interface" -> interfaceTypeDefinition(description)
            "union" -> unionTypeDefinition(description)
            "enum" -> enumTypeDefinition(description)
            "input" -> inputObjectTypeDefinition(description)
            "directive" -> directiveDefinition(description)
            "extend" -> {
                if (description != null) {
                    tokens.failAt(keywordLocation, "Type extensions cannot have descriptions")
                }
                extension()
            }
            else -> tokens.failAt(keywordLocation, "Unknown schema definition '$kind'")
        }
    }

    private fun description(): String? =
        if (tokens.peek().kind == TokenKind.STRING) tokens.string() else null

    // schema {
    //   query: Query
    //   mutation: Mutation
    // }
    private fun schemaDefinition(description: String?): SchemaDefinition {
        val directives = tokens.directiveUsages(constant = true)
        val operations = operationTypes()
        return SchemaRootDefinition(description, directives, operations)
    }

    private fun operationTypes(): List<RootOperationTypeDefinition> {
        tokens.expect(TokenKind.PUNCT, "{")
        if (tokens.check(TokenKind.PUNCT, "}")) {
            tokens.fail("Schema definition cannot be empty")
        }

        val operations = mutableListOf<RootOperationTypeDefinition>()
        val seen = mutableSetOf<String>()

        while (!tokens.match(TokenKind.PUNCT, "}")) {
            val location = tokens.peek().location
            val operation = tokens.name()
            if (operation !in OPERATION_TYPES) {
                tokens.failAt(location, "Unknown operation type '$operation'")
            }
            if (!seen.add(operation)) {
                tokens.failAt(location, "Duplicate operation type '$operation'")
            }

            tokens.expect(TokenKind.PUNCT, ":")
            operations += RootOperationTypeDefinition(operation, tokens.name())
        }

        return operations
    }

    // scalar DateTime
    private fun scalarTypeDefinition(description: String?): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        return ScalarTypeDefinition(description, name, directives)
    }

    // type User {
    //   id: ID!,
    //   name: String
    // }
    private fun objectTypeDefinition(description: String?): SchemaDefinition {
        val name = tokens.name()
        val interfaces = implementsInterfaces()
        val directives = tokens.directiveUsages(constant = true)
        val fields = if (tokens.check(TokenKind.PUNCT, "{")) fieldsDefinition() else emptyList()
        return ObjectTypeDefinition(description, name, interfaces, directives, fields)
    }

    // interface Node {
    //   id: ID!
    // }
    private fun interfaceTypeDefinition(description: String?): SchemaDefinition {
        val name = tokens.name()
        val interfaces = implementsInterfaces()
        val directives = tokens.directiveUsages(constant = true)
        val fields = if (tokens.check(TokenKind.PUNCT, "{")) fieldsDefinition() else emptyList()
        return InterfaceTypeDefinition(description, name, interfaces, directives, fields)
    }

    private fun implementsInterfaces(): List<String> {
        if (!tokens.match(TokenKind.NAME, "implements")) {
            return emptyList()
        }

        tokens.match(TokenKind.PUNCT, "&")
        val interfaces = mutableListOf(tokens.name())
        while (tokens.match(TokenKind.PUNCT, "&")) {
            interfaces += tokens.name()
        }

        return interfaces
    }

    // union SearchResult = User | Post
    private fun unionTypeDefinition(description: String?): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        val members = if (tokens.match(TokenKind.PUNCT, "=")) unionMembers() else emptyList()
        return UnionTypeDefinition(description, name, directives, members)
    }

    private fun unionMembers(): List<String> {
        tokens.match(TokenKind.PUNCT, "|")
        val members = mutableListOf(tokens.name())
        while (tokens.match(TokenKind.PUNCT, "|")) {
            members += tokens.name()
        }

        return members
    }

    // enum Role {
    //   ADMIN
    //   USER
    // }
    private fun enumTypeDefinition(description: String?): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        val values = if (tokens.check(TokenKind.PUNCT, "{")) enumValuesDefinition() else emptyList()
        return EnumTypeDefinition(description, name, directives, values)
    }

    private fun enumValuesDefinition(): List<EnumValueDefinition> {
        tokens.expect(TokenKind.PUNCT, "{")
        if (tokens.check(TokenKind.PUNCT, "}")) {
            tokens.fail("Enum values cannot be empty")
        }

        return buildList {
            while (!tokens.match(TokenKind.PUNCT, "}")) {
                val description = description()
                val location = tokens.peek().location
                val name = tokens.name()
                if (name in RESERVED_ENUM_VALUES) {
                    tokens.failAt(location, "Invalid enum value '$name'")
                }

                add(EnumValueDefinition(description, name, tokens.directiveUsages(constant = true)))
            }
        }
    }

    // input UserFilter {
    //   name: String,
    //   role: Role
    // }
    private fun inputObjectTypeDefinition(description: String?): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        val fields = if (tokens.check(TokenKind.PUNCT, "{")) inputFieldsDefinition() else emptyList()
        return InputObjectTypeDefinition(description, name, directives, fields)
    }

    private fun inputFieldsDefinition(): List<InputValueDefinition> {
        tokens.expect(TokenKind.PUNCT, "{")
        if (tokens.check(TokenKind.PUNCT, "}")) {
            tokens.fail("Input fields cannot be empty")
        }

        return buildList {
            while (!tokens.match(TokenKind.PUNCT, "}")) {
                add(inputValueDefinition())
            }
        }
    }

    // directive @auth(role: Role = ADMIN) on FIELD_DEFINITION | OBJECT
    private fun directiveDefinition(description: String?): SchemaDefinition {
        tokens.expect(TokenKind.PUNCT, "@")
        val name = tokens.name()
        val arguments = argumentsDefinition()
        val repeatable = tokens.match(TokenKind.NAME, "repeatable")
        tokens.expect(TokenKind.NAME, "on")
        val locations = directiveLocations()
        return DirectiveDefinition(description, name, arguments, repeatable, locations)
    }

    private fun directiveLocations(): List<String> {
        tokens.match(TokenKind.PUNCT, "|")
        val locations = mutableListOf(tokens.name())
        while (tokens.match(TokenKind.PUNCT, "|")) {
            locations += tokens.name()
        }

        return locations
    }

    private fun fieldsDefinition(): List<FieldDefinition> {
        tokens.expect(TokenKind.PUNCT, "{")
        if (tokens.check(TokenKind.PUNCT, "}")) {
            tokens.fail("Fields cannot be empty")
        }

        return buildList {
            while (!tokens.match(TokenKind.PUNCT, "}")) {
                add(fieldDefinition())
            }
        }
    }

    private fun fieldDefinition(): FieldDefinition {
        val description = description()
        val name = tokens.name()
        val arguments = argumentsDefinition()
        tokens.expect(TokenKind.PUNCT, ":")
        val type = tokens.typeRef()
        val directives = tokens.directiveUsages(constant = true)
        return FieldDefinition(description, name, arguments, type, directives)
    }

    private fun argumentsDefinition(): List<InputValueDefinition> {
        if (!tokens.match(TokenKind.PUNCT, "(")) {
            return emptyList()
        }

        if (tokens.check(TokenKind.PUNCT, ")")) {
            tokens.fail("Arguments cannot be empty")
        }

        return buildList {
            while (!tokens.match(TokenKind.PUNCT, ")")) {
                add(inputValueDefinition())
            }
        }
    }

    private fun inputValueDefinition(): InputValueDefinition {
        val description = description()
        val name = tokens.name()
        tokens.expect(TokenKind.PUNCT, ":")
        val type = tokens.typeRef()
        val defaultValue = if (tokens.match(TokenKind.PUNCT, "=")) tokens.value(true) else null
        val directives = tokens.directiveUsages(constant = true)
        return InputValueDefinition(description, name, type, defaultValue, directives)
    }

    private fun extension(): SchemaDefinition {
        return when (val kind = tokens.name()) {
            "schema" -> schemaExtension()
            "scalar" -> scalarTypeExtension()
            "type" -> objectTypeExtension()
            "interface" -> interfaceTypeExtension()
            "union" -> unionTypeExtension()
            "enum" -> enumTypeExtension()
            "input" -> inputObjectTypeExtension()
            else -> tokens.fail("Unknown extension '$kind'")
        }
    }

    private fun schemaExtension(): SchemaDefinition {
        val directives = tokens.directiveUsages(constant = true)
        val operations = operationTypes()
        return SchemaExtension(directives, operations)
    }

    private fun scalarTypeExtension(): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        return ScalarTypeExtension(name, directives)
    }

    private fun objectTypeExtension(): SchemaDefinition {
        val name = tokens.name()
        val interfaces = implementsInterfaces()
        val directives = tokens.directiveUsages(constant = true)
        val fields = if (tokens.check(TokenKind.PUNCT, "{")) fieldsDefinition() else emptyList()
        return ObjectTypeExtension(name, interfaces, directives, fields)
    }

    private fun interfaceTypeExtension(): SchemaDefinition {
        val name = tokens.name()
        val interfaces = implementsInterfaces()
        val directives = tokens.directiveUsages(constant = true)
        val fields = if (tokens.check(TokenKind.PUNCT, "{")) fieldsDefinition() else emptyList()
        return InterfaceTypeExtension(name, interfaces, directives, fields)
    }

    private fun unionTypeExtension(): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        val members = if (tokens.match(TokenKind.PUNCT, "=")) unionMembers() else emptyList()
        return UnionTypeExtension(name, directives, members)
    }

    private fun enumTypeExtension(): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        val values = if (tokens.check(TokenKind.PUNCT, "{")) enumValuesDefinition() else emptyList()
        return EnumTypeExtension(name, directives, values)
    }

    private fun inputObjectTypeExtension(): SchemaDefinition {
        val name = tokens.name()
        val directives = tokens.directiveUsages(constant = true)
        val fields = if (tokens.check(TokenKind.PUNCT, "{")) inputFieldsDefinition() else emptyList()
        return InputObjectTypeExtension(name, directives, fields)
    }
}

private val OPERATION_TYPES = setOf("query", "mutation", "subscription")
private val RESERVED_ENUM_VALUES = setOf("true", "false", "null")
