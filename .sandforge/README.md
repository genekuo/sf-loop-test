# `.sandforge/` — Sandforge deploy-target for sf-loop-test

These files let [Sandforge](https://github.com/jpoley/sandforge) run its **whole inner loop** for
sf-loop-test: import → agents push `work/*` → warm CI → PR review → `graduate` (spin a live stack,
machine-check the PRD) → one clean upstream PR.

| File | Role |
|------|------|
| [`prd.md`](prd.md) | Machine-readable success criteria (SC-1…SC-10). Parsed by `sandforge graduate`; the upstream PR is blocked unless every `staging-main` gate passes. |
| [`deploy-target.compose.yml`](deploy-target.compose.yml) | On-demand stack — Spring Boot `backend` + Caddy `lb` — spun up at `graduate`, exercised, then torn down. |
| [`Dockerfile`](Dockerfile) | Multi-stage build of the Spring Boot fat JAR on a slim JRE (used only by the compose stack). |
| [`Caddyfile`](Caddyfile) | LB config: answers `/healthz`, proxies everything else to `backend:9001`. |
| [`verify.sh`](verify.sh) | The SC runner. `verify.sh <SC-id>` checks one criterion against the live API through the LB; `verify.sh all` runs them all. |

Inner-loop CI (compile + tests on every push) lives in
[`../.github/workflows/ci.yml`](../.github/workflows/ci.yml) and is run by Sandforge's `act_runner`.

## Run the loop

```bash
sandforge init
sandforge import https://github.com/genekuo/sf-loop-test.git sf-loop-test
# …agents push work/* branches; CI runs automatically; open/review PRs on the local forge…
sandforge graduate sf-loop-test main      # spins this stack, runs SC-1…SC-10, assembles the PR
sandforge upstream  sf-loop-test main      # opens the single curated PR (only if staging-main gates pass)
```

## Try the checks by hand

```bash
cd .sandforge
SANDFORGE_STACK_PROJECT=book-local SANDFORGE_LB_PORT=8099 \
  docker compose -f deploy-target.compose.yml up -d --build --wait
BASE_URL=http://127.0.0.1:8099 ./verify.sh all
docker compose -p book-local -f deploy-target.compose.yml down -v
```
