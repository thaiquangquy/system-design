# system-design

## [rate-limiter](rate-limiter/my-rate-limiter/README.md): 
- A rate limiter service that implements the Sliding Window Counter algorithm, backed by Redis. It exposes a fast, unauthenticated endpoint to check/consume rate-limit quota for a key, and an admin-authenticated CRUD API to manage the rules that define limits per key prefix.
- Built with Java 21 and Spring Boot 3.3.

## [sample-api](common/sample-api)
- A minimal downstream API with two endpoints, `GET /user` and `GET /login`, returning canned JSON. Stands in for a real service so the gateway + rate limiter can be exercised end-to-end. No auth, no persistence.
- Shared under `common/` since it's a stand-in dependency for other system-design projects, not specific to rate-limiter.
- Built with Java 21 and Spring Boot 3.3.

## [key-value-store](key-value-store/kv-store/README.md)
- A single-node key-value store: a disk-backed LSM-style storage engine (write-ahead log + memtable + SSTable + bloom filter) behind a `put(key, value)`/`get(key)` REST API. Phase 1 of the design in [key-value-store/key-value-storage.md](key-value-store/key-value-storage.md) -- partitioning, replication, quorum, gossip, and anti-entropy are documented as future phases, not yet built.
- No external dependencies (no Redis, no DB) -- every test, including a full HTTP round-trip test, is plain JUnit 5, no Docker required.
- Built with Java 21 and Spring Boot 3.3.

## [gateway](rate-limiter/gateway)
- Sits in front of `sample-api`. For every request it calls the rate-limiter's `/api/v1/rate-limit/check` endpoint (key = route, ip = client IP, passed as separate params so each IP gets its own quota under one shared per-route rule), and only forwards the request downstream if allowed; otherwise it returns 429 immediately. Fails closed (503) if the rate limiter is unreachable.
- See [rate-limiter/gateway/demo.http](rate-limiter/gateway/demo.http) for an end-to-end walkthrough (seed rules, hit endpoints, observe 200→429).
- Built with Java 21 and Spring Boot 3.3, plain Spring MVC + `RestTemplate` (no Spring Cloud Gateway).

## [url-shortener](url-shortener/url-shortener/README.md)
- Converts long URLs into compact base62-encoded short codes and redirects short codes back to the original URL. Implements the full [`url-shortener/design.md`](url-shortener/design.md): shorten/redirect, a dedicated ID-ticket generator, Redis cache, per-IP rate limiting, 1-year expiration, and opt-in consistent-hash sharding with real primary/replica streaming replication (`docker compose --profile sharded up`).
- Built with Java 21 and Spring Boot 3.3, PostgreSQL + Redis.

## Running everything together

```bash
cd rate-limiter
docker compose up --build
```

Brings up Redis, rate-limiter (`:8080`), sample-api (`:8081`), and gateway (`:8082`), wired together and gated on `/actuator/health` so each service only starts once its dependencies are actually ready. Once all four containers report healthy (`docker compose ps`), run through [rate-limiter/gateway/demo.http](rate-limiter/gateway/demo.http) against `localhost` to exercise the full e2e flow.

```bash
cd url-shortener
docker compose up --build
```

Brings up Postgres, Redis, and url-shortener (`:8080`), gated on `/actuator/health`. Once healthy, run through [url-shortener/url-shortener/demo.http](url-shortener/url-shortener/demo.http) against `localhost`.

```bash
cd url-shortener
docker compose --profile sharded up --build
```

Brings up the full sharded topology instead: a dedicated id_ticket DB, two consistent-hash shards (each a real Postgres primary + streaming-replication replica), two app instances, and an nginx load balancer on `:8090`. See [url-shortener/url-shortener/README.md](url-shortener/url-shortener/README.md#running-sharded-demo-scale-simulation-of-designmd-710) for how to inspect the shards directly.
