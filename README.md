# TaskFlow backend

This directory contains the Spring Boot REST API for TaskFlow. It is responsible for authentication,
authorization, users, tasks, attachments, audit events, database migrations, and all business rules.
The Angular client is documented separately in [`../frontend/README.md`](../frontend/README.md), which
also contains the end-user guide.

## What the backend does

- authenticates users with a JWT stored in an `HttpOnly` cookie;
- applies isolated `ADMIN`, `MANAGER`, and `WORKER` role permissions;
- provides paged account search, profile and role updates, activation controls, one-time temporary
  passwords, and per-user audit timelines for administrators;
- limits managers to tasks they created and workers to tasks assigned to them;
- validates task assignment and progress transitions;
- calculates `OVERDUE` from a task deadline without storing it as a progress state;
- stores metadata in PostgreSQL and applies schema changes with Flyway;
- stores attachments locally or in a private Supabase Storage bucket;
- protects mutations with CSRF validation and records security-relevant audit events;
- returns structured problem responses without exposing stack traces to API clients.

## Technology

- Java 17 source compatibility;
- Spring Boot 3.5 and Spring Security;
- Spring Data JPA with PostgreSQL;
- Flyway database migrations;
- Maven Wrapper;
- H2 in PostgreSQL compatibility mode for integration tests;
- Docker deployment on Java 21.

## Directory map

```text
src/main/java/com/example/taskmanagement/
  attachment/   Attachment authorization and storage implementations
  audit/        Persistent security and domain audit events
  auth/         Login, logout, JWT cookies, and authenticated-user loading
  common/       Shared API errors and exception handling
  config/       Security and application configuration
  task/         Task API, business rules, and persistence
  user/         User API and persistence
src/main/resources/
  db/migration/ Flyway schema migrations
  application.yml
  application-prod.yml
src/test/       Integration and application tests
Dockerfile      Render-compatible multi-stage image
```

## Local development

### Requirements

- Java 17 or newer;
- Docker with Docker Compose for the repository's local PostgreSQL service.

From the repository root, copy the safe environment template and start PostgreSQL:

```powershell
Copy-Item .env.example .env
Copy-Item .env backend\.env
docker compose up -d postgres
```

Replace every placeholder secret before starting the application. The root `.env` is read by Docker
Compose, while `backend/.env` is imported by Spring Boot when the application starts from this
directory. Neither file should be committed.

Start the API on `http://localhost:8080`:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

On Unix-like systems, use `./mvnw spring-boot:run`.

## Configuration

Important environment variables are listed below. Keep secrets in the deployment platform's secret
manager, not in source control.

| Variable                      | Purpose                                                         |
| ----------------------------- | --------------------------------------------------------------- |
| `DB_URL`                      | JDBC PostgreSQL URL, including the database name                |
| `DB_USERNAME`                 | PostgreSQL login name                                           |
| `DB_PASSWORD`                 | PostgreSQL database password                                    |
| `JWT_SECRET`                  | Signing secret containing at least 32 random bytes              |
| `JWT_TTL`                     | Optional access-token lifetime; defaults to `15m`               |
| `SEED_USERS`                  | Creates the demo users when enabled and the user table is empty |
| `SEED_PASSWORD`               | Initial password shared by the seeded demo accounts             |
| `BOOTSTRAP_ADMIN_EMAIL`       | Initial administrator email when no active administrator exists |
| `BOOTSTRAP_ADMIN_PASSWORD`    | Initial administrator password; 15 to 200 characters            |
| `BOOTSTRAP_ADMIN_FIRST_NAME`  | Initial administrator first name                                |
| `BOOTSTRAP_ADMIN_LAST_NAME`   | Initial administrator last name                                 |
| `FRONTEND_ORIGIN`             | Exact allowed browser origin, without a trailing slash          |
| `COOKIE_SECURE`               | Must be `true` in HTTPS production environments                 |
| `COOKIE_SAME_SITE`            | Cookie SameSite policy; production uses `Strict`                |
| `REQUIRE_HTTPS`               | Requires forwarded/public HTTPS when `true`                     |
| `ATTACHMENT_STORAGE_PROVIDER` | `local` or `supabase`                                           |
| `ATTACHMENT_STORAGE`          | Local attachment directory when the provider is `local`         |
| `SUPABASE_URL`                | Supabase project URL when using Supabase Storage                |
| `SUPABASE_SECRET_KEY`         | Backend-only Supabase secret key                                |
| `SUPABASE_STORAGE_BUCKET`     | Private storage bucket; defaults to `task-attachments`          |

The complete safe template is [`../.env.example`](../.env.example).

## Supabase

For a persistent Render service, use the Supabase **Session pooler** connection because it is
IPv4-compatible. The database name must be part of `DB_URL`; `DB_NAME` is not a Spring datasource
property used by this application.

```properties
DB_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<project-reference>
DB_PASSWORD=<database-password>
```

For attachments, create a private bucket and configure:

```properties
ATTACHMENT_STORAGE_PROVIDER=supabase
SUPABASE_URL=https://<project-reference>.supabase.co
SUPABASE_SECRET_KEY=<backend-secret-key>
SUPABASE_STORAGE_BUCKET=task-attachments
```

The secret key remains on the backend. The Angular application does not connect directly to the
database or Supabase Storage.

## Main API routes

| Method  | Route                                        | Access                              |
| ------- | -------------------------------------------- | ----------------------------------- |
| `GET`   | `/api/auth/csrf`                             | Public; initializes CSRF protection |
| `POST`  | `/api/auth/login`                            | Public with a CSRF token            |
| `GET`   | `/api/auth/me`                               | Authenticated user                  |
| `POST`  | `/api/auth/change-password`                  | Authenticated user                  |
| `POST`  | `/api/auth/logout`                           | Authenticated with a CSRF token     |
| `GET`   | `/api/admin/users`                           | Administrator directory             |
| `POST`  | `/api/admin/users`                           | Administrator account creation      |
| `GET`   | `/api/admin/users/{id}`                      | Administrator                       |
| `PATCH` | `/api/admin/users/{id}`                      | Administrator profile/role update   |
| `POST`  | `/api/admin/users/{id}/activate`             | Administrator                       |
| `POST`  | `/api/admin/users/{id}/deactivate`           | Administrator                       |
| `POST`  | `/api/admin/users/{id}/reset-password`       | Administrator                       |
| `GET`   | `/api/admin/users/{id}/audit`                | Administrator timeline              |
| `GET`   | `/api/users?role=WORKER`                     | Manager                             |
| `GET`   | `/api/tasks`                                 | Tasks visible to the current user   |
| `GET`   | `/api/tasks/{id}`                            | Authorized creator or assignee      |
| `POST`  | `/api/tasks`                                 | Manager                             |
| `PATCH` | `/api/tasks/{id}/status`                     | Assigned worker                     |
| `POST`  | `/api/tasks/{id}/attachments`                | Authorized creator or assignee      |
| `GET`   | `/api/tasks/{id}/attachments/{attachmentId}` | Authorized creator or assignee      |

Persisted progress follows `ASSIGNED → IN_PROGRESS → COMPLETED`; direct
`ASSIGNED → COMPLETED` is also valid. `COMPLETED` is final. `OVERDUE` is calculated when an unfinished
task has passed its deadline.

## Tests and packaging

Run the complete backend suite:

```powershell
.\mvnw.cmd test
```

Build the executable JAR:

```powershell
.\mvnw.cmd -DskipTests package
```

The packaged application is written under `target/`.

## Deploy on Render

Create a Render Web Service with:

- Root Directory: `backend`
- Runtime: Docker
- Dockerfile Path: `./Dockerfile`
- Health Check Path: `/api/auth/csrf`

Render supplies `PORT`; `application.yml` reads it automatically. Use the Supabase Session pooler URL
shown above, set the exact Vercel production origin, and use these production security values:

```properties
COOKIE_SECURE=true
COOKIE_SAME_SITE=Strict
REQUIRE_HTTPS=true
```

Do not enable the existing `prod` Spring profile on Render unless you intentionally configure Spring
with a PKCS12 keystore. Render terminates public TLS before forwarding the request to the container.

After deployment, place the Render origin in `frontend/vercel.json` so browser `/api` requests are
proxied to this service.

### Bootstrap the first administrator

When no active administrator exists, startup requires all four `BOOTSTRAP_ADMIN_*` variables. The
created account is active but restricted to the password-change flow until it replaces the bootstrap
password. After that first successful change, remove `BOOTSTRAP_ADMIN_PASSWORD` from Render. The
initializer becomes a no-op while an active administrator exists.

Administrators can create users and reset another user's password. The API generates a high-entropy
temporary password, returns it once with `Cache-Control: no-store`, and stores only its BCrypt hash.
Deliver that value through an approved private channel; it cannot be retrieved later.

Account safeguards intentionally block self-deactivation, self-demotion, self-reset, removal of the
last active administrator, deactivation with unfinished role-related tasks, and role changes after any
task or attachment history. Activation, password, and role changes invalidate previously issued JWTs.

## Security notes

- Never commit `.env`, database passwords, JWT secrets, or Supabase secret keys.
- Rotate a secret immediately if it appears in a log, screenshot, chat, commit, or issue.
- Keep the JWT in its `HttpOnly` cookie; do not return it to or store it in the frontend.
- Keep the Supabase bucket private and authorize every upload and download through the API.
- Do not log or retain the clear-text bootstrap or generated temporary passwords.
- See [`../SECURITY.md`](../SECURITY.md) for the broader security model.
