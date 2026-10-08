package com.victorpena.contacttracker.contact;

import com.victorpena.contacttracker.model.*;
import com.victorpena.contacttracker.repository.*;
import com.victorpena.contacttracker.scraper.ObituaryPerson;
import com.victorpena.contacttracker.service.ObituaryPersistenceService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ObituaryPreservationTest {
    @Test void aLoadedPageWithNoParsedSurvivorsPreservesExistingPeople() throws Exception {
        var obituaries = mock(ObituaryRepository.class);
        var people = mock(PersonRepository.class);
        var obituary = new Obituary();
        var person = new Person();
        var id = Person.class.getDeclaredField("id");
        id.setAccessible(true); id.set(person, 10L);
        person.setFullName("Taylor Example"); person.setPhoneNumber("2025550100");
        when(obituaries.findByObituaryUrl("https://example.test/obit")).thenReturn(Optional.of(obituary));
        when(obituaries.save(obituary)).thenReturn(obituary);
        when(people.findByObituary_Id(null)).thenReturn(List.of(person));
        new ObituaryPersistenceService(obituaries,people).saveResults(
                List.of(new ObituaryPerson("Example deceased","https://example.test/obit",List.of(),true)));
        verify(people).findByObituary_Id(null);
        verifyNoMoreInteractions(people);
        assertThat(person.getFullName()).isEqualTo("Taylor Example");
        assertThat(person.getPhoneNumber()).isEqualTo("2025550100");
    }
}
