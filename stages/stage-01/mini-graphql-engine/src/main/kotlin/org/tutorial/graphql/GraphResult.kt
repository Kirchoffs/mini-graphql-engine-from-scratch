package org.tutorial.graphql

data class Location(val line: Int, val column: Int)

data class GraphError(
    val message: String, 
    val locations: List<Location> = emptyList(), 
    val path: List<Any> = emptyList()
)

class GraphException(
    message: String, 
    val location: Location? = null
) : RuntimeException(message) {
    fun error() = GraphError(message ?: "GraphQL error", listOfNotNull(location))
}

data class GraphResult(
    val data: Map<String, Any?>? = null, 
    val errors: List<GraphError> = emptyList()
)
