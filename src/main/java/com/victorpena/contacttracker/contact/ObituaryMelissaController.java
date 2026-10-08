package com.victorpena.contacttracker.contact;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/obituaries")
public class ObituaryMelissaController {

    private final ObituaryMelissaLookupService obituaryMelissaLookupService;

    public ObituaryMelissaController(
            ObituaryMelissaLookupService obituaryMelissaLookupService
    ) {
        this.obituaryMelissaLookupService = obituaryMelissaLookupService;
    }

    @GetMapping("/melissa")
    public List<SurvivorContactLookup> lookupSurvivors(
            @RequestParam(defaultValue = "5") int limit
    ) throws IOException {
        return obituaryMelissaLookupService.lookupSurvivors(limit);
    }
}
