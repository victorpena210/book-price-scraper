package com.victorpena.contacttracker.contact;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/people")
public class PersonPhoneController {

    private final PersonPhoneService personPhoneService;

    public PersonPhoneController(PersonPhoneService personPhoneService) {
        this.personPhoneService = personPhoneService;
    }

    /**
     * Shows saved people that do not yet have a phone number.
     *
     * GET /api/people/missing-phone
     */
    @GetMapping("/missing-phone")
    public List<PersonPhoneUpdateResult> getPeopleMissingPhones() {
        return personPhoneService.getPeopleMissingPhones();
    }

    /**
     * Saves one already-authorized phone result to one exact Person row.
     *
     * PATCH /api/people/phone?personId=57&phoneNumber=2105551234
     */
    @PatchMapping("/phone")
    public PersonPhoneUpdateResult savePhone(
            @RequestParam Long personId,
            @RequestParam String phoneNumber
    ) {
        return personPhoneService.savePhoneNumber(personId, phoneNumber);
    }

    /**
     * Saves several already-authorized phone results at once.
     *
     * POST /api/people/phones
     */
    @PostMapping("/phones")
    public List<PersonPhoneUpdateResult> savePhones(
            @RequestBody List<PersonPhoneUpdateRequest> updates
    ) {
        return personPhoneService.savePhoneNumbers(updates);
    }
}
