# Notes
## GraphResult Example
### GraphQL Schema (Schema Definition)
```graphql
type Query {
    users: [User!]!
}

type User {
    id: ID!
    name: String
}
```

### GraphQL Query (Query Operation)
```graphql
query {
    users {
        id
        name
    }
}
```


### GraphQL Response
```json
{
    "data": {
        "users": [
            {
                "id": "1",
                "name": "Alice"
            },
            {
                "id": "2",
                "name": "Bob"
            },
            {
                "id": "3",
                "name": null
            }
        ]
    },
    "errors": [
        {
            "message": "Failed to resolve name",
            "locations": [
                { "line": 4, "column": 5 }
            ],
            "path": ["users", 2, "name"]
        }
    ]
}
```
