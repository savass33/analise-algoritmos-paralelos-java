import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public final class MergeSortAlgorithm implements SortingAlgorithm {
    private static final int SEQUENTIAL_THRESHOLD = 64;
    private static final int PARALLEL_THRESHOLD = 4_096;

    @Override public String name() { return "Merge Sort"; }

    @Override public void sortSequential(int[] values) {
        int[] aux = new int[values.length];
        mergeSort(values, aux, 0, values.length);
    }

    @Override public void sortParallel(int[] values, int threads) {
        int[] aux = new int[values.length];
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.invoke(new MergeTask(values, aux, 0, values.length));
        } finally {
            pool.shutdown();
        }
    }

    private static void mergeSort(int[] values, int[] aux, int from, int to) {
        if (to - from <= SEQUENTIAL_THRESHOLD) {
            Arrays.sort(values, from, to);
            return;
        }
        int mid = (from + to) >>> 1;
        mergeSort(values, aux, from, mid);
        mergeSort(values, aux, mid, to);
        merge(values, aux, from, mid, to);
    }

    private static void merge(int[] values, int[] aux, int from, int mid, int to) {
        if (mid >= to || values[mid - 1] <= values[mid]) return;
        System.arraycopy(values, from, aux, from, to - from);
        int i = from, j = mid, k = from;
        while (i < mid && j < to) {
            values[k++] = aux[i] <= aux[j] ? aux[i++] : aux[j++];
        }
        while (i < mid) values[k++] = aux[i++];
        while (j < to) values[k++] = aux[j++];
    }

    private static final class MergeTask extends RecursiveAction {
        private final int[] values;
        private final int[] aux;
        private final int from;
        private final int to;

        private MergeTask(int[] values, int[] aux, int from, int to) {
            this.values = values; this.aux = aux; this.from = from; this.to = to;
        }

        @Override protected void compute() {
            if (to - from <= PARALLEL_THRESHOLD) {
                mergeSort(values, aux, from, to);
                return;
            }
            int mid = (from + to) >>> 1;
            invokeAll(new MergeTask(values, aux, from, mid),
                      new MergeTask(values, aux, mid, to));
            merge(values, aux, from, mid, to);
        }
    }
}
