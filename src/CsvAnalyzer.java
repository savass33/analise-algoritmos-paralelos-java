import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CsvAnalyzer {
    private CsvAnalyzer() {}

    public record Result(String algorithm, String implementation, int threads, int size,
                         String pattern, int sample, double durationMs) {}

    private record Key(String algorithm, String implementation, int threads, int size, String pattern) {}

    public static void main(String[] args) throws IOException {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("resultados", "resultados.csv");
        Path output = args.length > 1 ? Path.of(args[1]) : input.toAbsolutePath().getParent().resolve("resumo.csv");
        List<Result> results = read(input);
        writeSummary(results, output);
        System.out.println("Resumo gravado em: " + output.toAbsolutePath());
    }

    public static List<Result> read(Path input) throws IOException {
        List<Result> results = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] c = line.split(",", -1);
                results.add(new Result(c[1], c[2], Integer.parseInt(c[3]), Integer.parseInt(c[4]),
                        c[5], Integer.parseInt(c[6]), Double.parseDouble(c[9])));
            }
        }
        return results;
    }

    private static void writeSummary(List<Result> results, Path output) throws IOException {
        Map<Key, List<Double>> groups = new LinkedHashMap<>();
        for (Result r : results) {
            Key key = new Key(r.algorithm(), r.implementation(), r.threads(), r.size(), r.pattern());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(r.durationMs());
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("algorithm,implementation,threads,size,pattern,samples,mean_ms,median_ms,stddev_ms,min_ms,max_ms,speedup,efficiency\n");
            for (Map.Entry<Key, List<Double>> entry : groups.entrySet()) {
                Key key = entry.getKey();
                List<Double> values = entry.getValue().stream().sorted().toList();
                double mean = mean(values);
                double median = values.get(values.size() / 2);
                double stddev = stddev(values, mean);
                double serialMean = groups.entrySet().stream()
                        .filter(e -> e.getKey().algorithm().equals(key.algorithm())
                                && e.getKey().implementation().equals("serial")
                                && e.getKey().size() == key.size()
                                && e.getKey().pattern().equals(key.pattern()))
                        .mapToDouble(e -> mean(e.getValue())).findFirst().orElse(mean);
                double speedup = serialMean / mean;
                double efficiency = speedup / key.threads();
                writer.write(String.format(Locale.ROOT,
                        "%s,%s,%d,%d,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        key.algorithm(), key.implementation(), key.threads(), key.size(), key.pattern(), values.size(),
                        mean, median, stddev, values.get(0), values.get(values.size() - 1), speedup, efficiency));
            }
        }
    }

    public static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double stddev(List<Double> values, double mean) {
        if (values.size() < 2) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += Math.pow(value - mean, 2);
        return Math.sqrt(sum / (values.size() - 1));
    }
}
