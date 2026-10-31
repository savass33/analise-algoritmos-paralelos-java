import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public final class QuickSortAlgorithm implements SortingAlgorithm {
    private static final int INSERTION_THRESHOLD = 32;
    private static final int PARALLEL_THRESHOLD = 2_048;

    @Override public String name() { return "Quick Sort"; }

    @Override public void sortSequential(int[] values) {
        quickSort(values, 0, values.length - 1);
    }

    @Override public void sortParallel(int[] values, int threads) {
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.invoke(new QuickTask(values, 0, values.length - 1));
        } finally {
            pool.shutdown();
        }
    }

    private static void quickSort(int[] a, int left, int right) {
        while (right - left > INSERTION_THRESHOLD) {
            int pivot = partition(a, left, right);
            if (pivot - left < right - pivot) {
                quickSort(a, left, pivot - 1);
                left = pivot + 1;
            } else {
                quickSort(a, pivot + 1, right);
                right = pivot - 1;
            }
        }
        if (left < right) insertionRange(a, left, right);
    }

    private static int partition(int[] a, int left, int right) {
        int mid = (left + right) >>> 1;
        if (a[left] > a[mid]) swap(a, left, mid);
        if (a[left] > a[right]) swap(a, left, right);
        if (a[mid] > a[right]) swap(a, mid, right);
        swap(a, mid, right - 1);
        int pivot = a[right - 1];
        int i = left;
        int j = right - 1;
        while (true) {
            while (a[++i] < pivot) {}
            while (a[--j] > pivot) {}
            if (i >= j) break;
            swap(a, i, j);
        }
        swap(a, i, right - 1);
        return i;
    }

    private static void insertionRange(int[] a, int left, int right) {
        for (int i = left + 1; i <= right; i++) {
            int value = a[i];
            int j = i - 1;
            while (j >= left && a[j] > value) {
                a[j + 1] = a[j--];
            }
            a[j + 1] = value;
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private static final class QuickTask extends RecursiveAction {
        private final int[] values;
        private final int left;
        private final int right;

        private QuickTask(int[] values, int left, int right) {
            this.values = values; this.left = left; this.right = right;
        }

        @Override protected void compute() {
            if (right <= left) return;
            if (right - left < PARALLEL_THRESHOLD) {
                quickSort(values, left, right);
                return;
            }
            int pivot;
            try {
                pivot = partition(values, left, right);
            } catch (ArrayIndexOutOfBoundsException ex) {
                Arrays.sort(values, left, right + 1);
                return;
            }
            invokeAll(new QuickTask(values, left, pivot - 1),
                      new QuickTask(values, pivot + 1, right));
        }
    }
}
