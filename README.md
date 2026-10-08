# Contact Tracker

Contact Tracker manages saved people and obituary sources and lets you review Melissa Personator Search results. The application and project were previously named `book-price-scraper`.

## Install the renamed project

Stop the running application in Spring Tools. Extract this ZIP into a separate Downloads folder, then run:

```bash
bash ~/Downloads/contact-tracker-update/contact-tracker/scripts/install-rename.sh
```

The installer uses your existing project at `~/Desktop/IdeaProjects/Spring-Projects/book-price-scraper` and creates `contact-tracker` beside it. It preserves the original project, copies its Git history and local settings when present, and keeps the current database configuration. It refuses to overwrite an existing destination. Optional first and second arguments specify the existing project and destination directories.

## Open in Spring Tools for Eclipse

1. Choose **File → Import → Maven → Existing Maven Projects**.
2. Select `~/Desktop/IdeaProjects/Spring-Projects/contact-tracker` and finish the import.
3. Open **Run → Run Configurations → Spring Boot App** and select your previous application configuration. Rename the configuration to **ContactTrackerApplication**, change **Project** to `contact-tracker`, and set **Main type** to `com.victorpena.contacttracker.ContactTrackerApplication`.
4. Leave your existing **Environment** values, including `DB_PASSWORD` and `MELISSA_API_KEY`, in that configuration. Click **Apply**, then **Run**.
5. Open **http://localhost:8080/** and refresh the page. The heading and browser title should say **Contact Tracker**.

The original project remains available for rollback. Stop it before running Contact Tracker because both use the same local port and database. You can close the original project in Eclipse once the new project is working.

## Names and data

| Item | Name |
| --- | --- |
| App | Contact Tracker |
| Folder, Maven artifact and Eclipse project | `contact-tracker` |
| Java package | `com.victorpena.contacttracker` |
| Main class | `ContactTrackerApplication` |
| Existing MySQL database | `book_price_scraper` |

The MySQL database name remains `book_price_scraper` so the renamed app connects to the same saved records. No SQL migrations, table names, endpoints, lookup behavior, or credentials change as part of this rename. Startup scraping keeps its existing setting.

The homepage reads your saved database records. Opening it does not send a Melissa lookup. Use **Test first name**, **Test next 10**, the per-row **Test name** button, or **Test all remaining** to request lookups. Download session results before closing the page. See [MELISSA-SAVED-NAMES.md](MELISSA-SAVED-NAMES.md) for the lookup workflow and diagnostics.

## Verify locally

Run the dashboard checks:

```bash
node --test tests/saved-people.test.cjs
```

Run the selected mocked Java tests without starting the database-backed application context:

```bash
bash mvnw -Dtest='SavedPeopleMelissaServiceTest,SavedPeopleMelissaControllerTest,ObituaryPreservationTest,MelissaPersonSearchClientTest,MelissaTestControllerTest,ObituaryMelissaLookupServiceTest,ScraperStartupRunnerTest,SurvivorSectionExtractorTest' test
```

The `ContactTrackerApplicationTests` context test uses the configured database and is intentionally excluded from that command. Full Spring/Maven startup and live API verification require your Mac's dependencies, MySQL connection and environment variables.

Rename validation: all 46 Java source/test files were checked against the upload and differ only by the package and application-class names. Database settings and migration files were preserved. Seven dashboard tests, the Melissa result-code checks, and installer checks passed. The older `SurvivorParserChecks` has an existing residence-city expectation mismatch that fails identically in the original upload and renamed project; this rename does not alter that parser behavior. Full Spring/Maven startup was not run here.
