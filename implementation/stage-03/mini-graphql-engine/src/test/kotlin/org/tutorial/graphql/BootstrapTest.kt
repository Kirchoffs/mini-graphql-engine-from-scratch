package org.tutorial.graphql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BootstrapTest {
    @Test fun `errors retain source locations`() {
        assertEquals(
            listOf(Location(4, 2)), 
            GraphException("Invalid input", Location(4, 2)).error().locations
        )
    }
}
