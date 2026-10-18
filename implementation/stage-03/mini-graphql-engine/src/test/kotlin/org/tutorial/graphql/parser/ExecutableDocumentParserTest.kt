package org.tutorial.graphql.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.tutorial.graphql.GraphException
import org.tutorial.graphql.ast.Field
import org.tutorial.graphql.ast.Spread
import org.tutorial.graphql.ast.Value

class ExecutableDocumentParserTest {
    @Test
    fun `parses a simple query`() {
        val document = ExecutableDocumentParser("query GetOrder { order { id status } }").document()

        val operation = document.operations.single()
        assertTrue(operation.variables.isEmpty())

        val orderField = operation.selections.single() as Field
        assertEquals("order", orderField.name)
        assertEquals("order", orderField.responseName)
        assertEquals(listOf("id", "status"), orderField.selections.filterIsInstance<Field>().map { it.name })
    }
    
    @Test
    fun `parses a complex query with variables aliases and fragments`() {
        val documentText = """
            query GetOrder(${'$'}orderId: ID!, ${'$'}withBuyerContact: Boolean! = true) {
                order: order(id: ${'$'}orderId) {
                    id
                    status
                    buyer {
                        ...BuyerInfo
                        displayName @include(if: ${'$'}withBuyerContact)
                        email @include(if: ${'$'}withBuyerContact)
                    }
                    items {
                        ...ItemInfo
                        unitPrice
                    }
                    total: amount {
                        currency
                        value
                    }
                }
            }

            fragment BuyerInfo on User {
                id
            }

            fragment ItemInfo on OrderItem {
                sku
                quantity
                product {
                    id
                    name
                }
            }
        """

        val document = ExecutableDocumentParser(documentText).document()
        val operation = document.operations.single()

        assertEquals(listOf("orderId", "withBuyerContact"), operation.variables.map { it.name })
        assertEquals("ID!", operation.variables.first().type.toString())
        assertEquals(Value.Literal(true), operation.variables.last().default)

        val orderField = operation.selections.single() as Field
        assertEquals("order", orderField.responseName)
        assertEquals("order", orderField.name)

        assertEquals("User", document.fragments.getValue("BuyerInfo").type.toString())
        assertEquals("OrderItem", document.fragments.getValue("ItemInfo").type.toString())

        val buyerField = orderField.selections
            .filterIsInstance<Field>()
            .single { it.name == "buyer" }

        val buyerSelections = buyerField.selections

        assertTrue(
            buyerSelections.any { it is Spread && it.name == "BuyerInfo" },
            "buyer should spread BuyerInfo fragment"
        )

        val contactFields = buyerSelections
            .filterIsInstance<Field>()
            .filter { it.name in setOf("displayName", "email") }

        assertEquals(
            setOf("displayName", "email"),
            contactFields.map { it.name }.toSet(),
            "buyer should expose displayName and email"
        )

        contactFields.forEach { field ->
            val include = field.directives.single { it.name == "include" }
            val condition = include.arguments.getValue("if") as Value.Variable
            assertEquals(
                "withBuyerContact",
                condition.name,
                "field '${field.name}' should be gated by \$withBuyerContact"
            )
        }

        val itemsField = orderField.selections
            .filterIsInstance<Field>()
            .single { it.name == "items" }

        assertTrue(
            itemsField.selections.any { it is Spread && it.name == "ItemInfo" },
            "items should spread ItemInfo fragment"
        )
        assertTrue(
            itemsField.selections.filterIsInstance<Field>().any { it.name == "unitPrice" },
            "items should include unitPrice"
        )

        val totalField = orderField.selections
            .filterIsInstance<Field>()
            .single { it.responseName == "total" }
        assertEquals("amount", totalField.name)
    }
    
    @Test fun `rejects duplicate arguments and incomplete documents`() {
        listOf(
            "{ f(x: 1, x: -1) }", 
            "{",
            "{}",
            "query A { x } query A { y }"
        ).forEach { source ->
            assertThrows<GraphException> {
                ExecutableDocumentParser(source).document()
            }
        }
    }

    @Test fun `string values are not treated as syntax`() {
        val document = ExecutableDocumentParser("""{ f(x: "{", y: "[", z: "${'$'}") }""").document()
        val field = document.operations.single().selections.single() as Field

        assertEquals(Value.Literal("{"), field.arguments.getValue("x"))
        assertEquals(Value.Literal("["), field.arguments.getValue("y"))
        assertEquals(Value.Literal("$"), field.arguments.getValue("z"))
    }

    @Test fun `rejects string tokens used as syntax`() {
        listOf(
            """{ f ":" x }""",
            """{ "..." BuyerInfo }""",
            """fragment F "on" User { id }"""
        ).forEach { source ->
            assertThrows<GraphException> {
                ExecutableDocumentParser(source).document()
            }
        }
    }

    @Test fun `rejects out of range numbers and duplicate variables`() {
        listOf(
            "{ f(x: 2147483648) }",
            "{ f(x: 1e6174) }",
            "query A(${'$'}x: Int, ${'$'}x: Int) { f }"
        ).forEach { source ->
            assertThrows<GraphException> {
                ExecutableDocumentParser(source).document()
            }
        }
    }

    @Test fun `parses operation and fragment directives`() {
        val document = ExecutableDocumentParser("query Q @op { f } fragment F on T @frag { x }").document()

        assertEquals(listOf("op"), document.operations.single().directives.map { it.name })
        assertEquals(listOf("frag"), document.fragments.getValue("F").directives.map { it.name })
    }
}
