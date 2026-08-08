import java.util.ArrayList;
import java.util.List;

/** Letter combinations of a phone number (LeetCode 17). */
class Solution {

    private static final String[] LETTERS = {
        "", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"
    };

    public List<String> letterCombinations(String digits) {
        List<String> combinations = new ArrayList<>();
        if (digits == null || digits.isEmpty()) {
            return combinations;
        }
        build(digits, 0, new StringBuilder(digits.length()), combinations);
        return combinations;
    }

    private void build(String digits, int index, StringBuilder current, List<String> combinations) {
        if (index == digits.length()) {
            combinations.add(current.toString());
            return;
        }
        String letters = LETTERS[digits.charAt(index) - '0'];
        for (int i = 0; i < letters.length(); i++) {
            current.append(letters.charAt(i));
            build(digits, index + 1, current, combinations);
            current.deleteCharAt(current.length() - 1);
        }
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        String[] inputs = args.length > 0 ? args : new String[] {"23", "7", "234", ""};
        for (String digits : inputs) {
            List<String> combinations = solution.letterCombinations(digits);
            System.out.printf("\"%s\" -> %d combination(s): %s%n",
                    digits, combinations.size(), combinations);
        }
    }
}
