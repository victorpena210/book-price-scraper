# Melissa lookup review — September 20, 2026

The uploaded code had problems that can hide the reason a search failed. These
fixes make that reason visible. A live response for the reported Joe Gaddy
search was not available during this review, so the specific cause of the
website/API discrepancy is still unconfirmed.

## What was found and changed

- `parseCandidates` returned an empty list for a null response or a missing or
  incorrectly shaped `Records` field. It logged `TransmissionResults`, but did
  not interpret it, and ignored each record's `Results`. The client now checks
  both fields, reports API failures, and rejects unexpected response structures.
- No-match status records could become blank candidate objects. They now produce
  an empty candidate list with a meaningful diagnostic status.
- The client requested only five records and discarded pagination information.
  The diagnostic response now includes the returned count, total count, page
  count, and whether more pages are available. A caller can request the next page.
- `ObituaryMelissaLookupService` always used name-only searches even when the
  parsed survivor had a residence. It now includes that survivor's explicit city
  and/or state. Repeated queries are deduplicated by name and locality together.
- Matching mode is explicit. Existing list endpoints keep strict matching; the
  diagnostic endpoint can compare strict and loose matching on request. It does
  not automatically retry or broaden a search.
- Upstream HTTP/network errors now have safe messages that omit the API key and
  upstream request URL. No live lookup was performed as part of testing.

The uploaded endpoint, `Phone` column, `CurrentAddress` object, and lower-case
`PhoneRecords[].phoneNumber` mapping agree with Melissa's documentation. The
name fields used for the reported two-part name remain `full` and `last`, so the
diagnostic compares the same name request rather than guessing a different name.

## Use the updated project

1. Extract this ZIP, open the project in IntelliJ, and reload Maven.
2. Keep your existing database and Melissa configuration. Stop the old app and
   rebuild/restart this updated project.
3. In your browser, open the diagnostic endpoint with the same search inputs you
   entered on the Melissa website. For the reported name-only comparison:

   ```text
   http://localhost:8080/api/melissa/diagnose?name=Joe%20Gaddy
   ```

   If your website search included a location, supply the same location using
   `&city=...&state=...`, or `&postal=...`. Do not infer a survivor's location from
   the obituary listing's location.

4. Inspect `status`, `resultCodes`, `searchInputs`, `searchConditions`,
   `returnedRecords`, `totalRecords`, `totalPages`, and `morePagesAvailable`.
5. If the result is `NO_EXACT_MATCH`, you can explicitly compare loose matching
   by adding `&match=loose` to the same URL. If more pages exist, use `&page=1` for
   the second page, `&page=2` for the third, and so on. Each visit makes one API
   request. Candidates are not automatically saved or confirmed as an identity.

The existing `/api/melissa/search-by-name`, `/search-by-location`, and `/search`
routes still return candidate arrays on completed searches. On failure, direct
routes now return an HTTP error with `error`, `message`, and `resultCodes`.
Batch failures remain in each survivor's `error` field. Use `/diagnose` to inspect
full metadata for a particular search.

## Reading the result

| Diagnostic or code | Meaning for troubleshooting |
| --- | --- |
| `UE01` / `NO_MATCH` | This request produced no match. |
| `UE04` / `NO_EXACT_MATCH` | These inputs and options did not yield an exact match. |
| `UE02` or `US03` | Narrow the search; too many records matched. |
| `UE05` | The state or postal input was rejected. |
| `GE08` | The API key lacks the requested product/level access. |
| `GE14` | The account has exhausted its credits. |
| `UNEXPECTED_RESPONSE` | The response could not be interpreted reliably. |

The website returning a result does not establish which inputs, matching mode,
or API entitlement produced your application's response. The diagnostic makes
the application side of that comparison available without exposing the API key.

## Verification

Completed here:

- Compiled the result-code logic, exception, candidate type, diagnostic result
  type, and standalone checks with Java 17.
- Passed 17 result-code regression checks, including API access errors, excess
  matches, invalid locality, no exact match, possible matches, mixed success/error
  codes, and malformed code fields.
- Parsed all project Java sources for Java 17 syntax.

Full Maven compilation and Spring integration tests could not run: Maven could
not resolve `repo.maven.apache.org` to download the Spring Boot 4.1.0 parent.
This was also a build limitation on the original upload before changes. The
application, MySQL, and a live Melissa response were not tested here.

Run the focused integration tests on your machine from the project root:

```bash
./mvnw -Dtest=MelissaPersonSearchClientTest,MelissaTestControllerTest,ObituaryMelissaLookupServiceTest test
```

These tests use mock API responses and scraper results, and do not require a
Melissa key or a running MySQL database. They cover response mapping, error
propagation, pagination, explicit match settings, safe errors, locality handling,
deduplication, and lookup limits.

The standalone checks can also run with Java 17 alone:

```bash
mkdir -p target/melissa-checks
javac -d target/melissa-checks src/main/java/com/victorpena/contacttracker/contact/MelissaSearchException.java src/main/java/com/victorpena/contacttracker/contact/MelissaResultCodes.java src/test/java/com/victorpena/contacttracker/contact/MelissaResultCodesChecks.java
java -cp target/melissa-checks com.victorpena.contacttracker.contact.MelissaResultCodesChecks
```

## References

- [Melissa Personator Search reference](https://docs.melissa.com/cloud-api/personator-search/personator-search-reference-guide.html)
- [Melissa Personator Search result codes](https://docs.melissa.com/cloud-api/personator-search/result-codes.html)
