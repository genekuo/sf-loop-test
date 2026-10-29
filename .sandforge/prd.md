---
# Machine-readable header — Sandforge's PRD-validation step parses THIS block.
# Each success criterion has: id, statement, level, verify (how it's checked), gate.
#   level:  unit | integration | e2e | nonfunctional
#   gate:   work-staging | staging-main   (which promotion this criterion blocks)
# `sandforge graduate` runs every criterion against the on-demand deploy-target stack and emits a
# success-criteria report; the PR is blocked unless all `staging-main` gates pass.
prd:
  id: sf-loop-test
  title: "sf-loop-test — Spring Boot book catalog REST API"
  version: 0.1.0
  status: draft
  stack:
    backend: { lang: java, framework: "Spring Boot 4 (WebMVC)", api: rest-json }
    database: { engine: none, note: "in-memory ConcurrentHashMap" }
    lb: { kind: reverse-proxy, candidate: caddy, routes: ["/healthz -> lb", "/ -> backend:9001"] }
  success_criteria:
    - id: SC-1
      statement: "Through the LB, GET /healthz returns 200 within 2s."
      level: nonfunctional
      gate: work-staging
      verify: "curl -fsS --max-time 2 $BASE_URL/healthz -> HTTP 200"
    - id: SC-2
      statement: "GET /books returns 200 with a JSON array."
      level: integration
      gate: staging-main
      verify: "GET /books -> 200; body parses as a JSON array"
    - id: SC-3
      statement: "POST /books creates a book and returns 201 with the created resource."
      level: integration
      gate: staging-main
      verify: "POST {isbn,title,author,price} -> 201; response echoes the isbn"
    - id: SC-4
      statement: "POST /books with a duplicate ISBN is rejected with 422 (problem+json)."
      level: integration
      gate: staging-main
      verify: "POST same isbn twice -> second returns 422"
    - id: SC-5
      statement: "GET /books/{isbn} returns the created book (200); an unknown isbn returns 404."
      level: integration
      gate: staging-main
      verify: "GET /books/{isbn} -> 200 matching isbn; GET /books/<missing> -> 404"
    - id: SC-6
      statement: "PUT /books/{isbn} upserts: 201 when new, 200 when it updates an existing book."
      level: integration
      gate: staging-main
      verify: "PUT new isbn -> 201; PUT same isbn again -> 200 with updated fields"
    - id: SC-7
      statement: "DELETE /books/{isbn} returns 204 and the book is gone afterwards (404)."
      level: integration
      gate: staging-main
      verify: "DELETE /books/{isbn} -> 204; subsequent GET -> 404"
    - id: SC-8
      statement: "Unit, slice, and integration tests pass."
      level: unit
      gate: work-staging
      verify: "./gradlew --no-daemon test"
    - id: SC-9
      statement: "Cold container build + deploy-target stack healthy within budget on a laptop."
      level: nonfunctional
      gate: work-staging
      verify: "docker compose up --build --wait completes within the SC_9_BUDGET (default 420s)"
    - id: SC-10
      statement: "No secret values appear in container logs or image layers."
      level: nonfunctional
      gate: staging-main
      verify: "log + image scan finds no entries matching the known secret patterns"
---

# sf-loop-test — Sandforge deploy-target spec

This PRD is the machine-checked definition of "done" for graduating `sf-loop-test` through
Sandforge. The structured header above is parsed by `sandforge graduate`, which:

1. spins the on-demand deploy-target stack defined in
   [`deploy-target.compose.yml`](deploy-target.compose.yml) (Spring Boot backend + Caddy LB),
2. runs each success criterion via [`verify.sh`](verify.sh) against the live REST API through the
   LB, and
3. blocks the upstream PR unless every `staging-main` gate passes.

## Service under test

A Spring Boot 4 / Java 21 REST microservice with an in-memory catalog (no database). The API:

| Method | Path | Success | Error |
|--------|------|---------|-------|
| GET | `/books` | 200 `Book[]` | — |
| POST | `/books` | 201 `Book` | 422 duplicate ISBN |
| GET | `/books/{isbn}` | 200 `Book` | 404 |
| PUT | `/books/{isbn}` | 200 updated / 201 created | — |
| DELETE | `/books/{isbn}` | 204 | — |

Error bodies follow RFC 9457 Problem Details (`application/problem+json`).

## Gates

- **work-staging** (SC-1, SC-8, SC-9): liveness, the test suite, and the cold-build budget — run
  and reported, but do not block the upstream PR on their own.
- **staging-main** (SC-2…SC-7, SC-10): the REST contract and the no-secrets-leak scan — these
  **must** pass before `sandforge upstream` will open the PR.
