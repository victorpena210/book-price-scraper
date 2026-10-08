package com.victorpena.contacttracker.controller;

import com.victorpena.contacttracker.contact.ContactCandidate;
import com.victorpena.contacttracker.contact.MelissaPersonSearchClient;
import com.victorpena.contacttracker.contact.MelissaSearchResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/melissa")
public class MelissaTestController {

    private final MelissaPersonSearchClient melissaClient;

    public MelissaTestController(
            MelissaPersonSearchClient melissaClient
    ) {
        this.melissaClient = melissaClient;
    }

    /** Shows the request inputs, result codes and pagination without the API key. */
    @PostMapping("/diagnose")
    public MelissaSearchResult diagnose(
            @RequestParam String name,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "") String postal,
            @RequestParam(defaultValue = "strict") String match,
            @RequestParam(defaultValue = "0") int page
    ) {
        return melissaClient.searchDetailed(name, city, state, postal, match, page);
    }

    /*
     * Name + city + state test.
     *
     * Example:
     *
     * /api/melissa/search-by-location
     * ?name=Joe Gaddy
     * &city=San Marcos
     * &state=Texas
     */
    @PostMapping("/search-by-location")
    public List<ContactCandidate> searchByLocation(
            @RequestParam String name,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "") String state
    ) {

        return melissaClient.searchByNameAndCityState(
                name,
                city,
                state
        );
    }

    /*
     * Existing name-only test.
     */
    @PostMapping("/search-by-name")
    public List<ContactCandidate> searchByName(
            @RequestParam String name
    ) {

        return melissaClient.searchByName(
                name
        );
    }

    /*
     * Existing name + postal-code test.
     */
    @PostMapping("/search")
    public List<ContactCandidate> searchByPostal(
            @RequestParam String name,
            @RequestParam String postal
    ) {

        return melissaClient.searchByNameAndPostal(
                name,
                postal
        );
    }
}
