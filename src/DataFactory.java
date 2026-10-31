import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

public final class DataFactory {
    private DataFactory() {}

    public enum Pattern {
        RANDOM, SORTED, REVERSE, FEW_UNIQUE;

        public static Pattern parse(String value) {
            return Pattern.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public static int[] create(int size, Pattern pattern, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int[] values = new int[size];
        int range = Math.max(128, Math.min(100_000, size * 2));
        switch (pattern) {
            case RANDOM -> {
                for (int i = 0; i < size; i++) values[i] = random.nextInt(range);
            }
            case FEW_UNIQUE -> {
                for (int i = 0; i < size; i++) values[i] = random.nextInt(32);
            }
            case SORTED -> {
                for (int i = 0; i < size; i++) values[i] = i;
            }
            case REVERSE -> {
                for (int i = 0; i < size; i++) values[i] = size - i;
            }
        }
        return values;
    }

    public static boolean isSorted(int[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] > values[i]) return false;
        }
        return true;
    }

    public static boolean sameElements(int[] original, int[] sorted) {
        int[] expected = original.clone();
        Arrays.sort(expected);
        return Arrays.equals(expected, sorted);
    }
}
