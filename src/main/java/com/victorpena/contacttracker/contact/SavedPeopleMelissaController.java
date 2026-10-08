package com.victorpena.contacttracker.contact;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/people/melissa")
public class SavedPeopleMelissaController {
    private final SavedPeopleReader reader;
    private final SavedPeopleMelissaService service;
    private final MelissaPersonSearchClient client;

    public SavedPeopleMelissaController(SavedPeopleReader reader, SavedPeopleMelissaService service,
            MelissaPersonSearchClient client) {
        this.reader = reader;
        this.service = service;
        this.client = client;
    }

    @GetMapping("/records")
    public Records records() {
        var catalog = reader.catalog();
        return new Records(client.isConfigured(), catalog.totalPeople(), catalog.totalObituaries(),
                catalog.people(), MelissaPersonSearchClient.LOOKUP_VERSION);
    }

    @PostMapping(value = "/test", consumes = "application/json")
    public SavedPeopleMelissaService.BatchResult test(@RequestBody TestRequest request) {
        if (request == null) throw new IllegalArgumentException("Choose saved people to test.");
        return service.test(request.personIds());
    }

    public record TestRequest(List<Long> personIds) { }
    public record Records(boolean apiKeyConfigured, long totalPeople, long totalObituaries,
            List<SavedPeopleReader.SavedPerson> people, String lookupVersion) { }
}
