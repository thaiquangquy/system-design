# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Base62 URL shortener, Java 21 / Spring Boot 3.3, backed by PostgreSQL. Requires Docker for integration tests (Testcontainers spins up a real `postgres:16-alpine` container — no mocked/in-memory DB). Implements phase 1 (MVP, single node) of `../design.md`; phase 2 layers in caching, rate limiting, expiration, and sharding — see `../design.md` §10 for the decisions and this file's Architecture section for what's implemented so far.

## Commands

```bash
mvn test                                    # run full test suite (unit + Testcontainers ITs, requires Docker)
mvn test -Dtest=Base62EncoderTest           # run a single test class
mvn test -Dtest=ShortenServiceTest#createsAndEncodesShortCodeForNewLongUrl  # run a single test method
mvn spring-boot:run                         # run the app locally (needs a reachable Postgres, see Configuration below)
mvn package                                 # build the jar
```

`DB_HOST`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` configure the datasource (see `application.yml`), defaulting to `localhost` / `urlshortener` / `urlshortener` / `urlshortener`. `BASE_URL` (default `http://localhost:8080`) is prefixed onto the base62 code when building the `shortUrl` returned from `POST /api/v1/shorten`. Schema is managed by Hibernate `ddl-auto: update` — no migration tool for this MVP phase.

The Surefire config in `pom.xml` pins `api.version=1.41` as both a system property and env var for Testcontainers/docker-java — this works around a Docker Desktop API version mismatch. Don't remove it (same fix as `rate-limiter/my-rate-limiter`).

## Architecture

Two flows sharing one table (`short_url`: `id`, `short_url`, `long_url`, `created_at`):

- **`shorten` package** — the write path (`POST /api/v1/shorten`). `ShortenService#shorten` first checks `ShortUrlRepository#findByLongUrl`; if the long URL was already shortened it returns the existing code (idempotency, per `design.md` §6.1). Otherwise `createShortCode` inserts a row with `short_url = null` to obtain the DB-generated `id` (`GenerationType.IDENTITY`), encodes that id with `Base62Encoder`, then updates the row with the code — two round trips, acceptable for the phase-1 MVP. Phase 2 replaces this with a dedicated ID-ticket table per `design.md` §10 to avoid the null-then-update dance.
- **`redirect` package** — the read path (`GET /{shortUrlCode}`), directly queries `ShortUrlRepository#findByShortUrl` (no service layer — the lookup is a one-liner) and returns a 302 with `Location` set to the long URL, or throws `ShortUrlNotFoundException` (→ 404) if the code doesn't exist. No cache yet (phase 2 adds Redis, see `design.md` §6.2/§8).
- **`Base62Encoder`** — pure static utility, encodes a `long` id using the alphabet `0-9,a-z,A-Z` per `design.md` §5 (index 0-9 → digits, 10-35 → lowercase, 36-61 → uppercase).
- **Exceptions**: `ShortUrlNotFoundException` → 404, bean validation failures (`@URL`/`@NotBlank` on `ShortenRequest.longUrl`) → 400, both mapped centrally in `exception/GlobalExceptionHandler` — new errors should follow this pattern rather than handling them in the controller.

**Not yet implemented (phase 2, see `design.md` §10)**: Redis cache, per-IP rate limiting on `/api/v1/shorten`, 1-year expiration + purge job, dedicated ID-ticket table, and simulated sharding/replication across multiple Postgres instances.

## Testing conventions

- `support/PostgresTestSupport` is the shared base class for integration tests — starts one static Testcontainers Postgres instance and wires `spring.datasource.*` via `@DynamicPropertySource`. Extend it for any test needing a real Postgres (`*IT.java` naming, same convention as `rate-limiter/my-rate-limiter`'s `RedisTestSupport`).
- Pure unit tests (e.g. `ShortenServiceTest`) mock `ShortUrlRepository` directly with Mockito — no Spring context, no Testcontainers. `ShortenServiceTest` simulates `GenerationType.IDENTITY` by having the mocked `save()` assign an id on first invocation only, matching the real insert-then-update flow.
