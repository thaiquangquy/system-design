# Rate Limiter

A rate limiter service that implements the **Sliding Window Counter** algorithm, backed by Redis. It exposes a fast, unauthenticated endpoint to check/consume rate-limit quota for a key, and an admin-authenticated CRUD API to manage the rules that define limits per key prefix.

Built with Java 21 and Spring Boot 3.3.

## High-level architecture

The service is two independent verticals sharing Redis, connected only through an in-memory rule cache:

```
                     ┌─────────────────────┐
  admin client ─────▶│  RuleController      │  /api/v1/rules/**  (requires X-Admin-Token)
                     │  (rules package)     │
                     └─────────┬────────────┘
                               │ writes rules, publishes invalidation
                               ▼
                     ┌─────────────────────┐        pub/sub
                     │  Redis: rule hash    │◀──── ratelimit:rules:invalidate ────┐
                     │  + counters          │                                     │
                     └─────────┬────────────┘                                     │
                               │ reads rules (cached 5s, Caffeine)                 │
                               ▼                                                   │
                     ┌─────────────────────┐                              ┌───────┴────────┐
  client   ─────────▶│  RateLimitController │  /api/v1/rate-limit/check   │ RuleInvalidation│
                     │  (check package)     │  ──▶ atomic Lua script      │ Listener        │
                     └─────────────────────┘      (sliding_window.lua)   └────────────────┘
```

- **Rule management** (`rules` package): admins define rate-limit rules — a key prefix, a request limit, and a window in seconds — via a REST API. Rules are persisted as a Redis hash. Every write publishes an invalidation message over Redis pub/sub so all running instances refresh their local rule cache, not just the one that handled the write.
- **Rate limit check** (`check` package): the hot path. Given a `key` (e.g. an IP or user ID), it finds the longest matching rule prefix (via the 5-second Caffeine cache in front of Redis), then runs a single atomic Lua script against Redis to decide allow/deny and update counters, returning `X-RateLimit-*` headers.
- **Sliding window algorithm**: time is bucketed into fixed windows (`now / windowSeconds`). Each check weighs the previous window's count by how much of it still "bleeds" into the current window (`(windowSeconds - elapsed) / windowSeconds`), added to the current window's count. This smooths out the burst-at-boundary problem of naive fixed-window counters, while still being O(1) per request (two `GET`s + one `INCR`, all inside one Lua script for atomicity under concurrency).
- **Fail-closed matching**: a key with no matching rule is **denied**, not allowed.

## Code structure

```
src/main/java/com/example/ratelimiter/
├── RateLimiterApplication.java     Spring Boot entrypoint
├── admin/
│   └── AdminAuthFilter.java        Servlet filter guarding /api/v1/rules/** with X-Admin-Token
├── check/                          The rate-limit check vertical
│   ├── RateLimitController.java    GET /api/v1/rate-limit/check
│   ├── RateLimitService.java       Looks up rule, runs sliding_window.lua, builds result
│   └── RateLimitResult.java        allowed / limit / remaining / resetSeconds
├── rules/                          The rule management vertical
│   ├── RuleController.java         POST/GET/PUT/DELETE /api/v1/rules/{prefix}
│   ├── RuleService.java            Validates, persists, publishes cache invalidation
│   ├── RuleRepository.java         Redis hash storage (ratelimit:rules), JSON-encoded values
│   ├── RuleCache.java              Caffeine cache (5s TTL), longest-prefix-match lookup
│   ├── RuleInvalidationListener.java  Redis pub/sub subscriber that clears the local cache
│   ├── Rule.java                   Domain record: keyPrefix, limit, windowSeconds
│   ├── dto/                        Request DTOs (RuleRequest, RuleUpdateRequest)
│   ├── InvalidRuleException.java   → mapped to 400
│   └── RuleNotFoundException.java  → mapped to 404
├── config/
│   └── RedisConfig.java            StringRedisTemplate + RedisMessageListenerContainer beans
└── exception/
    └── GlobalExceptionHandler.java Central @RestControllerAdvice for rule exceptions

src/main/resources/
├── application.yml                 Redis connection + admin token config
└── scripts/sliding_window.lua      Atomic check-and-increment script

src/test/java/com/example/ratelimiter/
├── support/RedisTestSupport.java   Shared Testcontainers Redis base class for *IT tests
├── check/                          RateLimitServiceIT, RateLimitControllerIT
├── rules/                          RuleServiceTest (unit, Mockito), RuleCacheTest, RuleRepositoryTest, RuleControllerIT
└── admin/AdminAuthFilterTest.java
```

**Suggested reading order for onboarding**: `Rule.java` → `RuleRepository.java` → `RuleCache.java` → `RuleService.java` → `RuleController.java` (rule management, simplest to most composed), then `sliding_window.lua` → `RateLimitService.java` → `RateLimitController.java` (the check path). `AdminAuthFilter.java` and `RedisConfig.java` are cross-cutting and can be read any time.

## How to use

### Configuration

The app reads these environment variables (see `application.yml`):

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `ADMIN_TOKEN` | **yes** | — | Token clients must send as `X-Admin-Token` to manage rules. App fails to start without it. |
| `REDIS_HOST` | no | `localhost` | Redis host |
| `REDIS_PORT` | no | `6379` | Redis port |
| `REDIS_PASSWORD` | no | *(none)* | Redis auth password |

### Running

```bash
# start a local Redis if you don't have one
docker run -p 6379:6379 -d redis:7-alpine

ADMIN_TOKEN=changeme mvn spring-boot:run
```

The app listens on `http://localhost:8080`.

### 1. Define a rule (admin API)

```bash
curl -X POST http://localhost:8080/api/v1/rules \
  -H "X-Admin-Token: changeme" \
  -H "Content-Type: application/json" \
  -d '{"keyPrefix": "ip:", "limit": 100, "windowSeconds": 60}'
```

Rule matching is **longest-prefix-wins**: a key like `ip:1.2.3.4` matches the `ip:` rule above, but a more specific rule (e.g. `ip:1.2.3.4`) would take precedence over the broader `ip:` one if both exist.

Other admin endpoints (all require `X-Admin-Token`):

```bash
curl http://localhost:8080/api/v1/rules -H "X-Admin-Token: changeme"                      # list all rules
curl http://localhost:8080/api/v1/rules/ip: -H "X-Admin-Token: changeme"                  # get one rule
curl -X PUT http://localhost:8080/api/v1/rules/ip: -H "X-Admin-Token: changeme" \
  -H "Content-Type: application/json" -d '{"limit": 200, "windowSeconds": 60}'             # update
curl -X DELETE http://localhost:8080/api/v1/rules/ip: -H "X-Admin-Token: changeme"        # delete
```

### 2. Check / consume quota (public API, no auth)

```bash
curl -i "http://localhost:8080/api/v1/rate-limit/check?key=ip:1.2.3.4"
```

Each call both checks **and consumes** one unit of quota (if allowed). Response:

- `200 OK` with headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (seconds until the current window ends) — if under the limit.
- `429 Too Many Requests` with the same headers (`Remaining` at `0`) — if over the limit, or if no rule matches the key at all (fail-closed).

## Build and test

Requires Docker running locally (integration tests use Testcontainers to spin up a real `redis:7-alpine` container).

```bash
mvn test                                             # full suite: unit + integration tests
mvn test -Dtest=RuleServiceTest                      # a single test class
mvn test -Dtest=RuleServiceTest#createRejectsNonPositiveLimit   # a single test method
mvn package                                          # build the jar
```
