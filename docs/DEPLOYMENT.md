# Publishing GeoQuest with accounts: Render + Neon

The Java server and frontend run together in one Render container. Neon is standard PostgreSQL;
Google and GitHub handle sign-in directly through OAuth authorization code + PKCE. No hosted
authentication intermediary is used. Existing visitor mode needs only API_KEY.

## 1. Prepare the database

Create a Neon project using its free plan and select a region close to the Render region.
In Neon's SQL editor run `database/001_accounts.sql`. This creates profiles, provider identities,
hashed sessions, and results. Use a dedicated database role for the application with access only
to these tables. Do not expose the PostgreSQL connection to browser JavaScript.

Copy the JDBC connection parameters from Neon (Java connection option). Supply the username and
password separately using DATABASE_USER and DATABASE_PASSWORD. Example URL:

    jdbc:postgresql://YOUR-ENDPOINT.neon.tech/neondb?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory

The Java SSL factory uses trusted JVM certificate authorities and verifies the server hostname.
For transaction-pooled endpoints, the application's advisory lock is transaction-scoped.

## 2. Configure Google and GitHub

Choose the Render service name first so you know its public URL, for example:

    https://YOUR-SERVICE.onrender.com

Register a web OAuth client in Google Auth Platform. Request only `openid profile`. Configure the
consent screen and the exact authorized redirect URI:

    https://YOUR-SERVICE.onrender.com/auth/callback

For local development, register `http://localhost:8080/auth/callback` as well. Google projects in
Testing mode require adding the people who will test as test users. Complete Google's publishing
requirements before advertising sign-in more broadly.

Create a GitHub OAuth App and use the same public URL for its homepage and the exact callback URL
above. A separate development OAuth app is convenient for localhost. The app requests `read:user`;
it does not request repository or email permissions.

Store GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET and GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET only in
the server environment. Configure both to show both login options. One provider can be omitted by
leaving both of its values unset. Never commit client secrets or passwords.

## 3. Deploy the application

Commit and push the reviewed changes to the repository. Create a Render Blueprint from render.yaml
or create a Docker Web Service manually with the same environment variables. Select the Free plan
and keep a single instance. Set APP_URL to the exact HTTPS origin, without a path or trailing slash.
Set ACCOUNTS_ENABLED=true only after applying the SQL and configuring at least one login provider.
The server checks that the account schema exists before accepting traffic.

Render builds the Dockerfile and runs the Java tests. Configure the health check as `/` and wait
for deployment to become healthy. The container uses HOST=0.0.0.0 and PORT=8080. The existing
API_KEY remains the country API key, independent of OAuth credentials.

## 4. Verify the public deployment

- Open the public address in a private browser window and play a visitor round.
- Sign in with each configured provider; verify return to the hub with the provider's display name.
- Finish a round, return to the hub and open the matching mode/type ranking.
- Retry the final request: there must still be one result for that round.
- Log out and verify the cookie no longer authenticates. Check from another device as well.
- Use a disposable account to test deletion. Type DELETE after a recent login. Verify the profile,
  identities, sessions, and ranking results disappear. Its active in-memory rounds are removed too.
- Reopen the site after a restart. Accounts/results remain in PostgreSQL; unfinished rounds do not.

## Session and deletion behavior

The browser gets a random HttpOnly, SameSite=Lax cookie, marked Secure over HTTPS. Only its SHA-256
hash is stored in PostgreSQL. Sessions expire after seven days, can be revoked by logout, and are
removed when the profile is deleted. Mutations require the configured Origin. Deletion additionally
requires the literal confirmation DELETE and authentication within the last ten minutes.

Identities are keyed by provider + provider subject. Accounts from different providers are not
silently merged by email or display name. Account linking is outside this implementation.
No provider access/refresh tokens are stored. Deletion removes GeoQuest's data and sign-in link;
it does not delete the Google/GitHub account or automatically revoke its external OAuth grant.
Users can also remove GeoQuest from each provider's authorized-app settings.

Only completed, authenticated rounds enter the ranking. The best result per player is shown in
each mode/question-type category. The browser never submits a score or a user ID to save a result.
Result persistence must succeed before the final response is acknowledged; retrying is idempotent.
This prevents client-supplied scores, but does not claim to prevent automated play or all cheating.

## Costs and limits

Free hosting/database plans have quotas and may suspend or sleep. Confirm their current conditions
in the provider dashboards. Choose a plan with no automatic paid upgrade for initial tests.
One Render instance avoids cross-instance round loss; Redis is not required for this deployment.
Database backups and log retention are controlled by the provider and should be described in the
published privacy notice before broader release. No payment or paid resource is created by these files.

## Local PostgreSQL integration tests

Use a disposable database named geoquest_test, role geoquest_test, and password test-only-password.
Expose it on loopback and set TEST_DATABASE_URL to its JDBC URL when running Maven. The integration
test verifies that dedicated database name before clearing test data. Without TEST_DATABASE_URL,
only these external-database tests are skipped; provider/controller tests use controlled fixtures.

Reference docs:
- https://render.com/docs/blueprint-spec
- https://neon.com/docs/guides/java
- https://developers.google.com/identity/protocols/oauth2/web-server
- https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps
