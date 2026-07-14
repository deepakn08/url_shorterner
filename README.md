# URL Shortener

A small Spring Boot service that turns long URLs into short codes and redirects visitors
to the original link when they hit a short code.

## Features

- `POST /api/urls` — shorten a URL, with optional custom alias.
- `GET /{code}` — 301 redirect to the original URL, 404 if the code is unknown/inactive/expired.
- Duplicate URLs (submitted without a custom alias) resolve to the **same** existing short
  code instead of minting a new one.
- Custom aliases bypass that dedup: you can always mint a fresh, memorable alias for a URL
  even if it was already shortened before.
- Short codes are generated with `SecureRandom` over a 62-character URL-safe alphabet
  (`A-Z a-z 0-9`) and are guaranteed unique by a database unique constraint; on the rare
  collision, generation retries with a fresh code (see "Short code generation" below).

## Tech stack

- Java 21, Spring Boot 4.1 (Web, Data JPA, Validation)
- PostgreSQL (tested against a Supabase-hosted instance)
- Maven (wrapper included, no local Maven install needed)
- JUnit 5, Mockito, MockMvc for tests

## Prerequisites

- Java 21 (JDK)
- A PostgreSQL database (a free [Supabase](https://supabase.com) project works well)

## Configuration

The app reads its datasource config from environment variables (see
`src/main/resources/application.yml`):

| Variable            | Description                                              |
|----------------------|-----------------------------------------------------------|
| `DATABASE_HOST`      | JDBC URL, e.g. `jdbc:postgresql://<host>:6543/postgres`   |
| `DATABASE_USER`      | Database username                                         |
| `DATABASE_PASSWORD`  | Database password                                         |

Other settings (with defaults) in `application.yml`:

- `server.port` — `8080`
- `app.base-short-url-domain` — `http://localhost:8080` (used to build the `short_url` in responses)
- `app.short-code.length` — `7`
- `app.short-code.max-generation-attempts` — `5`

Schema is created/updated automatically on startup (`spring.jpa.hibernate.ddl-auto=update`) —
no separate migration step is needed for local development.

## Running the service

```bash
# macOS/Linux
export DATABASE_HOST="jdbc:postgresql://<host>:6543/postgres"
export DATABASE_USER="postgres"
export DATABASE_PASSWORD="<password>"
./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
$env:DATABASE_HOST = "jdbc:postgresql://<host>:6543/postgres"
$env:DATABASE_USER = "postgres"
$env:DATABASE_PASSWORD = "<password>"
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

## Running the tests

Tests are self-contained (Mockito unit tests for the service layer, MockMvc slice tests for
the controllers) and don't need a live database or the `DATABASE_*` environment variables:

```bash
./mvnw test
```

## API examples

**Shorten a URL:**

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com/some/very/long/path"}'
```

```json
{
  "short_code": "aZ3xQ9k",
  "short_url": "http://localhost:8080/aZ3xQ9k",
  "original_url": "https://example.com/some/very/long/path"
}
```

**Shorten with a custom alias:**

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com/some/very/long/path", "custom_alias": "my-link"}'
```

Returns `409 Conflict` if `my-link` is already taken.

**Redirect:**

```bash
curl -i http://localhost:8080/aZ3xQ9k
# HTTP/1.1 301 Moved Permanently
# Location: https://example.com/some/very/long/path
```

Unknown codes return `404 Not Found`.

## Short code generation — why it won't collide

Codes are 7 random characters drawn from a 62-character alphabet (`A-Z`, `a-z`, `0-9`), giving
~3.5 trillion possible codes. Uniqueness isn't just probabilistic, though: `short_code` has a
database-level unique constraint (`uk_urls_short_code`), so a collision is impossible to persist
even under concurrent requests. If a generated candidate does collide (astronomically unlikely
at this keyspace size, but not zero), the insert fails, and the service retries with a freshly
generated code in a new transaction, up to `app.short-code.max-generation-attempts` (default 5)
before giving up with a 500. Each attempt runs in its own transaction because Postgres aborts the
whole transaction on a unique-constraint violation — reusing one transaction across retries would
make every attempt after the first fail regardless of the new code.

## Design decisions

- **Duplicate URLs**: shortening the same URL twice (without a custom alias) returns the
  existing short code rather than creating a new row. This is enforced by a unique constraint
  on a SHA-256 hash of the URL (`url_hash`), not on the raw URL text, so the uniqueness check
  stays cheap regardless of URL length.
- **Custom aliases are exempt from dedup**: requesting a custom alias always creates a new
  mapping for that exact code, even if the same URL already has an auto-generated code. A
  custom alias is an explicit, deliberate request — it shouldn't silently return someone else's
  (or your own) previous auto-generated code instead.
- **Soft expiry/deactivation**: `Url` has `is_active` and `expires_at` columns. Redirect and
  dedup lookups both treat inactive or expired rows as if they don't exist (404 on redirect,
  a fresh code is generated on re-shorten), without deleting the row.
