package org.tutorial.graphql.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.tutorial.graphql.GraphException
import org.tutorial.graphql.ast.DirectiveDefinition
import org.tutorial.graphql.ast.EnumTypeDefinition
import org.tutorial.graphql.ast.EnumTypeExtension
import org.tutorial.graphql.ast.InputObjectTypeDefinition
import org.tutorial.graphql.ast.InputObjectTypeExtension
import org.tutorial.graphql.ast.InterfaceTypeDefinition
import org.tutorial.graphql.ast.InterfaceTypeExtension
import org.tutorial.graphql.ast.ObjectTypeDefinition
import org.tutorial.graphql.ast.ObjectTypeExtension
import org.tutorial.graphql.ast.ScalarTypeDefinition
import org.tutorial.graphql.ast.ScalarTypeExtension
import org.tutorial.graphql.ast.SchemaExtension
import org.tutorial.graphql.ast.SchemaRootDefinition
import org.tutorial.graphql.ast.UnionTypeDefinition
import org.tutorial.graphql.ast.UnionTypeExtension
import org.tutorial.graphql.ast.Value

class SchemaDocumentParserTest {
    @Test
    fun `parses schema definitions`() {
        val schema = SchemaDocumentParser(
            """
            "Root schema"
            schema @link(url: "https://unknown.com/schema") {
                query: Query
                mutation: Mutation
            }

            scalar DateTime @specifiedBy(url: "https://unknown.com/date")

            "A node"
            interface Node {
                id: ID!
            }

            type Query implements Node & Timestamped @key(fields: "id") {
                "Find a user"
                user(id: ID! = "1"): User @deprecated(reason: "use node")
                users: [User!]!
            }

            union SearchResult = User | Post

            enum Role {
                ADMIN
                USER @deprecated(reason: "use MEMBER")
            }

            input UserFilter {
                name: String = "x"
                role: Role = ADMIN
            }

            directive @auth(role: Role = ADMIN) repeatable on FIELD_DEFINITION | OBJECT

            extend type User @key(fields: "id") {
                email: String
            }
            """.trimIndent()
        ).schema()

        assertEquals(9, schema.definitions.size)

        val root = schema.definitions.filterIsInstance<SchemaRootDefinition>().single()
        assertEquals("Root schema", root.description)
        assertEquals(listOf("query", "mutation"), root.operations.map { it.operation })
        assertEquals(listOf("Query", "Mutation"), root.operations.map { it.type })
        assertEquals("link", root.directives.single().name)
        assertEquals(
            Value.Literal("https://unknown.com/schema"),
            root.directives.single().arguments.getValue("url")
        )

        val scalar = schema.definitions.filterIsInstance<ScalarTypeDefinition>().single()
        assertEquals("DateTime", scalar.name)
        assertEquals("specifiedBy", scalar.directives.single().name)

        val node = schema.definitions.filterIsInstance<InterfaceTypeDefinition>().single()
        assertEquals("Node", node.name)
        assertEquals("id", node.fields.single().name)
        assertEquals("ID!", node.fields.single().type.toString())

        val query = schema.definitions
            .filterIsInstance<ObjectTypeDefinition>()
            .single { it.name == "Query" }

        assertEquals(listOf("Node", "Timestamped"), query.interfaces)
        assertEquals(listOf("key"), query.directives.map { it.name })

        val userField = query.fields.single { it.name == "user" }
        assertEquals("Find a user", userField.description)
        assertEquals("User", userField.type.toString())
        assertEquals(Value.Literal("1"), userField.arguments.single().defaultValue)
        assertEquals("deprecated", userField.directives.single().name)

        val usersField = query.fields.single { it.name == "users" }
        assertEquals("[User!]!", usersField.type.toString())

        val union = schema.definitions.filterIsInstance<UnionTypeDefinition>().single()
        assertEquals("SearchResult", union.name)
        assertEquals(listOf("User", "Post"), union.members)

        val role = schema.definitions.filterIsInstance<EnumTypeDefinition>().single()
        assertEquals("Role", role.name)
        assertEquals(listOf("ADMIN", "USER"), role.values.map { it.name })
        assertEquals("deprecated", role.values.last().directives.single().name)

        val filter = schema.definitions.filterIsInstance<InputObjectTypeDefinition>().single()
        assertEquals(Value.Literal("x"), filter.fields.first { it.name == "name" }.defaultValue)
        assertEquals(
            Value.EnumValue("ADMIN"),
            filter.fields.first { it.name == "role" }.defaultValue
        )

        val auth = schema.definitions.filterIsInstance<DirectiveDefinition>().single()
        assertEquals("auth", auth.name)
        assertTrue(auth.repeatable)
        assertEquals(listOf("FIELD_DEFINITION", "OBJECT"), auth.locations)
        assertEquals(Value.EnumValue("ADMIN"), auth.arguments.single().defaultValue)

        val extension = schema.definitions.filterIsInstance<ObjectTypeExtension>().single()
        assertEquals("User", extension.name)
        assertEquals(listOf("key"), extension.directives.map { it.name })
        assertEquals("email", extension.fields.single().name)
    }

    @Test
    fun `parses optional type bodies and extensions`() {
        val schema = SchemaDocumentParser(
            """
            type Empty
            interface Marker
            union Anything
            enum Status
            input Filter
            scalar JSON

            extend schema { subscription: Subscription }
            extend scalar JSON @specifiedBy(url: "https://example.com/json")
            extend type Empty @key(fields: "id")
            extend interface Marker @key(fields: "id")
            extend union Anything = User | Post
            extend enum Status { ACTIVE }
            extend input Filter { q: String }
            """.trimIndent()
        ).schema()

        assertEquals(13, schema.definitions.size)

        assertTrue(schema.definitions.filterIsInstance<ObjectTypeDefinition>().single().fields.isEmpty())
        assertTrue(schema.definitions.filterIsInstance<InterfaceTypeDefinition>().single().fields.isEmpty())
        assertTrue(schema.definitions.filterIsInstance<UnionTypeDefinition>().single().members.isEmpty())
        assertTrue(schema.definitions.filterIsInstance<EnumTypeDefinition>().single().values.isEmpty())
        assertTrue(schema.definitions.filterIsInstance<InputObjectTypeDefinition>().single().fields.isEmpty())

        val schemaExtension = schema.definitions.filterIsInstance<SchemaExtension>().single()
        assertEquals(listOf("subscription"), schemaExtension.operations.map { it.operation })

        assertEquals(1, schema.definitions.filterIsInstance<ScalarTypeExtension>().size)
        assertEquals(1, schema.definitions.filterIsInstance<ObjectTypeExtension>().size)
        assertEquals(1, schema.definitions.filterIsInstance<InterfaceTypeExtension>().size)
        assertEquals(1, schema.definitions.filterIsInstance<UnionTypeExtension>().size)
        assertEquals(1, schema.definitions.filterIsInstance<EnumTypeExtension>().size)
        assertEquals(1, schema.definitions.filterIsInstance<InputObjectTypeExtension>().size)
    }

    @Test
    fun `supports block string descriptions`() {
        val source = "\"\"\"\nA user\n\"\"\"\ntype User {\n    id: ID!\n}"

        val schema = SchemaDocumentParser(source).schema()

        val user = schema.definitions.filterIsInstance<ObjectTypeDefinition>().single()
        assertEquals("A user", user.description)
    }

    @Test
    fun `rejects invalid schema syntax`() {
        listOf(
            "",
            "type",
            "type Query { }",
            "schema { }",
            "union U =",
            "enum E { true }",
            "input Filter { x: Int = \$y }",
            "directive @d on",
            "\"description\" extend type T { x: Int }",
            "foo Bar"
        ).forEach { source ->
            assertThrows<GraphException> {
                SchemaDocumentParser(source).schema()
            }
        }
    }
}
