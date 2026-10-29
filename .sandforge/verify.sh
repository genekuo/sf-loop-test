#!/usr/bin/env bash
# verify.sh — Sandforge PRD success-criteria runner for the sf-loop-test deploy-target.
#
# Contract:  verify.sh <SC-id>   runs one criterion against the running deploy-target stack and
#            exits 0=pass, non-zero=fail.
#            verify.sh all        runs every SC and prints a PASS/FAIL table.
#
# Env (injected by `sandforge graduate`):
#   BASE_URL                 LB origin (default http://127.0.0.1:${SANDFORGE_LB_PORT})
#   SANDFORGE_STACK_PROJECT  compose project name
#   SANDFORGE_LB_PORT        host port the LB publishes (default 8099)
#   COMPOSE                  compose file path (default deploy-target.compose.yml)
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LB_PORT="${SANDFORGE_LB_PORT:-8099}"
BASE_URL="${BASE_URL:-http://127.0.0.1:${LB_PORT}}"
STACK_PROJECT="${SANDFORGE_STACK_PROJECT:-sandforge-book}"
COMPOSE="${COMPOSE:-deploy-target.compose.yml}"
SC_9_BUDGET="${SC_9_BUDGET:-420}"
export SANDFORGE_STACK_PROJECT="$STACK_PROJECT" SANDFORGE_LB_PORT="$LB_PORT"

compose() { docker compose -p "$STACK_PROJECT" -f "$COMPOSE" "$@"; }

log()  { printf '  %s\n' "$*" >&2; }
fail() { printf 'FAIL: %s\n' "$*" >&2; return 1; }

# --- helpers ---------------------------------------------------------------

# post_book <isbn> <title> -> prints HTTP status code; body in $LAST_BODY
LAST_BODY=""
post_book() {
  local isbn="$1" title="$2" raw code
  raw="$(curl -sS -w '\n%{http_code}' -X POST "$BASE_URL/books" \
    -H 'Content-Type: application/json' \
    -d "{\"isbn\":\"$isbn\",\"title\":\"$title\",\"author\":\"QA Bot\",\"price\":9.99}")" \
    || { fail "POST /books failed"; return 1; }
  code="$(printf '%s' "$raw" | tail -n1)"
  LAST_BODY="$(printf '%s' "$raw" | sed '$d')"
  printf '%s' "$code"
}

# put_book <isbn> <title> -> prints HTTP status code; body in $LAST_BODY
put_book() {
  local isbn="$1" title="$2" raw code
  raw="$(curl -sS -w '\n%{http_code}' -X PUT "$BASE_URL/books/$isbn" \
    -H 'Content-Type: application/json' \
    -d "{\"isbn\":\"$isbn\",\"title\":\"$title\",\"author\":\"QA Bot\",\"price\":12.50}")" \
    || { fail "PUT /books/$isbn failed"; return 1; }
  code="$(printf '%s' "$raw" | tail -n1)"
  LAST_BODY="$(printf '%s' "$raw" | sed '$d')"
  printf '%s' "$code"
}

# new_isbn — a unique ISBN per call. We avoid `date +%s%N` because macOS `date` has no %N (it
# yields a literal "N", so same-second calls collide and trip the duplicate-ISBN 422 path). A
# monotonic counter + PID + $RANDOM is portable and collision-free within a run.
__isbn_seq=0
new_isbn() { __isbn_seq=$((__isbn_seq + 1)); printf '978-%s-%s-%s' "$$" "$__isbn_seq" "$RANDOM"; }

# --- success criteria ------------------------------------------------------

sc_1() {
  # LB /healthz returns 200 within 2s.
  local code
  code="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 2 "$BASE_URL/healthz")" \
    || { fail "healthz unreachable within 2s"; return 1; }
  log "healthz: HTTP $code"
  [ "$code" = "200" ] || { fail "healthz returned $code"; return 1; }
}

sc_2() {
  # GET /books -> 200 and a JSON array.
  local raw code body
  raw="$(curl -sS -w '\n%{http_code}' "$BASE_URL/books")" || { fail "GET /books failed"; return 1; }
  code="$(printf '%s' "$raw" | tail -n1)"
  body="$(printf '%s' "$raw" | sed '$d')"
  [ "$code" = "200" ] || { fail "GET /books returned $code"; return 1; }
  printf '%s' "$body" | jq -e 'type == "array"' >/dev/null \
    || { fail "GET /books body is not a JSON array"; return 1; }
  log "GET /books -> 200, array of $(printf '%s' "$body" | jq 'length')"
}

sc_3() {
  # POST creates a book -> 201 echoing the isbn.
  local isbn code
  isbn="$(new_isbn)"
  code="$(post_book "$isbn" "The Go Programming Language")" || return 1
  [ "$code" = "201" ] || { fail "POST returned $code, want 201"; return 1; }
  printf '%s' "$LAST_BODY" | jq -e --arg i "$isbn" '.isbn == $i' >/dev/null \
    || { fail "response isbn != $isbn"; return 1; }
  log "created book isbn=$isbn (201)"
}

sc_4() {
  # Duplicate ISBN -> 422.
  local isbn code
  isbn="$(new_isbn)"
  code="$(post_book "$isbn" "First")" || return 1
  [ "$code" = "201" ] || { fail "first POST returned $code, want 201"; return 1; }
  code="$(post_book "$isbn" "Duplicate")" || return 1
  [ "$code" = "422" ] || { fail "duplicate POST returned $code, want 422"; return 1; }
  log "duplicate isbn=$isbn rejected with 422"
}

sc_5() {
  # GET /books/{isbn} -> 200 matching; missing -> 404.
  local isbn code body
  isbn="$(new_isbn)"
  code="$(post_book "$isbn" "Fetchable")" || return 1
  [ "$code" = "201" ] || { fail "seed POST returned $code"; return 1; }
  body="$(curl -sS -w '\n%{http_code}' "$BASE_URL/books/$isbn")"
  code="$(printf '%s' "$body" | tail -n1)"
  [ "$code" = "200" ] || { fail "GET /books/$isbn returned $code, want 200"; return 1; }
  printf '%s' "$body" | sed '$d' | jq -e --arg i "$isbn" '.isbn == $i' >/dev/null \
    || { fail "fetched isbn mismatch"; return 1; }
  code="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/books/978-does-not-exist-$(date +%s%N)")"
  [ "$code" = "404" ] || { fail "missing isbn returned $code, want 404"; return 1; }
  log "GET existing -> 200, GET missing -> 404"
}

sc_6() {
  # PUT upsert: new -> 201, same isbn again -> 200.
  local isbn code
  isbn="$(new_isbn)"
  code="$(put_book "$isbn" "Created via PUT")" || return 1
  [ "$code" = "201" ] || { fail "first PUT returned $code, want 201"; return 1; }
  code="$(put_book "$isbn" "Updated via PUT")" || return 1
  [ "$code" = "200" ] || { fail "second PUT returned $code, want 200"; return 1; }
  printf '%s' "$LAST_BODY" | jq -e '.title == "Updated via PUT"' >/dev/null \
    || { fail "updated title not reflected"; return 1; }
  log "PUT create -> 201, PUT update -> 200"
}

sc_7() {
  # DELETE -> 204, then GET -> 404.
  local isbn code
  isbn="$(new_isbn)"
  code="$(post_book "$isbn" "Doomed")" || return 1
  [ "$code" = "201" ] || { fail "seed POST returned $code"; return 1; }
  code="$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/books/$isbn")"
  [ "$code" = "204" ] || { fail "DELETE returned $code, want 204"; return 1; }
  code="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/books/$isbn")"
  [ "$code" = "404" ] || { fail "GET after DELETE returned $code, want 404"; return 1; }
  log "DELETE -> 204, GET after -> 404"
}

sc_8() {
  # Full test suite on the host via the Gradle wrapper (no running stack needed).
  # Runs from the repo root (one level up from .sandforge/).
  ( cd "$SCRIPT_DIR/.." && ./gradlew --no-daemon test ) \
    || { fail "./gradlew test failed"; return 1; }
  log "gradle test suite passed"
}

sc_9() {
  # Cold build + up --wait within budget. Tears down first for an honest cold number.
  log "tearing down for a cold measurement..."
  compose down -v >/dev/null 2>&1
  local start end elapsed
  start="$(date +%s)"
  if ! compose up --build --wait --wait-timeout "$((SC_9_BUDGET + 60))" >/dev/null 2>&1; then
    compose logs --tail 40 >&2
    fail "stack did not become healthy"
    return 1
  fi
  end="$(date +%s)"
  elapsed=$(( end - start ))
  log "cold build + up --wait: ${elapsed}s (budget ${SC_9_BUDGET}s)"
  [ "$elapsed" -le "$SC_9_BUDGET" ] || { fail "took ${elapsed}s, budget ${SC_9_BUDGET}s"; return 1; }
}

sc_10() {
  # No secret values in container logs or baked image layers.
  local patterns='BEGIN [A-Z ]*PRIVATE KEY|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|xox[baprs]-[0-9A-Za-z-]+|SANDFORGE_CANARY_[A-Za-z0-9]+'
  local hits=0 svc img
  if compose logs --no-color 2>/dev/null | grep -nEi "$patterns" >&2; then
    hits=1
  fi
  for svc in backend lb; do
    img="$(compose images -q "$svc" 2>/dev/null | head -n1)"
    [ -n "$img" ] || continue
    if docker history --no-trunc --format '{{.CreatedBy}}' "$img" 2>/dev/null \
        | grep -nEi "$patterns" >&2; then
      hits=1
    fi
  done
  [ "$hits" -eq 0 ] || { fail "secret pattern found in logs/image layers"; return 1; }
  log "no secret patterns found in logs or image history"
}

# --- dispatch --------------------------------------------------------------

run_one() {
  local sc="$1" fn
  fn="sc_$(printf '%s' "$sc" | sed 's/^SC-//; s/^sc-//')"
  if ! declare -F "$fn" >/dev/null; then
    echo "unknown criterion: $sc" >&2
    return 2
  fi
  "$fn"
}

run_all() {
  local ids=(SC-1 SC-2 SC-3 SC-4 SC-5 SC-6 SC-7 SC-8 SC-9 SC-10)
  local sc rc results=() overall=0
  for sc in "${ids[@]}"; do
    printf '\n=== %s ===\n' "$sc" >&2
    if run_one "$sc"; then
      results+=("$sc PASS")
    else
      rc=$?; results+=("$sc FAIL"); overall=1
    fi
  done
  printf '\n==== success-criteria report ====\n'
  printf '%s\n' "${results[@]}"
  return "$overall"
}

main() {
  local arg="${1:-}"
  case "$arg" in
    ""|-h|--help)
      echo "usage: verify.sh <SC-1..SC-10|all>" >&2; exit 2 ;;
    all|ALL) run_all ;;
    *) run_one "$arg" ;;
  esac
}

main "$@"
