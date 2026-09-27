Add two things to this store's GraphQL API.

1. A field `orderCount: Int!` on `Customer`: how many orders that customer has
   placed. It must be batched: however many customers appear in one response,
   their order counts come from one database query, not one per customer.
2. A query `customerOrders(customerId: ID!, first: Int, after: String): OrderConnection!`
   returning that customer's orders ordered by id, cursor paginated the same way
   as the existing `orders` query, 10 per page when `first` is not given.
   If there is no customer with that id, it returns a GraphQL error classified
   `NOT_FOUND` whose message includes the id, for example `No customer 42`.

Follow the patterns already in the code. Keep the existing tests passing and add
tests for what you build. Run `./mvnw test` before you finish; `JAVA_HOME`
already points at the JDK 25 this project needs. Postgres is not available here,
so your tests must not need a database: `@GraphQlTest` slice tests run without one.
