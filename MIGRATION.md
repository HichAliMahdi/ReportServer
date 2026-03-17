# Database Migration Guide (H2 -> PostgreSQL/MySQL)

This project now supports production database profiles with Flyway migrations:

- `postgresql` profile
- `mysql` profile

## 1) Pick a Production Profile

PostgreSQL:

```bash
export SPRING_PROFILES_ACTIVE=postgresql
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/reportserver'
export SPRING_DATASOURCE_USERNAME='reportserver'
export SPRING_DATASOURCE_PASSWORD='change-me'
```

MySQL:

```bash
export SPRING_PROFILES_ACTIVE=mysql
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/reportserver?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME='reportserver'
export SPRING_DATASOURCE_PASSWORD='change-me'
```

## 2) Run Flyway Migrations

Migrations run automatically on app startup when `postgresql` or `mysql` profile is active.

- PostgreSQL scripts: `src/main/resources/db/migration/postgresql`
- MySQL scripts: `src/main/resources/db/migration/mysql`

## 3) Verify Connection Pooling (HikariCP)

Tune with environment variables:

- `DB_POOL_MIN_IDLE`
- `DB_POOL_MAX_SIZE`
- `DB_POOL_CONNECTION_TIMEOUT_MS`
- `DB_POOL_IDLE_TIMEOUT_MS`
- `DB_POOL_MAX_LIFETIME_MS`

## 4) Docker Production

Use:

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

The production compose file starts PostgreSQL and runs the app with `SPRING_PROFILES_ACTIVE=postgresql`.

## 5) Data Migration Notes

Schema migration is automated by Flyway. Existing H2 data migration is environment-specific.

Recommended approaches:

1. Fresh production bootstrap: start with empty PostgreSQL/MySQL schema and recreate users/datasources.
2. Controlled migration: export H2 data to SQL/CSV and import using a DBA-reviewed script.

For critical data, test migration in staging before production cutover.
