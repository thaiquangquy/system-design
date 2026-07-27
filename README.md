# system-design

## [rate-limiter](rate-limiter/my-rate-limiter/README.md): 
- A rate limiter service that implements the Sliding Window Counter algorithm, backed by Redis. It exposes a fast, unauthenticated endpoint to check/consume rate-limit quota for a key, and an admin-authenticated CRUD API to manage the rules that define limits per key prefix.
- Built with Java 21 and Spring Boot 3.3.

## [sample-api](common/sample-api)
- A minimal downstream API with two endpoints, `GET /user` and `GET /login`, returning canned JSON. Stands in for a real service so the gateway + rate limiter can be exercised end-to-end. No auth, no persistence.
- Shared under `common/` since it's a stand-in dependency for other system-design projects, not specific to rate-limiter.
- Built with Java 21 and Spring Boot 3.3.

## [gateway](rate-limiter/gateway)
- Sits in front of `sample-api`. For every request it calls the rate-limiter's `/api/v1/rate-limit/check` endpoint (key = route, ip = client IP, passed as separate params so each IP gets its own quota under one shared per-route rule), and only forwards the request downstream if allowed; otherwise it returns 429 immediately. Fails closed (503) if the rate limiter is unreachable.
- See [rate-limiter/gateway/demo.http](rate-limiter/gateway/demo.http) for an end-to-end walkthrough (seed rules, hit endpoints, observe 200→429).
- Built with Java 21 and Spring Boot 3.3, plain Spring MVC + `RestTemplate` (no Spring Cloud Gateway).

## Running everything together

```bash
cd rate-limiter
docker compose up --build
```

Brings up Redis, rate-limiter (`:8080`), sample-api (`:8081`), and gateway (`:8082`), wired together and gated on `/actuator/health` so each service only starts once its dependencies are actually ready. Once all four containers report healthy (`docker compose ps`), run through [rate-limiter/gateway/demo.http](rate-limiter/gateway/demo.http) against `localhost` to exercise the full e2e flow.
