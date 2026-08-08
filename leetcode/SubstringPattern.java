/**
 * Substring search where the pattern may contain a single "*" wildcard standing for any run of
 * characters, including none.
 */
class SubstringPattern {

    static boolean checkSubstringWithPattern(String text, String pattern) {
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("text and pattern must not be null.");
        }

        int star = pattern.indexOf('*');
        if (star < 0) {
            return text.contains(pattern);
        }
        if (pattern.indexOf('*', star + 1) >= 0) {
            throw new IllegalArgumentException("Pattern may contain at most one '*'.");
        }

        String prefix = pattern.substring(0, star);
        String suffix = pattern.substring(star + 1);

        int prefixAt = text.indexOf(prefix);
        if (prefixAt < 0) {
            return false;
        }
        // The wildcard swallows anything between the two parts, so the suffix only has to appear
        // somewhere after the prefix ends.
        return text.indexOf(suffix, prefixAt + prefix.length()) >= 0;
    }

    public static void main(String[] args) {
        String text = "Today is sunday";
        String[][] cases = {
            {"is", "true"},
            {"i*day", "true"},
            {"mon*day", "false"},
            {"", "true"},
            {"*", "true"},
            {"*day", "true"},
            {"Today*", "true"},
            {"sun*day", "true"},
            {"day*is", "true"},
            {"y*T", "false"},
            {"Sunday", "false"},
        };

        boolean allPassed = true;
        for (String[] testCase : cases) {
            String pattern = testCase[0];
            boolean expected = Boolean.parseBoolean(testCase[1]);
            boolean actual = checkSubstringWithPattern(text, pattern);
            allPassed &= actual == expected;
            System.out.printf("%-10s -> %-5s %s%n", "\"" + pattern + "\"", actual,
                    actual == expected ? "ok" : "FAILED, expected " + expected);
        }

        try {
            checkSubstringWithPattern(text, "a*b*c");
            allPassed = false;
            System.out.println("\"a*b*c\"   -> no exception, FAILED");
        } catch (IllegalArgumentException exception) {
            System.out.printf("%-10s -> rejected: %s%n", "\"a*b*c\"", exception.getMessage());
        }

        System.out.println(allPassed ? "all cases passed" : "some cases failed");
    }
}
