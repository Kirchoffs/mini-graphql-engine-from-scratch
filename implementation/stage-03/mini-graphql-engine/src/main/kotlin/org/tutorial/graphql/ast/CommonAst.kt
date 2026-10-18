package org.tutorial.graphql.ast

sealed interface Value {
    data class Literal(val value: Any?) : Value                    // 42
    data class Variable(val name: String) : Value                  // $id
    data class ListValue(val values: List<Value>) : Value          // [1, 2, 3]
    data class ObjectValue(val fields: Map<String, Value>) : Value // { id: "x", name: "xavier" }
    data class EnumValue(val name: String) : Value                 // BLUE
}

// element != null => [element]
// nonNull == true => element!
data class TypeRef(
    val name: String? = null,
    val element: TypeRef? = null,
    val nonNull: Boolean = false
) {
    init { require((name == null) != (element == null)) }

    fun named(): String = name ?: element!!.named()
    override fun toString(): String = (name ?: "[$element]") + if (nonNull) "!" else ""
}

// A directive usage such as `@include(if: $flag)`.
// In schema document:
//   enum Status {
//     ACTIVE
//     INACTIVE @deprecatedWithReason(reason: "Use ARCHIVED instead")
//     ARCHIVED
//   }
//
// In executable document:
//   query GetUser($id: String, $includeEmail: Boolean!) {
//     user(id: $id) {
//       name
//       email @include(if: $includeEmail)
//     }
//   }
//
// Directive definitions are schema nodes and live in SchemaAst.kt.
//   directive @length(
//     min: Int
//     max: Int
//   ) on INPUT_FIELD_DEFINITION | ARGUMENT_DEFINITION 
data class Directive(val name: String, val arguments: Map<String, Value>)
