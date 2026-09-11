# Development Guide

## Monorepo Structure

Maven multi-module project, three modules under the root `pom.xml`:

```
notification-system/
├── notification-common/   # shared events, domain, provider contracts (library, auto-configured)
├── notification-service/  # public REST API + Kafka producer
├── notification-worker/   # Kafka consumer(s)
├── nginx/                 # reverse-proxy config used by docker-compose
├── design.md              # source-of-truth system design
└── spec/                  # phased spec/plan docs (phase-1-simple, phase-2-enhanced, phase-3-final)
```

## Getting Started

### Prerequisites

- Java 25 (all modules build with `<java.version>25</java.version>`)
- Maven 3.9+ (or use the Maven wrapper if one is added later)
- Docker + Docker Compose, for running Postgres/Redis/Kafka locally or the
  full stack

### Installation

```bash
git clone git@github.com:thaiquangquy/system-design.git
cd system-design/notification-system
mvn -pl notification-common -am install   # builds and installs the shared module first
```

## Development Workflow

### Building

Build all modules from the root:

```bash
mvn -B clean package
```

Build a single module and its dependencies (mirrors what the Dockerfiles do):

```bash
mvn -B -pl notification-service -am clean package -DskipTests
```

### Running locally (without Docker)

Start the infra dependencies only, then run the Spring Boot apps from your
IDE or `mvn spring-boot:run`. Default local ports (from each module's
`application.yml`) assume the compose host ports below:

- Postgres: `localhost:25432` (db/user/password: `notifications`/`notifications`/`notifications`)
- Redis: `localhost:16379`
- Kafka: `localhost:19092`

```bash
docker compose up postgres redis kafka
mvn -pl notification-service spring-boot:run   # listens on :8080
mvn -pl notification-worker spring-boot:run    # listens on :8081
```

### Testing

Tests follow the module's Surefire include pattern (`*Test`, `*Tests`,
`*TestCase`, `*IT`). Per the repo-wide [`CLAUDE.md`](../CLAUDE.md):
simple classes use `@ExtendWith(MockitoExtension.class)` + `@Mock`/
`@InjectMocks`; `@SpringBootTest`/Testcontainers `*IT` tests are reserved for
real Spring wiring, DB, Redis, or Kafka behavior.

```bash
mvn -B test          # unit tests
mvn -B verify         # unit + integration tests + formatting check
```

`notification-service` pulls in Testcontainers modules for Postgres, Kafka,
and Redis, and `awaitility` for async assertions — used by its `*IT` tests.

### Formatting / Linting

Every module runs `spotless-maven-plugin` (Palantir Java Format) bound to
the `verify` phase:

```bash
mvn -B spotless:check   # check formatting
mvn -B spotless:apply   # auto-fix formatting
```

`mvn verify` fails the build if formatting is out of date.

## Environment Variables

`notification-service` and `notification-worker` read their infra
connection details from environment variables (see each module's
`application.yml` for defaults), all of which are set for you in
`docker-compose.yml`:

| Variable | Used by | Default (local, non-Docker) |
|---|---|---|
| `DB_HOST` | notification-service | `localhost:25432` |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | notification-service | `notifications` / `notifications` / `notifications` |
| `REDIS_HOST` / `REDIS_PORT` | notification-service | `localhost` / `16379` |
| `KAFKA_BOOTSTRAP_SERVERS` | notification-service, notification-worker | `localhost:19092` |
| `PUSH_PROVIDER_ENDPOINT` / `PUSH_PROVIDER_API_KEY` | notification-service | `http://localhost:9001/push` / `stub-push-key` |
| `SMS_PROVIDER_ENDPOINT` / `SMS_PROVIDER_API_KEY` | notification-service | `http://localhost:9002/sms` / `stub-sms-key` |
| `EMAIL_PROVIDER_ENDPOINT` / `EMAIL_PROVIDER_API_KEY` | notification-service | `http://localhost:9003/email` / `stub-email-key` |

## Project Structure Details

- **`notification-common`** ships a
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  file (`NotificationProviderAutoConfiguration`), so its stub providers are
  picked up automatically by any module that depends on it — no
  `@ComponentScan`/`scanBasePackages` needed in the consuming apps.
- **`notification-service`** organizes by technical layer under
  `com.example.notification`: `controller` (one per channel), `service`
  (channel implementations behind `NotificationServiceRegistry`), `dto`,
  `repository`/`domain` (JPA), `cache` (Redis-backed user/device lookups),
  `messaging` (Kafka producer), and `config`.
- **`notification-worker`** is intentionally thin: one Kafka consumer class
  per channel under `consumer/`, wired via `config/KafkaConsumerConfig`.

## CI/CD

No CI configuration (`.github/workflows`, `.gitlab-ci.yml`, etc.) exists in
this repo yet — builds/tests/formatting checks are run locally via the Maven
commands above.

## References

- [`design.md`](design.md) — full system design write-up (requirements,
  phased architecture, deep-dive considerations).
- [`spec/README.md`](spec/README.md) — index of phase specs/plans and the
  tech stack conventions they follow.
- [`spec/tech-debt.md`](spec/tech-debt.md) — known gaps and follow-up work.
