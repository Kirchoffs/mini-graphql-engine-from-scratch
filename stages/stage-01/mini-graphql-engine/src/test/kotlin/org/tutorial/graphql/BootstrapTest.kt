package org.tutorial.graphql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BootstrapTest {
    @Test fun `errors retain source locations`() {
        assertEquals(
            listOf(Location(2, 3)), 
            GraphException("Invalid input", Location(2, 3)).error().locations
        )
    }
}
