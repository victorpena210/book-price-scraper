package com.victorpena.contacttracker.contact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class MelissaPersonSearchClient {
    private static final Logger log = LoggerFactory.getLogger(MelissaPersonSearchClient.class);
    private static final String BASE_URL = "https://personatorsearch.melissadata.net";
    private static final String SEARCH_PATH = "/WEB/doPersonatorSearch";
    private static final String COLUMNS = "Phone,MelissaIdentityKey";
    private static final int RECORDS_PER_PAGE = 5;
    static final String LOOKUP_VERSION = "2026-10-08.3";

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public MelissaPersonSearchClient(@Value("${melissa.api-key:}") String apiKey) {
        this(apiKey, RestClient.builder().requestFactory(requestFactory()));
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(20000);
        return factory;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    // Allows request/response regression tests without a live Melissa account.
    MelissaPersonSearchClient(String apiKey, RestClient.Builder builder) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    public List<ContactCandidate> searchByNameAndCityState(String fullName, String city, String state) {
        return searchDetailed(fullName, city, state, "", "strict", 0).candidates();
    }

    public List<ContactCandidate> searchByNameAndPostal(String fullName, String postalCode) {
        return searchDetailed(fullName, "", "", postalCode, "strict", 0).candidates();
    }

    public List<ContactCandidate> searchByName(String fullName) {
        return searchDetailed(fullName, "", "", "", "strict", 0).candidates();
    }

    /** One request. Page zero uses the service's default first page; later pages are explicit. */
    public MelissaSearchResult searchDetailed(String fullName, String city, String state,
                                             String postalCode, String match, int page) {
        String name = cleanValue(fullName);
        if (name.isBlank()) {
            throw new IllegalArgumentException("A name is required.");
        }
        String conditions = cleanValue(match).toLowerCase(Locale.ROOT);
        if (!conditions.equals("strict") && !conditions.equals("loose")) {
            throw new IllegalArgumentException("match must be strict or loose.");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater.");
        }
        requireApiKey();

        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("full", name);
        inputs.put("last", extractLastName(name));
        putIfPresent(inputs, "city", city);
        putIfPresent(inputs, "state", state);
        putIfPresent(inputs, "postal", postalCode);

        // Do not force Page:0 on an initial lookup. Let the service choose its
        // default first page. This is one request, with no automatic paid retry.
        final String options = "MaxPhone:3,RecordsPerPage:" + RECORDS_PER_PAGE
                + ",SearchType:NameSearch,SearchConditions:" + conditions
                + (page == 0 ? "" : ",Page:" + page);
        Map<String, String> variables = new LinkedHashMap<>(inputs);
        variables.put("id", apiKey);
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(SEARCH_PATH)
                                .queryParam("id", "{id}")
                                .queryParam("format", "JSON")
                                .queryParam("t", "obituary-survivor-search")
                                .queryParam("cols", COLUMNS)
                                .queryParam("opt", options);
                        inputs.forEach((key, value) -> builder.queryParam(key, "{" + key + "}"));
                        return builder.build(variables);
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            // RestClient messages may contain the request URL and its API key.
            throw new MelissaSearchException("HTTP_ERROR",
                    "Melissa returned HTTP " + exception.getStatusCode().value() + ".", List.of());
        } catch (RestClientException exception) {
            throw new MelissaSearchException("REQUEST_FAILED",
                    "The Melissa request failed or its response could not be read.", List.of());
        }
        return parseResponse(response, inputs, conditions, page);
    }

    private MelissaSearchResult parseResponse(JsonNode response, Map<String, String> inputs,
                                              String conditions, int page) {
        if (response == null || !response.isObject()) {
            throw unexpected("Melissa returned an empty or unexpected response.", List.of());
        }
        String transmissionResults = response.path("TransmissionResults").asText("");
        List<String> transmissionCodes = MelissaResultCodes.parse(transmissionResults);
        Set<String> codes = new LinkedHashSet<>(transmissionCodes);
        JsonNode records = response.path("Records");
        if (records.isArray()) {
            for (JsonNode record : records) {
                codes.addAll(MelissaResultCodes.parse(record.path("Results").asText("")));
            }
        }
        List<String> resultCodes = List.copyOf(codes);
        try {
            MelissaResultCodes.requireCompletedSearch(resultCodes);
        } catch (MelissaSearchException exception) {
            if (resultCodes.contains("UE03")) {
                throw new MelissaSearchException(exception.getError(),
                        exception.getMessage() + responseSummary(response, records, page), resultCodes);
            }
            throw exception;
        }
        boolean reportsNoMatch = MelissaResultCodes.reportsNoMatch(resultCodes);

        if (!records.isArray()
                && !(reportsNoMatch && (records.isMissingNode() || records.isNull()))) {
            throw unexpected("Melissa did not return the expected Records array.", resultCodes);
        }
        int totalRecords = count(response, "TotalRecords", reportsNoMatch, resultCodes);
        int totalPages = count(response, "TotalPages", reportsNoMatch, resultCodes);
        List<ContactCandidate> candidates = new ArrayList<>();
        if (records.isArray()) {
            for (JsonNode record : records) {
                if (!record.isObject()) {
                    throw unexpected("Melissa returned an unexpected record structure.", resultCodes);
                }
                String name = record.path("FullName").asText("");
                List<String> recordCodes = MelissaResultCodes.parse(record.path("Results").asText(""));
                boolean noMatchStatus = MelissaResultCodes.reportsNoMatch(recordCodes)
                        || MelissaResultCodes.reportsNoMatch(transmissionCodes);
                if (noMatchStatus) {
                    if (codes.contains("US01") || codes.contains("US02")) {
                        throw unexpected("Melissa returned conflicting match and no-match statuses."
                                + responseSummary(response, records, page), resultCodes);
                    }
                    // A no-match row may echo the input name. A nonblank FullName
                    // is not evidence of a candidate when its status says no match.
                    continue;
                }
                if (name.isBlank() && (MelissaResultCodes.reportsNoMatch(recordCodes) || reportsNoMatch)) {
                    continue; // A no-match status record is not a person candidate.
                }
                if (name.isBlank()) {
                    throw unexpected("Melissa returned a candidate without its expected name field.", resultCodes);
                }
                JsonNode address = record.path("CurrentAddress");
                List<String> phones = new ArrayList<>();
                JsonNode phoneRecords = record.path("PhoneRecords");
                if (phoneRecords.isArray()) {
                    for (JsonNode phoneRecord : phoneRecords) {
                        // The documented field is lower-case phoneNumber.
                        String phone = phoneRecord.path("phoneNumber").asText("");
                        if (!phone.isBlank()) {
                            phones.add(phone);
                        }
                    }
                }
                candidates.add(new ContactCandidate(name,
                        address.path("AddressLine1").asText(""),
                        address.path("Suite").asText(""),
                        address.path("City").asText(""),
                        address.path("State").asText(""),
                        address.path("PostalCode").asText(""),
                        record.path("MelissaIdentityKey").asText(""), phones));
            }
        }
        if (candidates.isEmpty() && reportsNoMatch && !codes.contains("US01") && !codes.contains("US02")) {
            // No-match status rows may be included in the response totals. The
            // app reports person candidates, so those rows do not count or paginate.
            totalRecords = 0;
            totalPages = 0;
        }
        if ((candidates.isEmpty() && (totalRecords > 0 || codes.contains("US01") || codes.contains("US02")))
                || totalRecords < candidates.size() || (!candidates.isEmpty() && totalPages == 0)) {
            throw unexpected("Melissa's record totals and returned candidates disagree."
                    + responseSummary(response, records, page)
                    + " Parsed candidates=" + candidates.size() + ".", resultCodes);
        }
        String status = MelissaResultCodes.status(resultCodes, candidates.size());
        String message = switch (status) {
            case "NO_EXACT_MATCH" -> "Melissa reported no exact matches for these inputs and options.";
            case "NO_MATCH" -> "Melissa reported no matches for this request.";
            case "POSSIBLE_MATCHES" -> "Melissa returned possible matches.";
            default -> "Melissa returned matching candidates.";
        };
        log.info("Melissa result codes={}, returned={}, total={}, pages={}, requestedPage={}",
                resultCodes, candidates.size(), totalRecords, totalPages, page);
        return new MelissaSearchResult(status, message, inputs, conditions, page, RECORDS_PER_PAGE,
                transmissionResults, resultCodes, totalRecords, totalPages, candidates.size(),
                page < totalPages - 1, candidates);
    }

    // Only bounded numbers and our page mode are exposed; never include a raw
    // response, request URL, key, name, address, or phone in an error diagnostic.
    private String responseSummary(JsonNode response, JsonNode records, int page) {
        int rows = 0, namedRows = 0;
        if (records.isArray()) {
            for (JsonNode record : records) {
                rows++;
                if (!record.path("FullName").asText("").isBlank()) namedRows++;
            }
        }
        return " [lookup " + LOOKUP_VERSION + "; page=" + (page == 0 ? "service default" : page)
                + "; totalRecords=" + diagnosticCount(response, "TotalRecords")
                + "; totalPages=" + diagnosticCount(response, "TotalPages")
                + "; responseRows=" + rows + "; namedRows=" + namedRows + "]";
    }

    private String diagnosticCount(JsonNode response, String field) {
        String value = response.path(field).asText("").trim();
        return value.matches("[0-9]{1,9}") ? value : "missing/invalid";
    }

    private int count(JsonNode response, String field, boolean allowMissing, List<String> codes) {
        JsonNode value = response.path(field);
        if (allowMissing && (value.isMissingNode() || value.isNull() || value.asText("").isBlank())) {
            return 0;
        }
        try {
            int count = Integer.parseInt(value.asText("").trim());
            if (count >= 0) {
                return count;
            }
        } catch (NumberFormatException ignored) {
            // A malformed count must not masquerade as zero results.
        }
        throw unexpected("Melissa returned an invalid or missing " + field + ".", codes);
    }

    private MelissaSearchException unexpected(String message, List<String> codes) {
        return new MelissaSearchException("UNEXPECTED_RESPONSE", message, codes);
    }

    private void putIfPresent(Map<String, String> inputs, String key, String value) {
        String cleaned = cleanValue(value);
        if (!cleaned.isBlank()) {
            inputs.put(key, cleaned);
        }
    }

    private String extractLastName(String fullName) {
        String[] parts = fullName.split("\\s+");
        return parts[parts.length - 1].replaceAll("^[^\\p{L}'’-]+|[^\\p{L}'’-]+$", "");
    }

    private String cleanValue(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private void requireApiKey() {
        if (apiKey.isBlank()) {
            throw new MelissaSearchException("CONFIGURATION_ERROR",
                    "Melissa API key is missing. Set MELISSA_API_KEY or melissa.api-key.", List.of());
        }
    }
}
