# Notes
## EBNF of Executable Document
```
// 1. Document
Document ::= Definition { Definition }

Definition ::=
    OperationDefinition |
    FragmentDefinition


// 2. Operations
OperationDefinition ::=
    SelectionSet |
    OperationType [ Name ] [ VariableDefinitions ] [ Directives ] SelectionSet

OperationType ::= "query" | "mutation" | "subscription"

VariableDefinitions ::= "(" VariableDefinition { VariableDefinition } ")"

VariableDefinition ::= "$" Name ":" Type [ "=" ConstValue ]


// 3. Fragments
FragmentDefinition ::=
    "fragment" FragmentName "on" NamedType [ Directives ] SelectionSet

FragmentName ::= Name

FragmentSpread ::= "..." FragmentName [ Directives ]

InlineFragment ::= "..." [ "on" NamedType ] [ Directives ] SelectionSet


// 4. Selections
SelectionSet ::= "{" Selection { Selection } "}"

Selection ::=
    Field |
    FragmentSpread |
    InlineFragment

Field ::= [ Alias ] Name [ Arguments ] [ Directives ] [ SelectionSet ]

Alias ::= Name ":"


// 5. Arguments and Directives
Arguments ::= "(" Argument { Argument } ")"

Argument ::= Name ":" Value

Directives ::= Directive { Directive }

Directive ::= "@" Name [ Arguments ]


// 6. Types
Type ::= ( NamedType | "[" Type "]" ) [ "!" ]

NamedType ::= Name


// 7. Values
Value ::=
    Variable |
    IntValue |
    FloatValue |
    StringValue |
    BooleanValue |
    NullValue |
    EnumValue |
    ListValue |
    ObjectValue

Variable ::= "$" Name

ListValue ::= "[" [ Value { Value } ] "]"

ObjectValue ::= "{" [ ObjectField { ObjectField } ] "}"

ObjectField ::= Name ":" Value

ConstValue ::=
    IntValue |
    FloatValue |
    StringValue |
    BooleanValue |
    NullValue |
    EnumValue |
    ConstListValue |
    ConstObjectValue

ConstListValue ::= "[" [ ConstValue { ConstValue } ] "]"

ConstObjectValue ::= "{" [ ConstObjectField { ConstObjectField } ] "}"

ConstObjectField ::= Name ":" ConstValue


// 8. Scalar Values
BooleanValue ::= "true" | "false"

NullValue ::= "null"

EnumValue ::= Name
```

## Types
- Named Type: Int
- Wrappers:
    - List: [Int]
    - Non-Null: Int!
    - Combination of List and Non-Null: [Int!]!

## Directive
### Usage 1
```graphql
query GetUser($showEmail: Boolean!) {
    user(id: 1) {
        name
        email @include(if: $showEmail)
    }
}
```

### Usage 2
```graphql
union SearchResult = User | Post

type User {
    id: ID!
    name: String!
}

type Post {
    id: ID!
    title: String!
}

type Query {
    search(text: String!): [SearchResult]
}
```

```graphql
query($withUsers: Boolean!, $withPosts: Boolean!) {
    search(text: "GraphQL") {
        __typename

        ... on User @include(if: $withUsers) {
            name
        }

        ... on Post @include(if: $withPosts) {
            title
        }
    }
}
```

### Usage 3
```graphql
directive @cacheControl(maxAge: Int, scope: CacheControlScope) on QUERY | FIELD_DEFINITION

enum CacheControlScope {
  PUBLIC
  PRIVATE
}
```

```graphql
type User {
    id: ID!
    name: String!
    email: String @cacheControl(maxAge: 256, scope: PRIVATE)
}
```

```graphql
query GetUser @cacheControl(maxAge: 256, scope: PRIVATE) {
    user(id: 1) {
        name
        email
    }
}
```

## Selection Set
- Field
- Fragment Spread
- Inline Fragment

### Field
#### Scalar Field
```graphql
{
    user {
        name    # scalar field
        age     # scalar field
    }
}
```

#### Object Field
```graphql
{
    user {      # object field
        name    # scalar field
        age     # scalar field
    }
}
```

#### Field with Arguments
```graphql
{
    image(size: LARGE) { # field with argument
        url
    }
}
```

### Fragment Spread
```graphql
{
    user {
        ...UserDetails   # fragment spread
    }
}

fragment UserDetails on User {
    id
    name
    email
}
```

### Inline Fragment
```graphql
{
    search(text: "GraphQL") {
        __typename
        
        ... on User {    # inline fragment
            name
        }
        
        ... on Item {    # inline fragment
            title
        }
    }
}
```

## Queries
### Named Query
```
query GetUser {
  user(id: "42") {
    name
    email
  }
}
```

### Anonymous Query
```
{
  user(id: "42") {
    name
    email
  }
}
```
