# URL Shortener

A URL shortener that converts long URLs into compact, base62-encoded short codes and redirects visitors from the short code back to the original URL. No update/delete, no custom vanity codes — see [`../design.md`](../design.md) for the full system design.

Built with Java 21 and Spring Boot 3.3, backed by PostgreSQL + Redis. Implements the full design: shorten/redirect, a dedicated ID-ticket generator, a Redis cache in front of redirects, per-IP rate limiting on shorten, 1-year expiration with a purge job, and consistent-hash sharding with primary/replica routing (opt-in — see Sharding below). See `CLAUDE.md` for implementation details.

## High-level architecture

```
  client ──POST /api/v1/shorten──▶ ShortenController ─▶ ShortenService ─▶ IdTicketService (idgen DB)
                                                              │
                                                              ▼
                                             ShardedShortUrlOperations ─▶ Postgres (shard-routed)
                                    (idempotent lookup-or-create + base62 encode)

  client ──GET /{code}──────────▶ RedirectController ─▶ RedirectService ─▶ RedirectCacheService (Redis)
                                                              │ (cache miss)
                                                              ▼
                                             ShardedShortUrlOperations ─▶ Postgres (shard-routed)
                                    (302 redirect to longUrl, 404 if unknown)

  POST /api/v1/shorten also passes through RateLimitFilter (per-IP, Redis-backed) before reaching
  the controller. ExpiredUrlPurgeJob runs hourly, deleting rows past their expiresAt.
```

- **Shorten** (`shorten` package): looks up the long URL first — if it was already shortened, returns the existing code (idempotent). Otherwise gets a globally unique id from the ID-ticket generator, base62-encodes it (alphabet `0-9,a-z,A-Z`), and inserts the row.
- **Redirect** (`redirect` package): cache-aside lookup — Redis hit skips the DB; miss loads from Postgres and populates the cache — then responds with an HTTP 302 to the original long URL, or 404 if the code doesn't exist.
- **ID generation** (`idgen` package): a dedicated `id_ticket` table whose only job is handing out unique, monotonically increasing ids — decoupled from `short_url` so it never needs to be sharded (design.md §10).
- **Sharding** (`sharding` package): off by default. When enabled, a consistent-hash router picks a shard per shortUrl code, with writes routed to each shard's primary and reads to its replica.

## Code structure

```
src/main/java/com/example/urlshortener/
├── UrlShortenerApplication.java    Spring Boot entrypoint (@EnableScheduling)
├── shorten/                        The shorten vertical
│   ├── ShortenController.java      POST /api/v1/shorten
│   ├── ShortenService.java         Idempotent lookup-or-create + base62 encode
│   ├── ShortUrlRepository.java     Spring Data JPA repository
│   ├── ShortUrl.java               Entity: id, shortUrl, longUrl, createdAt, expiresAt
│   ├── Base62Encoder.java          Pure static id -> base62 code conversion
│   └── dto/                        ShortenRequest, ShortenResponse
├── redirect/
│   ├── RedirectController.java     GET /{shortUrlCode} -> 302 or 404
│   └── RedirectService.java        Cache-aside lookup
├── idgen/                          Dedicated ID-ticket generator (id_ticket table)
├── cache/                          RedirectCacheService (Redis cache-aside)
├── ratelimit/                      RateLimitFilter (per-IP, Redis fixed-window)
├── expiration/                     ExpiredUrlPurgeJob (@Scheduled)
├── sharding/                       Consistent-hash router + shard/replica DataSource routing
└── exception/
    ├── ShortUrlNotFoundException.java
    └── GlobalExceptionHandler.java Central @RestControllerAdvice

src/main/resources/
├── application.yml                 Datasource, Redis, rate-limit, expiration config
└── application-sharded.yml         Shard list + ddl-auto=none, activated by the "sharded" profile

src/test/java/com/example/urlshortener/
├── support/IntegrationTestSupport.java   Shared Testcontainers Postgres+Redis base for *IT tests
├── sharding/ShardRoutingIT.java          Proves shard routing against real, independent Postgres containers
└── <package>/                            Unit tests (Mockito) + feature ITs (cache, rate-limit, expiration)
```

**Suggested reading order for onboarding**: `Base62Encoder.java` → `ShortUrl.java` → `idgen/IdTicketService.java` → `ShortenService.java` → `ShortenController.java` (write path), then `cache/RedirectCacheService.java` → `RedirectService.java` (read path), then `sharding/` (read the package doc comment on `ShardedShortUrlOperations` first — it explains the transaction-per-shard-call design).

## How to use

### Configuration

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_HOST` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | no | `localhost` / `urlshortener` / `urlshortener` / `urlshortener` | PostgreSQL connection |
| `REDIS_HOST` / `REDIS_PORT` | no | `localhost` / `6379` | Redis connection |
| `BASE_URL` | no | `http://localhost:8080` | Prefixed onto the base62 code in the `shortUrl` response |
| `RATELIMIT_LIMIT` / `RATELIMIT_WINDOW_SECONDS` | no | `10` / `60` | Per-IP shorten throttle |
| `EXPIRATION_PURGE_CRON` | no | `0 0 * * * *` (hourly) | Expired-URL purge schedule |

### Running (single node, no sharding)

```bash
cd ..
docker compose up --build
```

Brings up Postgres, Redis, and the app on `http://localhost:8080`. See [`demo.http`](demo.http) for a runnable walkthrough.

```bash
curl -X POST http://localhost:8080/api/v1/shorten -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com/some/very/long/path"}'
# {"shortUrl":"http://localhost:8080/1"}   (shortening the same longUrl again returns the same code)

curl -i http://localhost:8080/1
# HTTP/1.1 302 Found
# Location: https://example.com/some/very/long/path
```

### Running sharded (demo-scale simulation of design.md §7/§10)

```bash
cd ..
docker compose --file docker-compose-sharded up -d
```

Brings up a dedicated id_ticket DB, two consistent-hash shards (each a real Postgres primary + streaming-replication replica), two app instances (`SPRING_PROFILES_ACTIVE=sharded`), and an nginx load balancer on `http://localhost:8090`. Use `8090` instead of `8080` for the same requests above. To see the sharding for yourself:

```bash
# rows land on different physical shards depending on the shortUrl code's hash
docker exec url-shortener-shard0-primary-1 psql -U urlshortener -d urlshortener -c "SELECT short_url, long_url FROM short_url;"
docker exec url-shortener-shard1-primary-1 psql -U urlshortener -d urlshortener -c "SELECT short_url, long_url FROM short_url;"

# each replica mirrors its primary via real streaming replication
docker exec url-shortener-shard0-replica-1 psql -U urlshortener -d urlshortener -c "SELECT short_url FROM short_url;"
```

This is a small-scale simulation of the mechanism (2 shards, not the 36.5 TB / 365B-row horizon from `design.md` §2) — see `CLAUDE.md`'s Sharding section for how the routing and replication actually work.

#### Debugging a third instance locally (e.g. in IntelliJ)

To run and debug an app instance on the host, joining the same shard ring as `url-shortener-1`/`url-shortener-2`, instead of building/running it as a container:

```bash
cd ..
docker compose --file docker-compose-sharded up -d
```

Create a Run/Debug configuration for `com.example.urlshortener.UrlShortenerApplication` with:

```
SPRING_PROFILES_ACTIVE=sharded-local
DB_HOST=localhost:25432
REDIS_HOST=localhost
REDIS_PORT=16379
BASE_URL=http://localhost:8090
```

The `sharded-local` profile (`application-sharded-local.yml`) overrides the shard JDBC URLs to use `localhost` + the host-mapped ports (`25433`-`25436`) published by the `sharded` compose services, instead of the Docker-network hostnames `application-sharded.yml` uses by default. `nginx.conf`'s upstream includes `host.docker.internal:8080` for exactly this instance, so requests to `http://localhost:8090` will round-robin across it too — start `url-shortener-1`/`url-shortener-2` as well if you want the full 3-way rotation, or leave them out to send everything to your local instance.
