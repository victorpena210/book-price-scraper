package com.victorpena.contacttracker.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BookScraperClient {

    private static final String BASE_URL =
            "https://www.legacy.com/us/obituaries/local/texas/austin-area";

    // Legacy embeds the obituary listing inside Next.js hydration data.
    // These are the escaped field markers that appear inside the script text.
    private static final String RESULTS_KEY = "\\\"results\\\":[";
    private static final String RESULTS_END_KEY = "\\\"results_exact\\\":";
    private static final String NAME_KEY = "\\\"full_name\\\":\\\"";
    private static final String NAME_END_KEY = "\\\",\\\"first_name\\\":";
    private static final String URL_KEY = "\\\"url\\\":\\\"";
    private static final String URL_END_KEY = "\\\",\\\"id\\\":";

    private static final long REQUEST_DELAY_MS = 300L;

    public List<ScrapedBook> scrapeFirstPage() throws IOException {
        scrapeObituaries();

        // This Legacy.com code is currently being used as a scraping playground.
        // The real book scraper will eventually return ScrapedBook objects here.
        return new ArrayList<>();
    }

    /**
     * Scrapes the Legacy listing and returns each obituary together with the
     * survivor names that our conservative parser could extract.
     *
     * Keeping this as a real return value lets later stages (such as Melissa
     * enrichment) consume the parsed survivors instead of scraping them again
     * from console output.
     */
    public List<ObituaryPerson> scrapeObituaries() throws IOException {

        Document listingDocument = connect(BASE_URL);
        List<ObituaryPerson> people = extractPeople(listingDocument);
        List<ObituaryPerson> parsedPeople = new ArrayList<>();

        System.out.println("PEOPLE FOUND: " + people.size());
        System.out.println("========================================");

        for (ObituaryPerson person : people) {
            System.out.println("PERSON: " + person.name());
            System.out.println("URL: " + person.obituaryUrl());

            try {
                Document obituary = connect(person.obituaryUrl());
                List<String> sections =
                        new SurvivorSectionExtractor().extract(obituary);

                /*
                 * TEMPORARY DEBUGGING:
                 * Show the exact survivor text before SurvivorParser changes it.
                 */
                System.out.println();
                System.out.println("========== RAW SURVIVOR SECTION ==========");
                System.out.println("PERSON: " + person.name());

                if (sections.isEmpty()) {
                    System.out.println("NO SURVIVOR SECTION EXTRACTED");
                } else {
                    for (String section : sections) {
                        System.out.println(section);
                    }
                }

                System.out.println("===========================================");
                System.out.println();

                List<Survivor> survivors =
                        new SurvivorParser().parseSections(sections);
                ObituaryPerson parsedPerson = new ObituaryPerson(
                        person.name(), person.obituaryUrl(), survivors);

                parsedPeople.add(parsedPerson);

                System.out.println("SURVIVORS:");
                if (sections.isEmpty()) {
                    System.out.println("No explicit survivor section found.");
                } else if (parsedPerson.survivors().isEmpty()) {
                    System.out.println("Section found, but no names could be confidently parsed.");
                } else {
                    for (Survivor survivor : parsedPerson.survivors()) {
                        System.out.println(survivor.name() + " — " + survivor.relationship());
                    }
                }
            } catch (IOException exception) {
                // One obituary page failing should not stop the rest of the run.
                parsedPeople.add(person);
                System.out.println(
                        "COULD NOT LOAD OBITUARY: " + exception.getMessage()
                );
            }

            System.out.println("========================================");
            pauseBetweenRequests();
        }

        return List.copyOf(parsedPeople);
    }

    private Document connect(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .get();
    }

    /**
     * Extracts each obituary person's name and the first obituary URL from
     * the primary results list embedded in Legacy's Next.js hydration script.
     */
    private List<ObituaryPerson> extractPeople(Document document) {

        Map<String, ObituaryPerson> peopleByName = new LinkedHashMap<>();

        for (Element script : document.select("script")) {
            String data = script.data();

            int resultsStart = data.indexOf(RESULTS_KEY);

            if (resultsStart == -1) {
                continue;
            }

            int resultsEnd = data.indexOf(RESULTS_END_KEY, resultsStart);

            if (resultsEnd == -1) {
                resultsEnd = data.length();
            }

            String resultsData = data.substring(resultsStart, resultsEnd);
            extractPeopleFromResultsData(resultsData, peopleByName);

            // The first hydration block with a results array is the listing
            // we are currently experimenting with, so we stop after it.
            if (!peopleByName.isEmpty()) {
                break;
            }
        }

        return new ArrayList<>(peopleByName.values());
    }

    private void extractPeopleFromResultsData(
            String data,
            Map<String, ObituaryPerson> peopleByName
    ) {

        int searchFrom = 0;

        while (true) {
            int nameKeyStart = data.indexOf(NAME_KEY, searchFrom);

            if (nameKeyStart == -1) {
                break;
            }

            int nameStart = nameKeyStart + NAME_KEY.length();
            int nameEnd = data.indexOf(NAME_END_KEY, nameStart);

            if (nameEnd == -1) {
                break;
            }

            int nextNameStart = data.indexOf(NAME_KEY, nameEnd + NAME_END_KEY.length());
            int urlKeyStart = data.indexOf(URL_KEY, nameEnd);

            // If the next person's record starts before we found a URL,
            // this result did not give us a usable obituary URL.
            if (urlKeyStart == -1
                    || (nextNameStart != -1 && urlKeyStart > nextNameStart)) {
                searchFrom = nameEnd + NAME_END_KEY.length();
                continue;
            }

            int urlStart = urlKeyStart + URL_KEY.length();
            int urlEnd = data.indexOf(URL_END_KEY, urlStart);

            if (urlEnd == -1
                    || (nextNameStart != -1 && urlEnd > nextNameStart)) {
                searchFrom = nameEnd + NAME_END_KEY.length();
                continue;
            }

            String name = decodeEmbeddedValue(data.substring(nameStart, nameEnd));
            String obituaryUrl = decodeEmbeddedValue(data.substring(urlStart, urlEnd));

            if (!name.isBlank() && !obituaryUrl.isBlank()) {
                peopleByName.putIfAbsent(
                        name,
                        new ObituaryPerson(name, obituaryUrl)
                );
            }

            searchFrom = urlEnd + URL_END_KEY.length();
        }
    }

    /**
     * Decodes the few escape sequences we encounter in the embedded Next.js
     * strings without pulling in a second JSON parsing dependency.
     */
    private String decodeEmbeddedValue(String value) {
        return value
                .replace("\\\\\\\"", "\"")
                .replace("\\u" + "0026", "&")
                .replace("\\/", "/")
                .trim();
    }

    private void pauseBetweenRequests() {
        try {
            Thread.sleep(REQUEST_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
