# Secure sign-in deployment

This update protects the dashboard, contact records, phone updates, and Melissa
API calls with Spring Security sessions. The only public application routes are
GET /login, GET /login.css, and GET /healthz, plus the CSRF-protected login POST.
There is no public account-registration route.

## Create Clay's account on Railway

In the **book-price-scraper app service → Variables**, add:

| Variable | Value |
| --- | --- |
| `APP_BOOTSTRAP_EMAIL` | `clay@zachryinsurance.com` |
| `APP_BOOTSTRAP_PASSWORD` | Enter the requested initial password privately in Railway |

Keep the existing Railway MySQL reference variables, SERVER_ADDRESS=0.0.0.0,
SERVER_PORT=8080, and PORT=8080. Deploy the security update and these variables.
The password must contain at least 12 characters and no more than 72 UTF-8 bytes.
It is never part of the source code, migration, ZIP, or application logs.

Flyway migration V5 adds only the `app_users` table. On startup, the app creates
the requested account if it is missing and stores a BCrypt hash with cost 12.
An existing account's password, status, and lock are never overwritten by a
redeploy. A concurrent first startup also preserves the first-created account.

Open **/login**, sign in with Clay's email and the initial password, and confirm
the dashboard loads. Then remove **both** APP_BOOTSTRAP_EMAIL and
APP_BOOTSTRAP_PASSWORD and deploy that change. The account remains in MySQL.
Removing only one variable is a configuration error. With neither variable set,
the app uses existing accounts; an empty user table leaves the app locked until
an administrator provisions an account.

## Verify before adding contacts and the Melissa license

- A private/incognito window visiting /index.html must redirect to /login.
- GET /api/people/melissa/records without a session must return HTTP 401.
- After sign-in, the dashboard displays the account email and a Sign out button.
- Sign out invalidates the session. The next API read must require sign-in.
- Paid calls require an authenticated session AND a valid CSRF token.
- The new Railway database remains empty until the existing contacts are imported.

Set the Railway healthcheck path to **/healthz** if a healthcheck is desired.
It reports only `{"status":"ok"}` and exposes no account or database details.

## Session behavior

Sessions expire after 30 minutes without activity. Session cookies are Secure,
HttpOnly, and SameSite=Lax. HTTPS is expected in production; Railway's proxy
headers are enabled. Login rotates the session ID. Protected responses use
no-store cache headers, and the login page is protected from framing.

Eight failed sign-in attempts lock the account for 15 minutes. This counter and
lock persist in MySQL across restarts and instances. Login errors are generic
for incorrect, unknown, disabled, and locked accounts. Email matching ignores
case and surrounding whitespace. After a successful login the failure counter
is cleared. A process restart signs users out because sessions are held in memory.
Use one app replica unless shared sessions or sticky routing are configured.

## API compatibility

Read-only saved-record endpoints remain GET. The following paid routes now use
POST to prevent links, prefetchers, or cross-site navigations from spending credits:

- /api/melissa/diagnose
- /api/melissa/search-by-name
- /api/melissa/search-by-location
- /api/melissa/search
- /api/obituaries/melissa

Their existing query/form parameters and result formats are unchanged. The
saved-name dashboard continues to use JSON POST /api/people/melissa/test.
Authenticated clients can GET /api/session to obtain csrfHeader and csrfToken;
send that header on each POST/PATCH request, including POST /logout. Logout
returns HTTP 204. No HTTP Basic or public API-key bypass is enabled.

## Local development

For http://localhost:8080 only, set **APP_COOKIE_SECURE=false** in the IDE run
configuration, along with the local database credentials and the two bootstrap
variables for the first run. Keep APP_COOKIE_SECURE unset (true) on Railway.
Never add bootstrap credentials or .env files to Git. Existing people, obituaries,
Melissa behavior, and the local database name remain unchanged.

## Validation

The security integration tests use a disposable H2 database and fake lookup
services. They test actual Spring Security filters, sign-in, password hashing,
account bootstrap, account lockout, session rotation, logout, CSRF, and anonymous
access. No live Melissa requests are sent. The frontend tests cover session
expiry, logout, CSRF headers, and the saved-name queue.

Run `./mvnw -Dtest=SecurityIntegrationTest,ContactTrackerApplicationTests test`
and `node --test tests/saved-people.test.cjs`.

The full pre-existing suite includes a failing parserRegressionChecks assertion:
it expects no residence city where the parser returns Austin and Dallas. This
security update leaves that unrelated parser and its expectations unchanged.
