# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Sliding Window Counter rate limiter, Java 21 / Spring Boot 3.3, backed by Redis. Requires Docker for tests (Testcontainers spins up a real `redis:7-alpine` container — no mocked Redis).

## Commands

```bash
mvn test                                    # run full test suite (unit + Testcontainers ITs, requires Docker)
mvn test -Dtest=RuleServiceTest             # run a single test class
mvn test -Dtest=RuleServiceTest#createRejectsNonPositiveLimit  # run a single test method
mvn spring-boot:run                         # run the app locally (needs REDIS_HOST/PORT and ADMIN_TOKEN)
mvn package                                 # build the jar
```

`ADMIN_TOKEN` is a required env var with no default (`application.yml` has no fallback) — the app fails fast on startup without it. `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` default to `localhost:6379` with no password.

The Surefire config in `pom.xml` pins `api.version=1.41` as both a system property and env var for Testcontainers/docker-java — this works around a Docker Desktop API version mismatch. Don't remove it.

## Architecture

Two independent verticals sharing Redis, wired together only through `RuleCache`:

- **`rules` package** — CRUD for rate-limit rules (`POST/GET/PUT/DELETE /api/v1/rules/{prefix}`), gated by `AdminAuthFilter` (checks `X-Admin-Token` against `ratelimiter.admin.token`, constant-time compare via `MessageDigest.isEqual`). `RuleRepository` persists rules as a Redis hash (`ratelimit:rules`, JSON-encoded values). `RuleService` validates and writes, then publishes to the `ratelimit:rules:invalidate` pub/sub channel on any create/update/delete.
- **`check` package** — the hot path (`GET /api/v1/rate-limit/check?key=...`), no auth. `RateLimitService` looks up the matching rule via `RuleCache`, then calls `sliding_window.lua` atomically against two Redis keys (current + previous window counters) to decide allow/deny and computes `X-RateLimit-*` response headers.

**Rule matching**: `RuleCache` (Caffeine, 5s TTL) loads all rules from `RuleRepository`, sorts by `keyPrefix` length descending, and returns the first rule whose prefix the request key starts with — i.e. longest-prefix-match wins. A key that matches no rule is **denied** (fail-closed), not allowed.

**Cache invalidation**: rule changes on one instance invalidate that instance's local cache immediately, but other instances only learn about the change via the Redis pub/sub message handled by `RuleInvalidationListener` (registered in `RedisConfig`). This means rule updates can take effect at different times across instances until the pub/sub message arrives — the 5s TTL is the worst-case staleness bound if pub/sub delivery fails.

**Sliding window algorithm** (`sliding_window.lua`): each window is bucketed by `now / windowSeconds` into a counter key (`ratelimit:cnt:{key}:{windowId}`, TTL = `2 * windowSeconds`). A request's effective count is `previousWindowCount * ((windowSeconds - elapsed) / windowSeconds) + currentWindowCount` — a linear decay weighting of the prior window. The whole check-and-increment happens in one Lua script for atomicity under concurrent requests (see `RateLimitServiceIT#exactlyLimitRequestsAllowedUnderConcurrency` for the concurrency guarantee this depends on).

**Exceptions**: `InvalidRuleException` → 400, `RuleNotFoundException` → 404, both mapped centrally in `GlobalExceptionHandler` — new rule-related validation errors should follow this pattern rather than handling errors in the controller.

## Testing conventions

- `RedisTestSupport` is the shared base class for integration tests — it starts one static Testcontainers Redis instance and wires `spring.data.redis.host/port` + a fixed `ratelimiter.admin.token=test-token` via `@DynamicPropertySource`. Extend it for any test needing a real Redis (`*IT.java` naming).
- Pure unit tests (e.g. `RuleServiceTest`) mock `RuleRepository`/`RuleCache`/`StringRedisTemplate` directly with Mockito — no Spring context, no Testcontainers.
- `RateLimitService` reads the wall clock directly (no injectable `Clock`), so tests asserting on the sliding-window math must derive expected values from the actual elapsed-time-in-window at assertion time rather than hardcoding it — see the comments in `RateLimitServiceIT#weightedPreviousWindowCountReducesCurrentWindowAllowance` for why a naive fixed assertion is flaky.
