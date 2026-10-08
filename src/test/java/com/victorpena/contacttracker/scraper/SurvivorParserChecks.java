package com.victorpena.contacttracker.scraper;

import java.util.List;

/**
 * Run without Spring, database, or network requests.
 */
public class SurvivorParserChecks {

    public static void main(
            String[] args
    ) {

        SurvivorParser parser =
                new SurvivorParser();

        /*
         * Test 1:
         * normal husband / children /
         * grandchildren with parenthetical spouses.
         */
        List<Survivor> actual =
                parser.parse(
                        "She is survived by her beloved husband, Robert Lee Dentino; "
                                + "her children, Robert Douglas Dentino, "
                                + "Nancy Kay Herrington (James), "
                                + "and David Haynes Dentino (Karin); "
                                + "and her grandchildren, Kathryn Whaley (Ethan), "
                                + "Jennifer Herrington, Hunter Dentino. "
                                + "Funeral services will be held Monday."
                );

        equal(
                List.of(
                        new Survivor(
                                "Robert Lee Dentino",
                                "husband"
                        ),
                        new Survivor(
                                "Robert Douglas Dentino",
                                "child"
                        ),
                        new Survivor(
                                "Nancy Kay Herrington",
                                "child"
                        ),
                        new Survivor(
                                "James",
                                "possible spouse of Nancy Kay Herrington"
                        ),
                        new Survivor(
                                "David Haynes Dentino",
                                "child"
                        ),
                        new Survivor(
                                "Karin",
                                "possible spouse of David Haynes Dentino"
                        ),
                        new Survivor(
                                "Kathryn Whaley",
                                "grandchild"
                        ),
                        new Survivor(
                                "Ethan",
                                "possible spouse of Kathryn Whaley"
                        ),
                        new Survivor(
                                "Jennifer Herrington",
                                "grandchild"
                        ),
                        new Survivor(
                                "Hunter Dentino",
                                "grandchild"
                        )
                ),
                actual
        );

        /*
         * Test 2:
         * no explicit survivor section.
         */
        equal(
                List.of(),
                parser.parse(
                        "He leaves behind a wonderful legacy and many memories."
                )
        );

        /*
         * Test 3:
         * negative wording should not count.
         */
        equal(
                List.of(),
                parser.parse(
                        "He is not survived by his wife Jane Smith."
                )
        );

        /*
         * Test 4:
         * stop after the valid survivor sentence.
         */
        equal(
                List.of(
                        new Survivor(
                                "Jane Smith",
                                "wife"
                        )
                ),
                parser.parse(
                        "His son Mark died before him. "
                                + "He is survived by his wife Jane Smith. "
                                + "His brother Fred preceded him in death."
                )
        );

        /*
         * Test 5:
         * explicit spouse inside parentheses.
         */
        equal(
                List.of(
                        new Survivor(
                                "John Smith",
                                "son"
                        ),
                        new Survivor(
                                "Mary",
                                "wife of John Smith"
                        )
                ),
                parser.parse(
                        "Survivors include his son "
                                + "John Smith (wife Mary)."
                )
        );

        /*
         * Test 6:
         * accented characters and apostrophes.
         */
        equal(
                List.of(
                        new Survivor(
                                "José Peña",
                                "brother"
                        ),
                        new Survivor(
                                "Ann-Marie O’Neil",
                                "sister"
                        )
                ),
                parser.parse(
                        "Also survived by his brother José Peña; "
                                + "his sister Ann-Marie O’Neil."
                )
        );

        /*
         * Test 7:
         * remove location tails.
         */
        equal(
                List.of(
                        new Survivor(
                                "John A. Smith",
                                "son"
                        ),
                        new Survivor(
                                "Mary Jones",
                                "daughter"
                        )
                ),
                parser.parse(
                        "Survived by son John A. Smith of Austin; "
                                + "daughter Mary Jones of Dallas."
                )
        );

        /*
         * Test 8:
         * relationship words with no names.
         */
        equal(
                List.of(),
                parser.parse(
                        "Survivors include three children "
                                + "and many grandchildren."
                )
        );

        /*
         * Test 9:
         * multiple survivor sentences.
         */
        equal(
                List.of(
                        new Survivor(
                                "Jane Smith",
                                "wife"
                        ),
                        new Survivor(
                                "John Smith",
                                "stepchild"
                        )
                ),
                parser.parse(
                        "He is survived by wife Jane Smith. "
                                + "He is also survived by "
                                + "stepchildren John Smith."
                )
        );

        /*
         * Test 10:
         * preceded-in-death text stops parsing.
         */
        equal(
                List.of(
                        new Survivor(
                                "Jane Smith",
                                "wife"
                        )
                ),
                parser.parse(
                        "Survived by wife Jane Smith; "
                                + "preceded in death by son Mark Smith."
                )
        );

        /*
         * Test 11:
         * null safety.
         */
        equal(
                List.of(),
                parser.parse(null)
        );

        /*
         * Test 12:
         * embedded husband/wife phrases.
         */
        equal(
                List.of(
                        new Survivor(
                                "Colby Collins",
                                "son"
                        ),
                        new Survivor(
                                "Samantha",
                                "wife of Colby Collins"
                        ),
                        new Survivor(
                                "Kyle Heinrich",
                                "son"
                        ),
                        new Survivor(
                                "Sarah",
                                "wife of Kyle Heinrich"
                        ),
                        new Survivor(
                                "Brynn Luckey",
                                "daughter"
                        ),
                        new Survivor(
                                "Jeremy",
                                "husband of Brynn Luckey"
                        )
                ),
                parser.parse(
                        "He is survived by sons "
                                + "Colby Collins and wife Samantha of Texas, "
                                + "Kyle Heinrich and wife Sarah of Wyoming; "
                                + "daughter Brynn Luckey "
                                + "and husband Jeremy of Texas."
                )
        );

        /*
         * Test 13:
         * parenthetical spouse followed by a location.
         */
        equal(
                List.of(
                        new Survivor(
                                "Noelia Monk",
                                "sister"
                        ),
                        new Survivor(
                                "Neiland",
                                "possible spouse of Noelia Monk"
                        ),
                        new Survivor(
                                "Elva Vela",
                                "sister"
                        ),
                        new Survivor(
                                "Ovidio",
                                "possible spouse of Elva Vela"
                        )
                ),
                parser.parse(
                        "She is survived by sisters "
                                + "Noelia Monk (Neiland) of Hallettsville, Tx, "
                                + "Elva Vela (Ovidio) of Sweeny, Tx."
                )
        );

        System.out.println(
                "All 13 survivor-parser checks passed."
        );
    }

    private static void equal(
            Object expected,
            Object actual
    ) {

        if (!expected.equals(actual)) {

            throw new AssertionError(
                    "Expected: "
                            + expected
                            + "\nActual: "
                            + actual
            );
        }
    }
}