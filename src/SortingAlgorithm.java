public interface SortingAlgorithm {
    String name();
    void sortSequential(int[] values);
    void sortParallel(int[] values, int threads);
}
