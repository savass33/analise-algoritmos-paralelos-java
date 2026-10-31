import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class BenchmarkApp {
    private BenchmarkApp() {}

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length == 0 || "benchmark".equalsIgnoreCase(args[0])) {
            Path output = args.length > 1 ? Path.of(args[1]) : Path.of("resultados", "resultados.csv");
            run(output);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "analyze" -> CsvAnalyzer.main(Arrays.copyOfRange(args, 1, args.length));
            case "charts" -> ChartExporter.main(Arrays.copyOfRange(args, 1, args.length));
            case "dashboard" -> ResultsDashboard.main(Arrays.copyOfRange(args, 1, args.length));
            case "verify" -> VerificationSuite.main(Arrays.copyOfRange(args, 1, args.length));
            default -> printUsage();
        }
    }

    private static void run(Path output) throws IOException {
        List<SortingAlgorithm> algorithms = List.of(
                new QuickSortAlgorithm(),
                new MergeSortAlgorithm(),
                new CountingSortAlgorithm(),
                new InsertionSortAlgorithm());
        int[] sizes = {2_000, 5_000, 10_000};
        int available = Runtime.getRuntime().availableProcessors();
        int[] parallelism = available >= 4 ? new int[]{2, 4} : new int[]{2};
        int samples = 5;

        Files.createDirectories(output.toAbsolutePath().getParent());
        Path systemInfo = output.toAbsolutePath().getParent().resolve("ambiente.txt");
        Files.writeString(systemInfo, environmentText(available), StandardCharsets.UTF_8);

        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("timestamp,algorithm,implementation,threads,size,pattern,sample,seed,duration_ns,duration_ms,valid\n");
            int scenario = 0;
            int total = algorithms.size() * sizes.length * DataFactory.Pattern.values().length;
            for (SortingAlgorithm algorithm : algorithms) {
                for (int size : sizes) {
                    for (DataFactory.Pattern pattern : DataFactory.Pattern.values()) {
                        long baseSeed = 31_337L + size * 17L + pattern.ordinal() * 1_000_003L;
                        warmUp(algorithm, DataFactory.create(size, pattern, baseSeed), parallelism);
                        for (int sample = 1; sample <= samples; sample++) {
                            long seed = baseSeed + sample * 97L;
                            int[] source = DataFactory.create(size, pattern, seed);
                            measure(writer, algorithm, source, pattern, sample, seed, 1, false);
                            for (int threads : parallelism) {
                                measure(writer, algorithm, source, pattern, sample, seed, threads, true);
                            }
                        }
                        scenario++;
                        System.out.printf(Locale.ROOT, "[%d/%d] %s | n=%d | %s%n",
                                scenario, total, algorithm.name(), size, pattern);
                    }
                }
            }
        }
        System.out.println("Resultados gravados em: " + output.toAbsolutePath());
    }

    private static void warmUp(SortingAlgorithm algorithm, int[] source, int[] parallelism) {
        int[] sequential = source.clone();
        algorithm.sortSequential(sequential);
        for (int threads : parallelism) {
            int[] parallel = source.clone();
            algorithm.sortParallel(parallel, threads);
        }
    }

    private static void measure(BufferedWriter writer, SortingAlgorithm algorithm, int[] source,
                                DataFactory.Pattern pattern, int sample, long seed,
                                int threads, boolean parallel) throws IOException {
        int[] values = source.clone();
        long start = System.nanoTime();
        if (parallel) algorithm.sortParallel(values, threads);
        else algorithm.sortSequential(values);
        long duration = System.nanoTime() - start;
        boolean valid = DataFactory.isSorted(values) && DataFactory.sameElements(source, values);
        if (!valid) throw new IllegalStateException("Resultado invalido em " + algorithm.name());
        writer.write(String.format(Locale.ROOT,
                "%s,%s,%s,%d,%d,%s,%d,%d,%d,%.6f,%s%n",
                Instant.now(), algorithm.name(), parallel ? "parallel" : "serial",
                threads, values.length, pattern, sample, seed, duration,
                duration / 1_000_000.0, valid));
    }

    private static String environmentText(int available) {
        List<String> lines = new ArrayList<>();
        lines.add("Data da execucao: " + Instant.now());
        lines.add("Sistema operacional: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        lines.add("Arquitetura: " + System.getProperty("os.arch"));
        lines.add("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        lines.add("Processadores logicos visiveis para a JVM: " + available);
        lines.add("Memoria maxima da JVM (MiB): " + Runtime.getRuntime().maxMemory() / 1024 / 1024);
        lines.add("Configuracao: tamanhos 2000, 5000 e 10000; padroes RANDOM, SORTED, REVERSE e FEW_UNIQUE; 5 amostras; serial, 2 e 4 threads.");
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private static void printUsage() {
        System.out.println("Uso: BenchmarkApp [benchmark|analyze|charts|dashboard|verify] [arquivo/pasta]");
    }
}
