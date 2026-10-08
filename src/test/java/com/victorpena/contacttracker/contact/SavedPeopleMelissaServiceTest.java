package com.victorpena.contacttracker.contact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SavedPeopleMelissaServiceTest {
    private final SavedPeopleReader reader = mock(SavedPeopleReader.class);
    private final MelissaPersonSearchClient client = mock(MelissaPersonSearchClient.class);
    private final SavedPeopleMelissaService service = new SavedPeopleMelissaService(reader, client);

    @BeforeEach void configured() { when(client.isConfigured()).thenReturn(true); }

    @Test void usesSavedLocationAndPreservesRequestedOrder() {
        var a = person(1, "Taylor Example", "Austin");
        var b = person(2, "Casey Example", "Dallas");
        when(reader.selected(List.of(2L,1L))).thenReturn(List.of(a,b));
        when(client.searchDetailed(anyString(),anyString(),anyString(),eq(""),eq("strict"),eq(0))).thenReturn(noMatch());
        var result = service.test(List.of(2L,1L));
        assertThat(result.results()).extracting(r -> r.person().personId()).containsExactly(2L,1L);
        assertThat(result.apiRequests()).isEqualTo(2);
        verify(client).searchDetailed("Taylor Example", "Austin", "TX", "", "strict", 0);
        assertThat(result.results().get(1).person().phoneNumber()).isEqualTo("2025550100");
        verify(reader).selected(List.of(2L,1L));
        verifyNoMoreInteractions(reader);
    }

    @Test void deduplicatesSameNameAndLocationButNotDifferentCities() {
        when(reader.selected(List.of(1L,2L,3L))).thenReturn(List.of(
                person(1," Taylor  Example ","Austin"),person(2,"taylor example","austin"),person(3,"Taylor Example","Dallas")));
        when(client.searchDetailed(anyString(),anyString(),anyString(),eq(""),eq("strict"),eq(0))).thenReturn(noMatch());
        var result = service.test(List.of(1L,2L,3L));
        assertThat(result.apiRequests()).isEqualTo(2);
        assertThat(result.results().get(1).reusedResult()).isTrue();
        assertThat(result.results().get(2).reusedResult()).isFalse();
    }

    @Test void ge08StopsAfterFirstRequestAndKeepsItsResultCode() {
        when(reader.selected(List.of(1L,2L))).thenReturn(List.of(person(1,"Taylor Example","Austin"),person(2,"Casey Example","Dallas")));
        when(client.searchDetailed(anyString(),anyString(),anyString(),eq(""),eq("strict"),eq(0)))
                .thenThrow(new MelissaSearchException("MELISSA_API_ERROR","Product not enabled (GE08).",List.of("GE08")));
        var result = service.test(List.of(1L,2L));
        assertThat(result.stopped()).isTrue();
        assertThat(result.apiRequests()).isEqualTo(1);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).resultCodes()).containsExactly("GE08");
    }

    @Test void recordSpecificFailureDoesNotStopOtherNames() {
        when(reader.selected(List.of(1L,2L))).thenReturn(List.of(person(1,"Taylor Example","Austin"),person(2,"Casey Example","Dallas")));
        when(client.searchDetailed(anyString(),anyString(),anyString(),eq(""),eq("strict"),eq(0)))
                .thenThrow(new MelissaSearchException("MELISSA_API_ERROR","Too many matches.",List.of("UE02")))
                .thenReturn(noMatch());
        var result = service.test(List.of(1L,2L));
        assertThat(result.stopped()).isFalse();
        assertThat(result.results()).hasSize(2);
    }

    @Test void rejectedFirstPageStopsBeforeSpendingOnAnotherName() {
        when(reader.selected(List.of(1L,2L))).thenReturn(List.of(person(1,"Taylor Example","Austin"),person(2,"Casey Example","Dallas")));
        when(client.searchDetailed(anyString(),anyString(),anyString(),eq(""),eq("strict"),eq(0)))
                .thenThrow(new MelissaSearchException("MELISSA_API_ERROR","Page out of range (UE03).",List.of("UE03")));
        var result = service.test(List.of(1L,2L));
        assertThat(result.stopped()).isTrue();
        assertThat(result.apiRequests()).isEqualTo(1);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).resultCodes()).containsExactly("UE03");
        verify(client,times(1)).searchDetailed(anyString(),anyString(),anyString(),eq(""),eq("strict"),eq(0));
    }

    @Test void validatesAllIdsBeforeSpendingAnyRequests() {
        when(reader.selected(List.of(1L,999L))).thenReturn(List.of(person(1,"Taylor Example","Austin")));
        assertThatThrownBy(() -> service.test(List.of(1L,999L))).isInstanceOf(IllegalArgumentException.class);
        verify(client,never()).searchDetailed(any(),any(),any(),any(),any(),anyInt());
        assertThatThrownBy(() -> service.test(Collections.nCopies(26,1L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingKeyDoesNotStartReadingOrLookingUpNames() {
        when(client.isConfigured()).thenReturn(false);
        assertThatThrownBy(() -> service.test(List.of(1L))).isInstanceOf(MelissaSearchException.class);
        verifyNoInteractions(reader);
        verify(client,never()).searchDetailed(any(),any(),any(),any(),any(),anyInt());
    }

    static SavedPeopleReader.SavedPerson person(long id, String name, String city) {
        return new SavedPeopleReader.SavedPerson(id,name,"sibling",city,"TX","2025550100","Example deceased","https://example.test/obit");
    }
    static MelissaSearchResult noMatch() {
        return new MelissaSearchResult("NO_MATCH","No match.",Map.of(),"strict",0,5,"UE01",List.of("UE01"),0,0,0,false,List.of());
    }
}
