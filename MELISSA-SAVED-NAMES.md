# Test your saved names with Melissa

## Lookup update 2026-10-08.3

This update also handles no-match status rows that echo a nonblank name. A returned name by itself is not treated as a match when its status is UE01 or UE04. No-match results have zero candidates and do not stop the queue.

The initial lookup now omits the explicit `Page:0` option and lets Melissa select its default first page, while retaining the five-candidate limit and strict matching. There is no automatic retry. Melissa's documentation lists zero as the minimum page, so the observed UE03 cannot establish that pages start at one; this change avoids overriding the service default rather than guessing a different page number. Explicit later-page requests retain their existing behavior. Live verification is still required.

If UE03 remains, the queue stops before testing another name. Its error message includes the update number, requested page mode, reported totals and row counts. It excludes raw responses, credentials and contact details. Download test results and share the error message. The homepage shows **Lookup update: 2026-10-08.3**, supplied by the running backend. This confirms which update is running.

After installation, use **File → Refresh**, then **Project → Clean** in Spring Tools, restart ContactTrackerApplication, and hard-refresh the browser. Test one complete saved name before another batch. A prior browser session can retain old results until refreshed; a changed backend version also clears the previous session results when you use Reload saved names.

This update adds a homepage that reads existing `people` rows from `book_price_scraper`. It displays the actual saved-person and obituary counts, shows whether the app has a Melissa key configured, and lets you test one name, the next ten, an individual row, or all remaining names.

The UE01 fix treats a completed no-match response as zero person candidates even when Melissa reports positive totals for status rows. The queue can continue to the next name. UE04 (no exact match) receives the same handling; contradictory match codes and account/service errors still produce errors.

## Install the renamed project

Follow the installation and Spring Tools run-configuration steps in [README.md](README.md). The installer creates `contact-tracker` beside the original project, preserves that original, and retains the current database settings. The main class is now `com.victorpena.contacttracker.ContactTrackerApplication`. The database remains `book_price_scraper`.

## Configure and test

In **Spring Tools for Eclipse → Run → Run Configurations → Spring Boot App → ContactTrackerApplication → Environment**, keep the working `DB_PASSWORD` and set `MELISSA_API_KEY` to your new **Personator Search** license key. In IntelliJ, use Run → Edit Configurations → Environment variables. Restart after changing an environment variable.

Open **http://localhost:8080/**. A hard refresh may be needed if the old instruction page is cached. Click **Test first name**. Once you have checked that response, use **Test next 10** or **Test all remaining**. The app calls Melissa only when you request a test. Opening the page or reloading the names does not send names to Melissa.

Each new lookup may consume account credits. The page makes one request at a time, uses the person's saved city/state when present, and shares results for identical normalized names and locations during the current browser session. This is query deduplication, not proof that two records represent the same person. The per-row **Test name** button explicitly sends a new lookup even if that record was tested earlier.

**Stop after current name** stops the queue after the in-flight request returns. Already completed results remain visible. **Download test results** exports the session's results as JSON. Browser results are not stored in MySQL and are lost on refresh or closing the page. Download them first; starting a new browser session may spend credits again.

## Errors and matches

- **GE08**: Melissa says the key is not enabled for the requested product or level. The queue stops after that error. This code cannot be repaired locally if the new key lacks Personator Search entitlement; Melissa must enable it.
- **Missing key**: configure `MELISSA_API_KEY`, restart, and reload.
- **GE05 / GE14 / UE03 / other service errors**: the queue stops on key, credits, account, option, page, network or service failures. These do not mean the saved person was deleted.
- **No match**: the API completed the search but returned no matching candidate. Record-specific input or too-many-matches errors are displayed and do not stop the rest of the queue.
- Candidates are for review; testing never automatically overwrites saved phone numbers. A matching name does not establish identity. Strict matching uses only the saved name and any known residence fields, without assuming the survivor lives where the obituary was published. This test retrieves only the first page (up to five candidates), and shows when more pages exist.

Official result-code reference: https://docs.melissa.com/cloud-api/personator-search/result-codes.html

## Data preservation

No new database migration is required. The original table names and database remain in use. Testing performs read-only database operations. The scraper no longer deletes saved people just because a later parse omits their names. Existing people are still reused and their stored phone numbers preserved when a new scrape updates their obituary details.

All API endpoints now require sign-in. Direct paid lookup endpoints additionally require POST and a CSRF token; `/api/people/missing-phone` remains a read-only GET. See [SECURE-LOGIN.md](SECURE-LOGIN.md). The old `/api/obituaries/melissa` endpoint still re-scrapes; the new page uses only `/api/people/melissa/records` and JSON `POST /api/people/melissa/test`. Do not use the old endpoint to test the saved database.

The JSON test endpoint accepts `{"personIds":[1,2]}` with 1–25 IDs and returns per-person diagnostics. It validates all IDs before making a request, deduplicates name/location queries within the request, and rejects concurrent saved-name batches. All paid requests are explicit actions, not startup actions.

## Validation

Passed: seven dependency-free JavaScript tests for list loading without lookups, one-name and all-name progression, deduplication across runs, GE08 stopping and retry, UE03 stopping, clearing stale results after a version change, stopping after an in-flight call, missing-key controls, and safe text rendering. Run with `node --test tests/saved-people.test.cjs`.

Passed: isolated Java 17 compilation and behavioral checks of the actual saved-name service with test doubles for its external dependencies (order, saved locality, duplicate queries, preserved phone values, account/service stop conditions, record-specific errors, unknown IDs, missing key, blank name).

Passed for this update: 90 isolated checks of the actual client/request builder/parser using Spring/Jackson test doubles. These cover UE01/UE04 status rows with positive totals and echoed names, no phantom pagination, preserved result codes, malformed responses, contradictory match codes, GE08/account failures, real candidate phone fields, default first-page options, explicit later pages, no automatic retries, and sanitized UE03 diagnostics. These checks do not exercise HTTP or JSON deserialization; the included `MelissaPersonSearchClientTest` covers those paths with mocked HTTP responses when run through Maven. The isolated service checks also verify that UE03 stops before another request.

Full Spring/Maven integration tests and live Melissa requests were not run in the editing environment: Maven Central downloads and a browser binary were unavailable, and the local MySQL database and live key are on your Mac. JUnit regression tests are included. On your Mac, run:

```bash
bash mvnw -Dtest='SavedPeopleMelissaServiceTest,SavedPeopleMelissaControllerTest,ObituaryPreservationTest,MelissaPersonSearchClientTest,MelissaTestControllerTest,ObituaryMelissaLookupServiceTest,ScraperStartupRunnerTest,SurvivorSectionExtractorTest' test
```

These selected tests use mocks and do not send paid Melissa requests or connect to your local MySQL database. The existing `ContactTrackerApplicationTests` context test is deliberately not selected because it uses your real datasource configuration.
