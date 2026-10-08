package com.victorpena.contacttracker.scraper;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookScraperClientTest {
    @Test void rejectsUnsafeLinksAndRedirectTargets() {
        for (String url : new String[]{"http://www.legacy.com/us/obituaries/a", "https://127.0.0.1/us/obituaries/a",
                "https://www.legacy.com.attacker.test/us/obituaries/a", "https://user@www.legacy.com/us/obituaries/a",
                "https://www.legacy.com:8443/us/obituaries/a", "https://www.legacy.com/us/obituaries/../../admin",
                "https://www.legacy.com/account", "file:///etc/passwd"}) {
            assertThatThrownBy(() -> BookScraperClient.validateFetchUrl(url)).isInstanceOf(IOException.class);
        }
        assertThatCode(() -> BookScraperClient.validateFetchUrl("https://www.legacy.com/us/obituaries/name/alice?id=1"))
                .doesNotThrowAnyException();
    }

    @Test void extractsListingLinksAndKeepsDifferentPeopleWithSameName() throws Exception {
        String data = "{\"results\":[{\"full_name\":\"Alice Example\",\"first_name\":\"Alice\",\"url\":\"https://www.legacy.com/us/obituaries/name/alice?id=1\",\"id\":1},"
                + "{\"full_name\":\"Alice Example\",\"first_name\":\"Alice\",\"url\":\"https://www.legacy.com/us/obituaries/name/alice?id=2\",\"id\":2}],\"results_exact\":2}";
        BookScraperClient scraper = spy(new BookScraperClient());
        doReturn(Jsoup.parse("<script>" + data.replace("\"", "\\\"") + "</script>"))
                .when(scraper).connect(BookScraperClient.AUSTIN_URL);
        assertThat(scraper.loadListing(BookScraperClient.AUSTIN_URL)).hasSize(2);
    }

    @Test void changedMarkupIsAnErrorRatherThanAnEmptySuccess() throws Exception {
        BookScraperClient scraper = spy(new BookScraperClient());
        doReturn(Jsoup.parse("<h1>No listing in this page</h1>")).when(scraper).connect(anyString());
        assertThatThrownBy(() -> scraper.loadListing(BookScraperClient.AUSTIN_URL)).isInstanceOf(IOException.class)
                .hasMessageContaining("No obituary links");
    }
}
