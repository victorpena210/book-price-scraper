package com.victorpena.contacttracker.scraper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative English rule parser for explicit obituary survivor wording.
 *
 * Ambiguous or unsupported wording may be omitted rather than guessed.
 *
 * In addition to the survivor's name and relationship, this parser now
 * preserves an explicitly stated residence when the obituary gives wording
 * such as:
 *
 *      Joe Gaddy of San Marcos, Texas
 *      James Gaddy of Boston, Massachusetts
 *      Donna Freebourn of Cedar Park, Texas
 *
 * The residence is captured before the existing name-cleanup logic removes
 * location text from the person's name.
 */
public class SurvivorParser {

    private static final Pattern START = Pattern.compile(
            "(?i)\\b(?:(?:is|was|also)\\s+)*survived\\s+by\\b"
                    + "|\\bsurvivors\\s+include\\b"
    );

    private static final Pattern STOP = Pattern.compile(
            "(?i)\\b(?:preceded\\s+in\\s+death|predeceased|funeral|visitation|"
                    + "memorial\\s+service|in\\s+lieu|donations|pallbearers|special\\s+thanks)\\b"
    );

    private static final Pattern RELATION = Pattern.compile(
            "(?i)\\b("
                    + "great[- ]grandchildren|great[- ]grandsons?|great[- ]granddaughters?|"
                    + "grandchildren|grandsons?|granddaughters?|"
                    + "step[- ]?children|step[- ]?sons?|step[- ]?daughters?|children|"
                    + "sons?[- ]in[- ]law|daughters?[- ]in[- ]law|"
                    + "brothers?[- ]in[- ]law|sisters?[- ]in[- ]law|"
                    + "mothers?[- ]in[- ]law|fathers?[- ]in[- ]law|"
                    + "husbands?|wives|wife|spouses?|partners?|"
                    + "granddogs?|dogs?|sons?|daughters?|sisters?|brothers?|siblings?|"
                    + "mothers?|fathers?|parents?|cousins?|aunts?|uncles?|nieces?|nephews?"
                    + ")\\b"
    );

    private static final Pattern NAME = Pattern.compile(
            "[\\p{Lu}][\\p{L}'’.-]*"
                    + "(?:\\s+(?:[\\p{Lu}][\\p{L}'’.-]*|de|del|la|van|von|da)){0,7}"
    );

    private static final Pattern EMBEDDED_PARTNER = Pattern.compile(
            "(?i)^(.+?)"
                    + "\\s*,?\\s*"
                    + "(?:and|with)\\s+"
                    + "(?:(?:his|her|their)\\s+)?"
                    + "(?:(?:loving|beloved|dear)\\s+)?"
                    + "(wife|husband|spouse|partner)"
                    + "\\s*,?\\s+"
                    + "(.+?)"
                    + "(?:\\s*,?\\s+of\\s+.+)?$"
    );

    /*
     * Matches residence phrases such as:
     *
     * of Cedar Park, Texas
     * from Boston, Massachusetts
     * of Hallettsville
     *
     * Group 1 = city
     * Group 2 = state, when present
     */
    private static final Pattern RESIDENCE_LOCATION =
            Pattern.compile(
                    "(?i)\\b(?:of|from)\\s+"
                            + "([\\p{L}][\\p{L} .'-]*?)"
                            + "(?:,\\s*"
                            + "([\\p{L}][\\p{L} .'-]*))?"
                            + "\\s*$"
            );

    public List<String> findSections(
            String text
    ) {

        if (text == null
                || text.isBlank()) {

            return List.of();
        }

        text =
                text.replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        List<String> sections =
                new ArrayList<>();

        Matcher starts =
                START.matcher(text);

        while (starts.find()) {

            String prefix =
                    text.substring(
                            Math.max(
                                    0,
                                    starts.start() - 12
                            ),
                            starts.start()
                    );

            /*
             * Avoid:
             *
             * not survived by...
             * never survived by...
             */
            if (prefix.matches(
                    "(?is).*\\b(?:not|never)\\s*$"
            )) {

                continue;
            }

            int end =
                    text.length();

            for (int i = starts.end();
                 i < text.length();
                 i++) {

                char c =
                        text.charAt(i);

                if (c == '!'
                        || c == '?'
                        || (c == '.'
                        && (i + 1 == text.length()
                        || Character.isWhitespace(
                        text.charAt(i + 1)
                )))) {

                    String before =
                            text.substring(
                                    starts.end(),
                                    i
                            );

                    /*
                     * Don't stop on initials or suffixes.
                     */
                    if (c == '.'
                            && before.matches(
                            "(?s).*(?:"
                                    + "\\b\\p{Lu}"
                                    + "|\\bJr"
                                    + "|\\bSr"
                                    + ")$"
                    )) {

                        continue;
                    }

                    end = i;
                    break;
                }
            }

            Matcher stop =
                    STOP.matcher(text);

            if (stop.find(
                    starts.end()
            )) {

                end =
                        Math.min(
                                end,
                                stop.start()
                        );
            }

            Matcher next =
                    START.matcher(text);

            if (next.find(
                    starts.end()
            )) {

                end =
                        Math.min(
                                end,
                                next.start()
                        );
            }

            if (end > starts.end()) {

                sections.add(
                        text.substring(
                                starts.end(),
                                end
                        ).trim()
                );
            }
        }

        return sections;
    }

    public List<Survivor> parse(
            String text
    ) {

        return parseSections(
                findSections(text)
        );
    }

    public List<Survivor> parseSections(
            List<String> sections
    ) {

        LinkedHashSet<Survivor> result =
                new LinkedHashSet<>();

        for (String section : sections) {

            Matcher relations =
                    RELATION.matcher(
                            section
                    );

            List<int[]> bounds =
                    new ArrayList<>();

            List<String> labels =
                    new ArrayList<>();

            while (relations.find()) {

                String before =
                        section.substring(
                                0,
                                relations.start()
                        );

                /*
                 * Do not treat relationship words
                 * inside parentheses as new groups.
                 */
                if (before.lastIndexOf('(')
                        > before.lastIndexOf(')')) {

                    continue;
                }

                /*
                 * Susan Kelly and husband Terry
                 *
                 * "husband" belongs to Susan rather
                 * than starting a new relationship list.
                 */
                if (isEmbeddedPartnerRelation(
                        section,
                        relations.start(),
                        relations.group()
                )) {

                    continue;
                }

                bounds.add(
                        new int[]{
                                relations.start(),
                                relations.end()
                        }
                );

                labels.add(
                        normalize(
                                relations.group()
                        )
                );
            }

            for (int i = 0;
                 i < bounds.size();
                 i++) {

                int end =
                        i + 1 < bounds.size()
                                ? bounds
                                .get(i + 1)[0]
                                : section.length();

                String names =
                        section.substring(
                                bounds.get(i)[1],
                                end
                        );

                String relationship =
                        labels.get(i);

                /*
                 * Pets may appear in survivor prose,
                 * but they are not people.
                 */
                if (relationship.equals("dog")
                        || relationship.equals(
                        "granddog"
                )) {

                    continue;
                }

                addNames(
                        names,
                        relationship,
                        result
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    private void addNames(
            String text,
            String relationship,
            LinkedHashSet<Survivor> result
    ) {

        for (String entry :
                splitEntries(text)) {

            String cleaned =
                    entry.trim()
                            .replaceFirst(
                                    "(?i)^(?:and\\s+)?"
                                            + "[:,-]?\\s*",
                                    ""
                            );

            if (cleaned.isBlank()) {
                continue;
            }

            /*
             * Capture the residence BEFORE cleanName(...)
             * removes the location tail.
             *
             * Example:
             *
             * Stuart Gaddy and wife Teresa
             * of Cross Plains, Texas
             */
            Residence entryResidence =
                    extractResidence(
                            cleaned
                    );

            /*
             * Examples:
             *
             * Colby Collins and his wife, Samantha
             *
             * Susan Kelly and husband Terry
             *
             * Valarie Martinez and her loving husband Lazarro
             *
             * Stuart Gaddy and wife Teresa
             * of Cross Plains, Texas
             */
            Matcher partnerMatch =
                    EMBEDDED_PARTNER.matcher(
                            cleaned
                    );

            if (partnerMatch.matches()) {

                String primaryText =
                        partnerMatch.group(1);

                String partnerRelationship =
                        partnerMatch
                                .group(2)
                                .toLowerCase(
                                        Locale.ROOT
                                );

                String partnerName =
                        cleanName(
                                partnerMatch
                                        .group(3)
                        );

                String lastPrimaryName =
                        null;

                for (String primaryToken :
                        splitTopLevelAnd(
                                primaryText
                        )) {

                    String primaryName =
                            cleanName(
                                    primaryToken
                            );

                    if (primaryName == null) {
                        continue;
                    }

                    /*
                     * The location belongs to the couple.
                     */
                    result.add(
                            new Survivor(
                                    primaryName,
                                    relationship,
                                    entryResidence.city(),
                                    entryResidence.state()
                            )
                    );

                    lastPrimaryName =
                            primaryName;
                }

                if (partnerName != null
                        && lastPrimaryName != null) {

                    /*
                     * Give the spouse/partner the same
                     * explicitly stated residence.
                     */
                    result.add(
                            new Survivor(
                                    partnerName,
                                    partnerRelationship
                                            + " of "
                                            + lastPrimaryName,
                                    entryResidence.city(),
                                    entryResidence.state()
                            )
                    );
                }

                continue;
            }

            /*
             * Ordinary list:
             *
             * John, Mary and Susan
             */
            for (String token :
                    splitTopLevelAnd(
                            cleaned
                    )) {

                addName(
                        token,
                        relationship,
                        result
                );
            }
        }
    }

    private List<String> splitEntries(
            String text
    ) {

        List<String> tokens =
                new ArrayList<>();

        int depth = 0;
        int start = 0;

        for (int i = 0;
             i < text.length();
             i++) {

            char c =
                    text.charAt(i);

            if (c == '(') {

                depth++;

            } else if (c == ')') {

                depth--;
            }

            /*
             * Semicolon is a strong separator.
             */
            if (depth == 0
                    && c == ';') {

                tokens.add(
                        text.substring(
                                start,
                                i
                        )
                );

                start =
                        i + 1;

                continue;
            }

            /*
             * A comma usually separates names,
             * unless it belongs to a spouse,
             * suffix, or location phrase.
             */
            if (depth == 0
                    && c == ',') {

                if (keepCommaInsideEntry(
                        text,
                        start,
                        i
                )) {

                    continue;
                }

                tokens.add(
                        text.substring(
                                start,
                                i
                        )
                );

                start =
                        i + 1;
            }
        }

        tokens.add(
                text.substring(start)
        );

        return tokens;
    }

    private boolean keepCommaInsideEntry(
            String text,
            int entryStart,
            int commaIndex
    ) {

        String before =
                text.substring(
                        entryStart,
                        commaIndex
                ).trim();

        String after =
                text.substring(
                        commaIndex + 1
                ).trim();

        /*
         * wife, Sarah
         * husband, John
         */
        if (before.matches(
                "(?is).*"
                        + "(?:wife|husband|spouse|partner)$"
        )) {

            return true;
        }

        /*
         * Samantha, of Riverton
         */
        if (after.matches(
                "(?is)^of\\b.*"
        )) {

            return true;
        }

        /*
         * Edward Copley, Jr.
         */
        if (after.matches(
                "(?is)^(?:"
                        + "Jr\\.?|Sr\\.?|II|III|IV"
                        + ")\\b.*"
        )) {

            return true;
        }

        /*
         * Texas, and her husband...
         */
        if (after.matches(
                "(?is)^and\\s+"
                        + "(?:(?:his|her|their)\\s+)?"
                        + "(?:(?:loving|beloved|dear)\\s+)?"
                        + "(?:wife|husband|spouse|partner)"
                        + "\\b.*"
        )) {

            return true;
        }

        /*
         * Keep:
         *
         * Austin, TX
         * Salado, Texas
         * Boston, Massachusetts
         *
         * together long enough for extractResidence(...)
         * and location cleanup to use it.
         */
        String nextPiece =
                after.split(
                        "[,;]",
                        2
                )[0].trim();

        return !nextPiece.isEmpty()
                && !SurvivorValidator.isValid(
                nextPiece
        );
    }

    private List<String> splitTopLevelAnd(
            String text
    ) {

        List<String> tokens =
                new ArrayList<>();

        int depth = 0;
        int start = 0;

        for (int i = 0;
             i < text.length();
             i++) {

            char c =
                    text.charAt(i);

            if (c == '(') {

                depth++;

            } else if (c == ')') {

                depth--;
            }

            if (depth == 0
                    && text.startsWith(
                    " and ",
                    i
            )) {

                tokens.add(
                        text.substring(
                                start,
                                i
                        )
                );

                i += 4;

                start =
                        i + 1;

            } else if (depth == 0
                    && c == '&') {

                tokens.add(
                        text.substring(
                                start,
                                i
                        )
                );

                start =
                        i + 1;
            }
        }

        tokens.add(
                text.substring(start)
        );

        return tokens;
    }

    private boolean isEmbeddedPartnerRelation(
            String section,
            int relationStart,
            String relation
    ) {

        String normalized =
                relation.toLowerCase(
                        Locale.ROOT
                );

        /*
         * Plural "spouses" intentionally does
         * NOT count as an embedded partner.
         */
        if (!normalized.matches(
                "husband|wife|spouse|partner"
        )) {

            return false;
        }

        String before =
                section.substring(
                        Math.max(
                                0,
                                relationStart - 40
                        ),
                        relationStart
                );

        return before.matches(
                "(?is).*\\b(?:and|with)"
                        + "\\s+"
                        + "(?:(?:his|her|their)"
                        + "\\s+)?"
                        + "(?:(?:loving|beloved|dear)"
                        + "\\s+)?$"
        );
    }

    private String cleanName(
            String raw
    ) {

        raw =
                basicNameCleanup(
                        raw
                );

        int open =
                raw.indexOf('(');

        int close =
                raw.indexOf(
                        ')',
                        open + 1
                );

        /*
         * A parenthetical in the middle of a
         * larger full name is normally a
         * nickname or maiden name.
         *
         * Katherine (Kathy) Patman Johnson
         *
         * Martha (Jenkins) Walker
         */
        if (open >= 0
                && close > open) {

            String before =
                    raw.substring(
                            0,
                            open
                    ).trim();

            String after =
                    raw.substring(
                            close + 1
                    ).trim();

            if (!before.isBlank()
                    && !after.isBlank()) {

                raw =
                        (before
                                + " "
                                + after)
                                .trim();
            }
        }

        raw =
                removeLocationTail(
                        raw
                );

        return isName(raw)
                && SurvivorValidator.isValid(
                raw
        )
                ? raw
                : null;
    }

    private void addName(
            String raw,
            String relationship,
            LinkedHashSet<Survivor> result
    ) {

        /*
         * VERY IMPORTANT:
         *
         * Capture location BEFORE basicNameCleanup(...)
         * and removeLocationTail(...) change the string.
         *
         * Example:
         *
         * Joe Gaddy of San Marcos, Texas
         *
         * becomes:
         *
         * name  = Joe Gaddy
         * city  = San Marcos
         * state = Texas
         */
        Residence residence =
                extractResidence(
                        raw
                );

        raw =
                basicNameCleanup(
                        raw
                );

        int open =
                raw.indexOf('(');

        int close =
                raw.indexOf(
                        ')',
                        open + 1
                );

        String partner =
                null;

        if (open >= 0
                && close > open) {

            String inside =
                    raw.substring(
                            open + 1,
                            close
                    ).trim();

            String before =
                    raw.substring(
                            0,
                            open
                    ).trim();

            String after =
                    raw.substring(
                            close + 1
                    ).trim();

            boolean explicitPartner =
                    inside.matches(
                            "(?i)^"
                                    + "(?:(?:his|her)\\s+)?"
                                    + "(?:wife|husband|spouse|partner)"
                                    + "\\s+.+$"
                    );

            boolean followedByLocation =
                    after.matches(
                            "(?i)^of\\b.*"
                    );

            boolean deceasedPartner =
                    inside.matches(
                            "(?i)^.+,"
                                    + "\\s*"
                                    + "(?:dec\\.?|deceased)$"
                    );

            boolean likelyNickname =
                    after.isBlank()
                            && looksLikeNickname(
                            before,
                            inside
                    );

            /*
             * Examples:
             *
             * Martha (Jenkins) Walker
             *   -> maiden-name information
             *
             * Anderson (Andy)
             *   -> nickname
             *
             * Noelia Monk (Neiland) of Hallettsville
             *   -> possible spouse
             *
             * Elizabeth (Ronald, dec.) Sutton
             *   -> possible spouse
             */
            if (!explicitPartner
                    && !followedByLocation
                    && !deceasedPartner
                    && (!after.isBlank()
                    || likelyNickname)) {

                raw =
                        (before
                                + " "
                                + after)
                                .trim();

            } else {

                partner =
                        inside
                                .replaceFirst(
                                        "(?i),"
                                                + "\\s*"
                                                + "(?:dec\\.?|deceased)$",
                                        ""
                                )
                                .trim();

                raw =
                        (before
                                + " "
                                + after)
                                .trim();
            }
        }

        /*
         * At this point we've already saved the
         * residence into the Residence object,
         * so it is now safe to remove the location
         * from the person's name.
         */
        raw =
                removeLocationTail(
                        raw
                );

        if (!isName(raw)
                || !SurvivorValidator.isValid(
                raw
        )) {

            return;
        }

        result.add(
                new Survivor(
                        raw,
                        relationship,
                        residence.city(),
                        residence.state()
                )
        );

        /*
         * Handle:
         *
         * Nancy Herrington (James)
         *
         * John Smith (wife Mary)
         */
        if (partner != null) {

            Matcher explicit =
                    Pattern.compile(
                            "(?i)^"
                                    + "(?:(?:his|her)\\s+)?"
                                    + "(wife|husband|spouse|partner)"
                                    + "\\s+(.+)$"
                    ).matcher(
                            partner
                    );

            String label =
                    "possible spouse of "
                            + raw;

            if (explicit.matches()) {

                label =
                        explicit.group(1)
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                + " of "
                                + raw;

                partner =
                        explicit.group(2);
            }

            partner =
                    cleanName(
                            partner
                    );

            if (partner != null) {

                /*
                 * A parenthetical spouse attached to
                 * a survivor with an explicit residence
                 * is given that same residence.
                 */
                result.add(
                        new Survivor(
                                partner,
                                label,
                                residence.city(),
                                residence.state()
                        )
                );
            }
        }
    }

    private boolean looksLikeNickname(
            String before,
            String inside
    ) {

        String first =
                before
                        .replaceAll(
                                "[^\\p{L}]",
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        );

        String second =
                inside
                        .replaceAll(
                                "[^\\p{L}]",
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (first.length() < 3
                || second.length() < 3) {

            return false;
        }

        return first
                .substring(
                        0,
                        3
                )
                .equals(
                        second.substring(
                                0,
                                3
                        )
                );
    }

    private String basicNameCleanup(
            String raw
    ) {

        raw =
                raw.trim()
                        .replaceFirst(
                                "(?i)^"
                                        + "(?:and\\s+)?"
                                        + "[:,-]?\\s*",
                                ""
                        );

        /*
         * Michael "Mike" Lucero
         *
         * becomes:
         *
         * Michael Lucero
         */
        raw =
                raw.replaceAll(
                        "\\s+\"[^\"]+\"\\s+",
                        " "
                );

        /*
         * Avoid treating:
         *
         * "of 57 years"
         *
         * as part of a person's name.
         */
        raw =
                raw.replaceFirst(
                        "(?i)^of\\s+\\d+"
                                + "\\s+years"
                                + "\\s*,?\\s*",
                        ""
                );

        /*
         * Edward Copley, Jr.
         *
         * becomes:
         *
         * Edward Copley Jr.
         */
        raw =
                raw.replaceAll(
                        "(?i),\\s*"
                                + "(Jr\\.?|Sr\\.?|II|III|IV)"
                                + "\\b",
                        " $1"
                );

        raw =
                raw.replaceAll(
                                "[\\s,;:.]+$",
                                ""
                        )
                        .trim();

        raw =
                raw.replaceFirst(
                                "(?i)\\s+"
                                        + "(?:and|or)$",
                                ""
                        )
                        .trim();

        return raw;
    }

    private String removeLocationTail(
            String raw
    ) {

        raw =
                raw.replaceFirst(
                        "(?i)\\s+"
                                + "(?:"
                                + "of|"
                                + "from|"
                                + "and\\s+(?:his|her|their)|"
                                + "his|"
                                + "her|"
                                + "their"
                                + ")"
                                + "\\b.*$",
                        ""
                );

        return raw
                .replaceAll(
                        "[\\s,;:.]+$",
                        ""
                )
                .trim();
    }

    private boolean isName(
            String text
    ) {

        return text != null
                && NAME.matcher(
                text
        ).matches()
                && !text.matches(
                "(?i)(?:"
                        + "Jr|Sr|II|III|IV|"
                        + "The|Many|Several"
                        + ")"
        );
    }

    /**
     * Residence explicitly attached to a survivor in the obituary.
     *
     * This is not assumed or inferred.
     */
    private record Residence(
            String city,
            String state
    ) {
    }

    /**
     * Extracts an explicitly stated residence from the end
     * of a survivor phrase.
     *
     * Examples:
     *
     * Joe Gaddy of San Marcos, Texas
     *      city  = San Marcos
     *      state = Texas
     *
     * Barbara Rossman of Knox
     *      city  = Knox
     *      state = null
     */
    private Residence extractResidence(
            String text
    ) {

        if (text == null
                || text.isBlank()) {

            return new Residence(
                    null,
                    null
            );
        }

        String cleaned =
                text.trim()
                        .replaceAll(
                                "[;:.]+$",
                                ""
                        );
        cleaned =
                cleaned.replaceFirst(
                        "(?i),\\s*(?:"
                                + "Dr\\.?|"
                                + "Mr\\.?|"
                                + "Mrs\\.?|"
                                + "Ms\\.?|"
                                + "Rev\\.?|"
                                + "Fr\\.?"
                                + ")\\s*$",
                        ""
                );

        Matcher matcher =
                RESIDENCE_LOCATION.matcher(
                        cleaned
                );

        if (!matcher.find()) {

            return new Residence(
                    null,
                    null
            );
        }

        String city =
                cleanLocationPart(
                        matcher.group(1)
                );

        String state =
                cleanLocationPart(
                        matcher.group(2)
                );

        return new Residence(
                city,
                state
        );
    }

    private String cleanLocationPart(
            String value
    ) {

        if (value == null) {
            return null;
        }

        value =
                value.replaceAll(
                                "\\s+",
                                " "
                        )
                        .replaceAll(
                                "^[\\s,]+|[\\s,]+$",
                                ""
                        )
                        .trim();

        return value.isBlank()
                ? null
                : value;
    }

    private String normalize(
            String value
    ) {

        value =
                value.toLowerCase(
                        Locale.ROOT
                )
                        .replace(
                                ' ',
                                '-'
                        );

        return switch (value) {

            case "children" ->
                    "child";

            case "grandchildren" ->
                    "grandchild";

            case "great-grandchildren" ->
                    "great-grandchild";

            case "stepchildren",
                 "step-children" ->
                    "stepchild";

            case "wives" ->
                    "wife";

            default ->
                    value
                            .replace(
                                    "sons-in-law",
                                    "son-in-law"
                            )
                            .replace(
                                    "daughters-in-law",
                                    "daughter-in-law"
                            )
                            .replace(
                                    "brothers-in-law",
                                    "brother-in-law"
                            )
                            .replace(
                                    "sisters-in-law",
                                    "sister-in-law"
                            )
                            .replaceFirst(
                                    "s$",
                                    ""
                            );
        };
    }
}