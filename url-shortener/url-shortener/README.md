# URL Shortener

A URL shortener that converts long URLs into compact, base62-encoded short codes and redirects visitors from the short code back to the original URL. No update/delete, no custom vanity codes — see [`../design.md`](../design.md) for the full system design.

Built with Java 21 and Spring Boot 3.3. This is the **phase 1 MVP**: a single-node service backed by PostgreSQL, with no cache, rate limiting, expiration, or sharding yet (those land in phase 2 — see `CLAUDE.md` and `../design.md` §10).

## High-level architecture

```
  client ──POST /api/v1/shorten──▶ ShortenController ──▶ ShortenService ──▶ ShortUrlRepository ──▶ Postgres
                                                          (idempotent lookup-or-create + base62 encode)

  client ──GET /{code}──────────▶ RedirectController ──▶ ShortUrlRepository ──▶ Postgres
                                                          (302 redirect to longUrl, 404 if unknown)
```

- **Shorten** (`shorten` package): looks up the long URL first — if it was already shortened, returns the existing code (idempotent). Otherwise inserts a new row to get a DB-generated id, base62-encodes it (alphabet `0-9,a-z,A-Z`), and stores the code.
- **Redirect** (`redirect` package): looks up the short code and responds with an HTTP 302 to the original long URL, or 404 if the code doesn't exist.

## Code structure

```
src/main/java/com/example/urlshortener/
├── UrlShortenerApplication.java    Spring Boot entrypoint
├── shorten/                        The shorten vertical
│   ├── ShortenController.java      POST /api/v1/shorten
│   ├── ShortenService.java         Idempotent lookup-or-create + base62 encode
│   ├── ShortUrlRepository.java     Spring Data JPA repository
│   ├── ShortUrl.java               Entity: id, shortUrl, longUrl, createdAt
│   ├── Base62Encoder.java          Pure static id -> base62 code conversion
│   └── dto/                        ShortenRequest, ShortenResponse
├── redirect/
│   └── RedirectController.java     GET /{shortUrlCode} -> 302 or 404
└── exception/
    ├── ShortUrlNotFoundException.java
    └── GlobalExceptionHandler.java Central @RestControllerAdvice

src/main/resources/
└── application.yml                 Datasource + base-url config

src/test/java/com/example/urlshortener/
├── support/PostgresTestSupport.java   Shared Testcontainers Postgres base class for *IT tests
├── shorten/                            Base62EncoderTest, ShortenServiceTest (unit, Mockito)
└── ShortenFlowIT.java                  End-to-end shorten -> redirect integration test
```

**Suggested reading order for onboarding**: `Base62Encoder.java` → `ShortUrl.java` → `ShortUrlRepository.java` → `ShortenService.java` → `ShortenController.java` (write path, simplest to most composed), then `RedirectController.java` (the read path).

## How to use

### Configuration

The app reads these environment variables (see `application.yml`):

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_HOST` | no | `localhost` | PostgreSQL host |
| `DB_NAME` | no | `urlshortener` | Database name |
| `DB_USER` | no | `urlshortener` | Database user |
| `DB_PASSWORD` | no | `urlshortener` | Database password |
| `BASE_URL` | no | `http://localhost:8080` | Prefixed onto the base62 code in the `shortUrl` response |

### Running

```bash
# start a local Postgres if you don't have one
docker run -p 5432:5432 -e POSTGRES_DB=urlshortener -e POSTGRES_USER=urlshortener -e POSTGRES_PASSWORD=urlshortener -d postgres:16-alpine

mvn spring-boot:run
```

Or via docker-compose (app + Postgres together):

```bash
cd ..
docker compose up --build
```

The app listens on `http://localhost:8080`.

### Shorten a URL

```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com/some/very/long/path"}'
# {"shortUrl":"http://localhost:8080/1"}
```

Shortening the same `longUrl` again returns the same `shortUrl`.

### Redirect

```bash
curl -i http://localhost:8080/1
# HTTP/1.1 302 Found
# Location: https://example.com/some/very/long/path
```

See [`demo.http`](demo.http) for a runnable walkthrough.
