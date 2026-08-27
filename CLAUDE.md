# CLAUDE.md

This file gives rules for all projects in this repository (`rate-limiter/`, `key-value-store/`,
`url-shortener/`, and future projects). Each project also has its own `CLAUDE.md` with project details. This file gives
rules that apply to all projects.

## Tech stack & codding style

- Spring boot 4.x with java 25 LTS
- Use modern Java features and spring boot utility class when possible for performance and clean code sake
- Use `var` for obviously return type to shorten the code
- Use SOLID to keep abstraction on the code base. i.e: rely on interface instead of concrete class
- Use Google Java style for coding style

## Java and Spring Boot test rules

- Simple classes: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`. No `@SpringBootTest`.
- `@SpringBootTest` / Testcontainers `*IT`: only for real Spring behavior (real DB, real Redis, real HTTP, bean wiring).
- Constructor injection only. No field injection.
- Config values: `@ConfigurationProperties` record, not `@Value`. Group related values, inject as one object.
- Lombok `@RequiredArgsConstructor` on final fields is fine. Never use Lombok as an excuse to keep field injection.
- Legacy field-injected class: `@Mock`/`@InjectMocks` for constructor parts, `ReflectionTestUtils.setField()` in
  `@BeforeEach` for the rest.
