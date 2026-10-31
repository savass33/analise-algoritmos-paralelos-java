import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class InsertionSortAlgorithm implements SortingAlgorithm {
    @Override public String name() { return "Insertion Sort"; }

    @Override public void sortSequential(int[] values) {
        insertion(values, 0, values.length);
    }

    @Override public void sortParallel(int[] values, int threads) {
        if (values.length < 2) return;
        int chunks = Math.min(threads, values.length);
        ExecutorService executor = Executors.newFixedThreadPool(chunks);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int chunk = 0; chunk < chunks; chunk++) {
                int from = values.length * chunk / chunks;
                int to = values.length * (chunk + 1) / chunks;
                futures.add(executor.submit(() -> insertion(values, from, to)));
            }
            for (Future<?> future : futures) future.get();

            int[] aux = new int[values.length];
            for (int width = 1; width < chunks; width *= 2) {
                for (int chunk = 0; chunk < chunks; chunk += width * 2) {
                    int from = values.length * chunk / chunks;
                    int midChunk = Math.min(chunk + width, chunks);
                    int toChunk = Math.min(chunk + width * 2, chunks);
                    int mid = values.length * midChunk / chunks;
                    int to = values.length * toChunk / chunks;
                    if (mid < to) merge(values, aux, from, mid, to);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execucao interrompida", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Falha no Insertion Sort paralelo", ex.getCause());
        } finally {
            executor.shutdown();
        }
    }

    private static void insertion(int[] values, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            int value = values[i];
            int j = i - 1;
            while (j >= from && values[j] > value) {
                values[j + 1] = values[j--];
            }
            values[j + 1] = value;
        }
    }

    private static void merge(int[] values, int[] aux, int from, int mid, int to) {
        System.arraycopy(values, from, aux, from, to - from);
        int i = from, j = mid, k = from;
        while (i < mid && j < to) values[k++] = aux[i] <= aux[j] ? aux[i++] : aux[j++];
        while (i < mid) values[k++] = aux[i++];
        while (j < to) values[k++] = aux[j++];
    }
}
