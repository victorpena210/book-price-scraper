package com.victorpena.contacttracker.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.LinkedHashSet;
import java.util.List;

public class SurvivorSectionExtractor {
    private final SurvivorParser parser = new SurvivorParser();

    public List<String> extract(Document source) {
        Document document = source.clone();
        // Exclude comments before examining paragraphs or text-only containers.
        document.select("script, style, nav, footer, aside, form, [role=comment], "
                + "[id~=(?i)guestbook|tribute|condolence|comment], "
                + "[class~=(?i)guestbook|tribute|condolence|comment], "
                + "[data-testid~=(?i)guestbook|tribute|condolence|comment]").remove();
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        for (Element element : document.select("p, li, div, article, section")) {
            // Smallest text block prevents a whole-page fallback from pulling in unrelated content.
            if (!element.select("p, li, div, article, section").stream()
                    .filter(child -> child != element).toList().isEmpty()) continue;
            sections.addAll(parser.findSections(element.text()));
        }
        return List.copyOf(sections);
    }
}
