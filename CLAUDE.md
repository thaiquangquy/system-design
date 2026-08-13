# CLAUDE.md

This file gives rules for all projects in this repository (`rate-limiter/`, `key-value-store/`,
`url-shortener/`, and future projects). Each project also has its own `CLAUDE.md` with project
details. This file gives rules that apply to all projects.

## Java and Spring Boot test rules

- Use SOLID to keep abstraction on the code base. i.e: rely on interface instead of concrete class
- Do not use `@SpringBootTest` for simple classes. Use `@ExtendWith(MockitoExtension.class)` with
  `@Mock` and `@InjectMocks` instead. `@SpringBootTest` starts the full Spring context. This takes
  seconds, not milliseconds. Use `@SpringBootTest`, or a Testcontainers `*IT` test, only to test real
  Spring behavior: a real database, a real Redis instance, a real HTTP call, or wiring between beans.
- Use constructor injection, not field injection. This rule applies also to config values. For
  config values, use `@ConfigurationProperties` instead of `@Value`. Group related config values
  into one properties class (a record works well), and inject that object through the constructor.
  Spring binds these values by name. Example:
  ```java
  @ConfigurationProperties(prefix = "ratelimit")
  public record RateLimitProperties(int capacity, int refillPerSecond) {}
  ```
  Constructor injection lets you write `new Foo(mockA, someProperties)` directly, with no framework.
  Field injection forces you to start Spring, or to use `ReflectionTestUtils.setField(...)`. Both
  options are worse than constructor injection.
- Use Lombok to remove constructor code. Do not use Lombok as a reason to keep field injection.
  `@RequiredArgsConstructor` on `final` fields creates the same constructor you would write by hand.
- If a class must keep field injection (for example: the class is not yet updated, or the change is
  out of scope), test it a different way. Use `@Mock` and `@InjectMocks` for the constructor-injected
  parts. Use `ReflectionTestUtils.setField(...)` in `@BeforeEach` for the `@Value` fields. This method
  also needs no Spring context.
