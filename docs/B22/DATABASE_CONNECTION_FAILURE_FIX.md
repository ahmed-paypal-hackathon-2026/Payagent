# B22.1 — Database Connection Failure Fix

## Root cause

Render Blueprint mapped `PAYAGENT_DB_URL` to `payagent-db` `connectionString`, which is a PostgreSQL URI (`postgresql://user:pass@host:port/db`). The Spring datasource expected a JDBC URL with separate `username`/`password`. The application previously bound `${PAYAGENT_DB_URL}` directly into `spring.datasource.url`. The `DbUrlEnvironmentPostProcessor` then blindly prefixed `jdbc:` and kept the embedded credentials, producing:

`jdbc:postgresql://USER:PASSWORD@HOST/DB`

which `org.postgresql.Driver` rejects.

## Fix

Spring datasource now builds the URL from separate pieces:

```yaml
url: jdbc:postgresql://${PAYAGENT_DB_HOST:localhost}:${PAYAGENT_DB_PORT:5432}/${PAYAGENT_DB_NAME:payagent}
```

credentials still supplied via `spring.datasource.username` and `spring.datasource.password`.

Render Blueprint now maps:
- `PAYAGENT_DB_HOST` ← `host`
- `PAYAGENT_DB_PORT` ← `port`
- `PAYAGENT_DB_NAME` ← `database`
- `PAYAGENT_DB_USER` ← `user`
- `PAYAGENT_DB_PASSWORD` ← `password`

Files changed:
- `services/api/src/main/resources/application.yml`
- `render.yaml`
- `docker-compose.yml`
- `.env` (untracked local)
- `.env.example`
- `docs/LOCAL_DEVELOPMENT.md`
- `services/api/src/test/.../DatasourceUrlConstructionTest.java` (new)
- `services/api/src/test/.../SettlementWebhookPostgreSqlServiceTest.java` (skip when no PG env)
- Deleted: `DbUrlEnvironmentPostProcessor.java`, `DbUrlEnvironmentPostProcessorTest.java`, `META-INF/spring.factories`

## Verification

- `./mvnw -B clean test` → **BUILD SUCCESS**; 366 tests, 0 failures, 0 errors.
- `./mvnw -B -q -Dtest=FlywayPostgreSqlVerificationTest,PayPalEventsPostgreSqlConcurrencyTest,SettlementWebhookPostgreSqlServiceTest test` → 6/6 pass on disposable PG 16.4.
- `./mvnw -B -DskipTests package` → **BUILD SUCCESS**, jar `payagent-api-0.0.1-SNAPSHOT.jar` produced.
- Packaged `BOOT-INF/classes/application.yml` contains the new URL; `${PAYAGENT_DB_URL}` absent; `spring.factories` absent.

## Remaining actions

- Redeploy backend only after the environment registry contains the five `PAYAGENT_DB_*` keys (Rendear will pick them up on Blueprint sync).
- Flyway must run against the production database before/as part of backend startup.
- Do not deploy yet without explicit authorization.

## Limitations

- No live database connection tested in this phase.
- Local `.env` was repaired (host/port/name only) but not validated against docker-compose.
