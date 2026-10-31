import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChartExporter {
    private static final Color BLUE = new Color(26, 82, 191);
    private static final Color TEAL = new Color(0, 145, 140);
    private static final Color ORANGE = new Color(224, 117, 0);
    private static final Color PURPLE = new Color(120, 72, 180);
    private static final Color GRID = new Color(220, 224, 230);

    private ChartExporter() {}

    public static void main(String[] args) throws IOException {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("resultados", "resultados.csv");
        Path output = args.length > 1 ? Path.of(args[1]) : input.toAbsolutePath().getParent().resolve("graficos");
        Files.createDirectories(output);
        List<CsvAnalyzer.Result> results = CsvAnalyzer.read(input);
        drawTimeBySize(results, output.resolve("tempo_por_tamanho.png"));
        drawSpeedup(results, output.resolve("speedup_por_threads.png"));
        drawPatternEffect(results, output.resolve("efeito_padrao_dados.png"));
        System.out.println("Graficos gravados em: " + output.toAbsolutePath());
    }

    private static void drawTimeBySize(List<CsvAnalyzer.Result> results, Path output) throws IOException {
        List<Series> series = new ArrayList<>();
        String[] algorithms = {"Quick Sort", "Merge Sort", "Counting Sort", "Insertion Sort"};
        Color[] colors = {BLUE, TEAL, ORANGE, PURPLE};
        for (int i = 0; i < algorithms.length; i++) {
            String algorithm = algorithms[i];
            Map<Integer, Double> points = groupedMean(results, algorithm, "parallel", 4, "RANDOM");
            series.add(new Series(algorithm + " (4 threads)", colors[i], points));
        }
        drawLineChart("Tempo medio por tamanho - dados aleatorios", "Tamanho do vetor", "Tempo medio (ms)", series, output);
    }

    private static void drawSpeedup(List<CsvAnalyzer.Result> results, Path output) throws IOException {
        String[] algorithms = {"Quick Sort", "Merge Sort", "Counting Sort", "Insertion Sort"};
        List<Bar> bars = new ArrayList<>();
        for (int a = 0; a < algorithms.length; a++) {
            double serial = meanFiltered(results, algorithms[a], "serial", 1, 10_000, "RANDOM");
            for (int threads : new int[]{2, 4}) {
                double parallel = meanFiltered(results, algorithms[a], "parallel", threads, 10_000, "RANDOM");
                bars.add(new Bar(algorithms[a] + "\n" + threads + " threads", serial / parallel,
                        threads == 2 ? new Color(73, 134, 215) : new Color(0, 145, 140)));
            }
        }
        drawBarChart("Speedup em n=10.000 - dados aleatorios", "Speedup (serial/paralelo)", bars, output, 1.0);
    }

    private static void drawPatternEffect(List<CsvAnalyzer.Result> results, Path output) throws IOException {
        List<Bar> bars = new ArrayList<>();
        String algorithm = "Quick Sort";
        String[] patterns = {"RANDOM", "SORTED", "REVERSE", "FEW_UNIQUE"};
        for (String pattern : patterns) {
            bars.add(new Bar(pattern + "\nserial", meanFiltered(results, algorithm, "serial", 1, 10_000, pattern), BLUE));
            bars.add(new Bar(pattern + "\n4 threads", meanFiltered(results, algorithm, "parallel", 4, 10_000, pattern), TEAL));
        }
        drawBarChart("Quick Sort e natureza dos dados - n=10.000", "Tempo medio (ms)", bars, output, -1.0);
    }

    private static Map<Integer, Double> groupedMean(List<CsvAnalyzer.Result> results, String algorithm,
                                                    String implementation, int threads, String pattern) {
        Map<Integer, List<Double>> grouped = new LinkedHashMap<>();
        results.stream().filter(r -> r.algorithm().equals(algorithm)
                        && r.implementation().equals(implementation)
                        && r.threads() == threads && r.pattern().equals(pattern))
                .sorted(Comparator.comparingInt(CsvAnalyzer.Result::size))
                .forEach(r -> grouped.computeIfAbsent(r.size(), ignored -> new ArrayList<>()).add(r.durationMs()));
        Map<Integer, Double> means = new LinkedHashMap<>();
        grouped.forEach((size, values) -> means.put(size, CsvAnalyzer.mean(values)));
        return means;
    }

    private static double meanFiltered(List<CsvAnalyzer.Result> results, String algorithm,
                                       String implementation, int threads, int size, String pattern) {
        List<Double> values = results.stream().filter(r -> r.algorithm().equals(algorithm)
                        && r.implementation().equals(implementation) && r.threads() == threads
                        && r.size() == size && r.pattern().equals(pattern))
                .map(CsvAnalyzer.Result::durationMs).toList();
        return CsvAnalyzer.mean(values);
    }

    private static void drawLineChart(String title, String xLabel, String yLabel,
                                      List<Series> series, Path output) throws IOException {
        int width = 1_600, height = 900, left = 145, right = 370, top = 100, bottom = 140;
        BufferedImage image = canvas(width, height);
        Graphics2D g = image.createGraphics();
        setup(g);
        double maxY = series.stream().flatMap(s -> s.points().values().stream()).mapToDouble(Double::doubleValue).max().orElse(1) * 1.12;
        int[] sizes = series.get(0).points().keySet().stream().mapToInt(Integer::intValue).toArray();
        axes(g, title, xLabel, yLabel, width, height, left, right, top, bottom, maxY);
        for (int tick = 0; tick <= 5; tick++) {
            double value = maxY * tick / 5.0;
            int y = height - bottom - (int) ((height - top - bottom) * tick / 5.0);
            gridLine(g, left, width - right, y);
            g.setColor(Color.DARK_GRAY); g.setFont(new Font("Arial", Font.PLAIN, 22));
            g.drawString(String.format(Locale.ROOT, "%.2f", value), 35, y + 8);
        }
        for (int i = 0; i < sizes.length; i++) {
            int x = left + i * (width - left - right) / Math.max(1, sizes.length - 1);
            g.setColor(Color.DARK_GRAY); g.drawString(String.format(Locale.ROOT, "%,d", sizes[i]), x - 35, height - bottom + 42);
        }
        int legendY = 135;
        int legendX = width - right + 45;
        for (Series s : series) {
            g.setColor(s.color()); g.fillRect(legendX, legendY - 15, 26, 8);
            g.setColor(Color.DARK_GRAY); g.drawString(s.name(), legendX + 36, legendY);
            legendY += 42;
        }
        for (Series s : series) {
            g.setColor(s.color()); g.setStroke(new BasicStroke(4f));
            int previousX = -1, previousY = -1, i = 0;
            for (Map.Entry<Integer, Double> point : s.points().entrySet()) {
                int x = left + i * (width - left - right) / Math.max(1, sizes.length - 1);
                int y = height - bottom - (int) ((height - top - bottom) * point.getValue() / maxY);
                if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
                g.fillOval(x - 7, y - 7, 14, 14);
                previousX = x; previousY = y; i++;
            }
        }
        g.dispose(); ImageIO.write(image, "png", output.toFile());
    }

    private static void drawBarChart(String title, String yLabel, List<Bar> bars, Path output, double reference) throws IOException {
        int width = 1_600, height = 900, left = 150, right = 65, top = 110, bottom = 170;
        BufferedImage image = canvas(width, height);
        Graphics2D g = image.createGraphics(); setup(g);
        double maxY = Math.max(reference, bars.stream().mapToDouble(Bar::value).max().orElse(1)) * 1.18;
        axes(g, title, "", yLabel, width, height, left, right, top, bottom, maxY);
        for (int tick = 0; tick <= 5; tick++) {
            double value = maxY * tick / 5.0;
            int y = height - bottom - (int) ((height - top - bottom) * tick / 5.0);
            gridLine(g, left, width - right, y);
            g.setColor(Color.DARK_GRAY); g.drawString(String.format(Locale.ROOT, "%.2f", value), 40, y + 8);
        }
        if (reference > 0) {
            int y = height - bottom - (int) ((height - top - bottom) * reference / maxY);
            g.setColor(new Color(180, 50, 50)); g.setStroke(new BasicStroke(3f)); g.drawLine(left, y, width - right, y);
        }
        int plotWidth = width - left - right;
        int slot = plotWidth / bars.size();
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            int barWidth = (int) (slot * 0.68);
            int x = left + i * slot + (slot - barWidth) / 2;
            int barHeight = (int) ((height - top - bottom) * bar.value() / maxY);
            int y = height - bottom - barHeight;
            g.setColor(bar.color()); g.fillRoundRect(x, y, barWidth, barHeight, 12, 12);
            g.setColor(Color.DARK_GRAY); g.setFont(new Font("Arial", Font.BOLD, 19));
            g.drawString(String.format(Locale.ROOT, "%.2f", bar.value()), x + 8, y - 10);
            g.setFont(new Font("Arial", Font.PLAIN, 17));
            String[] lines = bar.label().split("\\n");
            for (int line = 0; line < lines.length; line++) g.drawString(lines[line], x, height - bottom + 32 + line * 23);
        }
        g.dispose(); ImageIO.write(image, "png", output.toFile());
    }

    private static BufferedImage canvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, width, height); g.dispose(); return image;
    }

    private static void setup(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("Arial", Font.PLAIN, 22));
    }

    private static void axes(Graphics2D g, String title, String xLabel, String yLabel, int width, int height,
                             int left, int right, int top, int bottom, double maxY) {
        g.setColor(new Color(32, 38, 48)); g.setFont(new Font("Arial", Font.BOLD, 31)); g.drawString(title, left, 55);
        g.setStroke(new BasicStroke(2f)); g.drawLine(left, top, left, height - bottom); g.drawLine(left, height - bottom, width - right, height - bottom);
        g.setFont(new Font("Arial", Font.PLAIN, 22));
        if (!xLabel.isEmpty()) g.drawString(xLabel, width / 2 - 90, height - 35);
        g.rotate(-Math.PI / 2); g.drawString(yLabel, -height / 2 - 80, 35); g.rotate(Math.PI / 2);
    }

    private static void gridLine(Graphics2D g, int from, int to, int y) {
        g.setColor(GRID); g.setStroke(new BasicStroke(1f)); g.drawLine(from, y, to, y);
    }

    private record Series(String name, Color color, Map<Integer, Double> points) {}
    private record Bar(String label, double value, Color color) {}
}
