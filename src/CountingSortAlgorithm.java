import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class CountingSortAlgorithm implements SortingAlgorithm {
    @Override public String name() { return "Counting Sort"; }

    @Override public void sortSequential(int[] values) {
        if (values.length < 2) return;
        Bounds bounds = bounds(values);
        int[] counts = new int[bounds.range()];
        for (int value : values) counts[value - bounds.min()]++;
        writeBack(values, counts, bounds.min());
    }

    @Override public void sortParallel(int[] values, int threads) {
        if (values.length < 2) return;
        Bounds bounds = bounds(values);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<int[]>> futures = new ArrayList<>();
            for (int worker = 0; worker < threads; worker++) {
                int from = values.length * worker / threads;
                int to = values.length * (worker + 1) / threads;
                futures.add(executor.submit(() -> {
                    int[] local = new int[bounds.range()];
                    for (int i = from; i < to; i++) local[values[i] - bounds.min()]++;
                    return local;
                }));
            }
            int[] counts = new int[bounds.range()];
            for (Future<int[]> future : futures) {
                int[] local = future.get();
                for (int i = 0; i < counts.length; i++) counts[i] += local[i];
            }
            writeBack(values, counts, bounds.min());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execucao interrompida", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Falha na contagem paralela", ex.getCause());
        } finally {
            executor.shutdown();
        }
    }

    private static Bounds bounds(int[] values) {
        int min = values[0], max = values[0];
        for (int value : values) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        long range = (long) max - min + 1L;
        if (range > 20_000_000L) {
            throw new IllegalArgumentException("Intervalo de valores grande demais para Counting Sort");
        }
        return new Bounds(min, (int) range);
    }

    private static void writeBack(int[] values, int[] counts, int min) {
        int index = 0;
        for (int offset = 0; offset < counts.length; offset++) {
            int count = counts[offset];
            while (count-- > 0) values[index++] = offset + min;
        }
    }

    private record Bounds(int min, int range) {}
}
