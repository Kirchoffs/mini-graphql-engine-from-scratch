package org.tutorial.graphql.ast

import org.tutorial.graphql.GraphException
import org.tutorial.graphql.Location

data class ExecutableDocument(
    val operations: List<Operation>,
    val fragments: Map<String, Fragment>
) {
    fun operation(name: String?): Operation {
        return if (name == null) {
            operations.singleOrNull() ?: throw GraphException("An operation name is required") 
        } else {
            operations.find { it.name == name } ?: throw GraphException("Unknown operation '$name'")
        }
    }
}

data class Operation(
    val kind: String, // query, mutation, subscription
    val name: String?, 
    val variables: List<VariableDefinition>, 
    val selections: List<Selection>,
    val directives: List<Directive> = emptyList()
)

data class Fragment(
    val name: String, 
    val type: String, 
    val selections: List<Selection>,
    val directives: List<Directive> = emptyList()
)

data class VariableDefinition(
    val name: String, 
    val type: TypeRef, 
    val default: Value?
)

sealed interface Selection { val directives: List<Directive> }

data class Field(
    val name: String, 
    val alias: String? = null, 
    val arguments: Map<String, Value> = emptyMap(), 
    override val directives: List<Directive> = emptyList(), 
    val selections: List<Selection> = emptyList(), 
    val location: Location = Location(1, 1)
) : Selection {
    val responseName get() = alias ?: name 
}

data class Spread(
    val name: String, 
    override val directives: List<Directive>
) : Selection

data class InlineFragment(
    val type: String?, 
    val selections: List<Selection>, 
    override val directives: List<Directive>
) : Selection
