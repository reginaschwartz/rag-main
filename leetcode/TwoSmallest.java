import java.util.Arrays;

/** Finds the two smallest values in an array in a single pass. */
class TwoSmallest {

    /** Returns {smallest, secondSmallest} by position, so duplicates can appear twice. */
    static int[] twoSmallest(int[] numbers) {
        if (numbers == null || numbers.length < 2) {
            throw new IllegalArgumentException("Need at least two numbers.");
        }
        int smallest = Math.min(numbers[0], numbers[1]);
        int second = Math.max(numbers[0], numbers[1]);

        for (int i = 2; i < numbers.length; i++) {
            int value = numbers[i];
            if (value < smallest) {
                second = smallest;
                smallest = value;
            } else if (value < second) {
                second = value;
            }
        }
        return new int[] {smallest, second};
    }

    /** Same idea, but the second value must differ from the smallest. */
    static int[] twoSmallestDistinct(int[] numbers) {
        if (numbers == null || numbers.length < 2) {
            throw new IllegalArgumentException("Need at least two numbers.");
        }
        long smallest = Long.MAX_VALUE;
        long second = Long.MAX_VALUE;

        for (int value : numbers) {
            if (value < smallest) {
                second = smallest;
                smallest = value;
            } else if (value > smallest && value < second) {
                second = value;
            }
        }
        if (second == Long.MAX_VALUE) {
            throw new IllegalArgumentException("All values are equal, no distinct second smallest.");
        }
        return new int[] {(int) smallest, (int) second};
    }

    public static void main(String[] args) {
        int[][] samples = {
            {7, 3, 9, 1, 4},
            {5, 2},
            {1, 1, 2},
            {-3, -3, -7, 0},
            {Integer.MIN_VALUE, Integer.MAX_VALUE, 0},
        };

        for (int[] sample : samples) {
            System.out.printf("%-40s -> two smallest %s, distinct %s%n",
                    Arrays.toString(sample),
                    Arrays.toString(twoSmallest(sample)),
                    describeDistinct(sample));
        }
    }

    private static String describeDistinct(int[] numbers) {
        try {
            return Arrays.toString(twoSmallestDistinct(numbers));
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }
}
