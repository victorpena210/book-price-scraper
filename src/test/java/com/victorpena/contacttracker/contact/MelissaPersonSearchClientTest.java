package com.victorpena.contacttracker.contact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MelissaPersonSearchClientTest {
    private static final String RECORD = """
            {"Results":"VR01", "FullName":"Taylor Example", "MelissaIdentityKey":"test-id",
             "CurrentAddress":{"AddressLine1":"123 Example St", "Suite":"A", "City":"Example City",
                               "State":"TX", "PostalCode":"00000"},
             "PhoneRecords":[{"phoneNumber":"2025550100"}]}
            """;
    private MockRestServiceServer server;
    private MelissaPersonSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MelissaPersonSearchClient("test-api-key", builder);
    }

    @Test
    void sendsLocationAndRetainsDocumentedAddressAndPhoneFields() {
        server.expect(request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/WEB/doPersonatorSearch");
            Map<String, String> query = query(request.getURI());
            assertThat(query).containsEntry("full", "Taylor Example")
                    .containsEntry("last", "Example").containsEntry("city", "Example City")
                    .containsEntry("state", "TX").containsEntry("id", "test-api-key")
                    .containsEntry("cols", "Phone,MelissaIdentityKey");
            assertThat(query.get("opt")).contains("SearchConditions:strict", "RecordsPerPage:5")
                    .doesNotContain(",Page:", "ReturnAllPages", "progressive", "batch");
        }).andRespond(withSuccess(success(RECORD, 1, 1), MediaType.APPLICATION_JSON));

        MelissaSearchResult result = client.searchDetailed("  Taylor   Example ", "Example City", "TX", "", "strict", 0);
        assertThat(result.status()).isEqualTo("MATCHES_FOUND");
        assertThat(result.searchInputs()).doesNotContainKeys("id", "postal");
        assertThat(result.candidates()).hasSize(1);
        ContactCandidate candidate = result.candidates().get(0);
        assertThat(candidate.fullName()).isEqualTo("Taylor Example");
        assertThat(candidate.addressLine1()).isEqualTo("123 Example St");
        assertThat(candidate.city()).isEqualTo("Example City");
        assertThat(candidate.phoneNumbers()).containsExactly("2025550100");
        assertThat(result.resultCodes()).containsExactly("US01", "VR01");
        server.verify();
    }

    @Test
    void apiAccessErrorIsNotAnEmptyCandidateList() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"TransmissionResults\":\"GE08\",\"Records\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("GE08")
                .hasMessageContaining("not enabled");
        server.verify();
    }

    @Test
    void checksRecordLevelErrorsAsWellAsTransmissionErrors() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"TransmissionResults\":\"\",\"Records\":[{\"Results\":\"UE05\"}]}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("UE05");
        server.verify();
    }

    @Test
    void tooManyMatchesIsNotReportedAsNoMatches() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"TransmissionResults\":\"US03\",\"Records\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("too many matches");
        server.verify();
    }

    @Test
    void noExactMatchIsVisibleAndDoesNotTriggerAnotherPaidRequest() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"TransmissionResults\":\"UE04\",\"TotalRecords\":\"0\",\"TotalPages\":\"0\",\"Records\":[]}",
                MediaType.APPLICATION_JSON));
        MelissaSearchResult result = client.searchDetailed("Taylor Example", "", "", "", "strict", 0);
        assertThat(result.status()).isEqualTo("NO_EXACT_MATCH");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.resultCodes()).containsExactly("UE04");
        server.verify();
    }

    @Test
    void noMatchPlaceholderDoesNotBecomeABlankPerson() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"TransmissionResults\":\"\",\"TotalRecords\":\"0\",\"TotalPages\":\"0\",\"Records\":[{\"Results\":\"UE01\"}]}",
                MediaType.APPLICATION_JSON));
        assertThat(client.searchByName("Taylor Example")).isEmpty();
        server.verify();
    }

    @Test
    void countedNoMatchPlaceholderCompletesWithNoCandidatesOrMorePages() {
        server.expect(anything()).andRespond(withSuccess("""
                {"TransmissionResults":"", "TotalRecords":"1", "TotalPages":"1",
                 "Records":[{"Results":"UE01", "FullName":""}]}
                """, MediaType.APPLICATION_JSON));
        MelissaSearchResult result = client.searchDetailed("Taylor Example", "", "", "", "strict", 0);
        assertThat(result.status()).isEqualTo("NO_MATCH");
        assertThat(result.resultCodes()).containsExactly("UE01");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.returnedRecords()).isZero();
        assertThat(result.totalRecords()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.morePagesAvailable()).isFalse();
        server.verify();
    }

    @Test
    void noMatchRowEchoingANameIsNotACandidate() {
        server.expect(anything()).andRespond(withSuccess("""
                {"TransmissionResults":"", "TotalRecords":"0", "TotalPages":"0",
                 "Records":[{"Results":"UE01", "FullName":"Taylor Example",
                             "PhoneRecords":[{"phoneNumber":"2025550100"}]}]}
                """, MediaType.APPLICATION_JSON));
        MelissaSearchResult result = client.searchDetailed("Taylor Example", "", "", "", "strict", 0);
        assertThat(result.status()).isEqualTo("NO_MATCH");
        assertThat(result.resultCodes()).containsExactly("UE01");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.totalRecords()).isZero();
        server.verify();
    }

    @Test
    void transmissionNoExactMatchCanEchoANameWithoutARecordStatus() {
        server.expect(anything()).andRespond(withSuccess("""
                {"TransmissionResults":"UE04", "TotalRecords":"1", "TotalPages":"1",
                 "Records":[{"FullName":"Taylor Example"}]}
                """, MediaType.APPLICATION_JSON));
        MelissaSearchResult result = client.searchDetailed("Taylor Example", "", "", "", "strict", 0);
        assertThat(result.status()).isEqualTo("NO_EXACT_MATCH");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.totalRecords()).isZero();
        assertThat(result.morePagesAvailable()).isFalse();
        server.verify();
    }

    @Test
    void rejectedDefaultPageReportsSafeCountsWithoutAutomaticRetry() {
        server.expect(request -> assertThat(query(request.getURI()).get("opt")).doesNotContain(",Page:"))
                .andRespond(withSuccess("""
                {"TransmissionResults":"UE03", "TotalRecords":"0", "TotalPages":"0",
                 "Records":[{"FullName":"Taylor Example", "Results":"UE03"}]}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("UE03")
                .hasMessageContaining("page=service default").hasMessageContaining("totalRecords=0")
                .hasMessageContaining("totalPages=0").hasMessageContaining("namedRows=1")
                .hasMessageNotContaining("test-api-key").hasMessageNotContaining("Taylor Example");
        server.verify();
    }

    @Test
    void noExactMatchWithPositiveTotalsDoesNotTriggerAnotherPaidRequest() {
        server.expect(anything()).andRespond(withSuccess("""
                {"TransmissionResults":"UE04", "TotalRecords":"10", "TotalPages":"2",
                 "Records":[]}
                """, MediaType.APPLICATION_JSON));
        MelissaSearchResult result = client.searchDetailed("Taylor Example", "", "", "", "strict", 0);
        assertThat(result.status()).isEqualTo("NO_EXACT_MATCH");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.totalRecords()).isZero();
        assertThat(result.morePagesAvailable()).isFalse();
        server.verify();
    }

    @Test
    void contradictoryMatchCodeStillFailsInsteadOfBecomingANoMatch() {
        server.expect(anything()).andRespond(withSuccess("""
                {"TransmissionResults":"US01", "TotalRecords":"1", "TotalPages":"1",
                 "Records":[{"Results":"UE01"}]}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("conflicting");
        server.verify();
    }

    @Test
    void positiveTotalsWithoutNoMatchCodeStillFail() {
        server.expect(anything()).andRespond(withSuccess("""
                {"TransmissionResults":"", "TotalRecords":"1", "TotalPages":"1", "Records":[]}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("totals");
        server.verify();
    }

    @Test
    void nullBodyIsAnUpstreamFailure() {
        server.expect(anything()).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("empty or unexpected");
        server.verify();
    }

    @Test
    void malformedRecordsAreNotReportedAsNoMatches() {
        server.expect(anything()).andRespond(withSuccess("{\"Records\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("Records array");
        server.verify();
    }

    @Test
    void missingRecordCountCannotMasqueradeAsZero() {
        server.expect(anything()).andRespond(withSuccess("{\"Records\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("TotalRecords");
        server.verify();
    }

    @Test
    void reportsFirstPageTruncationAndLetsTheCallerRequestTheNextPage() {
        String fiveRecords = String.join(",", Collections.nCopies(5, RECORD));
        server.expect(anything()).andRespond(withSuccess(success(fiveRecords, 6, 2), MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(query(request.getURI()).get("opt")).contains("Page:1"))
                .andRespond(withSuccess(success(RECORD, 6, 2), MediaType.APPLICATION_JSON));
        MelissaSearchResult first = client.searchDetailed("Taylor Example", "", "", "", "strict", 0);
        assertThat(first.totalRecords()).isEqualTo(6);
        assertThat(first.returnedRecords()).isEqualTo(5);
        assertThat(first.morePagesAvailable()).isTrue();
        MelissaSearchResult second = client.searchDetailed("Taylor Example", "", "", "", "strict", 1);
        assertThat(second.morePagesAvailable()).isFalse();
        server.verify();
    }

    @Test
    void looseMatchingIsOnlySentWhenRequestedAndInputsAreEncoded() {
        server.expect(request -> {
            Map<String, String> query = query(request.getURI());
            assertThat(query).containsEntry("full", "Taylor O'Example+Test")
                    .containsEntry("postal", "00000");
            assertThat(query.get("opt")).contains("SearchConditions:loose");
        }).andRespond(withSuccess(success(RECORD, 1, 1), MediaType.APPLICATION_JSON));
        client.searchDetailed("Taylor O'Example+Test", "", "", "00000", "loose", 0);
        server.verify();
    }

    @Test
    void httpFailuresDoNotExposeTheKeyOrResponseBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("test-api-key"));
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("test-api-key").hasNoCause();
        server.verify();
    }

    @Test
    void networkFailuresAreDistinctFromNoMatchAndHaveASafeMessage() {
        server.expect(anything()).andRespond(request -> { throw new IOException("test-api-key"); });
        assertThatThrownBy(() -> client.searchByName("Taylor Example"))
                .isInstanceOf(MelissaSearchException.class).hasMessageContaining("request failed")
                .hasMessageNotContaining("test-api-key").hasNoCause();
        server.verify();
    }

    @Test
    void validatesInputsBeforeSendingAnyRequest() {
        assertThatThrownBy(() -> client.searchDetailed(" ", "", "", "", "strict", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.searchDetailed("Taylor Example", "", "", "", "strict,ReturnAllPages:true", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.searchDetailed("Taylor Example", "", "", "", "strict", -1))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    private static String success(String records, int totalRecords, int totalPages) {
        return "{\"TransmissionResults\":\"US01\",\"TotalRecords\":\"" + totalRecords
                + "\",\"TotalPages\":\"" + totalPages + "\",\"Records\":[" + records + "]}";
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(part -> decode(part[0]), part -> decode(part[1])));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
