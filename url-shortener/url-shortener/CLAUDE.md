# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Base62 URL shortener, Java 21 / Spring Boot 3.3, backed by PostgreSQL + Redis. Implements the full `../design.md`: phase 1 was the single-node MVP (shorten + redirect); phase 2 (this state) adds caching, per-IP rate limiting, expiration, a dedicated ID-ticket generator, and consistent-hash sharding with primary/replica routing, all off by default except cache/rate-limit/expiration (sharding is opt-in via the `sharded` Spring profile — see Sharding below). Requires Docker for integration tests — Testcontainers spins up real `postgres:16-alpine` and `redis:7-alpine` containers, no mocks.

## Commands

```bash
mvn test                                    # run full test suite (unit + Testcontainers ITs, requires Docker)
mvn test -Dtest=Base62EncoderTest           # run a single test class
mvn test -Dtest=ShortenServiceTest#createsAndEncodesShortCodeForNewLongUrl  # run a single test method
mvn spring-boot:run                         # run the app locally (needs a reachable Postgres + Redis, see Configuration below)
mvn package                                 # build the jar
```

`DB_HOST`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` and `REDIS_HOST`/`REDIS_PORT` configure the datasources (see `application.yml`). `BASE_URL` (default `http://localhost:8080`) is prefixed onto the base62 code in `POST /api/v1/shorten`'s response. `RATELIMIT_LIMIT`/`RATELIMIT_WINDOW_SECONDS` (default 10/60) and `EXPIRATION_PURGE_CRON` (default hourly) tune those two features. Schema is managed by Hibernate `ddl-auto: update` in the default (non-sharded) profile — see Sharding below for why that changes when sharding is enabled.

The Surefire config in `pom.xml` pins `api.version=1.41` as both a system property and env var for Testcontainers/docker-java — this works around a Docker Desktop API version mismatch. Don't remove it (same fix as `rate-limiter/my-rate-limiter`).

## Architecture

Two flows sharing one table (`short_url`: `id`, `short_url`, `long_url`, `created_at`, `expires_at`):

- **`shorten` package** — the write path (`POST /api/v1/shorten`). `ShortenService#shorten` first checks for an existing row by longUrl (idempotency, per `design.md` §6.1); if new, it asks `idgen.IdTicketService` for a globally unique id, base62-encodes it with `Base62Encoder`, and inserts a single row (the id and the code are independent — see ID generation below). No `@Transactional` here; see Sharding for why.
- **`redirect` package** — the read path (`GET /{shortUrlCode}`). `RedirectService#resolve` checks `cache.RedirectCacheService` first; on a miss it loads from the DB and populates the cache, then returns a 302 with `Location` set, or throws `ShortUrlNotFoundException` (→ 404).
- **`Base62Encoder`** — pure static utility, encodes a `long` id using the alphabet `0-9,a-z,A-Z` per `design.md` §5.
- **`idgen` package** — `IdTicketService#nextId` inserts into a dedicated `id_ticket` table (auto-increment, no other columns) in its own `REQUIRES_NEW` transaction, decoupled from `short_url` per `design.md` §10's "DB-based ticket server" decision. This table is never sharded — sharding it would break the "globally unique" guarantee it exists for.
- **`cache` package** — `RedirectCacheService` wraps a `StringRedisTemplate` for `shortURL -> longURL` cache-aside. LRU eviction is configured on the Redis server (`--maxmemory-policy allkeys-lru` in docker-compose), not the app.
- **`ratelimit` package** — `RateLimitFilter` (a `OncePerRequestFilter`) throttles `POST /api/v1/shorten` per client IP using a fixed-window Redis counter (`INCR` + `EXPIRE`), returning 429 over the limit. Client IP comes from `X-Forwarded-For` when present (the sharded topology puts nginx in front), else `request.getRemoteAddr()`.
- **`expiration` package** — `ExpiredUrlPurgeJob` runs on an `@Scheduled` cron (hourly by default) and deletes rows past `expiresAt` (`createdAt` + 1 year default, set in `ShortUrl`'s constructor).
- **`sharding` package** — see below.
- **Exceptions**: `ShortUrlNotFoundException` → 404, bean validation failures → 400, both mapped centrally in `exception/GlobalExceptionHandler`.

## Sharding

Off by default (`urlshortener.sharding.enabled=false`) — with it off, every service above talks straight through `sharding.ShardedShortUrlOperations` to the single `ShortUrlRepository`/DataSource exactly as phase 1 did. **Enabling it changes nothing about the code path, only which physical database each call resolves to.**

- `ConsistentHashShardRouter` — a virtual-node hash ring (`design.md` §10) mapping a shortUrl code to a shard id. Pure and unit-tested (`ConsistentHashShardRouterTest`), no DB involved.
- `ShardRoutingDataSource` (`AbstractRoutingDataSource`) + `ShardRoutingContext` (a `ThreadLocal`) — the datasource resolves its target by reading the ThreadLocal, which `ShardedShortUrlOperations` sets immediately before, and clears immediately after, **each individual repository call**. This matters: a single Spring-managed transaction holds one physical connection for its whole duration, so changing the route mid-transaction would silently keep using the connection from the first-resolved route. That's why `ShortenService`/`RedirectService`/`ExpiredUrlPurgeJob` are not wrapped in an outer `@Transactional` — each call to `ShardedShortUrlOperations` is its own transaction/connection, which is also the honest model of a sharded system (no cross-shard ACID transaction without a distributed coordinator).
- `ShardedShortUrlOperations` — writes and `findByShortUrl` route to the shard derived from the shortUrl code (writes → `{shard}-primary`, reads → `{shard}-replica`). `findByLongUrl` can't be routed to one shard (longUrl isn't the shard key), so it scatter-gathers across every shard's replica — acceptable at the small shard counts this "simulated locally" setup targets; real scale would need a secondary global index. `deleteExpired` (used by the purge job) loops over every shard's primary, each delete wrapped in its own `TransactionTemplate`-managed transaction (Spring Data's derived delete queries require an explicit transaction from the caller, unlike `save`/`findBy...` which `SimpleJpaRepository` already wraps).
- `ShardingConfig` — when `urlshortener.sharding.enabled=true`, replaces the auto-configured `DataSource` bean with a `ShardRoutingDataSource` whose default target (used when no route is set, e.g. `IdTicketService`) is the normal `spring.datasource.*` connection, plus one primary + one replica target per configured shard (`urlshortener.sharding.shards[]`, see `application-sharded.yml`).
- Because Hibernate's `ddl-auto` schema generation only ever touches the datasource's *default* target, sharded mode sets `ddl-auto=none` and instead provisions each shard's `short_url` table via `postgres-init/shard-schema.sql` (mounted on the shard *-primary* containers only — replicas inherit the schema through actual streaming replication) and the idgen DB's `id_ticket` table via `postgres-init/idgen-schema.sql`.
- `postgres-init/replica-entrypoint.sh` bootstraps each replica via `pg_basebackup -R` against its primary on first start (writes `standby.signal` + `primary_conninfo` automatically); `postgres-init/pg-hba-replication.sh` opens the primary to replication connections. `POSTGRES_HOST_AUTH_METHOD=trust` on the primaries is a local-demo-only simplification — do not carry that into a real deployment.
- Run it: `docker compose --profile sharded up --build` (see root `README.md` / this module's `README.md` for the full topology and manual verification steps).

## Testing conventions

- `support/IntegrationTestSupport` starts one static Testcontainers Postgres **and** Redis instance and wires both via `@DynamicPropertySource` — extend it for any `*IT.java` that boots the full app (it needs both to start at all, since cache/rate-limit depend on Redis).
- `ShardRoutingIT` is the exception: it extends `IntegrationTestSupport` for Postgres/Redis but also starts two more independent Testcontainers Postgres instances directly (`SHARD0`, `SHARD1`, each with `withInitScript("shard-schema.sql")`) and activates sharding via `@DynamicPropertySource`, proving the real routing mechanism end-to-end — same shortUrl code always lands in the same physical DB, and redirects resolve correctly regardless of shard. "Replica" is pointed at the same container as "primary" in this test (it's about routing correctness, not replication — that's proven separately via docker-compose, see above).
- Pure unit tests (e.g. `ShortenServiceTest`, `ConsistentHashShardRouterTest`) mock their collaborators with Mockito — no Spring context, no Testcontainers.
