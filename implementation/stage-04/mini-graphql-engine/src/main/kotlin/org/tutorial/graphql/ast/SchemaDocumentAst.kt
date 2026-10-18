package org.tutorial.graphql.ast

data class SchemaDocument(val definitions: List<SchemaDefinition>)

sealed interface SchemaDefinition

data class SchemaRootDefinition(
    val description: String?,
    val directives: List<Directive>,
    val operations: List<RootOperationTypeDefinition>
) : SchemaDefinition

data class RootOperationTypeDefinition(
    val operation: String,
    val type: String
)

data class ScalarTypeDefinition(
    val description: String?,
    val name: String,
    val directives: List<Directive>
) : SchemaDefinition

data class ObjectTypeDefinition(
    val description: String?,
    val name: String,
    val interfaces: List<String>,
    val directives: List<Directive>,
    val fields: List<FieldDefinition>
) : SchemaDefinition

data class InterfaceTypeDefinition(
    val description: String?,
    val name: String,
    val interfaces: List<String>,
    val directives: List<Directive>,
    val fields: List<FieldDefinition>
) : SchemaDefinition

data class UnionTypeDefinition(
    val description: String?,
    val name: String,
    val directives: List<Directive>,
    val members: List<String>
) : SchemaDefinition

data class EnumTypeDefinition(
    val description: String?,
    val name: String,
    val directives: List<Directive>,
    val values: List<EnumValueDefinition>
) : SchemaDefinition

data class InputObjectTypeDefinition(
    val description: String?,
    val name: String,
    val directives: List<Directive>,
    val fields: List<InputValueDefinition>
) : SchemaDefinition

data class DirectiveDefinition(
    val description: String?,
    val name: String,
    val arguments: List<InputValueDefinition>,
    val repeatable: Boolean,
    val locations: List<String>
) : SchemaDefinition

data class FieldDefinition(
    val description: String?,
    val name: String,
    val arguments: List<InputValueDefinition>,
    val type: TypeRef,
    val directives: List<Directive>
)

data class InputValueDefinition(
    val description: String?,
    val name: String,
    val type: TypeRef,
    val defaultValue: Value?,
    val directives: List<Directive>
)

data class EnumValueDefinition(
    val description: String?,
    val name: String,
    val directives: List<Directive>
)

data class SchemaExtension(
    val directives: List<Directive>,
    val operations: List<RootOperationTypeDefinition>
) : SchemaDefinition

data class ScalarTypeExtension(
    val name: String,
    val directives: List<Directive>
) : SchemaDefinition

data class ObjectTypeExtension(
    val name: String,
    val interfaces: List<String>,
    val directives: List<Directive>,
    val fields: List<FieldDefinition>
) : SchemaDefinition

data class InterfaceTypeExtension(
    val name: String,
    val interfaces: List<String>,
    val directives: List<Directive>,
    val fields: List<FieldDefinition>
) : SchemaDefinition

data class UnionTypeExtension(
    val name: String,
    val directives: List<Directive>,
    val members: List<String>
) : SchemaDefinition

data class EnumTypeExtension(
    val name: String,
    val directives: List<Directive>,
    val values: List<EnumValueDefinition>
) : SchemaDefinition

data class InputObjectTypeExtension(
    val name: String,
    val directives: List<Directive>,
    val fields: List<InputValueDefinition>
) : SchemaDefinition
