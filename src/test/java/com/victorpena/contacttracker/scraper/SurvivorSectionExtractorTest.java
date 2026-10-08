package com.victorpena.contacttracker.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SurvivorSectionExtractorTest {
    private final SurvivorSectionExtractor extractor = new SurvivorSectionExtractor();

    @Test
    void excludesGuestbookAndScripts() {
        assertEquals(List.of("his wife Jane Smith"), extractor.extract(Jsoup.parse("""
                <article><p>He is survived by his wife Jane Smith. Funeral Monday.</p></article>
                <div id="guestbook"><p>He is survived by son Wrong Name.</p></div>
                <script>He is survived by daughter Wrong Person.</script>
                """)));
    }

    @Test
    void acceptsTextContainerAndDeduplicates() {
        assertEquals(List.of("son John Smith"), extractor.extract(Jsoup.parse("""
                <div><div>Survivors include son John Smith.</div></div>
                <p>Survivors include son John Smith.</p>
                """)));
    }

    @Test
    void rejectsGenericLeavesBehind() {
        assertEquals(List.of(), extractor.extract(Jsoup.parse(
                "<p>She leaves behind a wonderful legacy.</p>")));
    }

    @Test
    void parserRegressionChecks() {
        SurvivorParserChecks.main(new String[0]);
    }
}
