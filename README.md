# Contact Tracker

A private dashboard for saved contacts, obituary sources, and Melissa Personator
Search. The GitHub repository retains its original name, book-price-scraper.

## Deploy and sign in

See [SECURE-LOGIN.md](SECURE-LOGIN.md) for the Railway setup and initial account.
The dashboard and every API require sign-in. Initial accounts are provisioned
through private deployment variables; there is no public registration page.

The app requires Java 17 or later, Maven, and MySQL. The main class is
`com.victorpena.contacttracker.ContactTrackerApplication`.

Railway app variables:

```dotenv
SPRING_DATASOURCE_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}
SPRING_DATASOURCE_USERNAME=${{MySQL.MYSQLUSER}}
SPRING_DATASOURCE_PASSWORD=${{MySQL.MYSQLPASSWORD}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
SERVER_ADDRESS=0.0.0.0
SERVER_PORT=8080
PORT=8080
```

For the first deployment, also set APP_BOOTSTRAP_EMAIL and APP_BOOTSTRAP_PASSWORD
privately in Railway. After the account is created, remove both variables. Add
MELISSA_API_KEY when ready to use paid lookups. Keep startup scraping disabled.
Use /healthz for a healthcheck and port 8080 for the public domain.

## Run locally

Import this folder as an Existing Maven Project in Spring Tools for Eclipse or
IntelliJ. Select ContactTrackerApplication and set environment variables:

- DB_PASSWORD for the existing local database login.
- APP_COOKIE_SECURE=false for local HTTP development.
- APP_BOOTSTRAP_EMAIL and APP_BOOTSTRAP_PASSWORD for the first account creation.
- MELISSA_API_KEY when ready to test the licensed Personator Search service.

Start the app and open http://localhost:8080/login. Local defaults still connect
to the existing book_price_scraper database as bookscraper_admin. Railway uses
its own database through the variables above. Source deployment does not transfer
local database records.

## Saved contacts

Use **Import names** on the homepage with the prefilled Legacy Austin URL:
https://www.legacy.com/us/obituaries/local/texas/austin-area

The signed-in POST `/api/obituaries/import` starts a background import; GET at
the same path returns progress. The import reads up to 100 entries from the
current results page, then saves each obituary and its parsed survivor names to
the connected database. It makes no Melissa requests. Refreshing the browser
reconnects to progress; an app restart resets the progress display, but committed
records remain. Repeating an import reuses matching obituary URLs and person
records, preserving saved phone numbers. One import runs at a time per app
instance; this version is intended for the existing single-replica deployment.

Only the exact Austin listing URL is accepted. Fetched links and every redirect
must stay on HTTPS www.legacy.com obituary paths. HTTP 401, 403, or 429 stops the
import with a visible message; no access restrictions are bypassed. Other failed
detail pages are counted and existing saved people are preserved. An empty or
changed listing is reported as an error. This imports the current page, not the
whole Austin archive, and the parser only saves names it can extract from
survivor sections. Review extracted names before using lookup matches.

The homepage reads saved records without making a Melissa request. Use Test first
name before testing a batch. Results are possible matches for review; testing
does not overwrite saved records or phone numbers. Download results before
closing the page or signing out.

[MELISSA-SAVED-NAMES.md](MELISSA-SAVED-NAMES.md) describes the lookup workflow.
[MELISSA-LOOKUP-FIX.md](MELISSA-LOOKUP-FIX.md) describes lookup diagnostics.
Direct paid diagnostic endpoints now require authenticated POST requests and a
CSRF token. They cannot be opened as GET links.

## Tests

```bash
./mvnw -Dtest=SecurityIntegrationTest,ContactTrackerApplicationTests test
./mvnw -Dtest=BookScraperClientTest,ObituaryImportServiceTest,ObituaryImportIntegrationTest test
node --test tests/saved-people.test.cjs
```

Test databases are disposable H2 instances; tests never use local or Railway
MySQL or the live Melissa API. `./mvnw test` also runs the earlier parser tests;
the existing parserRegressionChecks city-expectation failure is documented in
SECURE-LOGIN.md. The security update does not change that parser.
