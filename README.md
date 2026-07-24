# TaskFlow backend

TaskFlow's backend is a Spring Boot REST API for a company task-management workspace. It owns
authentication, authorization, user administration, task rules, attachments, audit events, email
delivery, and database migrations. The Angular client and end-user guide are maintained in the
[TaskFlow frontend repository](https://github.com/OPTIMISTICLE/task_flow_frontend).

## System overview

- `ADMIN` users manage accounts, invitations, recovery, activation, MFA resets, and user audit
  history. Administrators do not have access to task data.
- `MANAGER` users create tasks, assign active workers, and see only tasks they created.
- `WORKER` users see only their assigned tasks and control their progress.
- Authentication uses a signed JWT in an `HttpOnly` cookie plus a database-backed 12-hour session.
- Browser mutations require the CSRF cookie/header pair, and CORS accepts one configured frontend
  origin.
- Optional TOTP MFA includes single-use recovery codes and per-device session revocation.
- PostgreSQL stores application data; Flyway applies every schema change.
- Attachments are stored on the local filesystem or in a private Supabase Storage bucket.
- Invitation, recovery, confirmation, and security emails are queued in a transactional outbox and
  delivered through the Gmail API over HTTPS.

Task progress is persisted as `ASSIGNED`, `IN_PROGRESS`, or `COMPLETED`. `OVERDUE` is calculated for
unfinished tasks whose deadline has passed. A worker can move from Assigned to In progress or
Completed, and from In progress to Completed. Completed tasks are final.

## Technology and layout

- Java 17 source compatibility, with Java 17 or newer supported at runtime;
- Spring Boot 3.5, Spring Security, Spring Data JPA, and Jakarta Validation;
- PostgreSQL and versioned Flyway migrations;
- Maven Wrapper and H2 integration tests in PostgreSQL compatibility mode;
- multi-stage Docker image running on Java 21.

```text
src/main/java/com/example/taskmanagement/
  attachment/   Protected attachment metadata and storage providers
  audit/        Security and domain audit events
  auth/         Login, sessions, JWT cookies, MFA, and password flows
  common/       Problem responses and shared exception handling
  config/       Application and security configuration
  task/         Task API, rules, and persistence
  user/         User administration and worker directory
src/main/resources/
  db/migration/ Versioned Flyway migrations
src/test/       Integration and application tests
Dockerfile      Render-compatible multi-stage image
```

## Local development

### Requirements

- Java 17 or newer;
- PostgreSQL 17 or a compatible PostgreSQL server;
- PowerShell on Windows for the commands below.

When this repository is checked out inside the complete TaskFlow workspace, the parent
`docker-compose.yml` can start PostgreSQL with `docker compose up -d postgres`. A standalone clone
can use any PostgreSQL instance whose database and credentials match `DB_URL`, `DB_USERNAME`, and
`DB_PASSWORD`.

Create an ignored `.env` in the backend repository root. The following is a safe local template;
replace every angle-bracket placeholder and never commit the resulting file:

```properties
DB_URL=jdbc:postgresql://localhost:5432/task_management
DB_USERNAME=task_app
DB_PASSWORD=<local-database-password>
JWT_SECRET=<at-least-32-random-bytes>
MFA_ENCRYPTION_KEY=<base64-encoded-32-byte-key>
FRONTEND_ORIGIN=http://localhost:4200
COOKIE_SECURE=false
COOKIE_SAME_SITE=Strict
REQUIRE_HTTPS=false
SEED_USERS=true
SEED_PASSWORD=<strong-demo-password>
MAIL_ENABLED=false
ATTACHMENT_STORAGE_PROVIDER=local
ATTACHMENT_STORAGE=./data/attachments
```

Generate stable application keys, for example:

```powershell
openssl rand -base64 48
openssl rand -base64 32
```

Use the first value for `JWT_SECRET` and the second for `MFA_ENCRYPTION_KEY`. Rotating the MFA key
without migrating credentials makes existing TOTP enrollments unreadable.

Run the API from the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

On Unix-like systems, use `./mvnw spring-boot:run`. The API listens on
`http://localhost:8080` unless `SERVER_PORT` or `PORT` is set.

## Configuration reference

Keep production secrets in the hosting platform's secret manager. Render values should be entered
without shell quotes or trailing spaces.

| Variable | Purpose |
| --- | --- |
| `DB_URL` | Complete JDBC PostgreSQL URL, including the database name |
| `DB_USERNAME` | PostgreSQL login name |
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | JWT signing secret containing at least 32 random bytes |
| `JWT_TTL` | JWT/cookie lifetime; normally `12h` |
| `SESSION_TTL` | Persistent session lifetime; defaults to `12h` |
| `INVITATION_TTL` | Invitation lifetime; defaults to `24h` |
| `PASSWORD_RESET_TTL` | Recovery-link lifetime; defaults to `30m` |
| `MFA_CHALLENGE_TTL` | Login MFA challenge lifetime; defaults to `5m` |
| `MFA_ENCRYPTION_KEY` | Stable Base64-encoded 32-byte AES key for TOTP secrets |
| `FRONTEND_ORIGIN` | Exact browser origin, without a trailing slash |
| `COOKIE_SECURE` | Must be `true` for an HTTPS production frontend |
| `COOKIE_SAME_SITE` | Authentication cookie SameSite policy; normally `Strict` |
| `REQUIRE_HTTPS` | Requires the public/forwarded request scheme to be HTTPS |
| `SEED_USERS` | Creates demo accounts when enabled and the user table is empty |
| `SEED_PASSWORD` | Initial password shared by seeded demo accounts |
| `BOOTSTRAP_ADMIN_*` | First administrator's email, password, first name, and last name |
| `MAIL_ENABLED` | Enables queued Gmail API delivery |
| `MAIL_FROM_NAME` | Sender display name; defaults to `TaskFlow` |
| `GMAIL_SENDER_EMAIL` | Gmail account or configured alias used as the sender |
| `GMAIL_CLIENT_ID` | Backend-only Google OAuth client ID |
| `GMAIL_CLIENT_SECRET` | Backend-only Google OAuth client secret |
| `GMAIL_REFRESH_TOKEN` | Backend-only offline token granted the `gmail.send` scope |
| `ATTACHMENT_STORAGE_PROVIDER` | `local` or `supabase` |
| `ATTACHMENT_STORAGE` | Local attachment directory when using `local` |
| `SUPABASE_URL` | Supabase project URL when using Supabase Storage |
| `SUPABASE_SECRET_KEY` | Backend-only Supabase secret key |
| `SUPABASE_STORAGE_BUCKET` | Private bucket name; defaults to `task-attachments` |
| `MAX_FILE_SIZE` | Maximum individual upload; defaults to `10MB` |
| `MAX_REQUEST_SIZE` | Maximum multipart request; defaults to `11MB` |

## Supabase

### PostgreSQL

For Render, use Supabase's IPv4-compatible **Session pooler** connection details. The database name
must be the final path segment of `DB_URL`. `DB_NAME` is not read by Spring's datasource.

```properties
DB_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<project-reference>
DB_PASSWORD=<database-password>
```

A direct Supabase database hostname may resolve only to IPv6 and fail from an IPv4-only hosting
environment. Copy the host, port, username, and database from the Supabase connection panel instead
of assembling them from the public project URL.

### Private attachment storage

Create the bucket before enabling the provider; its name must exactly match
`SUPABASE_STORAGE_BUCKET`.

```properties
ATTACHMENT_STORAGE_PROVIDER=supabase
SUPABASE_URL=https://<project-reference>.supabase.co
SUPABASE_SECRET_KEY=<backend-secret-key>
SUPABASE_STORAGE_BUCKET=task-attachments
```

Keep the bucket private. The backend secret key is used only by the API, and every upload and
download is authorized through its task. The frontend does not connect directly to PostgreSQL or
Supabase Storage. A publishable/anonymous Supabase key is not a replacement for
`SUPABASE_SECRET_KEY` in this integration.

## API overview

All protected mutation routes require a valid session and CSRF token.

| Method | Route | Access |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Public; initializes CSRF protection |
| `POST` | `/api/auth/login` | Public login initiation |
| `POST` | `/api/auth/mfa/challenge` | Public MFA login completion |
| `POST` | `/api/auth/invitations/accept` | Public invitation completion |
| `POST` | `/api/auth/password-recovery/request` | Public, enumeration-safe recovery request |
| `POST` | `/api/auth/password-recovery/complete` | Public recovery completion |
| `POST` | `/api/auth/email/confirm` | Public email confirmation |
| `GET` | `/api/auth/me` | Current authenticated user |
| `POST` | `/api/auth/change-password` | Change the current user's password |
| `POST` | `/api/auth/logout` | End the current session |
| `GET` | `/api/auth/sessions` | List the current user's sessions |
| `DELETE` | `/api/auth/sessions/{id}` | Revoke one owned session |
| `POST` | `/api/auth/sessions/revoke-others` | Revoke every other owned session |
| `GET` | `/api/auth/mfa` | Read the current user's MFA state |
| `POST` | `/api/auth/mfa/setup` | Start TOTP enrollment |
| `POST` | `/api/auth/mfa/confirm` | Confirm TOTP and issue recovery codes |
| `POST` | `/api/auth/mfa/disable` | Disable the current user's MFA |
| `POST` | `/api/auth/mfa/recovery-codes` | Replace recovery codes |
| `GET`, `POST` | `/api/admin/users` | Administrator directory and account creation |
| `GET`, `PATCH` | `/api/admin/users/{id}` | Administrator account detail and update |
| `POST` | `/api/admin/users/{id}/activate` | Administrator activation |
| `POST` | `/api/admin/users/{id}/deactivate` | Administrator deactivation |
| `POST` | `/api/admin/users/{id}/reset-password` | Administrator recovery-email request |
| `POST` | `/api/admin/users/{id}/resend-invitation` | Administrator invitation replacement |
| `POST` | `/api/admin/users/{id}/reset-mfa` | Administrator MFA/session reset |
| `GET` | `/api/admin/users/{id}/audit` | Administrator user-audit timeline |
| `GET` | `/api/users?role=WORKER` | Manager's active-worker directory |
| `GET`, `POST` | `/api/tasks` | Visible task list; manager creation |
| `GET` | `/api/tasks/{id}` | Authorized creator or assignee |
| `PATCH` | `/api/tasks/{id}/status` | Assigned worker |
| `POST` | `/api/tasks/{id}/attachments` | Authorized creator or assignee |
| `GET` | `/api/tasks/{id}/attachments/{attachmentId}` | Authorized creator or assignee |

Account safeguards prevent self-deactivation, self-demotion, self-reset, removal of the last active
administrator, deactivation with unfinished role-related tasks, and role changes after task or
attachment history. Activation, password, role, MFA, and administrative security changes invalidate
affected sessions where required.

## Tests and packaging

The integration suite uses H2 and does not require the local PostgreSQL container:

```powershell
.\mvnw.cmd test
```

Build the executable JAR:

```powershell
.\mvnw.cmd -DskipTests package
```

The packaged application is written under `target/`.

## Deploy on Render

Because this is a standalone repository, create a Render Web Service with:

- **Root Directory:** leave empty (repository root);
- **Runtime:** Docker;
- **Dockerfile Path:** `./Dockerfile`;
- **Health Check Path:** `/api/auth/csrf`.

Render supplies `PORT`, which the application reads automatically. Configure the Supabase Session
pooler, private attachment bucket, Gmail credentials, stable cryptographic keys, and the exact Vercel
production origin. Use:

```properties
COOKIE_SECURE=true
COOKIE_SAME_SITE=Strict
REQUIRE_HTTPS=true
```

Do not activate the existing `prod` Spring profile on Render unless a PKCS12 keystore is deliberately
configured. Render normally terminates public TLS and forwards the request to the container.

After deployment, set the Render service origin as the `/api` destination in the frontend's
`vercel.json`.

### Bootstrap the first administrator

When no active administrator exists, startup requires all four variables:

```text
BOOTSTRAP_ADMIN_EMAIL
BOOTSTRAP_ADMIN_PASSWORD
BOOTSTRAP_ADMIN_FIRST_NAME
BOOTSTRAP_ADMIN_LAST_NAME
```

The account is active but restricted to changing its initial password. After that password change,
remove `BOOTSTRAP_ADMIN_PASSWORD` from Render. The initializer becomes a no-op while an active
administrator exists.

Administrators create pending users and TaskFlow queues a single-use 24-hour invitation. Password
recovery links expire after 30 minutes. Creating a replacement invitation or recovery link
invalidates the previous token.

## Gmail API delivery

Render Free blocks outbound SMTP ports, so TaskFlow sends through the Gmail API over HTTPS rather
than Gmail SMTP or an App Password.

1. Create a Google Cloud project and enable the **Gmail API**.
2. Configure an External OAuth consent screen with
   `https://www.googleapis.com/auth/gmail.send`.
3. Add the sender Gmail account as a test user while the consent app is in Testing. For long-lived
   production credentials, publish the consent app before generating the final refresh token.
4. Create a Web application OAuth client and temporarily add
   `https://developers.google.com/oauthplayground` as an authorized redirect URI.
5. In OAuth Playground, enable **Use your own OAuth credentials**, request offline access and forced
   consent, authorize the exact `gmail.send` scope, exchange the code, and copy the refresh token.
6. Remove the temporary Playground redirect URI and configure the following Render secrets:

```text
MAIL_ENABLED=true
MAIL_FROM_NAME=TaskFlow
GMAIL_SENDER_EMAIL=your-account@gmail.com
GMAIL_CLIENT_ID=<oauth-client-id>
GMAIL_CLIENT_SECRET=<oauth-client-secret>
GMAIL_REFRESH_TOKEN=<offline-refresh-token>
```

The sender must be the Gmail account that granted consent or one of its configured aliases. Never
use the normal Google password and never expose OAuth credentials to the frontend.

### Gmail troubleshooting

- **401 Unauthorized:** verify the client ID, client secret, and refresh token belong to the same
  OAuth client. Revoke and regenerate the token if access was withdrawn.
- **403 Forbidden or insufficient permissions:** confirm the Gmail API is enabled and generate a new
  refresh token after explicitly granting `https://www.googleapis.com/auth/gmail.send`. An existing
  refresh token does not gain scopes when the consent-screen configuration changes.
- **Token stops working after seven days:** OAuth refresh tokens for an External app left in Testing
  can expire after seven days. Publish the consent app and issue a new token when appropriate.
- **Mail still fails after fixing Render:** redeploy, then issue a fresh invitation or recovery
  request. Do not reuse an expired link or assume an outbox item that exhausted its retries will send
  automatically.
- **Bucket not found during attachment upload:** create the private bucket in the same Supabase
  project and make its name exactly match `SUPABASE_STORAGE_BUCKET`.

## Security notes

- Never commit `.env`, database passwords, JWT/MFA keys, Supabase secret keys, Gmail OAuth secrets,
  refresh tokens, invitation tokens, or recovery codes.
- Rotate a secret immediately if it appears in a log, screenshot, chat, commit, or issue.
- Keep the JWT in its `HttpOnly` cookie; do not return it to or store it in browser-readable storage.
- Keep production HTTPS, secure-cookie, CSRF, CORS, rate-limit, ownership, and audit protections
  enabled.
- Treat attachment names and contents as untrusted and keep the Supabase bucket private.
- Browser developer tools can display data after HTTPS decrypts it at the browser endpoint; HTTPS
  protects the data while it travels across the network, not from the signed-in user viewing their
  authorized response.
