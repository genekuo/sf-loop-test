# book-service

A reactive book catalog REST API built with Spring Boot and Spring WebFlux.

## Requirements

- Java 25
- No local Gradle needed — use the bundled wrapper (`./gradlew`)

## Build & Test

```bash
./gradlew build      # compile and run tests
./gradlew test       # run tests only
./gradlew bootRun    # start the server
```

The server listens on port `9001` by default (configurable in
`src/main/resources/application.properties`).

## Data model

A `Book` is identified by its ISBN:

| Field    | Type         | Constraints              |
|----------|--------------|--------------------------|
| `isbn`   | `String`     | required, not blank      |
| `title`  | `String`     | required, not blank      |
| `author` | `String`     | required, not blank      |
| `price`  | `BigDecimal` | required, zero or more   |

## API

Base path: `/books`

| Method   | Path             | Description                        | Success status |
|----------|------------------|------------------------------------|----------------|
| `GET`    | `/books`         | List all books                     | `200 OK`       |
| `GET`    | `/books/{isbn}`  | Get a book by ISBN                 | `200 OK`       |
| `POST`   | `/books`         | Create a new book                  | `201 Created`  |
| `PUT`    | `/books/{isbn}`  | Create or replace a book by ISBN   | `200`/`201`    |
| `DELETE` | `/books/{isbn}`  | Delete a book by ISBN              | `204 No Content` |

A `POST` with an ISBN that already exists returns `422 Unprocessable Content`.
A `GET` or `DELETE` of an unknown ISBN returns `404 Not Found`. Not-found and
duplicate-ISBN errors are returned as RFC 9457 Problem Details
(`application/problem+json`). Validation failures return `400 Bad Request`.

For `PUT`, the ISBN is taken from the path; the `isbn` in the request body is
ignored for identification, but it must still be present and non-blank to pass
validation.

### Example

```bash
# Create a book
curl -X POST http://localhost:9001/books \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"978-0134685991","title":"Effective Java","author":"Joshua Bloch","price":45.00}'

# List all books
curl http://localhost:9001/books

# Fetch one book
curl http://localhost:9001/books/978-0134685991

# Delete a book
curl -X DELETE http://localhost:9001/books/978-0134685991
```
