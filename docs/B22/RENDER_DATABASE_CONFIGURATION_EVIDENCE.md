# Render Database Configuration Evidence

## Implemented
- `render.yaml` maps `PAYAGENT_DB_HOST`→`host`, `PAYAGENT_DB_PORT`→`port`, `PAYAGENT_DB_NAME`→`database`, `PAYAGENT_DB_USER`→`user`, `PAYAGENT_DB_PASSWORD`→`password` for `payagent-api`.
- `application.yml` constructs `jdbc:postgresql://${PAYAGENT_DB_HOST:localhost}:${PAYAGENT_DB_PORT:5432}/${PAYAGENT_DB_NAME:payagent}`.

## Locally tested
- Default placeholder resolves to `localhost:5432/payagent`.
- Custom placeholders resolve correctly.
- Packaged jar contains the new config and no residual `spring.factories` pointing at removed processor.

## Unknown
- Live Render values are not printed; presence in Dashboard not verified (no CLI/dashboard access).
- Whether hosted `payagent-db` exists and supplies these properties.
