# Vercel migration

Status on 2026-11-02: production cutover completed. The public application runs on
Vercel, using the existing Neon database. Google and GitHub logins completed on the
new domain. The former Render service was permanently deleted after verification;
it is no longer a rollback destination.

For current deployment steps, use [DEPLOYMENT.md](DEPLOYMENT.md).

## Confirmed access and destination

The browser-authenticated Vercel dashboard allowed creation of the empty Hobby
project `geoquest-migration` in team `raibrs` (display name `Rai`).
Project ID: `prj_9t9ly8lzKamEMrYmoyrrSTngMlDF`.
Dashboard: https://vercel.com/raibrs/geoquest-migration

Production domain: https://geoquest-raibrs.vercel.app
The project was created through the authenticated dashboard. The connected API
does not currently resolve this project; use the confirmed dashboard context
instead of creating duplicate projects. The existing `geoquest` name was
unavailable, so the project has a separate migration name.

## Deployment architecture

Keep the Java 25 / Spring Boot application and the existing root Dockerfile.
The Vercel Container runtime builds and runs this image; `vercel.json` selects
the Container framework explicitly. The HTML, CSS, JavaScript and HTTP API stay
in the same application and origin. Keep the existing Neon database.

Set `PORT=8080` in Vercel to match the Dockerfile. Keep `HOST=0.0.0.0`.
Configure `APP_URL` with the stable HTTPS production origin, without a trailing
slash. The default Vercel container port is 80, so the explicit port matters.

Reference: https://vercel.com/docs/functions/runtimes/container

## Shared state implementation

- Set `SHARED_STATE_ENABLED=true` in production. `GameService` then uses
  `PostgresRoundStorage` instead of its process-local `ConcurrentHashMap`.
  Preserve the original timed deadline, hidden answer keys, ownership, mistake
  review, expiry, and exact-answer retries. Serialize concurrent answers using
  database transactions with `SELECT ... FOR UPDATE`; Java `synchronized` alone
  cannot coordinate instances. The saved retry response commits with the score.
- `SharedStateConfig` enables Spring Session JDBC for the temporary OAuth
  authorization request, retaining the secure HttpOnly `JSESSIONID` cookie. Existing
  seven-day player sessions already use PostgreSQL; preserve their design.
- Verify that expired-state cleanup works even when an instance is stopped.
  An in-process scheduled task cannot be the only expiry enforcement.
- Test two independent application instances against the same test database:
  start on A, answer on B, retry on A, expire on B; complete the OAuth handshake
  across instances as well. Confirm duplicate requests never score twice.

Apply `database/003_shared_rounds.sql` and `database/004_shared_oauth_sessions.sql`
before enabling shared state. These additive tables do not replace player profiles,
identities, persistent player sessions, or ranking results.

Validation: 140 Java tests passed with the disposable local PostgreSQL database,
including round restoration, concurrent duplicate answers, ownership, timed batches,
deadline expiry, OAuth request restoration, and secure session-cookie configuration.
The migration-era 12 frontend tests also passed. After the language persistence fix,
all 14 frontend tests passed. The Docker image built successfully. These are recorded
validation results, not a new test run for this documentation update.

## Account and environment setup

1. Open the authenticated Vercel dashboard for the existing project. CLI/integration
   access is optional and must be checked separately; do not create duplicate projects.
2. Confirm the intended team and GeoQuest project before linking or deploying.
3. Keep `.vercel/` ignored: environment pulls can contain secrets.
4. Configure the existing variable names from `.env.example` directly in the
   Vercel environment settings. Do not commit or paste credential values.
5. Do not connect a preview deployment to production data for migration tests.
   Use a separate database/Neon branch and separate OAuth test configuration.
6. Set Google and GitHub authorized callback URLs for the chosen stable origin:
   `/login/oauth2/code/google` and `/login/oauth2/code/github`, respectively.

The eight existing API/Neon/Google/GitHub variables were copied individually to
Vercel as Production Secrets, with user authorization. Other Render variables
were not exported. Config variables are configured separately for Production.

## Completed cutover and verification

- Commit `60293da` introduced shared round storage and temporary OAuth sessions.
- The production deployment reached Ready on the stable Vercel domain.
- Browser checks covered guest entry, a complete Capitals round, Survival ending
  on an error, and 12 answers with automatic advancement in the 60-second mode.
- The timer ended the round at its deadline, and the public ranking loaded.
- Both Google and GitHub returned to the Vercel domain with authenticated profiles.
- Render service `srv-dbh2tcks728c73anvrk0` was deleted with user confirmation.
  The Render workspace then showed no services; Vercel remained accessible.
- Commit `5a122aa` fixed language persistence. Portuguese remained selected after
  reload, a new tab and a real Google OAuth return on the published application.
- Account deletion and concurrency have automated coverage. No real production
  account was deleted for QA, and physical mobile-device checks remain separate.

## Updating and rolling back

Push reviewed changes to `main` and check the production deployment in Vercel.
For rollback, select a previously verified Vercel deployment compatible with the
current database schema. The old Render host is retired. Do not assume a code
rollback also reverses database migrations or provider configuration.

The isolated Neon test branch is `vercel-migration-check`
(`br-square-hat-b7l65d3j`); tests used its empty `geoquest_test` database rather
than production data. A network SSL timeout affected one older account test on
that branch; all database tests subsequently passed against local PostgreSQL.
