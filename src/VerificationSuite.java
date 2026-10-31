import java.util.Arrays;
import java.util.List;

public final class VerificationSuite {
    private VerificationSuite() {}

    public static void main(String[] args) {
        List<SortingAlgorithm> algorithms = List.of(
                new QuickSortAlgorithm(), new MergeSortAlgorithm(),
                new CountingSortAlgorithm(), new InsertionSortAlgorithm());
        int[] sizes = {0, 1, 2, 31, 128, 1_001};
        int checks = 0;
        for (SortingAlgorithm algorithm : algorithms) {
            for (int size : sizes) {
                for (DataFactory.Pattern pattern : DataFactory.Pattern.values()) {
                    int[] source = DataFactory.create(size, pattern, 42L + size + pattern.ordinal());
                    int[] expected = source.clone(); Arrays.sort(expected);
                    int[] sequential = source.clone(); algorithm.sortSequential(sequential);
                    require(Arrays.equals(expected, sequential), algorithm.name() + " serial");
                    for (int threads : new int[]{2, 4}) {
                        int[] parallel = source.clone(); algorithm.sortParallel(parallel, threads);
                        require(Arrays.equals(expected, parallel), algorithm.name() + " paralelo " + threads);
                    }
                    checks += 3;
                }
            }
        }
        System.out.println("Verificacao concluida: " + checks + " casos validos.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Falha: " + message);
    }
}
