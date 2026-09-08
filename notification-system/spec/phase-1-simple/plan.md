# Phase 1 — Simple: Plan

Implements [`spec.md`](spec.md). New Spring Boot module:
`notification-system/notification-service/` (sibling pattern to
`url-shortener/url-shortener/`, `key-value-store/kv-store/`).

## Module layout

```
notification-service/
  src/main/java/.../notification/
    controller/   PushController, SmsController, EmailController
    service/      NotificationService (interface) + impl per channel
    provider/     PushProvider, SmsProvider, EmailProvider (interfaces)
                  StubPushProvider, StubSmsProvider, StubEmailProvider (impls)
    domain/       User, Device (JPA entities)
    repository/   UserRepository, DeviceRepository (Spring Data JPA)
    config/       NotificationProviderProperties (@ConfigurationProperties record)
    dto/          NotificationRequest, NotificationResponse
  src/main/resources/application.yml
  src/test/java/...
```

## Tasks

1. Scaffold `notification-service` — Spring Boot 4.x, Java 25, Spring Web,
   Spring Data JPA, H2 (dev) / Postgres (prod profile) dependencies.
2. Create `User` and `Device` JPA entities + `UserRepository` /
   `DeviceRepository` (Spring Data JPA interfaces — no custom impl needed).
3. Create `NotificationProviderProperties` record (`@ConfigurationProperties`)
   grouping provider endpoint/credential config per channel.
4. Define `PushProvider`, `SmsProvider`, `EmailProvider` interfaces — one
   method each, e.g. `SendResult send(SendCommand command)`.
5. Implement stub providers (`StubPushProvider`, etc.) that log the payload
   and return a fake `provider_message_id` — swappable later for real
   APNs/FCM/SMS/Email SDK clients without changing callers.
6. Implement `NotificationService` interface + one impl per channel
   (`PushNotificationService`, `SmsNotificationService`,
   `EmailNotificationService`), each: resolve `User`/`Device` from repo,
   validate contact info present, call the matching provider interface.
7. Implement `PushController`, `SmsController`, `EmailController` exposing
   the three `POST /v1/notifications/*` endpoints from the spec, mapping
   request/response DTOs.
8. Add request validation (Bean Validation annotations on DTOs) for email
   format, phone format (E.164), non-empty content.
9. Add `application.yml` with H2 in-memory DB for local dev.
10. Write unit tests: `@ExtendWith(MockitoExtension.class)` for each
    `NotificationService` impl (mock repos + provider interfaces), and
    `@WebMvcTest`/`MockMvc` for each controller.

## Testing

- Unit: mock `UserRepository`/`DeviceRepository`/provider interfaces per
  channel service — no `@SpringBootTest`.
- One `@SpringBootTest` smoke test hitting all three endpoints against H2 +
  stub providers to confirm wiring.

## Definition of done

- `./mvnw test` (or gradle equivalent) passes.
- Manually POST to all three endpoints locally and get `SENT` responses.
- No references to queues, cache, or auth anywhere in this module — those
  arrive in Phase 2/3.
